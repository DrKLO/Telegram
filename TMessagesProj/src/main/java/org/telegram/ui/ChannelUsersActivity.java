/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class ChannelUsersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listViewAdapter;
    private EmptyTextProgressView emptyView;

    private ArrayList<TLRPC.ChannelParticipant> participants = new ArrayList<>();
    private int chatId;
    private int type;
    private boolean loadingUsers;
    private boolean firstLoaded;
    private boolean isAdmin;
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

        ActionBarMenu menu = actionBar.createMenu();

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(0xfff0f0f0);
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

        final ListView listView = new ListView(context);
        listView.setEmptyView(emptyView);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setDrawSelectorOnTop(true);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        if (Build.VERSION.SDK_INT >= 11) {
            listView.setVerticalScrollbarPosition(LocaleController.isRTL ? ListView.SCROLLBAR_POSITION_LEFT : ListView.SCROLLBAR_POSITION_RIGHT);
        }
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (type == 2) {
                    if (isAdmin) {
                        if (i == 0) {
                            Bundle args = new Bundle();
                            args.putBoolean("onlyUsers", true);
                            args.putBoolean("destroyAfterSelect", true);
                            args.putBoolean("returnAsResult", true);
                            args.putBoolean("needForwardCount", false);
                            args.putBoolean("allowUsernameSearch", false);
                            args.putString("selectAlertString", LocaleController.getString("ChannelAddTo", R.string.ChannelAddTo));
                            ContactsActivity fragment = new ContactsActivity(args);
                            fragment.setDelegate(new ContactsActivity.ContactsActivityDelegate() {
                                @Override
                                public void didSelectContact(TLRPC.User user, String param) {
                                    MessagesController.getInstance().addUserToChat(chatId, user, null, param != null ? Utilities.parseInt(param) : 0, null, ChannelUsersActivity.this);
                                }
                            });
                            presentFragment(fragment);
                        } else if (!isPublic && i == 1) {
                            presentFragment(new GroupInviteActivity(chatId));
                        }
                    }

                } else if (type == 1) {
                    if (isAdmin) {
                        if (isMegagroup && (i == 1 || i == 2)) {
                            TLRPC.Chat chat = MessagesController.getInstance().getChat(chatId);
                            if (chat == null) {
                                return;
                            }
                            boolean changed = false;
                            if (i == 1 && !chat.democracy) {
                                chat.democracy = true;
                                changed = true;
                            } else if (i == 2 && chat.democracy) {
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
                        if (i == participantsStartRow + participants.size()) {
                            Bundle args = new Bundle();
                            args.putBoolean("onlyUsers", true);
                            args.putBoolean("destroyAfterSelect", true);
                            args.putBoolean("returnAsResult", true);
                            args.putBoolean("needForwardCount", false);
                            args.putBoolean("allowUsernameSearch", true);
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
                if (i >= participantsStartRow && i < participants.size() + participantsStartRow) {
                    participant = participants.get(i - participantsStartRow);
                }
                if (participant != null) {
                    Bundle args = new Bundle();
                    args.putInt("user_id", participant.user_id);
                    presentFragment(new ProfileActivity(args));
                }
            }
        });

        if (isAdmin || isMegagroup && type == 0) {
            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (getParentActivity() == null) {
                        return false;
                    }
                    TLRPC.ChannelParticipant participant = null;
                    if (i >= participantsStartRow && i < participants.size() + participantsStartRow) {
                        participant = participants.get(i - participantsStartRow);
                    }
                    if (participant != null) {
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
        TLRPC.TL_channels_editAdmin req = new TLRPC.TL_channels_editAdmin();
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
                            AlertsCreator.showAddUserAlert(error.text, ChannelUsersActivity.this, !isMegagroup);
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
                                FileLog.e("tmessages", e);
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

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            if (type == 2) {
                if (isAdmin) {
                    if (!isPublic) {
                        if (i == 0 || i == 1) {
                            return true;
                        } else if (i == 2) {
                            return false;
                        }
                    } else {
                        if (i == 0) {
                            return true;
                        } else if (i == 1) {
                            return false;
                        }
                    }
                }
            } else if (type == 1) {
                if (i == participantsStartRow + participants.size()) {
                    return isAdmin;
                } else if (i == participantsStartRow + participants.size() + 1) {
                    return false;
                } else if (isMegagroup && isAdmin && i < 4) {
                    return i == 1 || i == 2;
                }
            }
            return i != participants.size() + participantsStartRow && participants.get(i - participantsStartRow).user_id != UserConfig.getClientUserId();
        }

        @Override
        public int getCount() {
            if (participants.isEmpty() && type == 0 || loadingUsers && !firstLoaded) {
                return 0;
            } else if (type == 1) {
                return participants.size() + (isAdmin ? 2 : 1) + (isAdmin && isMegagroup ? 4 : 0);
            }
            return participants.size() + participantsStartRow + 1;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int viewType = getItemViewType(i);
            if (viewType == 0) {
                if (view == null) {
                    view = new UserCell(mContext, 1, 0, false);
                    view.setBackgroundColor(0xffffffff);
                }
                UserCell userCell = (UserCell) view;
                TLRPC.ChannelParticipant participant = participants.get(i - participantsStartRow);
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
            } else if (viewType == 1) {
                if (view == null) {
                    view = new TextInfoPrivacyCell(mContext);
                }
                if (type == 0) {
                    ((TextInfoPrivacyCell) view).setText(String.format("%1$s\n\n%2$s", LocaleController.getString("NoBlockedGroup", R.string.NoBlockedGroup), LocaleController.getString("UnblockText", R.string.UnblockText)));
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                } else if (type == 1) {
                    if (isAdmin) {
                        if (isMegagroup) {
                            ((TextInfoPrivacyCell) view).setText(LocaleController.getString("MegaAdminsInfo", R.string.MegaAdminsInfo));
                            view.setBackgroundResource(R.drawable.greydivider_bottom);
                        } else {
                            ((TextInfoPrivacyCell) view).setText(LocaleController.getString("ChannelAdminsInfo", R.string.ChannelAdminsInfo));
                            view.setBackgroundResource(R.drawable.greydivider_bottom);
                        }
                    } else {
                        ((TextInfoPrivacyCell) view).setText("");
                        view.setBackgroundResource(R.drawable.greydivider_bottom);
                    }
                } else if (type == 2) {
                    if ((!isPublic && i == 2 || i == 1) && isAdmin) {
                        if (isMegagroup) {
                            ((TextInfoPrivacyCell) view).setText("");
                        } else {
                            ((TextInfoPrivacyCell) view).setText(LocaleController.getString("ChannelMembersInfo", R.string.ChannelMembersInfo));
                        }
                        view.setBackgroundResource(R.drawable.greydivider);
                    } else {
                        ((TextInfoPrivacyCell) view).setText("");
                        view.setBackgroundResource(R.drawable.greydivider_bottom);
                    }
                }
            } else if (viewType == 2) {
                if (view == null) {
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                TextSettingsCell actionCell = (TextSettingsCell) view;
                if (type == 2) {
                    if (i == 0) {
                        actionCell.setText(LocaleController.getString("AddMember", R.string.AddMember), true);
                    } else if (i == 1) {
                        actionCell.setText(LocaleController.getString("ChannelInviteViaLink", R.string.ChannelInviteViaLink), false);
                    }
                } else if (type == 1) {
                    actionCell.setTextAndIcon(LocaleController.getString("ChannelAddAdmin", R.string.ChannelAddAdmin), R.drawable.managers, false);
                }
            } else if (viewType == 3) {
                if (view == null) {
                    view = new ShadowSectionCell(mContext);
                }
            } else if (viewType == 4) {
                if (view == null) {
                    view = new TextCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                ((TextCell) view).setTextAndIcon(LocaleController.getString("ChannelAddAdmin", R.string.ChannelAddAdmin), R.drawable.managers);
            } else if (viewType == 5) {
                if (view == null) {
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                ((HeaderCell) view).setText(LocaleController.getString("WhoCanAddMembers", R.string.WhoCanAddMembers));
            } else if (viewType == 6) {
                if (view == null) {
                    view = new RadioCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                RadioCell radioCell = (RadioCell) view;
                TLRPC.Chat chat = MessagesController.getInstance().getChat(chatId);
                if (i == 1) {
                    radioCell.setTag(0);
                    radioCell.setText(LocaleController.getString("WhoCanAddMembersAllMembers", R.string.WhoCanAddMembersAllMembers), chat != null && chat.democracy, true);
                } else if (i == 2) {
                    radioCell.setTag(1);
                    radioCell.setText(LocaleController.getString("WhoCanAddMembersAdmins", R.string.WhoCanAddMembersAdmins), chat != null && !chat.democracy, false);
                }
            }
            return view;
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

        @Override
        public int getViewTypeCount() {
            return 7;
        }

        @Override
        public boolean isEmpty() {
            return getCount() == 0 || participants.isEmpty() && loadingUsers;
        }
    }
}
