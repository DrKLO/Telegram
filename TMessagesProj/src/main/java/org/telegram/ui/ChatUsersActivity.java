/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
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
import android.widget.Toast;

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
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class ChatUsersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listViewAdapter;
    private EmptyTextProgressView emptyView;
    private RecyclerListView listView;
    private SearchAdapter searchListViewAdapter;
    private ActionBarMenuItem searchItem;
    private ActionBarMenuItem doneItem;

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private boolean isChannel;

    private String initialBannedRights;
    private TLRPC.TL_chatBannedRights defaultBannedRights = new TLRPC.TL_chatBannedRights();
    private ArrayList<TLObject> participants = new ArrayList<>();
    private SparseArray<TLObject> participantsMap = new SparseArray<>();
    private int chatId;
    private int type;
    private boolean loadingUsers;
    private boolean firstLoaded;

    private int permissionsSectionRow;
    private int sendMessagesRow;
    private int sendMediaRow;
    private int sendStickersRow;
    private int sendPollsRow;
    private int embedLinksRow;
    private int changeInfoRow;
    private int addUsersRow;
    private int pinMessagesRow;

    private int recentActionsRow;
    private int addNewRow;
    private int addNew2Row;
    private int removedUsersRow;
    private int addNewSectionRow;
    private int restricted1SectionRow;
    private int participantsStartRow;
    private int participantsEndRow;
    private int participantsDividerRow;
    private int participantsDivider2Row;

    private int participantsInfoRow;
    private int blockedEmptyRow;
    private int rowCount;
    private int selectType;

    private ChatUsersActivityDelegate delegate;

    private boolean needOpenSearch;

    private boolean searchWas;
    private boolean searching;

    private final static int search_button = 0;
    private final static int done_button = 1;

    public final static int TYPE_BANNED = 0;
    public final static int TYPE_ADMIN = 1;
    public final static int TYPE_USERS = 2;
    public final static int TYPE_KICKED = 3;

    public interface ChatUsersActivityDelegate {
        void didAddParticipantToList(int uid, TLObject participant);
    }

    public ChatUsersActivity(Bundle args) {
        super(args);
        chatId = arguments.getInt("chat_id");
        type = arguments.getInt("type");
        needOpenSearch = arguments.getBoolean("open_search");
        selectType = arguments.getInt("selectType");
        currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
        if (currentChat != null && currentChat.default_banned_rights != null) {
            defaultBannedRights.view_messages = currentChat.default_banned_rights.view_messages;
            defaultBannedRights.send_stickers = currentChat.default_banned_rights.send_stickers;
            defaultBannedRights.send_media = currentChat.default_banned_rights.send_media;
            defaultBannedRights.embed_links = currentChat.default_banned_rights.embed_links;
            defaultBannedRights.send_messages = currentChat.default_banned_rights.send_messages;
            defaultBannedRights.send_games = currentChat.default_banned_rights.send_games;
            defaultBannedRights.send_inline = currentChat.default_banned_rights.send_inline;
            defaultBannedRights.send_gifs = currentChat.default_banned_rights.send_gifs;
            defaultBannedRights.pin_messages = currentChat.default_banned_rights.pin_messages;
            defaultBannedRights.send_polls = currentChat.default_banned_rights.send_polls;
            defaultBannedRights.invite_users = currentChat.default_banned_rights.invite_users;
            defaultBannedRights.change_info = currentChat.default_banned_rights.change_info;
        }
        initialBannedRights = ChatObject.getBannedRightsString(defaultBannedRights);
        isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
    }

    private void updateRows() {
        currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
        if (currentChat == null) {
            return;
        }
        recentActionsRow = -1;
        addNewRow = -1;
        addNew2Row = -1;
        addNewSectionRow = -1;
        restricted1SectionRow = -1;
        participantsStartRow = -1;
        participantsDividerRow = -1;
        participantsDivider2Row = -1;
        participantsEndRow = -1;
        participantsInfoRow = -1;
        blockedEmptyRow = -1;
        permissionsSectionRow = -1;
        sendMessagesRow = -1;
        sendMediaRow = -1;
        sendStickersRow = -1;
        sendPollsRow = -1;
        embedLinksRow = -1;
        addUsersRow = -1;
        pinMessagesRow = -1;
        changeInfoRow = -1;
        removedUsersRow = -1;

        rowCount = 0;
        if (type == TYPE_KICKED) {
            permissionsSectionRow = rowCount++;
            sendMessagesRow = rowCount++;
            sendMediaRow = rowCount++;
            sendStickersRow = rowCount++;
            sendPollsRow = rowCount++;
            embedLinksRow = rowCount++;
            addUsersRow = rowCount++;
            pinMessagesRow = rowCount++;
            changeInfoRow = rowCount++;
            if (ChatObject.isChannel(currentChat)) {
                participantsDivider2Row = rowCount++;
                removedUsersRow = rowCount++;
            }
            participantsDividerRow = rowCount++;
            if (ChatObject.canBlockUsers(currentChat)) {
                addNewRow = rowCount++;
            }
            if (!participants.isEmpty()) {
                participantsStartRow = rowCount;
                rowCount += participants.size();
                participantsEndRow = rowCount;
            }
            if (addNewRow != -1 || participantsStartRow != -1) {
                addNewSectionRow = rowCount++;
            }
        } else if (type == TYPE_BANNED) {
            if (ChatObject.canBlockUsers(currentChat)) {
                addNewRow = rowCount++;
                if (!participants.isEmpty()) {
                    participantsInfoRow = rowCount++;
                }
            }
            if (!participants.isEmpty()) {
                restricted1SectionRow = rowCount++;
                participantsStartRow = rowCount;
                rowCount += participants.size();
                participantsEndRow = rowCount;
            }
            if (participantsStartRow != -1) {
                if (participantsInfoRow == -1) {
                    participantsInfoRow = rowCount++;
                } else {
                    addNewSectionRow = rowCount++;
                }
            } else {
                if (searchItem != null) {
                    searchItem.setVisibility(View.INVISIBLE);
                }
                blockedEmptyRow = rowCount++;
            }
        } else if (type == TYPE_ADMIN) {
            if (ChatObject.isChannel(currentChat) && currentChat.megagroup && (info == null || info.participants_count <= 200)) {
                recentActionsRow = rowCount++;
                addNewSectionRow = rowCount++;
            }
            if (ChatObject.canAddAdmins(currentChat)) {
                addNewRow = rowCount++;
            }
            if (!participants.isEmpty()) {
                participantsStartRow = rowCount;
                rowCount += participants.size();
                participantsEndRow = rowCount;
            }
            participantsInfoRow = rowCount++;
        } else if (type == TYPE_USERS) {
            if (selectType == 0 && ChatObject.canAddUsers(currentChat)) {
                if (ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE) && (!ChatObject.isChannel(currentChat) || currentChat.megagroup || TextUtils.isEmpty(currentChat.username))) {
                    addNew2Row = rowCount++;
                    addNewSectionRow = rowCount++;
                }
                addNewRow = rowCount++;
            }
            if (!participants.isEmpty()) {
                participantsStartRow = rowCount;
                rowCount += participants.size();
                participantsEndRow = rowCount;
            }
            if (rowCount != 0) {
                participantsInfoRow = rowCount++;
            }
        }
        if (searchItem != null && !actionBar.isSearchFieldVisible()) {
            searchItem.setVisibility(selectType == 0 && participants.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        loadChatParticipants(0, 200);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (type == TYPE_KICKED) {
            actionBar.setTitle(LocaleController.getString("ChannelPermissions", R.string.ChannelPermissions));
        } else if (type == TYPE_BANNED) {
            actionBar.setTitle(LocaleController.getString("ChannelBlacklist", R.string.ChannelBlacklist));
        } else if (type == TYPE_ADMIN) {
            actionBar.setTitle(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators));
        } else if (type == TYPE_USERS) {
            if (selectType == 0) {
                if (isChannel) {
                    actionBar.setTitle(LocaleController.getString("ChannelSubscribers", R.string.ChannelSubscribers));
                } else {
                    actionBar.setTitle(LocaleController.getString("ChannelMembers", R.string.ChannelMembers));
                }
            } else {
                if (selectType == 1) {
                    actionBar.setTitle(LocaleController.getString("ChannelAddAdmin", R.string.ChannelAddAdmin));
                } else if (selectType == 2) {
                    actionBar.setTitle(LocaleController.getString("ChannelBlockUser", R.string.ChannelBlockUser));
                } else if (selectType == 3) {
                    actionBar.setTitle(LocaleController.getString("ChannelAddException", R.string.ChannelAddException));
                }
            }
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard()) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    processDone();
                }
            }
        });
        if (selectType != 0 || type == TYPE_USERS || type == TYPE_BANNED || type == TYPE_KICKED) {
            searchListViewAdapter = new SearchAdapter(context);
            ActionBarMenu menu = actionBar.createMenu();
            searchItem = menu.addItem(search_button, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    searching = true;
                    emptyView.setShowAtCenter(true);
                    if (doneItem != null) {
                        doneItem.setVisibility(View.GONE);
                    }
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
                    if (doneItem != null) {
                        doneItem.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onTextChanged(EditText editText) {
                    if (searchListViewAdapter == null) {
                        return;
                    }
                    String text = editText.getText().toString();
                    if (text.length() != 0) {
                        searchWas = true;
                        if (listView != null && listView.getAdapter() != searchListViewAdapter) {
                            listView.setAdapter(searchListViewAdapter);
                            searchListViewAdapter.notifyDataSetChanged();
                            listView.setFastScrollVisible(false);
                            listView.setVerticalScrollBarEnabled(true);
                        }
                    }
                    searchListViewAdapter.searchDialogs(text);
                }
            });
            if (type == TYPE_KICKED) {
                searchItem.setSearchFieldHint(LocaleController.getString("ChannelSearchException", R.string.ChannelSearchException));
            } else {
                searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
            }
            searchItem.setVisibility(selectType == 0 && participants.isEmpty() ? View.GONE : View.VISIBLE);

            if (type == TYPE_KICKED) {
                doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
            }
        }

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        emptyView = new EmptyTextProgressView(context);
        if (type == TYPE_BANNED || type == TYPE_USERS || type == TYPE_KICKED) {
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
            boolean listAdapter = listView.getAdapter() == listViewAdapter;
            if (listAdapter) {
                if (position == addNewRow) {
                    if (type == TYPE_BANNED || type == TYPE_KICKED) {
                        Bundle bundle = new Bundle();
                        bundle.putInt("chat_id", chatId);
                        bundle.putInt("type", ChatUsersActivity.TYPE_USERS);
                        bundle.putInt("selectType", type == TYPE_BANNED ? 2 : 3);
                        ChatUsersActivity fragment = new ChatUsersActivity(bundle);
                        fragment.setInfo(info);
                        presentFragment(fragment);
                    } else if (type == TYPE_ADMIN) {
                        Bundle bundle = new Bundle();
                        bundle.putInt("chat_id", chatId);
                        bundle.putInt("type", ChatUsersActivity.TYPE_USERS);
                        bundle.putInt("selectType", 1);
                        ChatUsersActivity fragment = new ChatUsersActivity(bundle);
                        fragment.setDelegate((uid, participant) -> {
                            if (participant != null && participantsMap.get(uid) == null) {
                                participants.add(participant);
                                Collections.sort(participants, (lhs, rhs) -> {
                                    int type1 = getChannelAdminParticipantType(lhs);
                                    int type2 = getChannelAdminParticipantType(rhs);
                                    if (type1 > type2) {
                                        return 1;
                                    } else if (type1 < type2) {
                                        return -1;
                                    }
                                    return 0;
                                });
                                updateRows();
                                listViewAdapter.notifyDataSetChanged();
                            }
                        });
                        fragment.setInfo(info);
                        presentFragment(fragment);
                    } else if (type == TYPE_USERS) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlyUsers", true);
                        args.putBoolean("destroyAfterSelect", true);
                        args.putBoolean("returnAsResult", true);
                        args.putBoolean("needForwardCount", false);
                        if (isChannel) {
                            args.putString("selectAlertString", LocaleController.getString("ChannelAddTo", R.string.ChannelAddTo));
                            args.putInt("channelId", currentChat.id);
                        } else {
                            if (!ChatObject.isChannel(currentChat)) {
                                args.putInt("chat_id", currentChat.id);
                            }
                            args.putString("selectAlertString", LocaleController.getString("AddToTheGroup", R.string.AddToTheGroup));
                        }
                        ContactsActivity fragment = new ContactsActivity(args);
                        fragment.setDelegate((user, param, activity) -> {
                            if (user != null && user.bot && isChannel) {
                                openRightsEdit(user.id, null, null, null, true, ChatRightsEditActivity.TYPE_ADMIN, true);
                            } else {
                                MessagesController.getInstance(currentAccount).addUserToChat(chatId, user, null, param != null ? Utilities.parseInt(param) : 0, null, ChatUsersActivity.this, null);
                            }
                        });
                        presentFragment(fragment);
                    }
                    return;
                } else if (position == recentActionsRow) {
                    presentFragment(new ChannelAdminLogActivity(currentChat));
                    return;
                } else if (position == removedUsersRow) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", chatId);
                    args.putInt("type", ChatUsersActivity.TYPE_BANNED);
                    ChatUsersActivity fragment = new ChatUsersActivity(args);
                    fragment.setInfo(info);
                    presentFragment(fragment);
                    return;
                } else if (position == addNew2Row) {
                    presentFragment(new GroupInviteActivity(chatId));
                    return;
                } else if (position > permissionsSectionRow && position <= changeInfoRow) {
                    TextCheckCell2 checkCell = (TextCheckCell2) view;
                    if (!checkCell.isEnabled()) {
                        return;
                    }
                    if (checkCell.hasIcon()) {
                        if (!TextUtils.isEmpty(currentChat.username) && (position == pinMessagesRow || position == changeInfoRow)) {
                            Toast.makeText(getParentActivity(), LocaleController.getString("EditCantEditPermissionsPublic", R.string.EditCantEditPermissionsPublic), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getParentActivity(), LocaleController.getString("EditCantEditPermissions", R.string.EditCantEditPermissions), Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    checkCell.setChecked(!checkCell.isChecked());
                    if (position == changeInfoRow) {
                        defaultBannedRights.change_info = !defaultBannedRights.change_info;
                    } else if (position == addUsersRow) {
                        defaultBannedRights.invite_users = !defaultBannedRights.invite_users;
                    } else if (position == pinMessagesRow) {
                        defaultBannedRights.pin_messages = !defaultBannedRights.pin_messages;
                    } else {
                        boolean disabled = !checkCell.isChecked();
                        if (position == sendMessagesRow) {
                            defaultBannedRights.send_messages = !defaultBannedRights.send_messages;
                        } else if (position == sendMediaRow) {
                            defaultBannedRights.send_media = !defaultBannedRights.send_media;
                        } else if (position == sendStickersRow) {
                            defaultBannedRights.send_stickers = defaultBannedRights.send_games = defaultBannedRights.send_gifs = defaultBannedRights.send_inline = !defaultBannedRights.send_stickers;
                        } else if (position == embedLinksRow) {
                            defaultBannedRights.embed_links = !defaultBannedRights.embed_links;
                        } else if (position == sendPollsRow) {
                            defaultBannedRights.send_polls = !defaultBannedRights.send_polls;
                        }
                        if (disabled) {
                            if (defaultBannedRights.view_messages && !defaultBannedRights.send_messages) {
                                defaultBannedRights.send_messages = true;
                                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendMessagesRow);
                                if (holder != null) {
                                    ((TextCheckCell2) holder.itemView).setChecked(false);
                                }
                            }
                            if ((defaultBannedRights.view_messages || defaultBannedRights.send_messages) && !defaultBannedRights.send_media) {
                                defaultBannedRights.send_media = true;
                                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendMediaRow);
                                if (holder != null) {
                                    ((TextCheckCell2) holder.itemView).setChecked(false);
                                }
                            }
                            if ((defaultBannedRights.view_messages || defaultBannedRights.send_messages) && !defaultBannedRights.send_polls) {
                                defaultBannedRights.send_polls = true;
                                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendPollsRow);
                                if (holder != null) {
                                    ((TextCheckCell2) holder.itemView).setChecked(false);
                                }
                            }
                            if ((defaultBannedRights.view_messages || defaultBannedRights.send_messages) && !defaultBannedRights.send_stickers) {
                                defaultBannedRights.send_stickers = defaultBannedRights.send_games = defaultBannedRights.send_gifs = defaultBannedRights.send_inline = true;
                                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendStickersRow);
                                if (holder != null) {
                                    ((TextCheckCell2) holder.itemView).setChecked(false);
                                }
                            }
                            if ((defaultBannedRights.view_messages || defaultBannedRights.send_messages) && !defaultBannedRights.embed_links) {
                                defaultBannedRights.embed_links = true;
                                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(embedLinksRow);
                                if (holder != null) {
                                    ((TextCheckCell2) holder.itemView).setChecked(false);
                                }
                            }
                        } else {
                            if ((!defaultBannedRights.embed_links || !defaultBannedRights.send_inline || !defaultBannedRights.send_media || !defaultBannedRights.send_polls) && defaultBannedRights.send_messages) {
                                defaultBannedRights.send_messages = false;
                                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendMessagesRow);
                                if (holder != null) {
                                    ((TextCheckCell2) holder.itemView).setChecked(true);
                                }
                            }
                        }
                    }
                    return;
                }
            }

            TLRPC.TL_chatBannedRights bannedRights = null;
            TLRPC.TL_chatAdminRights adminRights = null;
            final TLObject participant;
            int user_id = 0;
            int promoted_by = 0;
            boolean canEditAdmin = false;
            if (listAdapter) {
                participant = listViewAdapter.getItem(position);
                if (participant instanceof TLRPC.ChannelParticipant) {
                    TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                    user_id = channelParticipant.user_id;
                    bannedRights = channelParticipant.banned_rights;
                    adminRights = channelParticipant.admin_rights;
                    canEditAdmin = !(channelParticipant instanceof TLRPC.TL_channelParticipantAdmin || channelParticipant instanceof TLRPC.TL_channelParticipantCreator) || channelParticipant.can_edit;
                    if (participant instanceof TLRPC.TL_channelParticipantCreator) {
                        adminRights = new TLRPC.TL_chatAdminRights();
                        adminRights.change_info = adminRights.post_messages = adminRights.edit_messages =
                        adminRights.delete_messages = adminRights.ban_users = adminRights.invite_users =
                        adminRights.pin_messages = adminRights.add_admins = true;
                    }
                } else if (participant instanceof TLRPC.ChatParticipant) {
                    TLRPC.ChatParticipant chatParticipant = (TLRPC.ChatParticipant) participant;
                    user_id = chatParticipant.user_id;
                    canEditAdmin = currentChat.creator;
                    if (participant instanceof TLRPC.TL_chatParticipantCreator) {
                        adminRights = new TLRPC.TL_chatAdminRights();
                        adminRights.change_info = adminRights.post_messages = adminRights.edit_messages =
                        adminRights.delete_messages = adminRights.ban_users = adminRights.invite_users =
                        adminRights.pin_messages = adminRights.add_admins = true;
                    }
                }
            } else {
                TLObject object = searchListViewAdapter.getItem(position);
                if (object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    MessagesController.getInstance(currentAccount).putUser(user, false);
                    participant = participantsMap.get(user_id = user.id);
                } else if (object instanceof TLRPC.ChannelParticipant || object instanceof TLRPC.ChatParticipant) {
                    participant = object;
                } else {
                    participant = null;
                }
                if (participant instanceof TLRPC.ChannelParticipant) {
                    if (participant instanceof TLRPC.TL_channelParticipantCreator) {
                        return;
                    }
                    TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                    user_id = channelParticipant.user_id;
                    canEditAdmin = !(channelParticipant instanceof TLRPC.TL_channelParticipantAdmin || channelParticipant instanceof TLRPC.TL_channelParticipantCreator) || channelParticipant.can_edit;
                    bannedRights = channelParticipant.banned_rights;
                    adminRights = channelParticipant.admin_rights;
                } else if (participant instanceof TLRPC.ChatParticipant) {
                    if (participant instanceof TLRPC.TL_chatParticipantCreator) {
                        return;
                    }
                    TLRPC.ChatParticipant chatParticipant = (TLRPC.ChatParticipant) participant;
                    user_id = chatParticipant.user_id;
                    canEditAdmin = currentChat.creator;
                    bannedRights = null;
                    adminRights = null;
                } else if (participant == null) {
                    canEditAdmin = true;
                }
            }
            if (user_id != 0) {
                if (selectType != 0) {
                    if (selectType == 3 || selectType == 1) {
                        if (canEditAdmin && (participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_chatParticipantAdmin)) {
                            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                            final TLRPC.TL_chatBannedRights br = bannedRights;
                            final TLRPC.TL_chatAdminRights ar = adminRights;
                            final boolean canEdit = canEditAdmin;
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setMessage(LocaleController.formatString("AdminWillBeRemoved", R.string.AdminWillBeRemoved, ContactsController.formatName(user.first_name, user.last_name)));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> openRightsEdit(user.id, participant, ar, br, canEdit, selectType == 1 ? 0 : 1, false));
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showDialog(builder.create());
                        } else {
                            openRightsEdit(user_id, participant, adminRights, bannedRights, canEditAdmin, selectType == 1 ? 0 : 1, false);
                        }
                    } else {
                        removeUser(user_id);
                    }
                } else {
                    boolean canEdit = false;
                    if (type == TYPE_ADMIN) {
                        canEdit = user_id != UserConfig.getInstance(currentAccount).getClientUserId() && (currentChat.creator || canEditAdmin);
                    } else if (type == TYPE_BANNED || type == TYPE_KICKED) {
                        canEdit = ChatObject.canBlockUsers(currentChat);
                    }
                    if (type == TYPE_BANNED || type != TYPE_ADMIN && isChannel || type == TYPE_USERS && selectType == 0) {
                        Bundle args = new Bundle();
                        args.putInt("user_id", user_id);
                        presentFragment(new ProfileActivity(args));
                    } else {
                        if (bannedRights == null) {
                            bannedRights = new TLRPC.TL_chatBannedRights();
                            bannedRights.view_messages = true;
                            bannedRights.send_stickers = true;
                            bannedRights.send_media = true;
                            bannedRights.embed_links = true;
                            bannedRights.send_messages = true;
                            bannedRights.send_games = true;
                            bannedRights.send_inline = true;
                            bannedRights.send_gifs = true;
                            bannedRights.pin_messages = true;
                            bannedRights.send_polls = true;
                            bannedRights.invite_users = true;
                            bannedRights.change_info = true;
                        }
                        ChatRightsEditActivity fragment = new ChatRightsEditActivity(user_id, chatId, adminRights, defaultBannedRights, bannedRights, type == TYPE_ADMIN ? ChatRightsEditActivity.TYPE_ADMIN : ChatRightsEditActivity.TYPE_BANNED, canEdit, participant == null);
                        fragment.setDelegate((rights, rightsAdmin, rightsBanned) -> {
                            if (participant instanceof TLRPC.ChannelParticipant) {
                                TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                                channelParticipant.admin_rights = rightsAdmin;
                                channelParticipant.banned_rights = rightsBanned;
                                TLObject p = participantsMap.get(channelParticipant.user_id);
                                if (p instanceof TLRPC.ChannelParticipant) {
                                    channelParticipant = (TLRPC.ChannelParticipant) p;
                                    channelParticipant.admin_rights = rightsAdmin;
                                    channelParticipant.banned_rights = rightsBanned;
                                }
                            }
                        });
                        presentFragment(fragment);
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

    private void openRightsEdit2(int userId, int date, TLObject participant, TLRPC.TL_chatAdminRights adminRights, TLRPC.TL_chatBannedRights bannedRights, boolean canEditAdmin, int type, boolean removeFragment) {
        ChatRightsEditActivity fragment = new ChatRightsEditActivity(userId, chatId, adminRights, defaultBannedRights, bannedRights, type, true, false);
        fragment.setDelegate((rights, rightsAdmin, rightsBanned) -> {
            if (type == 0) {
                for (int a = 0; a < participants.size(); a++) {
                    TLObject p = participants.get(a);
                    if (p instanceof TLRPC.ChannelParticipant) {
                        TLRPC.ChannelParticipant p2 = (TLRPC.ChannelParticipant) p;
                        if (p2.user_id == userId) {
                            TLRPC.ChannelParticipant newPart;
                            if (rights == 1) {
                                newPart = new TLRPC.TL_channelParticipantAdmin();
                            } else {
                                newPart = new TLRPC.TL_channelParticipant();
                            }
                            newPart.admin_rights = rightsAdmin;
                            newPart.banned_rights = rightsBanned;
                            newPart.inviter_id = UserConfig.getInstance(currentAccount).getClientUserId();
                            newPart.user_id = userId;
                            newPart.date = date;
                            participants.set(a, newPart);
                            break;
                        }
                    } else if (p instanceof TLRPC.ChatParticipant) {
                        TLRPC.ChatParticipant chatParticipant = (TLRPC.ChatParticipant) p;
                        TLRPC.ChatParticipant newParticipant;
                        if (rights == 1) {
                            newParticipant = new TLRPC.TL_chatParticipantAdmin();
                        } else {
                            newParticipant = new TLRPC.TL_chatParticipant();
                        }
                        newParticipant.user_id = chatParticipant.user_id;
                        newParticipant.date = chatParticipant.date;
                        newParticipant.inviter_id = chatParticipant.inviter_id;
                        int index = info.participants.participants.indexOf(chatParticipant);
                        if (index >= 0) {
                            info.participants.participants.set(index, newParticipant);
                        }
                        loadChatParticipants(0, 200);
                    }
                }
            } else if (type == 1) {
                if (rights == 0) {
                    for (int a = 0; a < participants.size(); a++) {
                        TLObject p = participants.get(a);
                        if (p instanceof TLRPC.ChannelParticipant) {
                            TLRPC.ChannelParticipant p2 = (TLRPC.ChannelParticipant) p;
                            if (p2.user_id == userId) {
                                participants.remove(a);
                                updateRows();
                                listViewAdapter.notifyDataSetChanged();
                                break;
                            }
                        }
                    }
                }
            }
        });
        presentFragment(fragment);
    }

    private void openRightsEdit(int user_id, TLObject participant, TLRPC.TL_chatAdminRights adminRights, TLRPC.TL_chatBannedRights bannedRights, boolean canEditAdmin, int type, boolean removeFragment) {
        ChatRightsEditActivity fragment = new ChatRightsEditActivity(user_id, chatId, adminRights, defaultBannedRights, bannedRights, type, canEditAdmin, participant == null);
        fragment.setDelegate((rights, rightsAdmin, rightsBanned) -> {
            if (participant instanceof TLRPC.ChannelParticipant) {
                TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                channelParticipant.admin_rights = rightsAdmin;
                channelParticipant.banned_rights = rightsBanned;
                TLObject p = participantsMap.get(channelParticipant.user_id);
                if (p instanceof TLRPC.ChannelParticipant) {
                    channelParticipant = (TLRPC.ChannelParticipant) p;
                    channelParticipant.admin_rights = rightsAdmin;
                    channelParticipant.banned_rights = rightsBanned;
                    channelParticipant.promoted_by = UserConfig.getInstance(currentAccount).getClientUserId();
                }
                if (delegate != null) {
                    delegate.didAddParticipantToList(user_id, p);
                }
            }
            removeSelfFromStack();
        });
        presentFragment(fragment, removeFragment);
    }

    private void removeUser(int userId) {
        if (!ChatObject.isChannel(currentChat)) {
            return;
        }
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
        MessagesController.getInstance(currentAccount).deleteUserFromChat(chatId, user, null);
        finishFragment();
    }

    private boolean createMenuForParticipant(final TLObject participant, boolean resultOnly) {
        if (participant == null || selectType != 0) {
            return false;
        }
        int userId;
        boolean canEdit;
        int date;
        TLRPC.TL_chatBannedRights bannedRights;
        TLRPC.TL_chatAdminRights adminRights;
        if (participant instanceof TLRPC.ChannelParticipant) {
            TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
            userId = channelParticipant.user_id;
            canEdit = channelParticipant.can_edit;
            bannedRights = channelParticipant.banned_rights;
            adminRights = channelParticipant.admin_rights;
            date = channelParticipant.date;
        } else if (participant instanceof TLRPC.ChatParticipant) {
            TLRPC.ChatParticipant chatParticipant = (TLRPC.ChatParticipant) participant;
            userId = chatParticipant.user_id;
            date = chatParticipant.date;
            canEdit = ChatObject.canAddAdmins(currentChat);
            bannedRights = null;
            adminRights = null;
        } else {
            userId = 0;
            canEdit = false;
            bannedRights = null;
            adminRights = null;
            date = 0;
        }
        if (userId == 0 || userId == UserConfig.getInstance(currentAccount).getClientUserId()) {
            return false;
        }
        if (type == TYPE_USERS) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
            boolean allowSetAdmin = ChatObject.canAddAdmins(currentChat) && (participant instanceof TLRPC.TL_channelParticipant || participant instanceof TLRPC.TL_channelParticipantBanned || participant instanceof TLRPC.TL_chatParticipant || canEdit);
            boolean canEditAdmin = !(participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_channelParticipantCreator || participant instanceof TLRPC.TL_chatParticipantCreator || participant instanceof TLRPC.TL_chatParticipantAdmin) || canEdit;
            boolean editingAdmin = participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_chatParticipantAdmin;

            final ArrayList<String> items;
            final ArrayList<Integer> actions;
            final ArrayList<Integer> icons;
            if (!resultOnly) {
                items = new ArrayList<>();
                actions = new ArrayList<>();
                icons = new ArrayList<>();
            } else {
                items = null;
                actions = null;
                icons = null;
            }

            if (allowSetAdmin) {
                if (resultOnly) {
                    return true;
                }
                items.add(editingAdmin ? LocaleController.getString("EditAdminRights", R.string.EditAdminRights) : LocaleController.getString("SetAsAdmin", R.string.SetAsAdmin));
                icons.add(R.drawable.actions_addadmin);
                actions.add(0);
            }
            boolean hasRemove = false;
            if (ChatObject.canBlockUsers(currentChat) && canEditAdmin) {
                if (resultOnly) {
                    return true;
                }
                if (!isChannel) {
                    if (ChatObject.isChannel(currentChat)) {
                        items.add(LocaleController.getString("ChangePermissions", R.string.ChangePermissions));
                        icons.add(R.drawable.actions_permissions);
                        actions.add(1);
                    }
                    items.add(LocaleController.getString("KickFromGroup", R.string.KickFromGroup));
                    icons.add(R.drawable.actions_remove_user);
                    actions.add(2);
                } else {
                    items.add(LocaleController.getString("ChannelRemoveUser", R.string.ChannelRemoveUser));
                    icons.add(R.drawable.actions_remove_user);
                    actions.add(2);
                }
                hasRemove = true;
            }
            if (actions == null || actions.isEmpty()) {
                return false;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setItems(items.toArray(new CharSequence[actions.size()]), AndroidUtilities.toIntArray(icons), (dialogInterface, i) -> {
                if (actions.get(i) == 2) {
                    MessagesController.getInstance(currentAccount).deleteUserFromChat(chatId, user, null);
                    for (int a = 0; a < participants.size(); a++) {
                        TLObject p = participants.get(a);
                        if (p instanceof TLRPC.ChannelParticipant) {
                            TLRPC.ChannelParticipant p2 = (TLRPC.ChannelParticipant) p;
                            if (p2.user_id == userId) {
                                participants.remove(a);
                                updateRows();
                                listViewAdapter.notifyDataSetChanged();
                                break;
                            }
                        }
                    }
                    if (searchItem != null && actionBar.isSearchFieldVisible()) {
                        actionBar.closeSearchField();
                    }
                } else {
                    if (canEditAdmin && (participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_chatParticipantAdmin)) {
                        AlertDialog.Builder builder2 = new AlertDialog.Builder(getParentActivity());
                        builder2.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder2.setMessage(LocaleController.formatString("AdminWillBeRemoved", R.string.AdminWillBeRemoved, ContactsController.formatName(user.first_name, user.last_name)));
                        builder2.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> openRightsEdit2(userId, date, participant, adminRights, bannedRights, canEditAdmin, actions.get(i), false));
                        builder2.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder2.create());
                    } else {
                        openRightsEdit2(userId, date, participant, adminRights, bannedRights, canEditAdmin, actions.get(i), false);
                    }
                }
            });
            AlertDialog alertDialog = builder.create();
            showDialog(alertDialog);
            if (hasRemove) {
                alertDialog.setItemColor(items.size() - 1, Theme.getColor(Theme.key_dialogTextRed2), Theme.getColor(Theme.key_dialogRedIcon));
            }
        } else {
            CharSequence[] items;
            int[] icons;
            if (type == TYPE_KICKED && ChatObject.canBlockUsers(currentChat)) {
                if (resultOnly) {
                    return true;
                }
                items = new CharSequence[]{
                        LocaleController.getString("ChannelEditPermissions", R.string.ChannelEditPermissions),
                        LocaleController.getString("ChannelDeleteFromList", R.string.ChannelDeleteFromList)};
                icons = new int[]{
                        R.drawable.actions_permissions,
                        R.drawable.chats_delete};
            } else if (type == TYPE_BANNED && ChatObject.canBlockUsers(currentChat)) {
                if (resultOnly) {
                    return true;
                }
                items = new CharSequence[]{
                        ChatObject.canAddUsers(currentChat) ? (isChannel ? LocaleController.getString("ChannelAddToChannel", R.string.ChannelAddToChannel) : LocaleController.getString("ChannelAddToGroup", R.string.ChannelAddToGroup)) : null,
                        LocaleController.getString("ChannelDeleteFromList", R.string.ChannelDeleteFromList)};
                icons = new int[]{
                        R.drawable.actions_addmember2,
                        R.drawable.chats_delete};
            } else if (type == TYPE_ADMIN && ChatObject.canAddAdmins(currentChat) && canEdit) {
                if (resultOnly) {
                    return true;
                }
                if (currentChat.creator || !(participant instanceof TLRPC.TL_channelParticipantCreator) && canEdit) {
                    items = new CharSequence[]{
                            LocaleController.getString("EditAdminRights", R.string.EditAdminRights),
                            LocaleController.getString("ChannelRemoveUserAdmin", R.string.ChannelRemoveUserAdmin)};
                    icons = new int[]{
                            R.drawable.actions_addadmin,
                            R.drawable.actions_remove_user};
                } else {
                    items = new CharSequence[]{
                            LocaleController.getString("ChannelRemoveUserAdmin", R.string.ChannelRemoveUserAdmin)};
                    icons = new int[]{
                            R.drawable.actions_remove_user};
                }
            } else {
                items = null;
                icons = null;
            }
            if (items == null) {
                return false;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setItems(items, icons, (dialogInterface, i) -> {
                if (type == TYPE_ADMIN) {
                    if (i == 0 && items.length == 2) {
                        ChatRightsEditActivity fragment = new ChatRightsEditActivity(userId, chatId, adminRights, null, null, ChatRightsEditActivity.TYPE_ADMIN, true, false);
                        fragment.setDelegate((rights, rightsAdmin, rightsBanned) -> {
                            if (participant != null) {
                                if (participant instanceof TLRPC.ChannelParticipant) {
                                    TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                                    channelParticipant.admin_rights = rightsAdmin;
                                    channelParticipant.banned_rights = rightsBanned;
                                }
                                TLObject p = participantsMap.get(userId);
                                if (p instanceof TLRPC.ChannelParticipant) {
                                    TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) p;
                                    channelParticipant.admin_rights = rightsAdmin;
                                    channelParticipant.banned_rights = rightsBanned;
                                }
                            }
                        });
                        presentFragment(fragment);
                    } else {
                        MessagesController.getInstance(currentAccount).setUserAdminRole(chatId, MessagesController.getInstance(currentAccount).getUser(userId), new TLRPC.TL_chatAdminRights(), !isChannel, ChatUsersActivity.this, false);
                        TLObject p = participantsMap.get(userId);
                        if (p != null) {
                            participantsMap.remove(userId);
                            participants.remove(p);
                            updateRows();
                            listViewAdapter.notifyDataSetChanged();
                        }
                    }
                } else if (type == TYPE_BANNED || type == TYPE_KICKED) {
                    if (i == 0) {
                        if (type == TYPE_KICKED) {
                            ChatRightsEditActivity fragment = new ChatRightsEditActivity(userId, chatId, null, defaultBannedRights, bannedRights, ChatRightsEditActivity.TYPE_BANNED, true, false);
                            fragment.setDelegate((rights, rightsAdmin, rightsBanned) -> {
                                if (participant != null) {
                                    if (participant instanceof TLRPC.ChannelParticipant) {
                                        TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                                        channelParticipant.admin_rights = rightsAdmin;
                                        channelParticipant.banned_rights = rightsBanned;
                                    }
                                    TLObject p = participantsMap.get(userId);
                                    if (p instanceof TLRPC.ChannelParticipant) {
                                        TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) p;
                                        channelParticipant.admin_rights = rightsAdmin;
                                        channelParticipant.banned_rights = rightsBanned;
                                    }
                                }
                            });
                            presentFragment(fragment);
                        } else if (type == TYPE_BANNED) {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                            MessagesController.getInstance(currentAccount).addUserToChat(chatId, user, null, 0, null, ChatUsersActivity.this, null);
                        }
                    } else if (i == 1) {
                        TLRPC.TL_channels_editBanned req = new TLRPC.TL_channels_editBanned();
                        req.user_id = MessagesController.getInstance(currentAccount).getInputUser(userId);
                        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(chatId);
                        req.banned_rights = new TLRPC.TL_chatBannedRights();
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
                        if (searchItem != null && actionBar.isSearchFieldVisible()) {
                            actionBar.closeSearchField();
                        }
                    }
                    if (i == 0 && type == TYPE_BANNED || i == 1) {
                        participants.remove(participant);
                        updateRows();
                        listViewAdapter.notifyDataSetChanged();
                    }
                } else {
                    if (i == 0) {
                        MessagesController.getInstance(currentAccount).deleteUserFromChat(chatId, MessagesController.getInstance(currentAccount).getUser(userId), null);
                    }
                }
            });
            AlertDialog alertDialog = builder.create();
            showDialog(alertDialog);
            if (type == TYPE_ADMIN) {
                alertDialog.setItemColor(items.length - 1, Theme.getColor(Theme.key_dialogTextRed2), Theme.getColor(Theme.key_dialogRedIcon));
            }
        }
        return true;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            boolean byChannelUsers = (Boolean) args[2];
            if (chatFull.id == chatId && (!byChannelUsers || !ChatObject.isChannel(currentChat))) {
                info = chatFull;
                AndroidUtilities.runOnUIThread(() -> loadChatParticipants(0, 200));
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        return checkDiscard();
    }

    public void setDelegate(ChatUsersActivityDelegate chatUsersActivityDelegate) {
        delegate = chatUsersActivityDelegate;
    }

    private boolean checkDiscard() {
        String newBannedRights = ChatObject.getBannedRightsString(defaultBannedRights);
        if (!newBannedRights.equals(initialBannedRights)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("UserRestrictionsApplyChanges", R.string.UserRestrictionsApplyChanges));
            if (isChannel) {
                builder.setMessage(LocaleController.getString("ChannelSettingsChangedAlert", R.string.ChannelSettingsChangedAlert));
            } else {
                builder.setMessage(LocaleController.getString("GroupSettingsChangedAlert", R.string.GroupSettingsChangedAlert));
            }
            builder.setPositiveButton(LocaleController.getString("ApplyTheme", R.string.ApplyTheme), (dialogInterface, i) -> processDone());
            builder.setNegativeButton(LocaleController.getString("PassportDiscard", R.string.PassportDiscard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return true;
    }

    public boolean hasSelectType() {
        return selectType != 0;
    }

    private String formatUserPermissions(TLRPC.TL_chatBannedRights rights) {
        if (rights == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (rights.view_messages && defaultBannedRights.view_messages != rights.view_messages) {
            builder.append(LocaleController.getString("UserRestrictionsNoRead", R.string.UserRestrictionsNoRead));
        }
        if (rights.send_messages && defaultBannedRights.send_messages != rights.send_messages) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(LocaleController.getString("UserRestrictionsNoSend", R.string.UserRestrictionsNoSend));
        }
        if (rights.send_media && defaultBannedRights.send_media != rights.send_media) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(LocaleController.getString("UserRestrictionsNoSendMedia", R.string.UserRestrictionsNoSendMedia));
        }
        if (rights.send_stickers && defaultBannedRights.send_stickers != rights.send_stickers) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(LocaleController.getString("UserRestrictionsNoSendStickers", R.string.UserRestrictionsNoSendStickers));
        }
        if (rights.send_polls && defaultBannedRights.send_polls != rights.send_polls) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(LocaleController.getString("UserRestrictionsNoSendPolls", R.string.UserRestrictionsNoSendPolls));
        }
        if (rights.embed_links && defaultBannedRights.embed_links != rights.embed_links) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(LocaleController.getString("UserRestrictionsNoEmbedLinks", R.string.UserRestrictionsNoEmbedLinks));
        }
        if (rights.invite_users && defaultBannedRights.invite_users != rights.invite_users) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(LocaleController.getString("UserRestrictionsNoInviteUsers", R.string.UserRestrictionsNoInviteUsers));
        }
        if (rights.pin_messages && defaultBannedRights.pin_messages != rights.pin_messages) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(LocaleController.getString("UserRestrictionsNoPinMessages", R.string.UserRestrictionsNoPinMessages));
        }
        if (rights.change_info && defaultBannedRights.change_info != rights.change_info) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(LocaleController.getString("UserRestrictionsNoChangeInfo", R.string.UserRestrictionsNoChangeInfo));
        }
        if (builder.length() != 0) {
            builder.replace(0, 1, builder.substring(0, 1).toUpperCase());
            builder.append('.');
        }
        return builder.toString();
    }

    private void processDone() {
        if (type != TYPE_KICKED) {
            return;
        }
        String newBannedRights = ChatObject.getBannedRightsString(defaultBannedRights);
        if (!newBannedRights.equals(initialBannedRights)) {
            MessagesController.getInstance(currentAccount).setDefaultBannedRole(chatId, defaultBannedRights, ChatObject.isChannel(currentChat), this);
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chatId);
            if (chat != null) {
                chat.default_banned_rights = defaultBannedRights;
            }
        }
        finishFragment();
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
    }

    private int getChannelAdminParticipantType(TLObject participant) {
        if (participant instanceof TLRPC.TL_channelParticipantCreator || participant instanceof TLRPC.TL_channelParticipantSelf) {
            return 0;
        } else if (participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_channelParticipant) {
            return 1;
        }  else {
            return 2;
        }
    }

    private void loadChatParticipants(int offset, int count) {
        if (loadingUsers) {
            return;
        }
        if (!ChatObject.isChannel(currentChat)) {
            loadingUsers = false;
            participants.clear();
            participantsMap.clear();
            if (type == TYPE_ADMIN) {
                if (info != null) {
                    for (int a = 0, size = info.participants.participants.size(); a < size; a++) {
                        TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                        if (participant instanceof TLRPC.TL_chatParticipantCreator || participant instanceof TLRPC.TL_chatParticipantAdmin) {
                            participants.add(participant);
                        }
                        participantsMap.put(participant.user_id, participant);
                    }
                }
            } else if (type == TYPE_USERS) {
                if (info != null) {
                    int selfUserId = UserConfig.getInstance(currentAccount).clientUserId;
                    for (int a = 0, size = info.participants.participants.size(); a < size; a++) {
                        TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                        if (selectType != 0 && participant.user_id == selfUserId) {
                            continue;
                        }
                        participants.add(participant);
                        participantsMap.put(participant.user_id, participant);
                    }
                }
            }
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
            if (searchItem != null && !actionBar.isSearchFieldVisible()) {
                searchItem.setVisibility(selectType == 0 && participants.isEmpty() ? View.GONE : View.VISIBLE);
            }
            updateRows();
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
        } else {
            loadingUsers = true;
            if (emptyView != null && !firstLoaded) {
                emptyView.showProgress();
            }
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
            TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
            req.channel = MessagesController.getInstance(currentAccount).getInputChannel(chatId);
            if (type == TYPE_BANNED) {
                req.filter = new TLRPC.TL_channelParticipantsKicked();
            } else if (type == TYPE_ADMIN) {
                req.filter = new TLRPC.TL_channelParticipantsAdmins();
            } else if (type == TYPE_USERS) {
                req.filter = new TLRPC.TL_channelParticipantsRecent();
            } else if (type == TYPE_KICKED) {
                req.filter = new TLRPC.TL_channelParticipantsBanned();
            }
            req.filter.q = "";
            req.offset = offset;
            req.limit = count;
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
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
                    participants.clear();
                    participants.addAll(res.participants);
                    participantsMap.clear();
                    for (int a = 0, size = res.participants.size(); a < size; a++) {
                        TLRPC.ChannelParticipant participant = res.participants.get(a);
                        participantsMap.put(participant.user_id, participant);
                    }
                    try {
                        if (type == TYPE_BANNED || type == TYPE_KICKED || type == TYPE_USERS) {
                            int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                            Collections.sort(res.participants, (lhs, rhs) -> {
                                TLRPC.User user1 = MessagesController.getInstance(currentAccount).getUser(rhs.user_id);
                                TLRPC.User user2 = MessagesController.getInstance(currentAccount).getUser(lhs.user_id);
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
                        } else if (type == TYPE_ADMIN) {
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
        private ArrayList<TLObject> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private SearchAdapterHelper searchAdapterHelper;
        private Timer searchTimer;

        private int groupStartRow;
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
            if (TextUtils.isEmpty(query)) {
                searchResult.clear();
                searchResultNames.clear();
                searchAdapterHelper.mergeResults(null);
                searchAdapterHelper.queryServerSearch(null, type != 0, false, true, false, ChatObject.isChannel(currentChat) ? chatId : 0, type);
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
                int kickedType;
                final ArrayList<TLRPC.ChatParticipant> participantsCopy = !ChatObject.isChannel(currentChat) && info != null ? new ArrayList<>(info.participants.participants) : null;
                final ArrayList<TLRPC.TL_contact> contactsCopy = selectType == 1 ? new ArrayList<>(ContactsController.getInstance(currentAccount).contacts) : null;

                searchAdapterHelper.queryServerSearch(query, selectType != 0, false, true, false, ChatObject.isChannel(currentChat) ? chatId : 0, type);
                if (participantsCopy != null || contactsCopy != null) {
                    Utilities.searchQueue.postRunnable(() -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
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
                        ArrayList<TLObject> resultArray = new ArrayList<>();
                        ArrayList<CharSequence> resultArrayNames = new ArrayList<>();
                        ArrayList<TLObject> resultArray2 = new ArrayList<>();

                        if (participantsCopy != null) {
                            for (int a = 0; a < participantsCopy.size(); a++) {
                                TLRPC.ChatParticipant participant = participantsCopy.get(a);
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
                                        resultArray2.add(participant);
                                        break;
                                    }
                                }
                            }
                        }

                        if (contactsCopy != null) {
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
                        }
                        updateSearchResults(resultArray, resultArrayNames, resultArray2);
                    });
                }
            });
        }

        private void updateSearchResults(final ArrayList<TLObject> users, final ArrayList<CharSequence> names, final ArrayList<TLObject> participants) {
            AndroidUtilities.runOnUIThread(() -> {
                searchResult = users;
                searchResultNames = names;
                searchAdapterHelper.mergeResults(searchResult);
                if (!ChatObject.isChannel(currentChat)) {
                    ArrayList<TLObject> search = searchAdapterHelper.getGroupSearch();
                    search.clear();
                    search.addAll(participants);
                }
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
                    view = new ManageChatUserCell(mContext, 2, 2, selectType == 0);
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
                    } else if (object instanceof TLRPC.ChannelParticipant) {
                        user = MessagesController.getInstance(currentAccount).getUser(((TLRPC.ChannelParticipant) object).user_id);
                    } else if (object instanceof TLRPC.ChatParticipant) {
                        user = MessagesController.getInstance(currentAccount).getUser(((TLRPC.ChatParticipant) object).user_id);
                    } else {
                        return;
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
                        count = searchResult.size();
                        if (count != 0) {
                            if (count + 1 > position) {
                                ok = true;
                                name = searchResultNames.get(position - 1);
                                if (name != null && !TextUtils.isEmpty(un)) {
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
                    userCell.setData(user, name, username, false);

                    break;
                }
                case 1: {
                    GraySectionCell sectionCell = (GraySectionCell) holder.itemView;
                    if (position == groupStartRow) {
                        if (type == TYPE_BANNED) {
                            sectionCell.setText(LocaleController.getString("ChannelBlockedUsers", R.string.ChannelBlockedUsers));
                        } else if (type == TYPE_KICKED) {
                            sectionCell.setText(LocaleController.getString("ChannelRestrictedUsers", R.string.ChannelRestrictedUsers));
                        } else {
                            if (isChannel) {
                                sectionCell.setText(LocaleController.getString("ChannelSubscribers", R.string.ChannelSubscribers));
                            } else {
                                sectionCell.setText(LocaleController.getString("ChannelMembers", R.string.ChannelMembers));
                            }
                        }
                    } else if (position == globalStartRow) {
                        sectionCell.setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
                    } else if (position == contactsStartRow) {
                        sectionCell.setText(LocaleController.getString("Contacts", R.string.Contacts));
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
            if (i == globalStartRow || i == groupStartRow || i == contactsStartRow) {
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
            if (type == 7) {
                return ChatObject.canBlockUsers(currentChat);
            } else if (type == 0) {
                ManageChatUserCell cell = (ManageChatUserCell) holder.itemView;
                TLRPC.User user = cell.getCurrentUser();
                return user != null && !user.self;
            }
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
                    view = new ManageChatUserCell(mContext, type == TYPE_BANNED || type == TYPE_KICKED ? 7 : 6, type == TYPE_BANNED || type == TYPE_KICKED ? 6 : 2, selectType == 0);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    ((ManageChatUserCell) view).setDelegate((cell, click) -> {
                        TLObject participant = listViewAdapter.getItem((Integer) cell.getTag());
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
                    if (isChannel) {
                        textView.setText(LocaleController.getString("NoBlockedChannel2", R.string.NoBlockedChannel2));
                    } else {
                        textView.setText(LocaleController.getString("NoBlockedGroup2", R.string.NoBlockedGroup2));
                    }
                    textView.setTextColor(Theme.getColor(Theme.key_emptyListPlaceholder));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                    textView.setGravity(Gravity.CENTER_HORIZONTAL);
                    linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0));

                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    break;
                case 5:
                    HeaderCell headerCell = new HeaderCell(mContext, false, 21, 11, false);
                    headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    headerCell.setHeight(43);
                    view = headerCell;
                    break;
                case 6:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                default:
                case 7:
                    view = new TextCheckCell2(mContext);
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
                    TLObject item = getItem(position);
                    int userId;
                    int kickedBy;
                    int promotedBy;
                    TLRPC.TL_chatBannedRights bannedRights;
                    boolean banned;
                    boolean creator;
                    boolean admin;
                    if (item instanceof TLRPC.ChannelParticipant) {
                        TLRPC.ChannelParticipant participant = (TLRPC.ChannelParticipant) item;
                        userId = participant.user_id;
                        kickedBy = participant.kicked_by;
                        promotedBy = participant.promoted_by;
                        bannedRights = participant.banned_rights;
                        banned = participant instanceof TLRPC.TL_channelParticipantBanned;
                        creator = participant instanceof TLRPC.TL_channelParticipantCreator;
                        admin = participant instanceof TLRPC.TL_channelParticipantAdmin;
                    } else {
                        TLRPC.ChatParticipant participant = (TLRPC.ChatParticipant) item;
                        userId = participant.user_id;
                        kickedBy = 0;
                        promotedBy = 0;
                        bannedRights = null;
                        banned = false;
                        creator = participant instanceof TLRPC.TL_chatParticipantCreator;
                        admin = participant instanceof TLRPC.TL_chatParticipantAdmin;
                    }
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                    if (user != null) {
                        if (type == TYPE_KICKED) {
                            userCell.setData(user, null, formatUserPermissions(bannedRights), position != participantsEndRow - 1);
                        } else if (type == TYPE_BANNED) {
                            String role = null;
                            if (banned) {
                                TLRPC.User user1 = MessagesController.getInstance(currentAccount).getUser(kickedBy);
                                if (user1 != null) {
                                    role = LocaleController.formatString("UserRemovedBy", R.string.UserRemovedBy, ContactsController.formatName(user1.first_name, user1.last_name));
                                }
                            }
                            userCell.setData(user, null, role, position != participantsEndRow - 1);
                        } else if (type == TYPE_ADMIN) {
                            String role = null;
                            if (creator) {
                                role = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                            } else if (admin) {
                                TLRPC.User user1 = MessagesController.getInstance(currentAccount).getUser(promotedBy);
                                if (user1 != null) {
                                    role = LocaleController.formatString("EditAdminPromotedBy", R.string.EditAdminPromotedBy, ContactsController.formatName(user1.first_name, user1.last_name));
                                }
                            }
                            userCell.setData(user, null, role, position != participantsEndRow - 1);
                        } else if (type == TYPE_USERS) {
                            userCell.setData(user, null, null, position != participantsEndRow - 1);
                        }
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == participantsInfoRow) {
                        if (type == TYPE_BANNED || type == TYPE_KICKED) {
                            if (ChatObject.canBlockUsers(currentChat)) {
                                if (isChannel) {
                                    privacyCell.setText(LocaleController.getString("NoBlockedChannel2", R.string.NoBlockedChannel2));
                                } else {
                                    privacyCell.setText(LocaleController.getString("NoBlockedGroup2", R.string.NoBlockedGroup2));
                                }
                            } else {
                                if (isChannel) {
                                    privacyCell.setText(LocaleController.getString("NoBlockedChannel2", R.string.NoBlockedChannel2));
                                } else {
                                    privacyCell.setText(LocaleController.getString("NoBlockedGroup2", R.string.NoBlockedGroup2));
                                }
                            }
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        } else if (type == TYPE_ADMIN) {
                            if (addNewRow != -1) {
                                if (isChannel) {
                                    privacyCell.setText(LocaleController.getString("ChannelAdminsInfo", R.string.ChannelAdminsInfo));
                                } else {
                                    privacyCell.setText(LocaleController.getString("MegaAdminsInfo", R.string.MegaAdminsInfo));
                                }
                                privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                            } else {
                                privacyCell.setText("");
                                privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                            }
                        } else if (type == TYPE_USERS) {
                            if (!isChannel || selectType != 0) {
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
                    actionCell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                    if (position == addNewRow) {
                        if (type == TYPE_KICKED) {
                            actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                            actionCell.setText(LocaleController.getString("ChannelAddException", R.string.ChannelAddException), null, R.drawable.actions_removed, participantsStartRow != -1);
                        } else if (type == TYPE_BANNED) {
                            actionCell.setText(LocaleController.getString("ChannelBlockUser", R.string.ChannelBlockUser), null, R.drawable.actions_removed, false);
                        } else if (type == TYPE_ADMIN) {
                            actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                            actionCell.setText(LocaleController.getString("ChannelAddAdmin", R.string.ChannelAddAdmin), null, R.drawable.add_admin, true);
                        } else if (type == TYPE_USERS) {
                            actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                            if (isChannel) {
                                actionCell.setText(LocaleController.getString("AddSubscriber", R.string.AddSubscriber), null, R.drawable.actions_addmember2, true);
                            } else {
                                actionCell.setText(LocaleController.getString("AddMember", R.string.AddMember), null, R.drawable.actions_addmember2, true);
                            }
                        }
                    } else if (position == recentActionsRow) {
                        actionCell.setText(LocaleController.getString("EventLog", R.string.EventLog), null, R.drawable.group_log, false);
                    } else if (position == addNew2Row) {
                        actionCell.setText(LocaleController.getString("ChannelInviteViaLink", R.string.ChannelInviteViaLink), null, R.drawable.profile_link, false);
                    }
                    break;
                case 3:
                    if (position == addNewSectionRow || type == TYPE_KICKED && position == participantsDividerRow && addNewRow == -1 && participantsStartRow == -1) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case 5:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == restricted1SectionRow) {
                        if (type == TYPE_BANNED) {
                            int count = info != null ? info.kicked_count : participants.size();
                            if (count != 0) {
                                headerCell.setText(LocaleController.formatPluralString("RemovedUser", count));
                            } else {
                                headerCell.setText(LocaleController.getString("ChannelBlockedUsers", R.string.ChannelBlockedUsers));
                            }
                        } else {
                            headerCell.setText(LocaleController.getString("ChannelRestrictedUsers", R.string.ChannelRestrictedUsers));
                        }
                    } else if (position == permissionsSectionRow) {
                        headerCell.setText(LocaleController.getString("ChannelPermissionsHeader", R.string.ChannelPermissionsHeader));
                    }
                    break;
                case 6:
                    TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                    settingsCell.setTextAndValue(LocaleController.getString("ChannelBlacklist", R.string.ChannelBlacklist), String.format("%d", info != null ? info.kicked_count : 0), false);
                    break;
                case 7:
                    TextCheckCell2 checkCell = (TextCheckCell2) holder.itemView;
                    if (position == changeInfoRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsChangeInfo", R.string.UserRestrictionsChangeInfo), !defaultBannedRights.change_info && TextUtils.isEmpty(currentChat.username), false);
                    } else if (position == addUsersRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsInviteUsers", R.string.UserRestrictionsInviteUsers), !defaultBannedRights.invite_users, true);
                    } else if (position == pinMessagesRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsPinMessages", R.string.UserRestrictionsPinMessages), !defaultBannedRights.pin_messages && TextUtils.isEmpty(currentChat.username), true);
                    } else if (position == sendMessagesRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSend", R.string.UserRestrictionsSend), !defaultBannedRights.send_messages, true);
                    } else if (position == sendMediaRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSendMedia", R.string.UserRestrictionsSendMedia), !defaultBannedRights.send_media, true);
                    } else if (position == sendStickersRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSendStickers", R.string.UserRestrictionsSendStickers), !defaultBannedRights.send_stickers, true);
                    } else if (position == embedLinksRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsEmbedLinks", R.string.UserRestrictionsEmbedLinks), !defaultBannedRights.embed_links, true);
                    } else if (position == sendPollsRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSendPolls", R.string.UserRestrictionsSendPolls), !defaultBannedRights.send_polls, true);
                    }

                    if (position == sendMediaRow || position == sendStickersRow || position == embedLinksRow || position == sendPollsRow) {
                        checkCell.setEnabled(!defaultBannedRights.send_messages && !defaultBannedRights.view_messages);
                    } else if (position == sendMessagesRow) {
                        checkCell.setEnabled(!defaultBannedRights.view_messages);
                    }
                    if (ChatObject.canBlockUsers(currentChat)) {
                        if (position == addUsersRow && !ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE) ||
                                position == pinMessagesRow && !ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_PIN) ||
                                position == changeInfoRow && !ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_CHANGE_INFO) ||
                                !TextUtils.isEmpty(currentChat.username) && (position == pinMessagesRow || position == changeInfoRow)) {
                            checkCell.setIcon(R.drawable.permission_locked);
                        } else {
                            checkCell.setIcon(0);
                        }
                    } else {
                        checkCell.setIcon(0);
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
            if (position == addNewRow || position == addNew2Row || position == recentActionsRow) {
                return 2;
            } else if (position >= participantsStartRow && position < participantsEndRow) {
                return 0;
            } else if (position == addNewSectionRow || position == participantsDividerRow || position == participantsDivider2Row) {
                return 3;
            } else if (position == restricted1SectionRow || position == permissionsSectionRow) {
                return 5;
            } else if (position == participantsInfoRow) {
                return 1;
            } else if (position == blockedEmptyRow) {
                return 4;
            } else if (position == removedUsersRow) {
                return 6;
            } else if (position == changeInfoRow || position == addUsersRow || position == pinMessagesRow || position == sendMessagesRow ||
                    position == sendMediaRow || position == sendStickersRow || position == embedLinksRow || position == sendPollsRow) {
                return 7;
            }
            return 0;
        }

        public TLObject getItem(int position) {
            if (participantsStartRow != -1 && position >= participantsStartRow && position < participantsEndRow) {
                return participants.get(position - participantsStartRow);
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
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ManageChatUserCell.class, ManageChatTextCell.class, TextCheckCell2.class, TextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
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

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection),

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2Track),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2TrackChecked),

                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, null, new Drawable[]{Theme.avatar_broadcastDrawable, Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon),
        };
    }
}
