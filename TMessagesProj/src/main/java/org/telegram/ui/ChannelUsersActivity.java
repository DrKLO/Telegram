/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class ChannelUsersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listViewAdapter;
    private EmptyTextProgressView emptyView;
    private RecyclerListView listView;

    private ArrayList<TLRPC.ChannelParticipant> participants = new ArrayList<>();
    private int chatId;
    private int type;
    private boolean loadingUsers;
    private boolean firstLoaded;
    private boolean isAdmin;
    private boolean isModerator;
    private boolean isPublic;
    private boolean isMegagroup;
    private int participantsStartRow;

    public ChannelUsersActivity(Bundle args) {
        super(args);
        chatId = arguments.getInt("chat_id");
        type = arguments.getInt("type");
        TLRPC.Chat chat = MessagesController.getInstance().getChat(chatId);
        if (chat != null) {
            if (chat.creator) {
                isAdmin = true;
                isPublic = (chat.flags & TLRPC.CHAT_FLAG_IS_PUBLIC) != 0;
            } else if (chat.editor) {
                isModerator = true;
            }
            isMegagroup = chat.megagroup;
        }
        if (type == 0) {
            participantsStartRow = 0;
        } else if (type == 1) {
            participantsStartRow = isAdmin && isMegagroup ? 4 : 0;
        } else if (type == 2) {
            participantsStartRow = isAdmin ? (isPublic ? 2 : 3) : 0;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatInfoDidLoaded);
        getChannelParticipants(0, 200);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatInfoDidLoaded);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (type == 0) {
            actionBar.setTitle(LocaleController.getString("ChannelBlockedUsers", R.string.ChannelBlockedUsers));
        } else if (type == 1) {
            actionBar.setTitle(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators));
        } else if (type == 2) {
            actionBar.setTitle(LocaleController.getString("ChannelMembers", R.string.ChannelMembers));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        emptyView = new EmptyTextProgressView(context);
        if (type == 0) {
            if (isMegagroup) {
                emptyView.setText(LocaleController.getString("NoBlockedGroup", R.string.NoBlockedGroup));
            } else {
                emptyView.setText(LocaleController.getString("NoBlocked", R.string.NoBlocked));
            }
        }
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setEmptyView(emptyView);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (type == 2) {
                    if (isAdmin) {
                        if (position == 0) {
                            Bundle args = new Bundle();
                            args.putBoolean("onlyUsers", true);
                            args.putBoolean("destroyAfterSelect", true);
                            args.putBoolean("returnAsResult", true);
                            args.putBoolean("needForwardCount", false);
                            args.putString("selectAlertString", LocaleController.getString("ChannelAddTo", R.string.ChannelAddTo));
                            ContactsActivity fragment = new ContactsActivity(args);
                            fragment.setDelegate(new ContactsActivity.ContactsActivityDelegate() {
                                @Override
                                public void didSelectContact(TLRPC.User user, String param) {
                                    MessagesController.getInstance().addUserToChat(chatId, user, null, param != null ? Utilities.parseInt(param) : 0, null, ChannelUsersActivity.this);
                                }
                            });
                            presentFragment(fragment);
                        } else if (!isPublic && position == 1) {
                            presentFragment(new GroupInviteActivity(chatId));
                        }
                    }

                } else if (type == 1) {
                    if (isAdmin) {
                        if (isMegagroup && (position == 1 || position == 2)) {
                            TLRPC.Chat chat = MessagesController.getInstance().getChat(chatId);
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
                                MessagesController.getInstance().toogleChannelInvites(chatId, chat.democracy);
                                int count = listView.getChildCount();
                                for (int a = 0; a < count; a++) {
                                    View child = listView.getChildAt(a);
                                    if (child instanceof RadioCell) {
                                        int num = (Integer) child.getTag();
                                        ((RadioCell) child).setChecked(num == 0 && chat.democracy || num == 1 && !chat.democracy, true);
                                    }
                                }
                            }
                            return;
                        }
                        if (position == participantsStartRow + participants.size()) {
                            Bundle args = new Bundle();
                            args.putBoolean("onlyUsers", true);
                            args.putBoolean("destroyAfterSelect", true);
                            args.putBoolean("returnAsResult", true);
                            args.putBoolean("needForwardCount", false);
                            args.putBoolean("addingToChannel", !isMegagroup);
                            /*if (isMegagroup) {
                                args.putBoolean("allowBots", false);
                            }*/
                            args.putString("selectAlertString", LocaleController.getString("ChannelAddUserAdminAlert", R.string.ChannelAddUserAdminAlert));
                            ContactsActivity fragment = new ContactsActivity(args);
                            fragment.setDelegate(new ContactsActivity.ContactsActivityDelegate() {
                                @Override
                                public void didSelectContact(TLRPC.User user, String param) {
                                    setUserChannelRole(user, new TLRPC.TL_channelRoleEditor());
                                }
                            });
                            presentFragment(fragment);
                            return;
                        }
                    }
                }
                TLRPC.ChannelParticipant participant = null;
                if (position >= participantsStartRow && position < participants.size() + participantsStartRow) {
                    participant = participants.get(position - participantsStartRow);
                }
                if (participant != null) {
                    Bundle args = new Bundle();
                    args.putInt("user_id", participant.user_id);
                    presentFragment(new ProfileActivity(args));
                }
            }
        });

        if (isAdmin || isModerator && type == 2 || isMegagroup && type == 0) {
            listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
                @Override
                public boolean onItemClick(View view, int position) {
                    if (getParentActivity() == null) {
                        return false;
                    }
                    TLRPC.ChannelParticipant participant = null;
                    if (position >= participantsStartRow && position < participants.size() + participantsStartRow) {
                        participant = participants.get(position - participantsStartRow);
                    }
                    if (participant != null) {
                        if (participant.user_id == UserConfig.getClientUserId()) {
                            return false;
                        }
                        final TLRPC.ChannelParticipant finalParticipant = participant;
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        CharSequence[] items = null;
                        if (type == 0) {
                            items = new CharSequence[]{LocaleController.getString("Unblock", R.string.Unblock)};
                        } else if (type == 1) {
                            items = new CharSequence[]{LocaleController.getString("ChannelRemoveUserAdmin", R.string.ChannelRemoveUserAdmin)};
                        } else if (type == 2) {
                            items = new CharSequence[]{LocaleController.getString("ChannelRemoveUser", R.string.ChannelRemoveUser)};
                        }
                        builder.setItems(items, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (i == 0) {
                                    if (type == 0) {
                                        participants.remove(finalParticipant);
                                        listViewAdapter.notifyDataSetChanged();
                                        TLRPC.TL_channels_kickFromChannel req = new TLRPC.TL_channels_kickFromChannel();
                                        req.kicked = false;
                                        req.user_id = MessagesController.getInputUser(finalParticipant.user_id);
                                        req.channel = MessagesController.getInputChannel(chatId);
                                        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                            @Override
                                            public void run(TLObject response, TLRPC.TL_error error) {
                                                if (response != null) {
                                                    final TLRPC.Updates updates = (TLRPC.Updates) response;
                                                    MessagesController.getInstance().processUpdates(updates, false);
                                                    if (!updates.chats.isEmpty()) {
                                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                TLRPC.Chat chat = updates.chats.get(0);
                                                                MessagesController.getInstance().loadFullChat(chat.id, 0, true);
                                                            }
                                                        }, 1000);
                                                    }
                                                }
                                            }
                                        });
                                    } else if (type == 1) {
                                        setUserChannelRole(MessagesController.getInstance().getUser(finalParticipant.user_id), new TLRPC.TL_channelRoleEmpty());
                                    } else if (type == 2) {
                                        MessagesController.getInstance().deleteUserFromChat(chatId, MessagesController.getInstance().getUser(finalParticipant.user_id), null);
                                    }
                                }
                            }
                        });
                        showDialog(builder.create());
                        return true;
                    } else {
                        return false;
                    }
                }
            });
        }

        if (loadingUsers) {
            emptyView.showProgress();
        } else {
            emptyView.showTextView();
        }
        return fragmentView;
    }

    public void setUserChannelRole(TLRPC.User user, TLRPC.ChannelParticipantRole role) {
        if (user == null || role == null) {
            return;
        }
        final TLRPC.TL_channels_editAdmin req = new TLRPC.TL_channels_editAdmin();
        req.channel = MessagesController.getInputChannel(chatId);
        req.user_id = MessagesController.getInputUser(user);
        req.role = role;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, final TLRPC.TL_error error) {
                if (error == null) {
                    MessagesController.getInstance().processUpdates((TLRPC.Updates) response, false);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            MessagesController.getInstance().loadFullChat(chatId, 0, true);
                        }
                    }, 1000);
                } else {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            AlertsCreator.processError(error, ChannelUsersActivity.this, req, !isMegagroup);
                        }
                    });
                }
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoaded) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        getChannelParticipants(0, 200);
                    }
                });
            }
        }
    }

    private int getChannelAdminParticipantType(TLRPC.ChannelParticipant participant) {
        if (participant instanceof TLRPC.TL_channelParticipantCreator || participant instanceof TLRPC.TL_channelParticipantSelf) {
            return 0;
        } else if (participant instanceof TLRPC.TL_channelParticipantEditor) {
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
        req.channel = MessagesController.getInputChannel(chatId);
        if (type == 0) {
            req.filter = new TLRPC.TL_channelParticipantsKicked();
        } else if (type == 1) {
            req.filter = new TLRPC.TL_channelParticipantsAdmins();
        } else if (type == 2) {
            req.filter = new TLRPC.TL_channelParticipantsRecent();
        }
        req.offset = offset;
        req.limit = count;
        int reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (error == null) {
                            TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                            MessagesController.getInstance().putUsers(res.users, false);
                            participants = res.participants;
                            try {
                                if (type == 0 || type == 2) {
                                    Collections.sort(participants, new Comparator<TLRPC.ChannelParticipant>() {
                                        @Override
                                        public int compare(TLRPC.ChannelParticipant lhs, TLRPC.ChannelParticipant rhs) {
                                            TLRPC.User user1 = MessagesController.getInstance().getUser(rhs.user_id);
                                            TLRPC.User user2 = MessagesController.getInstance().getUser(lhs.user_id);
                                            int status1 = 0;
                                            int status2 = 0;
                                            if (user1 != null && user1.status != null) {
                                                if (user1.id == UserConfig.getClientUserId()) {
                                                    status1 = ConnectionsManager.getInstance().getCurrentTime() + 50000;
                                                } else {
                                                    status1 = user1.status.expires;
                                                }
                                            }
                                            if (user2 != null && user2.status != null) {
                                                if (user2.id == UserConfig.getClientUserId()) {
                                                    status2 = ConnectionsManager.getInstance().getCurrentTime() + 50000;
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
                                        }
                                    });
                                } else if (type == 1) {
                                    Collections.sort(res.participants, new Comparator<TLRPC.ChannelParticipant>() {
                                        @Override
                                        public int compare(TLRPC.ChannelParticipant lhs, TLRPC.ChannelParticipant rhs) {
                                            int type1 = getChannelAdminParticipantType(lhs);
                                            int type2 = getChannelAdminParticipantType(rhs);
                                            if (type1 > type2) {
                                                return 1;
                                            } else if (type1 < type2) {
                                                return -1;
                                            }
                                            return 0;
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                        loadingUsers = false;
                        firstLoaded = true;
                        if (emptyView != null) {
                            emptyView.showTextView();
                        }
                        if (listViewAdapter != null) {
                            listViewAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        });
        ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int postion = holder.getAdapterPosition();
            if (type == 2) {
                if (isAdmin) {
                    if (!isPublic) {
                        if (postion == 0 || postion == 1) {
                            return true;
                        } else if (postion == 2) {
                            return false;
                        }
                    } else {
                        if (postion == 0) {
                            return true;
                        } else if (postion == 1) {
                            return false;
                        }
                    }
                }
            } else if (type == 1) {
                if (postion == participantsStartRow + participants.size()) {
                    return isAdmin;
                } else if (postion == participantsStartRow + participants.size() + 1) {
                    return false;
                } else if (isMegagroup && isAdmin && postion < 4) {
                    return postion == 1 || postion == 2;
                }
            }
            return postion != participants.size() + participantsStartRow && participants.get(postion - participantsStartRow).user_id != UserConfig.getClientUserId();
        }

        @Override
        public int getItemCount() {
            if (participants.isEmpty() && type == 0 || loadingUsers && !firstLoaded) {
                return 0;
            } else if (type == 1) {
                return participants.size() + (isAdmin ? 2 : 1) + (isAdmin && isMegagroup ? 4 : 0);
            }
            return participants.size() + participantsStartRow + 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new UserCell(mContext, 1, 0, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 4:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
                    UserCell userCell = (UserCell) holder.itemView;
                    TLRPC.ChannelParticipant participant = participants.get(position - participantsStartRow);
                    TLRPC.User user = MessagesController.getInstance().getUser(participant.user_id);
                    if (user != null) {
                        if (type == 0) {
                            userCell.setData(user, null, user.phone != null && user.phone.length() != 0 ? PhoneFormat.getInstance().format("+" + user.phone) : LocaleController.getString("NumberUnknown", R.string.NumberUnknown), 0);
                        } else if (type == 1) {
                            String role = null;
                            if (participant instanceof TLRPC.TL_channelParticipantCreator || participant instanceof TLRPC.TL_channelParticipantSelf) {
                                role = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                            } else if (participant instanceof TLRPC.TL_channelParticipantModerator) {
                                role = LocaleController.getString("ChannelModerator", R.string.ChannelModerator);
                            }  else if (participant instanceof TLRPC.TL_channelParticipantEditor) {
                                role = LocaleController.getString("ChannelEditor", R.string.ChannelEditor);
                            }
                            userCell.setData(user, null, role, 0);
                        } else if (type == 2) {
                            userCell.setData(user, null, null, 0);
                        }
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (type == 0) {
                        privacyCell.setText(String.format("%1$s\n\n%2$s", LocaleController.getString("NoBlockedGroup", R.string.NoBlockedGroup), LocaleController.getString("UnblockText", R.string.UnblockText)));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (type == 1) {
                        if (isAdmin) {
                            if (isMegagroup) {
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
                        if ((!isPublic && position == 2 || position == 1) && isAdmin) {
                            if (isMegagroup) {
                                privacyCell.setText("");
                            } else {
                                privacyCell.setText(LocaleController.getString("ChannelMembersInfo", R.string.ChannelMembersInfo));
                            }
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        } else {
                            privacyCell.setText("");
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        }
                    }
                    break;
                case 2:
                    TextSettingsCell actionCell = (TextSettingsCell) holder.itemView;
                    if (type == 2) {
                        if (position == 0) {
                            actionCell.setText(LocaleController.getString("AddMember", R.string.AddMember), true);
                        } else if (position == 1) {
                            actionCell.setText(LocaleController.getString("ChannelInviteViaLink", R.string.ChannelInviteViaLink), false);
                        }
                    } else if (type == 1) {
                        actionCell.setTextAndIcon(LocaleController.getString("ChannelAddAdmin", R.string.ChannelAddAdmin), R.drawable.managers, false);
                    }
                    break;
                case 4:
                    ((TextCell) holder.itemView).setTextAndIcon(LocaleController.getString("ChannelAddAdmin", R.string.ChannelAddAdmin), R.drawable.managers);
                    break;
                case 5:
                    ((HeaderCell) holder.itemView).setText(LocaleController.getString("WhoCanAddMembers", R.string.WhoCanAddMembers));
                    break;
                case 6:
                    RadioCell radioCell = (RadioCell) holder.itemView;
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(chatId);
                    if (position == 1) {
                        radioCell.setTag(0);
                        radioCell.setText(LocaleController.getString("WhoCanAddMembersAllMembers", R.string.WhoCanAddMembersAllMembers), chat != null && chat.democracy, true);
                    } else if (position == 2) {
                        radioCell.setTag(1);
                        radioCell.setText(LocaleController.getString("WhoCanAddMembersAdmins", R.string.WhoCanAddMembersAdmins), chat != null && !chat.democracy, false);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (type == 1) {
                if (isAdmin) {
                    if (isMegagroup) {
                        if (i == 0) {
                            return 5;
                        } else if (i == 1 || i == 2) {
                            return 6;
                        } else if (i == 3) {
                            return 3;
                        }
                    }
                    if (i == participantsStartRow + participants.size()) {
                        return 4;
                    } else if (i == participantsStartRow + participants.size() + 1) {
                        return 1;
                    }
                }
            } else if (type == 2) {
                if (isAdmin) {
                    if (!isPublic) {
                        if (i == 0 || i == 1) {
                            return 2;
                        } else if (i == 2) {
                            return 1;
                        }
                    } else {
                        if (i == 0) {
                            return 2;
                        } else if (i == 1) {
                            return 1;
                        }
                    }
                }
            }
            if (i == participants.size() + participantsStartRow) {
                return 1;
            }
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate сellDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor(int color) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof UserCell) {
                        ((UserCell) child).update(0);
                    }
                }
            }
        };

        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{UserCell.class, TextSettingsCell.class, TextCell.class, RadioCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
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

                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusColor"}, null, null, сellDelegate, Theme.key_windowBackgroundWhiteGrayText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusOnlineColor"}, null, null, сellDelegate, Theme.key_windowBackgroundWhiteBlueText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, null, new Drawable[]{Theme.avatar_photoDrawable, Theme.avatar_broadcastDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),
        };
    }
}
