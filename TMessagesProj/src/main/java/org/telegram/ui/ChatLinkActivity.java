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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
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
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    private int currentChatId;

    private int helpRow;
    private int createChatRow;
    private int chatStartRow;
    private int chatEndRow;
    private int removeChatRow;
    private int detailRow;
    private int rowCount;

    private boolean searchWas;
    private boolean searching;

    private final static int search_button = 0;

    public ChatLinkActivity(int chatId) {
        super();

        currentChatId = chatId;
        currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
        isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
    }

    private void updateRows() {
        currentChat = MessagesController.getInstance(currentAccount).getChat(currentChatId);
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
            detailRow = rowCount++;
        } else {
            chatStartRow = rowCount;
            rowCount += chats.size();
            chatEndRow = rowCount;
            createChatRow = rowCount++;
            detailRow = rowCount++;
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
        loadChats();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
    }

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
                    args.putInt("chat_id", chat.id);
                    presentFragment(new ChatActivity(args));
                }
                return;
            }
            if (position == createChatRow) {
                if (isChannel && info.linked_chat_id == 0) {
                    Bundle args = new Bundle();
                    ArrayList<Integer> result = new ArrayList<>();
                    result.add(getUserConfig().getClientUserId());
                    args.putIntegerArrayList("result", result);
                    args.putInt("chatType", ChatObject.CHAT_TYPE_MEGAGROUP);
                    GroupCreateFinalActivity activity = new GroupCreateFinalActivity(args);
                    activity.setDelegate(new GroupCreateFinalActivity.GroupCreateFinalActivityDelegate() {
                        @Override
                        public void didStartChatCreation() {

                        }

                        @Override
                        public void didFinishChatCreation(GroupCreateFinalActivity fragment, int chatId) {
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
                            final AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(getParentActivity(), 3)};
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
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, null);
                                getMessagesController().loadFullChat(currentChatId, 0, true);
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
                        button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
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
                waitingForFullChatProgressAlert = new AlertDialog(getParentActivity(), 3);
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
        if (TextUtils.isEmpty(chat.username)) {
            message = LocaleController.formatString("DiscussionLinkGroupPublicPrivateAlert", R.string.DiscussionLinkGroupPublicPrivateAlert, chat.title, currentChat.title);
        } else {
            if (TextUtils.isEmpty(currentChat.username)) {
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
        imageView.setImage(ImageLocation.getForChat(chat, false), "50_50", avatarDrawable, chat);
        builder.setPositiveButton(LocaleController.getString("DiscussionLinkGroup", R.string.DiscussionLinkGroup), (dialogInterface, i) -> {
            if (chatFull.hidden_prehistory) {
                MessagesController.getInstance(currentAccount).toogleChannelInvitesHistory(chat.id, false);
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
            MessagesController.getInstance(currentAccount).convertToMegaGroup(getParentActivity(), chat.id, this, param -> {
                MessagesController.getInstance(currentAccount).toogleChannelInvitesHistory(param, false);
                linkChat(getMessagesController().getChat(param), createFragment);
            });
            return;
        }
        final AlertDialog[] progressDialog = new AlertDialog[]{createFragment != null ? null : new AlertDialog(getParentActivity(), 3)};
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
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, null);
            getMessagesController().loadFullChat(currentChatId, 0, true);
            if (createFragment != null) {
                removeSelfFromStack();
                createFragment.finishFragment();
            } else {
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
            }
            loadingChats = false;
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

    public class HintInnerCell extends FrameLayout {

        private ImageView imageView;
        private TextView messageTextView;

        public HintInnerCell(Context context) {
            super(context);

            imageView = new ImageView(context);
            imageView.setImageResource(Theme.getCurrentTheme().isDark() ? R.drawable.tip6_dark : R.drawable.tip6);
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 20, 8, 0));

            messageTextView = new TextView(context);
            messageTextView.setTextColor(Theme.getColor(Theme.key_chats_message));
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            messageTextView.setGravity(Gravity.CENTER);
            if (isChannel) {
                if (info != null && info.linked_chat_id != 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(info.linked_chat_id);
                    if (chat != null) {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("DiscussionChannelGroupSetHelp", R.string.DiscussionChannelGroupSetHelp, chat.title)));
                    }
                } else {
                    messageTextView.setText(LocaleController.getString("DiscussionChannelHelp", R.string.DiscussionChannelHelp));
                }
            } else {
                TLRPC.Chat chat = getMessagesController().getChat(info.linked_chat_id);
                if (chat != null) {
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("DiscussionGroupHelp", R.string.DiscussionGroupHelp, chat.title)));
                }
            }

            addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 124, 52, 27));
        }
    }

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<TLRPC.Chat> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private Runnable searchRunnable;

        private int searchStartRow;
        private int totalCount;

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

                        int found = 0;
                        for (String q : search) {
                            if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                found = 1;
                            } else if (chat.username != null && chat.username.startsWith(q)) {
                                found = 2;
                            }

                            if (found != 0) {
                                if (found == 1) {
                                    resultArrayNames.add(AndroidUtilities.generateSearchName(chat.title, null, q));
                                } else {
                                    resultArrayNames.add(AndroidUtilities.generateSearchName("@" + chat.username, null, "@" + q));
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
            totalCount = 0;
            int count = searchResult.size();
            if (count != 0) {
                searchStartRow = totalCount;
                totalCount += count + 1;
            } else {
                searchStartRow = -1;
            }
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
            String un = chat.username;
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
            if (loadingChats) {
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
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 2:
                    view = new ManageChatTextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
                    userCell.setData(chat, null, TextUtils.isEmpty(chat.username) ? null : "@" + chat.username, position != chatEndRow - 1 || info.linked_chat_id != 0);
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
                            actionCell.setColors(Theme.key_windowBackgroundWhiteRedText5, Theme.key_windowBackgroundWhiteRedText5);
                            actionCell.setText(LocaleController.getString("DiscussionUnlinkGroup", R.string.DiscussionUnlinkGroup), null, R.drawable.actions_remove_user, false);
                        } else {
                            actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                            actionCell.setText(LocaleController.getString("DiscussionCreateGroup", R.string.DiscussionCreateGroup), null, R.drawable.menu_groups, true);
                        }
                    } else {
                        actionCell.setColors(Theme.key_windowBackgroundWhiteRedText5, Theme.key_windowBackgroundWhiteRedText5);
                        actionCell.setText(LocaleController.getString("DiscussionUnlinkChannel", R.string.DiscussionUnlinkChannel), null, R.drawable.actions_remove_user, false);
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
            }
            return 1;
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
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ManageChatUserCell.class, ManageChatTextCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, null, new Drawable[]{Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(listView, 0, new Class[]{HintInnerCell.class}, new String[]{"messageTextView"}, null, null, null, Theme.key_chats_message),

                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon),
        };
    }
}
