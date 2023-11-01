/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.JoinToSendSettingsView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingStickerDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Iterator;

public class ChatLinkActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listViewAdapter;
    private RecyclerListView listView;
    private ActionBarMenuItem searchItem;
    private EmptyTextProgressView emptyView;
    private SearchAdapter searchAdapter;

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private TLRPC.Chat waitingForFullChat;
    private AlertDialog waitingForFullChatProgressAlert;
    private boolean isChannel;

    private ArrayList<TLRPC.Chat> chats = new ArrayList<>();
    private boolean loadingChats;
    private boolean waitingForChatCreate;
    private boolean chatsLoaded;

    private JoinToSendSettingsView joinToSendSettings;

    private long currentChatId;

    private int helpRow;
    private int createChatRow;
    private int chatStartRow;
    private int chatEndRow;
    private int removeChatRow;
    private int detailRow;
    private int joinToSendRow;
    private int rowCount;

    private boolean searchWas;
    private boolean searching;

    private final static int search_button = 0;

    private static class EmptyView extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {

        private BackupImageView stickerView;
        private LoadingStickerDrawable drawable;

        private int currentAccount = UserConfig.selectedAccount;

        private static final String stickerSetName = AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME;

        public EmptyView(Context context) {
            super(context);

            setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
            setOrientation(LinearLayout.VERTICAL);

            stickerView = new BackupImageView(context);
            drawable = new LoadingStickerDrawable(stickerView, "M476.1,397.4c25.8-47.2,0.3-105.9-50.9-120c-2.5-6.9-7.8-12.7-15-16.4l0.4-229.4c0-12.3-10-22.4-22.4-22.4" +
                    "H128.5c-12.3,0-22.4,10-22.4,22.4l-0.4,229.8v0c0,6.7,2.9,12.6,7.6,16.7c-51.6,15.9-79.2,77.2-48.1,116.4" +
                    "c-8.7,11.7-13.4,27.5-14,47.2c-1.7,34.5,21.6,45.8,55.9,45.8c52.3,0,99.1,4.6,105.1-36.2c16.5,0.9,7.1-37.3-6.5-53.3" +
                    "c18.4-22.4,18.3-52.9,4.9-78.2c-0.7-5.3-3.8-9.8-8.1-12.6c-1.5-2-1.6-2-2.1-2.7c0.2-1,1.2-11.8-3.4-20.9h138.5" +
                    "c-4.8,8.8-4.7,17-2.9,22.1c-5.3,4.8-6.8,12.3-5.2,17c-11.4,24.9-10,53.8,4.3,77.5c-6.8,9.7-11.2,21.7-12.6,31.6" +
                    "c-0.2-0.2-0.4-0.3-0.6-0.5c0.8-3.3,0.4-6.4-1.3-7.8c9.3-12.1-4.5-29.2-17-21.7c-3.8-2.8-10.6-3.2-18.1-0.5" +
                    "c-2.4-10.6-21.1-10.6-28.6-1c-1.3,0.3-2.9,0.8-4.5,1.9c-5.2-0.9-10.9,0.1-14.1,4.4c-6.9,3-9.5,10.4-7.8,17c-0.9,1.8-1.1,4-0.8,6.3" +
                    "c-1.6,1.2-2.3,3.1-2,4.9c0.1,0.6,10.4,56.6,11.2,62c0.3,1.8,1.5,3.2,3.1,3.9c8.7,3.4,12,3.8,30.1,9.4c2.7,0.8,2.4,0.8,6.7-0.1" +
                    "c16.4-3.5,30.2-8.9,30.8-9.2c1.6-0.6,2.7-2,3.1-3.7c0.1-0.4,6.8-36.5,10-53.2c0.9,4.2,3.3,7.3,7.4,7.5c1.2,7.8,4.4,14.5,9.5,19.9" +
                    "c16.4,17.3,44.9,15.7,64.9,16.1c38.3,0.8,74.5,1.5,84.4-24.4C488.9,453.5,491.3,421.3,476.1,397.4z", AndroidUtilities.dp(104), AndroidUtilities.dp(104));
            stickerView.setImageDrawable(drawable);
            addView(stickerView, LayoutHelper.createLinear(104, 104, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 2, 0, 0));
        }

        private void setSticker() {
            TLRPC.messages_StickerSet set = MediaDataController.getInstance(currentAccount).getStickerSetByName(stickerSetName);
            if (set == null) {
                set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(stickerSetName);
            }
            if (set != null && set.documents.size() >= 3) {
                TLRPC.Document document = set.documents.get(2);
                ImageLocation imageLocation = ImageLocation.getForDocument(document);
                stickerView.setImage(imageLocation, "104_104", "tgs", drawable, set);
            } else {
                MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(stickerSetName, false, set == null);
                stickerView.setImageDrawable(drawable);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            setSticker();
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.diceStickersDidLoad) {
                String name = (String) args[0];
                if (stickerSetName.equals(name)) {
                    setSticker();
                }
            }
        }
    }

    public ChatLinkActivity(long chatId) {
        super();

        currentChatId = chatId;
        currentChat = getMessagesController().getChat(chatId);
        isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
    }

    private void updateRows() {
        currentChat = getMessagesController().getChat(currentChatId);
        if (currentChat == null) {
            return;
        }

        rowCount = 0;
        helpRow = -1;
        createChatRow = -1;
        chatStartRow = -1;
        chatEndRow = -1;
        removeChatRow = -1;
        detailRow = -1;
        joinToSendRow = -1;

        helpRow = rowCount++;
        if (isChannel) {
            if (info.linked_chat_id == 0) {
                createChatRow = rowCount++;
            }
            chatStartRow = rowCount;
            rowCount += chats.size();
            chatEndRow = rowCount;
            if (info.linked_chat_id != 0) {
                createChatRow = rowCount++;
            }
        } else {
            chatStartRow = rowCount;
            rowCount += chats.size();
            chatEndRow = rowCount;
            createChatRow = rowCount++;
        }
        detailRow = rowCount++;
        if (!isChannel || chats.size() > 0 && info.linked_chat_id != 0) {
            TLRPC.Chat chat = isChannel ? chats.get(0) : currentChat;
            if (chat != null && (!ChatObject.isPublic(chat) || isChannel) && (chat.creator || chat.admin_rights != null && chat.admin_rights.ban_users)) {
                joinToSendRow = rowCount++;
            }
        }

        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
        if (searchItem != null) {
            searchItem.setVisibility(chats.size() > 10 ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        loadChats();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
    }

    private boolean joinToSendProgress = false;
    private boolean joinRequestProgress = false;

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == currentChatId) {
                info = chatFull;
                loadChats();
                updateRows();
            } else if (waitingForFullChat != null && waitingForFullChat.id == chatFull.id) {
                try {
                    waitingForFullChatProgressAlert.dismiss();
                } catch (Throwable ignore) {

                }
                waitingForFullChatProgressAlert = null;
                showLinkAlert(waitingForFullChat, false);
                waitingForFullChat = null;
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int updateMask = (Integer) args[0];
            if ((updateMask & MessagesController.UPDATE_MASK_CHAT) != 0 && currentChat != null) {
                TLRPC.Chat newCurrentChat = getMessagesController().getChat(currentChat.id);
                if (newCurrentChat != null) {
                    currentChat = newCurrentChat;
                }
                if (chats.size() > 0) {
                    TLRPC.Chat linkedChat = getMessagesController().getChat(chats.get(0).id);
                    if (linkedChat != null) {
                        chats.set(0, linkedChat);
                    }
                }
                final TLRPC.Chat chat = isChannel ? (chats.size() > 0 ? chats.get(0) : null) : currentChat;
                if (chat != null && joinToSendSettings != null) {
                    if (!joinRequestProgress) {
                        joinToSendSettings.setJoinRequest(chat.join_request);
                    }
                    if (!joinToSendProgress) {
                        joinToSendSettings.setJoinToSend(chat.join_to_send);
                    }
                }
            }
        }
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("Discussion", R.string.Discussion));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(search_button, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                emptyView.setShowAtCenter(true);
            }

            @Override
            public void onSearchCollapse() {
                searchAdapter.searchDialogs(null);
                searching = false;
                searchWas = false;
                listView.setAdapter(listViewAdapter);
                listViewAdapter.notifyDataSetChanged();
                listView.setFastScrollVisible(true);
                listView.setVerticalScrollBarEnabled(false);
                emptyView.setShowAtCenter(false);
                fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                fragmentView.setTag(Theme.key_windowBackgroundGray);
                emptyView.showProgress();
            }

            @Override
            public void onTextChanged(EditText editText) {
                if (searchAdapter == null) {
                    return;
                }
                String text = editText.getText().toString();
                if (text.length() != 0) {
                    searchWas = true;
                    if (listView != null && listView.getAdapter() != searchAdapter) {
                        listView.setAdapter(searchAdapter);
                        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        fragmentView.setTag(Theme.key_windowBackgroundWhite);
                        searchAdapter.notifyDataSetChanged();
                        listView.setFastScrollVisible(false);
                        listView.setVerticalScrollBarEnabled(true);
                        emptyView.showProgress();
                    }
                }
                searchAdapter.searchDialogs(text);
            }
        });
        searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
        searchAdapter = new SearchAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView.setTag(Theme.key_windowBackgroundGray);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        emptyView = new EmptyTextProgressView(context);
        emptyView.showProgress();
        emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setEmptyView(emptyView);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }
            final TLRPC.Chat chat;
            if (listView.getAdapter() == searchAdapter) {
                chat = searchAdapter.getItem(position);
            } else if (position >= chatStartRow && position < chatEndRow) {
                chat = chats.get(position - chatStartRow);
            } else {
                chat = null;
            }
            if (chat != null) {
                if (isChannel && info.linked_chat_id == 0) {
                    showLinkAlert(chat, true);
                } else {
                    Bundle args = new Bundle();
                    args.putLong("chat_id", chat.id);
                    presentFragment(new ChatActivity(args));
                }
                return;
            }
            if (position == createChatRow) {
                if (isChannel && info.linked_chat_id == 0) {
                    Bundle args = new Bundle();
                    long[] array = new long[]{getUserConfig().getClientUserId()};
                    args.putLongArray("result", array);
                    args.putInt("chatType", ChatObject.CHAT_TYPE_MEGAGROUP);
                    if (currentChat != null) {
                        String title = LocaleController.formatString("GroupCreateDiscussionDefaultName", R.string.GroupCreateDiscussionDefaultName, currentChat.title);
                        args.putString("title", title);
                    }
                    GroupCreateFinalActivity activity = new GroupCreateFinalActivity(args);
                    activity.setDelegate(new GroupCreateFinalActivity.GroupCreateFinalActivityDelegate() {
                        @Override
                        public void didStartChatCreation() {

                        }

                        @Override
                        public void didFinishChatCreation(GroupCreateFinalActivity fragment, long chatId) {
                            linkChat(getMessagesController().getChat(chatId), fragment);
                        }

                        @Override
                        public void didFailChatCreation() {

                        }
                    });
                    presentFragment(activity);
                } else {
                    if (chats.isEmpty()) {
                        return;
                    }
                    TLRPC.Chat c = chats.get(0);

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    String title;
                    String message;
                    if (isChannel) {
                        title = LocaleController.getString("DiscussionUnlinkGroup", R.string.DiscussionUnlinkGroup);
                        message = LocaleController.formatString("DiscussionUnlinkChannelAlert", R.string.DiscussionUnlinkChannelAlert, c.title);
                    } else {
                        title = LocaleController.getString("DiscussionUnlink", R.string.DiscussionUnlinkChannel);
                        message = LocaleController.formatString("DiscussionUnlinkGroupAlert", R.string.DiscussionUnlinkGroupAlert, c.title);
                    }
                    builder.setTitle(title);
                    builder.setMessage(AndroidUtilities.replaceTags(message));
                    builder.setPositiveButton(LocaleController.getString("DiscussionUnlink", R.string.DiscussionUnlink), (dialogInterface, i) -> {
                        if (!isChannel || info.linked_chat_id != 0) {
                            final AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER)};
                            TLRPC.TL_channels_setDiscussionGroup req = new TLRPC.TL_channels_setDiscussionGroup();
                            if (isChannel) {
                                req.broadcast = MessagesController.getInputChannel(currentChat);
                                req.group = new TLRPC.TL_inputChannelEmpty();
                            } else {
                                req.broadcast = new TLRPC.TL_inputChannelEmpty();
                                req.group = MessagesController.getInputChannel(currentChat);
                            }
                            int requestId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                                try {
                                    progressDialog[0].dismiss();
                                } catch (Throwable ignore) {

                                }
                                progressDialog[0] = null;
                                info.linked_chat_id = 0;
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, false);
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().loadFullChat(currentChatId, 0, true), 1000);
                                if (!isChannel) {
                                    finishFragment();
                                }
                            }));
                            AndroidUtilities.runOnUIThread(() -> {
                                if (progressDialog[0] == null) {
                                    return;
                                }
                                progressDialog[0].setOnCancelListener(dialog -> ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true));
                                showDialog(progressDialog[0]);
                            }, 500);
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    AlertDialog dialog = builder.create();
                    showDialog(dialog);
                    TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    }
                }
            }
        });

        updateRows();
        return fragmentView;
    }

    private void showLinkAlert(TLRPC.Chat chat, boolean query) {
        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(chat.id);
        if (chatFull == null) {
            if (query) {
                getMessagesController().loadFullChat(chat.id, 0, true);
                waitingForFullChat = chat;
                waitingForFullChatProgressAlert = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                AndroidUtilities.runOnUIThread(() -> {
                    if (waitingForFullChatProgressAlert == null) {
                        return;
                    }
                    waitingForFullChatProgressAlert.setOnCancelListener(dialog -> waitingForFullChat = null);
                    showDialog(waitingForFullChatProgressAlert);
                }, 500);
            }
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

        TextView messageTextView = new TextView(getParentActivity());
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        String message;
        if (!ChatObject.isPublic(chat)) {
            message = LocaleController.formatString("DiscussionLinkGroupPublicPrivateAlert", R.string.DiscussionLinkGroupPublicPrivateAlert, chat.title, currentChat.title);
        } else {
            if (!ChatObject.isPublic(currentChat)) {
                message = LocaleController.formatString("DiscussionLinkGroupPrivateAlert", R.string.DiscussionLinkGroupPrivateAlert, chat.title, currentChat.title);
            } else {
                message = LocaleController.formatString("DiscussionLinkGroupPublicAlert", R.string.DiscussionLinkGroupPublicAlert, chat.title, currentChat.title);
            }
        }
        if (chatFull.hidden_prehistory) {
            message += "\n\n" + LocaleController.getString("DiscussionLinkGroupAlertHistory", R.string.DiscussionLinkGroupAlertHistory);
        }
        messageTextView.setText(AndroidUtilities.replaceTags(message));

        FrameLayout frameLayout2 = new FrameLayout(getParentActivity());
        builder.setView(frameLayout2);

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(12));

        BackupImageView imageView = new BackupImageView(getParentActivity());
        imageView.setRoundRadius(AndroidUtilities.dp(20));
        frameLayout2.addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 22, 5, 22, 0));

        TextView textView = new TextView(getParentActivity());
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setText(chat.title);

        frameLayout2.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 11, (LocaleController.isRTL ? 76 : 21), 0));
        frameLayout2.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 57, 24, 9));
        avatarDrawable.setInfo(chat);
        imageView.setForUserOrChat(chat, avatarDrawable);
        builder.setPositiveButton(LocaleController.getString("DiscussionLinkGroup", R.string.DiscussionLinkGroup), (dialogInterface, i) -> {
            if (chatFull.hidden_prehistory) {
                getMessagesController().toggleChannelInvitesHistory(chat.id, false);
            }
            linkChat(chat, null);
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void linkChat(TLRPC.Chat chat, BaseFragment createFragment) {
        if (chat == null) {
            return;
        }
        if (!ChatObject.isChannel(chat)) {
            getMessagesController().convertToMegaGroup(getParentActivity(), chat.id, this, param -> {
                if (param != 0) {
                    getMessagesController().toggleChannelInvitesHistory(param, false);
                    linkChat(getMessagesController().getChat(param), createFragment);
                }
            });
            return;
        }
        final AlertDialog[] progressDialog = new AlertDialog[]{createFragment != null ? null : new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER)};
        TLRPC.TL_channels_setDiscussionGroup req = new TLRPC.TL_channels_setDiscussionGroup();
        req.broadcast = MessagesController.getInputChannel(currentChat);
        req.group = MessagesController.getInputChannel(chat);
        int requestId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (progressDialog[0] != null) {
                try {
                    progressDialog[0].dismiss();
                } catch (Throwable ignore) {

                }
                progressDialog[0] = null;
            }
            info.linked_chat_id = chat.id;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, false);
            AndroidUtilities.runOnUIThread(() -> getMessagesController().loadFullChat(currentChatId, 0, true), 1000);
            if (createFragment != null) {
                removeSelfFromStack();
                createFragment.finishFragment();
            } else {
                finishFragment();
            }
        }), ConnectionsManager.RequestFlagInvokeAfter);
        AndroidUtilities.runOnUIThread(() -> {
            if (progressDialog[0] == null) {
                return;
            }
            progressDialog[0].setOnCancelListener(dialog -> ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true));
            showDialog(progressDialog[0]);
        }, 500);
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
    }

    private void loadChats() {
        if (info.linked_chat_id != 0) {
            chats.clear();
            TLRPC.Chat chat = getMessagesController().getChat(info.linked_chat_id);
            if (chat != null) {
                chats.add(chat);
            }
            if (searchItem != null) {
                searchItem.setVisibility(View.GONE);
            }
        }
        if (loadingChats || !isChannel || info.linked_chat_id != 0) {
            return;
        }
        loadingChats = true;
        TLRPC.TL_channels_getGroupsForDiscussion req = new TLRPC.TL_channels_getGroupsForDiscussion();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.messages_Chats) {
                TLRPC.messages_Chats res = (TLRPC.messages_Chats) response;
                getMessagesController().putChats(res.chats, false);
                chats = res.chats;
                Iterator<TLRPC.Chat> i = chats.iterator();
                while (i.hasNext()) {
                    if (ChatObject.isForum(i.next()))
                        i.remove();
                }
            }
            loadingChats = false;
            chatsLoaded = true;
            updateRows();
        }));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    public class HintInnerCell extends FrameLayout {

        private EmptyView emptyView;
        private TextView messageTextView;

        public HintInnerCell(Context context) {
            super(context);

            emptyView = new EmptyView(context);
            addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0));

            messageTextView = new TextView(context);
            messageTextView.setTextColor(Theme.getColor(Theme.key_chats_message));
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            messageTextView.setGravity(Gravity.CENTER);
            if (isChannel) {
                if (info != null && info.linked_chat_id != 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(info.linked_chat_id);
                    if (chat != null) {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("DiscussionChannelGroupSetHelp2", R.string.DiscussionChannelGroupSetHelp2, chat.title)));
                    }
                } else {
                    messageTextView.setText(LocaleController.getString("DiscussionChannelHelp3", R.string.DiscussionChannelHelp3));
                }
            } else {
                TLRPC.Chat chat = getMessagesController().getChat(info.linked_chat_id);
                if (chat != null) {
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("DiscussionGroupHelp", R.string.DiscussionGroupHelp, chat.title)));
                }
            }

            addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 143, 52, 18));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }


    private class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<TLRPC.Chat> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private Runnable searchRunnable;

        public SearchAdapter(Context context) {
            mContext = context;
        }

        public void searchDialogs(final String query) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            if (TextUtils.isEmpty(query)) {
                searchResult.clear();
                searchResultNames.clear();
                notifyDataSetChanged();
            } else {
                Utilities.searchQueue.postRunnable(searchRunnable = () -> processSearch(query), 300);
            }
        }

        private void processSearch(final String query) {
            AndroidUtilities.runOnUIThread(() -> {
                searchRunnable = null;

                ArrayList<TLRPC.Chat> chatsCopy = new ArrayList<>(chats);

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
                    String[] search = new String[1 + (search2 != null ? 1 : 0)];
                    search[0] = search1;
                    if (search2 != null) {
                        search[1] = search2;
                    }
                    ArrayList<TLRPC.Chat> resultArray = new ArrayList<>();
                    ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                    for (int a = 0; a < chatsCopy.size(); a++) {
                        TLRPC.Chat chat = chatsCopy.get(a);

                        String name = chat.title.toLowerCase();
                        String tName = LocaleController.getInstance().getTranslitString(name);
                        if (name.equals(tName)) {
                            tName = null;
                        }

                        String username = null;
                        int found = 0;
                        for (String q : search) {
                            if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                found = 1;
                            } else if (chat.username != null && chat.username.startsWith(q)) {
                                found = 2;
                                username = chat.username;
                            } else if (chat.usernames != null && !chat.usernames.isEmpty()) {
                                for (int i = 0; i < chat.usernames.size(); ++i) {
                                    TLRPC.TL_username u = chat.usernames.get(i);
                                    if (u.active && u.username.startsWith(q)) {
                                        found = 2;
                                        username = u.username;
                                        break;
                                    }
                                }
                            }

                            if (found != 0) {
                                if (found == 1) {
                                    resultArrayNames.add(AndroidUtilities.generateSearchName(chat.title, null, q));
                                } else {
                                    resultArrayNames.add(AndroidUtilities.generateSearchName("@" + username, null, "@" + q));
                                }
                                resultArray.add(chat);
                                break;
                            }
                        }
                    }
                    updateSearchResults(resultArray, resultArrayNames);
                });
            });
        }

        private void updateSearchResults(final ArrayList<TLRPC.Chat> chats, final ArrayList<CharSequence> names) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchResult = chats;
                searchResultNames = names;
                if (listView.getAdapter() == searchAdapter) {
                    emptyView.showTextView();
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
            return searchResult.size();
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
        }

        public TLRPC.Chat getItem(int i) {
            return searchResult.get(i);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new ManageChatUserCell(mContext, 6, 2, false);
            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            TLRPC.Chat chat = searchResult.get(position);
            String un = ChatObject.getPublicUsername(chat);
            CharSequence username = null;
            CharSequence name = searchResultNames.get(position);
            if (name != null && !TextUtils.isEmpty(un)) {
                if (name.toString().startsWith("@" + un)) {
                    username = name;
                    name = null;
                }
            }

            ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
            userCell.setTag(position);
            userCell.setData(chat, name, username, false);
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ((ManageChatUserCell) holder.itemView).recycle();
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
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 0 || type == 2;
        }

        @Override
        public int getItemCount() {
            if (loadingChats && !chatsLoaded) {
                return 0;
            }
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new ManageChatUserCell(mContext, 6, 2, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 2:
                    view = new ManageChatTextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    final TLRPC.Chat chat = isChannel ? chats.get(0) : currentChat;
                    view = joinToSendSettings = new JoinToSendSettingsView(mContext, chat) {
                        private void migrateIfNeeded(Runnable onError, Runnable onSuccess) {
                            if (!ChatObject.isChannel(currentChat)) {
                                getMessagesController().convertToMegaGroup(getParentActivity(), chat.id, ChatLinkActivity.this, param -> {
                                    if (param != 0) {
                                        if (isChannel) {
                                            chats.set(0, getMessagesController().getChat(param));
                                        } else {
                                            currentChatId = param;
                                            currentChat = getMessagesController().getChat(param);
                                        }
                                        onSuccess.run();
                                    }
                                }, onError);
                            } else {
                                onSuccess.run();
                            }
                        }

                        @Override
                        public boolean onJoinRequestToggle(boolean newValue, Runnable cancel) {
                            if (joinRequestProgress) {
                                return false;
                            }
                            joinRequestProgress = true;
                            migrateIfNeeded(overrideCancel(cancel), () -> {
                                chat.join_request = newValue;
                                getMessagesController().toggleChatJoinRequest(chat.id, newValue, () -> {
                                    joinRequestProgress = false;
                                }, () -> {
                                    joinRequestProgress = false;
                                    cancel.run();
                                });
                            });
                            return true;
                        }

                        private Runnable overrideCancel(Runnable cancel) {
                            return () -> {
                                joinToSendProgress = false;
                                joinRequestProgress = false;
                                cancel.run();
                            };
                        }

                        @Override
                        public boolean onJoinToSendToggle(boolean newValue, Runnable cancel) {
                            if (joinToSendProgress) {
                                return false;
                            }
                            joinToSendProgress = true;
                            migrateIfNeeded(overrideCancel(cancel), () -> {
                                chat.join_to_send = newValue;
                                getMessagesController().toggleChatJoinToSend(chat.id, newValue, () -> {
                                    joinToSendProgress = false;
                                    if (!newValue && chat.join_request) {
                                        chat.join_request = false;
                                        joinRequestProgress = true;
                                        getMessagesController().toggleChatJoinRequest(chat.id, false, () -> {
                                            joinRequestProgress = false;
                                        }, () -> {
                                            chat.join_request = true;
                                            isJoinRequest = true;
                                            joinRequestCell.setChecked(true);
                                        });
                                    }
                                }, () -> {
                                    joinToSendProgress = false;
                                    cancel.run();
                                });
                            });
                            return true;
                        }
                    };
                    break;
                case 3:
                default:
                    view = new HintInnerCell(mContext);
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
                    TLRPC.Chat chat = chats.get(position - chatStartRow);
                    String username;
                    userCell.setData(chat, null, TextUtils.isEmpty(username = ChatObject.getPublicUsername(chat)) ? null : "@" + username, position != chatEndRow - 1 || info.linked_chat_id != 0);
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == detailRow) {
                        if (isChannel) {
                            privacyCell.setText(LocaleController.getString("DiscussionChannelHelp2", R.string.DiscussionChannelHelp2));
                        } else {
                            privacyCell.setText(LocaleController.getString("DiscussionGroupHelp2", R.string.DiscussionGroupHelp2));
                        }
                    }
                    break;
                case 2:
                    ManageChatTextCell actionCell = (ManageChatTextCell) holder.itemView;
                    if (isChannel) {
                        if (info.linked_chat_id != 0) {
                            actionCell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                            actionCell.setText(LocaleController.getString("DiscussionUnlinkGroup", R.string.DiscussionUnlinkGroup), null, R.drawable.msg_remove, false);
                        } else {
                            actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                            actionCell.setText(LocaleController.getString("DiscussionCreateGroup", R.string.DiscussionCreateGroup), null, R.drawable.msg_groups, true);
                        }
                    } else {
                        actionCell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                        actionCell.setText(LocaleController.getString("DiscussionUnlinkChannel", R.string.DiscussionUnlinkChannel), null, R.drawable.msg_remove, false);
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
            if (position == helpRow) {
                return 3;
            } else if (position == createChatRow || position == removeChatRow) {
                return 2;
            } else if (position >= chatStartRow && position < chatEndRow) {
                return 0;
            } else if (position == joinToSendRow) {
                return 4;
            }
            return 1;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

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

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ManageChatUserCell.class, ManageChatTextCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HintInnerCell.class}, new String[]{"messageTextView"}, null, null, null, Theme.key_chats_message));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon));

        return themeDescriptions;
    }
}
