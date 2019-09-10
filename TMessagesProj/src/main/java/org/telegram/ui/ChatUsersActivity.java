/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
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
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
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
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ChatUsersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listViewAdapter;
    private EmptyTextProgressView emptyView;
    private RecyclerListView listView;
    private SearchAdapter searchListViewAdapter;
    private ActionBarMenuItem searchItem;
    private ActionBarMenuItem doneItem;
    private UndoView undoView;

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private boolean isChannel;

    private String initialBannedRights;
    private TLRPC.TL_chatBannedRights defaultBannedRights = new TLRPC.TL_chatBannedRights();
    private ArrayList<TLObject> participants = new ArrayList<>();
    private ArrayList<TLObject> bots = new ArrayList<>();
    private ArrayList<TLObject> contacts = new ArrayList<>();
    private boolean botsEndReached;
    private boolean contactsEndReached;
    private SparseArray<TLObject> participantsMap = new SparseArray<>();
    private SparseArray<TLObject> botsMap = new SparseArray<>();
    private SparseArray<TLObject> contactsMap = new SparseArray<>();
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

    private int slowmodeRow;
    private int slowmodeSelectRow;
    private int slowmodeInfoRow;

    private int contactsHeaderRow;
    private int contactsStartRow;
    private int contactsEndRow;
    private int botHeaderRow;
    private int botStartRow;
    private int botEndRow;
    private int membersHeaderRow;

    private int participantsInfoRow;
    private int blockedEmptyRow;
    private int rowCount;
    private int selectType;

    private int delayResults;

    private ChatUsersActivityDelegate delegate;

    private boolean needOpenSearch;

    private boolean searchWas;
    private boolean searching;

    private int selectedSlowmode;
    private int initialSlowmode;

    private final static int search_button = 0;
    private final static int done_button = 1;

    public final static int TYPE_BANNED = 0;
    public final static int TYPE_ADMIN = 1;
    public final static int TYPE_USERS = 2;
    public final static int TYPE_KICKED = 3;

    public interface ChatUsersActivityDelegate {
        void didAddParticipantToList(int uid, TLObject participant);
        void didChangeOwner(TLRPC.User user);
    }

    private class ChooseView extends View {

        private Paint paint;
        private TextPaint textPaint;

        private int circleSize;
        private int gapSize;
        private int sideSide;
        private int lineSize;

        private boolean moving;
        private boolean startMoving;
        private float startX;

        private int startMovingItem;

        private ArrayList<String> strings = new ArrayList<>();
        private ArrayList<Integer> sizes = new ArrayList<>();

        public ChooseView(Context context) {
            super(context);

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(13));

            for (int a = 0; a < 7; a++) {
                String string;
                switch (a) {
                    case 0:
                        string = LocaleController.getString("SlowmodeOff", R.string.SlowmodeOff);
                        break;
                    case 1:
                        string = LocaleController.formatString("SlowmodeSeconds", R.string.SlowmodeSeconds, 10);
                        break;
                    case 2:
                        string = LocaleController.formatString("SlowmodeSeconds", R.string.SlowmodeSeconds, 30);
                        break;
                    case 3:
                        string = LocaleController.formatString("SlowmodeMinutes", R.string.SlowmodeMinutes, 1);
                        break;
                    case 4:
                        string = LocaleController.formatString("SlowmodeMinutes", R.string.SlowmodeMinutes, 5);
                        break;
                    case 5:
                        string = LocaleController.formatString("SlowmodeMinutes", R.string.SlowmodeMinutes, 15);
                        break;
                    case 6:
                    default:
                        string = LocaleController.formatString("SlowmodeHours", R.string.SlowmodeHours, 1);
                        break;
                }
                strings.add(string);
                sizes.add((int) Math.ceil(textPaint.measureText(string)));
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
                for (int a = 0; a < strings.size(); a++) {
                    int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                    if (x > cx - AndroidUtilities.dp(15) && x < cx + AndroidUtilities.dp(15)) {
                        startMoving = a == selectedSlowmode;
                        startX = x;
                        startMovingItem = selectedSlowmode;
                        break;
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (startMoving) {
                    if (Math.abs(startX - x) >= AndroidUtilities.getPixelsInCM(0.5f, true)) {
                        moving = true;
                        startMoving = false;
                    }
                } else if (moving) {
                    for (int a = 0; a < strings.size(); a++) {
                        int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                        int diff = lineSize / 2 + circleSize / 2 + gapSize;
                        if (x > cx - diff && x < cx + diff) {
                            if (selectedSlowmode != a) {
                                setItem(a);
                            }
                            break;
                        }
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (!moving) {
                    for (int a = 0; a < strings.size(); a++) {
                        int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                        if (x > cx - AndroidUtilities.dp(15) && x < cx + AndroidUtilities.dp(15)) {
                            if (selectedSlowmode != a) {
                                setItem(a);
                            }
                            break;
                        }
                    }
                } else {
                    if (selectedSlowmode != startMovingItem) {
                        setItem(selectedSlowmode);
                    }
                }
                startMoving = false;
                moving = false;
            }
            return true;
        }

        private void setItem(int index) {
            if (info == null) {
                return;
            }
            selectedSlowmode = index;
            info.slowmode_seconds = getSecondsForIndex(index);
            info.flags |= 131072;
            for (int a = 0; a < 3; a++) {
                RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(slowmodeInfoRow);
                if (holder != null) {
                    listViewAdapter.onBindViewHolder(holder, slowmodeInfoRow);
                }
            }
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(74), MeasureSpec.EXACTLY));
            int width = MeasureSpec.getSize(widthMeasureSpec);
            circleSize = AndroidUtilities.dp(6);
            gapSize = AndroidUtilities.dp(2);
            sideSide = AndroidUtilities.dp(22);
            lineSize = (getMeasuredWidth() - circleSize * strings.size() - gapSize * 2 * (strings.size() - 1)  - sideSide * 2) / (strings.size() - 1);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            int cy = getMeasuredHeight() / 2 + AndroidUtilities.dp(11);

            for (int a = 0; a < strings.size(); a++) {
                int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                if (a <= selectedSlowmode) {
                    paint.setColor(Theme.getColor(Theme.key_switchTrackChecked));
                } else {
                    paint.setColor(Theme.getColor(Theme.key_switchTrack));
                }
                canvas.drawCircle(cx, cy, a == selectedSlowmode ? AndroidUtilities.dp(6) : circleSize / 2, paint);
                if (a != 0) {
                    int x = cx - circleSize / 2 - gapSize - lineSize;
                    int width = lineSize;
                    if (a == selectedSlowmode || a == selectedSlowmode + 1) {
                        width -= AndroidUtilities.dp(3);
                    }
                    if (a == selectedSlowmode + 1) {
                        x += AndroidUtilities.dp(3);
                    }
                    canvas.drawRect(x, cy - AndroidUtilities.dp(1), x + width, cy + AndroidUtilities.dp(1), paint);
                }
                int size = sizes.get(a);
                String text = strings.get(a);
                if (a == 0) {
                    canvas.drawText(text, AndroidUtilities.dp(22), AndroidUtilities.dp(28), textPaint);
                } else if (a == strings.size() - 1) {
                    canvas.drawText(text, getMeasuredWidth() - size - AndroidUtilities.dp(22), AndroidUtilities.dp(28), textPaint);
                } else {
                    canvas.drawText(text, cx - size / 2, AndroidUtilities.dp(28), textPaint);
                }
            }
        }
    }

    public ChatUsersActivity(Bundle args) {
        super(args);
        chatId = arguments.getInt("chat_id");
        type = arguments.getInt("type");
        needOpenSearch = arguments.getBoolean("open_search");
        selectType = arguments.getInt("selectType");
        currentChat = getMessagesController().getChat(chatId);
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
        currentChat = getMessagesController().getChat(chatId);
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
        contactsHeaderRow = -1;
        contactsStartRow = -1;
        contactsEndRow = -1;
        botHeaderRow = -1;
        botStartRow = -1;
        botEndRow = -1;
        membersHeaderRow = -1;
        slowmodeRow = -1;
        slowmodeSelectRow = -1;
        slowmodeInfoRow = -1;

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
            if (!ChatObject.isChannel(currentChat) && currentChat.creator || currentChat.megagroup && ChatObject.canBlockUsers(currentChat)) {
                participantsDivider2Row = rowCount++;
                slowmodeRow = rowCount++;
                slowmodeSelectRow = rowCount++;
                slowmodeInfoRow = rowCount++;
            }
            if (ChatObject.isChannel(currentChat)) {
                if (participantsDivider2Row == -1) {
                    participantsDivider2Row = rowCount++;
                }
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
                /*if (ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE) && (!ChatObject.isChannel(currentChat) || currentChat.megagroup || TextUtils.isEmpty(currentChat.username))) {
                    addNew2Row = rowCount++;
                    addNewSectionRow = rowCount++;
                }*/
                addNewRow = rowCount++;
            }
            boolean hasAnyOther = false;
            if (!contacts.isEmpty()) {
                contactsHeaderRow = rowCount++;
                contactsStartRow = rowCount;
                rowCount += contacts.size();
                contactsEndRow = rowCount;
                hasAnyOther = true;
            }
            if (!bots.isEmpty()) {
                botHeaderRow = rowCount++;
                botStartRow = rowCount;
                rowCount += bots.size();
                botEndRow = rowCount;
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
            if (rowCount != 0) {
                participantsInfoRow = rowCount++;
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        loadChatParticipants(0, 200);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
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

            if (type == TYPE_KICKED) {
                doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));
            }
        }

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        emptyView = new EmptyTextProgressView(context);
        if (type == TYPE_BANNED || type == TYPE_USERS || type == TYPE_KICKED) {
            emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        }
        emptyView.setShowAtCenter(true);
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
                        fragment.setDelegate(new ChatUsersActivityDelegate() {
                            @Override
                            public void didAddParticipantToList(int uid, TLObject participant) {
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
                                    if (listViewAdapter != null) {
                                        listViewAdapter.notifyDataSetChanged();
                                    }
                                }
                            }

                            @Override
                            public void didChangeOwner(TLRPC.User user) {
                                onOwnerChaged(user);
                            }
                        });
                        fragment.setInfo(info);
                        presentFragment(fragment);
                    } else if (type == TYPE_USERS) {
                        Bundle args = new Bundle();
                        args.putBoolean("addToGroup", true);
                        args.putInt(isChannel ? "channelId" : "chatId", currentChat.id);
                        GroupCreateActivity fragment = new GroupCreateActivity(args);
                        fragment.setInfo(info);
                        fragment.setIgnoreUsers(contactsMap != null && contactsMap.size() != 0 ? contactsMap : participantsMap);
                        fragment.setDelegate(new GroupCreateActivity.ContactsAddActivityDelegate() {
                            @Override
                            public void didSelectUsers(ArrayList<TLRPC.User> users, int fwdCount) {
                                for (int a = 0, N = users.size(); a < N; a++) {
                                    TLRPC.User user = users.get(a);
                                    getMessagesController().addUserToChat(chatId, user, null, fwdCount, null, ChatUsersActivity.this, null);
                                }
                            }

                            @Override
                            public void needAddBot(TLRPC.User user) {
                                openRightsEdit(user.id, null, null, null, "", true, ChatRightsEditActivity.TYPE_ADMIN, false);
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
            String rank = "";
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
                    rank = channelParticipant.rank;
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
                    getMessagesController().putUser(user, false);
                    participant = getAnyParticipant(user_id = user.id);
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
                    rank = channelParticipant.rank;
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
                        if (selectType != 1 && canEditAdmin && (participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_chatParticipantAdmin)) {
                            final TLRPC.User user = getMessagesController().getUser(user_id);
                            final TLRPC.TL_chatBannedRights br = bannedRights;
                            final TLRPC.TL_chatAdminRights ar = adminRights;
                            final boolean canEdit = canEditAdmin;
                            final String rankFinal = rank;
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setMessage(LocaleController.formatString("AdminWillBeRemoved", R.string.AdminWillBeRemoved, ContactsController.formatName(user.first_name, user.last_name)));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> openRightsEdit(user.id, participant, ar, br, rankFinal, canEdit, selectType == 1 ? 0 : 1, false));
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showDialog(builder.create());
                        } else {
                            openRightsEdit(user_id, participant, adminRights, bannedRights, rank, canEditAdmin, selectType == 1 ? 0 : 1, selectType == 1 || selectType == 3);
                        }
                    } else {
                        removeUser(user_id);
                    }
                } else {
                    boolean canEdit = false;
                    if (type == TYPE_ADMIN) {
                        canEdit = user_id != getUserConfig().getClientUserId() && (currentChat.creator || canEditAdmin);
                    } else if (type == TYPE_BANNED || type == TYPE_KICKED) {
                        canEdit = ChatObject.canBlockUsers(currentChat);
                    }
                    if (type == TYPE_BANNED || type != TYPE_ADMIN && isChannel || type == TYPE_USERS && selectType == 0) {
                        if (user_id == getUserConfig().getClientUserId()) {
                            return;
                        }
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
                        ChatRightsEditActivity fragment = new ChatRightsEditActivity(user_id, chatId, adminRights, defaultBannedRights, bannedRights, rank, type == TYPE_ADMIN ? ChatRightsEditActivity.TYPE_ADMIN : ChatRightsEditActivity.TYPE_BANNED, canEdit, participant == null);
                        fragment.setDelegate(new ChatRightsEditActivity.ChatRightsEditActivityDelegate() {
                            @Override
                            public void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank) {
                                if (participant instanceof TLRPC.ChannelParticipant) {
                                    TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                                    channelParticipant.admin_rights = rightsAdmin;
                                    channelParticipant.banned_rights = rightsBanned;
                                    channelParticipant.rank = rank;
                                    updateParticipantWithRights(channelParticipant, rightsAdmin, rightsBanned, 0, false);
                                }
                            }

                            @Override
                            public void didChangeOwner(TLRPC.User user) {
                                onOwnerChaged(user);
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
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {

                }
            });
        }

        undoView = new UndoView(context);
        frameLayout.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        if (loadingUsers) {
            emptyView.showProgress();
        } else {
            emptyView.showTextView();
        }
        updateRows();
        return fragmentView;
    }
    
    private void onOwnerChaged(TLRPC.User user) {
        undoView.showWithAction(-chatId, isChannel ? UndoView.ACTION_OWNER_TRANSFERED_CHANNEL : UndoView.ACTION_OWNER_TRANSFERED_GROUP, user);
        boolean foundAny = false;
        currentChat.creator = false;
        for (int a = 0; a < 3; a++) {
            SparseArray<TLObject> map;
            ArrayList<TLObject> arrayList;
            boolean found = false;
            if (a == 0) {
                map = contactsMap;
                arrayList = contacts;
            } else if (a == 1) {
                map = botsMap;
                arrayList = bots;
            } else {
                map = participantsMap;
                arrayList = participants;
            }
            TLObject object = map.get(user.id);
            if (object instanceof TLRPC.ChannelParticipant) {
                TLRPC.TL_channelParticipantCreator creator = new TLRPC.TL_channelParticipantCreator();
                creator.user_id = user.id;
                map.put(user.id, creator);
                int index = arrayList.indexOf(object);
                if (index >= 0) {
                    arrayList.set(index, creator);
                }
                found = true;
                foundAny = true;
            }
            int selfUserId = getUserConfig().getClientUserId();
            object = map.get(selfUserId);
            if (object instanceof TLRPC.ChannelParticipant) {
                TLRPC.TL_channelParticipantAdmin admin = new TLRPC.TL_channelParticipantAdmin();
                admin.user_id = selfUserId;
                admin.self = true;
                admin.inviter_id = selfUserId;
                admin.promoted_by = selfUserId;
                admin.date = (int) (System.currentTimeMillis() / 1000);
                admin.admin_rights = new TLRPC.TL_chatAdminRights();
                admin.admin_rights.change_info = admin.admin_rights.post_messages = admin.admin_rights.edit_messages =
                        admin.admin_rights.delete_messages = admin.admin_rights.ban_users = admin.admin_rights.invite_users =
                                admin.admin_rights.pin_messages = admin.admin_rights.add_admins = true;
                map.put(selfUserId, admin);

                int index = arrayList.indexOf(object);
                if (index >= 0) {
                    arrayList.set(index, admin);
                }
                found = true;
            }
            if (found) {
                Collections.sort(arrayList, (lhs, rhs) -> {
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
        }
        if (!foundAny) {
            TLRPC.TL_channelParticipantCreator creator = new TLRPC.TL_channelParticipantCreator();
            creator.user_id = user.id;
            participantsMap.put(user.id, creator);
            participants.add(creator);
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
        }
        listViewAdapter.notifyDataSetChanged();
        if (delegate != null) {
            delegate.didChangeOwner(user);
        }
    }

    private void openRightsEdit2(int userId, int date, TLObject participant, TLRPC.TL_chatAdminRights adminRights, TLRPC.TL_chatBannedRights bannedRights, String rank, boolean canEditAdmin, int type, boolean removeFragment) {
        ChatRightsEditActivity fragment = new ChatRightsEditActivity(userId, chatId, adminRights, defaultBannedRights, bannedRights, rank, type, true, false);
        fragment.setDelegate(new ChatRightsEditActivity.ChatRightsEditActivityDelegate() {
            @Override
            public void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank) {
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
                                newPart.inviter_id = getUserConfig().getClientUserId();
                                newPart.user_id = userId;
                                newPart.date = date;
                                newPart.flags |= 4;
                                newPart.rank = rank;
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
                        removeParticipants(userId);
                    }
                }
            }

            @Override
            public void didChangeOwner(TLRPC.User user) {
                onOwnerChaged(user);
            }
        });
        presentFragment(fragment);
    }

    private void openRightsEdit(int user_id, TLObject participant, TLRPC.TL_chatAdminRights adminRights, TLRPC.TL_chatBannedRights bannedRights, String rank, boolean canEditAdmin, int type, boolean removeFragment) {
        ChatRightsEditActivity fragment = new ChatRightsEditActivity(user_id, chatId, adminRights, defaultBannedRights, bannedRights, rank, type, canEditAdmin, participant == null);
        fragment.setDelegate(new ChatRightsEditActivity.ChatRightsEditActivityDelegate() {
            @Override
            public void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank) {
                if (participant instanceof TLRPC.ChannelParticipant) {
                    TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                    channelParticipant.admin_rights = rightsAdmin;
                    channelParticipant.banned_rights = rightsBanned;
                    channelParticipant.rank = rank;
                }
                if (removeFragment) {
                    removeSelfFromStack();
                }
            }

            @Override
            public void didChangeOwner(TLRPC.User user) {
                onOwnerChaged(user);
            }
        });
        presentFragment(fragment, removeFragment);
    }

    private void removeUser(int userId) {
        if (!ChatObject.isChannel(currentChat)) {
            return;
        }
        TLRPC.User user = getMessagesController().getUser(userId);
        getMessagesController().deleteUserFromChat(chatId, user, null);
        finishFragment();
    }

    private TLObject getAnyParticipant(int userId) {
        boolean updated = false;
        for (int a = 0; a < 3; a++) {
            SparseArray<TLObject> map;
            if (a == 0) {
                map = contactsMap;
            } else if (a == 1) {
                map = botsMap;
            } else {
                map = participantsMap;
            }
            TLObject p = map.get(userId);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    private void removeParticipants(TLObject object) {
        if (object instanceof TLRPC.ChatParticipant) {
            TLRPC.ChatParticipant chatParticipant = (TLRPC.ChatParticipant) object;
            removeParticipants(chatParticipant.user_id);
        } else if (object instanceof TLRPC.ChannelParticipant) {
            TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) object;
            removeParticipants(channelParticipant.user_id);
        }
    }

    private void removeParticipants(int userId) {
        boolean updated = false;
        for (int a = 0; a < 3; a++) {
            SparseArray<TLObject> map;
            ArrayList<TLObject> arrayList;
            if (a == 0) {
                map = contactsMap;
                arrayList = contacts;
            } else if (a == 1) {
                map = botsMap;
                arrayList = bots;
            } else {
                map = participantsMap;
                arrayList = participants;
            }
            TLObject p = map.get(userId);
            if (p != null) {
                map.remove(userId);
                arrayList.remove(p);
                updated = true;
            }
        }
        if (updated) {
            updateRows();
            listViewAdapter.notifyDataSetChanged();
        }
    }

    private void updateParticipantWithRights(TLRPC.ChannelParticipant channelParticipant, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, int user_id, boolean withDelegate) {
        boolean delegateCalled = false;
        for (int a = 0; a < 3; a++) {
            SparseArray<TLObject> map;
            if (a == 0) {
                map = contactsMap;
            } else if (a == 1) {
                map = botsMap;
            } else {
                map = participantsMap;
            }
            TLObject p = map.get(channelParticipant.user_id);
            if (p instanceof TLRPC.ChannelParticipant) {
                channelParticipant = (TLRPC.ChannelParticipant) p;
                channelParticipant.admin_rights = rightsAdmin;
                channelParticipant.banned_rights = rightsBanned;
                if (withDelegate) {
                    channelParticipant.promoted_by = getUserConfig().getClientUserId();
                }
            }
            if (withDelegate && p != null && !delegateCalled && delegate != null) {
                delegateCalled = true;
                delegate.didAddParticipantToList(user_id, p);
            }
        }
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
        String rank;
        if (participant instanceof TLRPC.ChannelParticipant) {
            TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
            userId = channelParticipant.user_id;
            canEdit = channelParticipant.can_edit;
            bannedRights = channelParticipant.banned_rights;
            adminRights = channelParticipant.admin_rights;
            date = channelParticipant.date;
            rank = channelParticipant.rank;
        } else if (participant instanceof TLRPC.ChatParticipant) {
            TLRPC.ChatParticipant chatParticipant = (TLRPC.ChatParticipant) participant;
            userId = chatParticipant.user_id;
            date = chatParticipant.date;
            canEdit = ChatObject.canAddAdmins(currentChat);
            bannedRights = null;
            adminRights = null;
            rank = "";
        } else {
            userId = 0;
            canEdit = false;
            bannedRights = null;
            adminRights = null;
            date = 0;
            rank = null;
        }
        if (userId == 0 || userId == getUserConfig().getClientUserId()) {
            return false;
        }
        if (type == TYPE_USERS) {
            final TLRPC.User user = getMessagesController().getUser(userId);
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
                    getMessagesController().deleteUserFromChat(chatId, user, null);
                    removeParticipants(userId);
                    if (searchItem != null && actionBar.isSearchFieldVisible()) {
                        actionBar.closeSearchField();
                    }
                } else {
                    if (actions.get(i) == 1 && canEditAdmin && (participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_chatParticipantAdmin)) {
                        AlertDialog.Builder builder2 = new AlertDialog.Builder(getParentActivity());
                        builder2.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder2.setMessage(LocaleController.formatString("AdminWillBeRemoved", R.string.AdminWillBeRemoved, ContactsController.formatName(user.first_name, user.last_name)));
                        builder2.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> openRightsEdit2(userId, date, participant, adminRights, bannedRights, rank, canEditAdmin, actions.get(i), false));
                        builder2.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder2.create());
                    } else {
                        openRightsEdit2(userId, date, participant, adminRights, bannedRights, rank, canEditAdmin, actions.get(i), false);
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
                        ChatRightsEditActivity fragment = new ChatRightsEditActivity(userId, chatId, adminRights, null, null, rank, ChatRightsEditActivity.TYPE_ADMIN, true, false);
                        fragment.setDelegate(new ChatRightsEditActivity.ChatRightsEditActivityDelegate() {
                            @Override
                            public void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank) {
                                if (participant instanceof TLRPC.ChannelParticipant) {
                                    TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                                    channelParticipant.admin_rights = rightsAdmin;
                                    channelParticipant.banned_rights = rightsBanned;
                                    channelParticipant.rank = rank;
                                    updateParticipantWithRights(channelParticipant, rightsAdmin, rightsBanned, 0, false);
                                }
                            }

                            @Override
                            public void didChangeOwner(TLRPC.User user) {
                                onOwnerChaged(user);
                            }
                        });
                        presentFragment(fragment);
                    } else {
                        getMessagesController().setUserAdminRole(chatId, getMessagesController().getUser(userId), new TLRPC.TL_chatAdminRights(), "", !isChannel, ChatUsersActivity.this, false);
                        removeParticipants(userId);
                    }
                } else if (type == TYPE_BANNED || type == TYPE_KICKED) {
                    if (i == 0) {
                        if (type == TYPE_KICKED) {
                            ChatRightsEditActivity fragment = new ChatRightsEditActivity(userId, chatId, null, defaultBannedRights, bannedRights, rank, ChatRightsEditActivity.TYPE_BANNED, true, false);
                            fragment.setDelegate(new ChatRightsEditActivity.ChatRightsEditActivityDelegate() {
                                @Override
                                public void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank) {
                                    if (participant instanceof TLRPC.ChannelParticipant) {
                                        TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                                        channelParticipant.admin_rights = rightsAdmin;
                                        channelParticipant.banned_rights = rightsBanned;
                                        channelParticipant.rank = rank;
                                        updateParticipantWithRights(channelParticipant, rightsAdmin, rightsBanned, 0, false);
                                    }
                                }

                                @Override
                                public void didChangeOwner(TLRPC.User user) {
                                    onOwnerChaged(user);
                                }
                            });
                            presentFragment(fragment);
                        } else if (type == TYPE_BANNED) {
                            TLRPC.User user = getMessagesController().getUser(userId);
                            getMessagesController().addUserToChat(chatId, user, null, 0, null, ChatUsersActivity.this, null);
                        }
                    } else if (i == 1) {
                        TLRPC.TL_channels_editBanned req = new TLRPC.TL_channels_editBanned();
                        req.user_id = getMessagesController().getInputUser(userId);
                        req.channel = getMessagesController().getInputChannel(chatId);
                        req.banned_rights = new TLRPC.TL_chatBannedRights();
                        getConnectionsManager().sendRequest(req, (response, error) -> {
                            if (response != null) {
                                final TLRPC.Updates updates = (TLRPC.Updates) response;
                                getMessagesController().processUpdates(updates, false);
                                if (!updates.chats.isEmpty()) {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        TLRPC.Chat chat = updates.chats.get(0);
                                        getMessagesController().loadFullChat(chat.id, 0, true);
                                    }, 1000);
                                }
                            }
                        });
                        if (searchItem != null && actionBar.isSearchFieldVisible()) {
                            actionBar.closeSearchField();
                        }
                    }
                    if (i == 0 && type == TYPE_BANNED || i == 1) {
                        removeParticipants(participant);
                    }
                } else {
                    if (i == 0) {
                        getMessagesController().deleteUserFromChat(chatId, getMessagesController().getUser(userId), null);
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
                boolean hadInfo = info != null;
                info = chatFull;
                if (!hadInfo) {
                    selectedSlowmode = initialSlowmode = getCurrentSlowmode();
                }
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
    
    private int getCurrentSlowmode() {
        if (info != null) {
            if (info.slowmode_seconds == 10) {
                return 1;
            } else if (info.slowmode_seconds == 30) {
                return 2;
            } else if (info.slowmode_seconds == 60) {
                return 3;
            } else if (info.slowmode_seconds == 5 * 60) {
                return 4;
            } else if (info.slowmode_seconds == 15 * 60) {
                return 5;
            } else if (info.slowmode_seconds == 60 * 60) {
                return 6;
            }
        }
        return 0;
    }

    private int getSecondsForIndex(int index) {
        if (index == 1) {
            return 10;
        } else if (index == 2) {
            return 30;
        } else if (index == 3) {
            return 60;
        } else if (index == 4) {
            return 5 * 60;
        } else if (index == 5) {
            return 15 * 60;
        } else if (index == 6) {
            return 60 * 60;
        }
        return 0;
    }

    private boolean checkDiscard() {
        String newBannedRights = ChatObject.getBannedRightsString(defaultBannedRights);
        if (!newBannedRights.equals(initialBannedRights) || initialSlowmode != selectedSlowmode) {
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
        if (!ChatObject.isChannel(currentChat) && selectedSlowmode != initialSlowmode && info != null) {
            MessagesController.getInstance(currentAccount).convertToMegaGroup(getParentActivity(), chatId, this, param -> {
                chatId = param;
                currentChat = MessagesController.getInstance(currentAccount).getChat(param);
                processDone();
            });
            return;
        }
        String newBannedRights = ChatObject.getBannedRightsString(defaultBannedRights);
        if (!newBannedRights.equals(initialBannedRights)) {
            getMessagesController().setDefaultBannedRole(chatId, defaultBannedRights, ChatObject.isChannel(currentChat), this);
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            if (chat != null) {
                chat.default_banned_rights = defaultBannedRights;
            }
        }
        if (selectedSlowmode != initialSlowmode && info != null) {
            getMessagesController().setChannelSlowMode(chatId, info.slowmode_seconds);
        }
        finishFragment();
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
        if (info != null) {
            selectedSlowmode = initialSlowmode = getCurrentSlowmode();
        }
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
        contactsEndReached = false;
        botsEndReached = false;
        loadChatParticipants(offset, count, true);
    }

    private void loadChatParticipants(int offset, int count, boolean reset) {
        if (!ChatObject.isChannel(currentChat)) {
            loadingUsers = false;
            participants.clear();
            bots.clear();
            contacts.clear();
            participantsMap.clear();
            contactsMap.clear();
            botsMap.clear();
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
                    int selfUserId = getUserConfig().clientUserId;
                    for (int a = 0, size = info.participants.participants.size(); a < size; a++) {
                        TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                        if (selectType != 0 && participant.user_id == selfUserId) {
                            continue;
                        }
                        if (selectType == 1) {
                            if (getContactsController().isContact(participant.user_id)) {
                                contacts.add(participant);
                                contactsMap.put(participant.user_id, participant);
                            } else {
                                participants.add(participant);
                                participantsMap.put(participant.user_id, participant);
                            }
                        } else {
                            if (getContactsController().isContact(participant.user_id)) {
                                contacts.add(participant);
                                contactsMap.put(participant.user_id, participant);
                            } else {
                                TLRPC.User user = getMessagesController().getUser(participant.user_id);
                                if (user != null && user.bot) {
                                    bots.add(participant);
                                    botsMap.put(participant.user_id, participant);
                                } else {
                                    participants.add(participant);
                                    participantsMap.put(participant.user_id, participant);
                                }
                            }
                        }
                    }
                }
            }
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
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
            req.channel = getMessagesController().getInputChannel(chatId);
            if (type == TYPE_BANNED) {
                req.filter = new TLRPC.TL_channelParticipantsKicked();
            } else if (type == TYPE_ADMIN) {
                req.filter = new TLRPC.TL_channelParticipantsAdmins();
            } else if (type == TYPE_USERS) {
                if (info != null && info.participants_count <= 200 && currentChat != null && currentChat.megagroup) {
                    req.filter = new TLRPC.TL_channelParticipantsRecent();
                } else {
                    if (selectType == 1) {
                        if (!contactsEndReached) {
                            delayResults = 2;
                            req.filter = new TLRPC.TL_channelParticipantsContacts();
                            contactsEndReached = true;
                            loadChatParticipants(0, 200, false);
                        } else {
                            req.filter = new TLRPC.TL_channelParticipantsRecent();
                        }
                    } else {
                        if (!contactsEndReached) {
                            delayResults = 3;
                            req.filter = new TLRPC.TL_channelParticipantsContacts();
                            contactsEndReached = true;
                            loadChatParticipants(0, 200, false);
                        } else if (!botsEndReached) {
                            req.filter = new TLRPC.TL_channelParticipantsBots();
                            botsEndReached = true;
                            loadChatParticipants(0, 200, false);
                        } else {
                            req.filter = new TLRPC.TL_channelParticipantsRecent();
                        }
                    }
                }
            } else if (type == TYPE_KICKED) {
                req.filter = new TLRPC.TL_channelParticipantsBanned();
            }
            req.filter.q = "";
            req.offset = offset;
            req.limit = count;
            int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                    if (type == TYPE_ADMIN) {
                        getMessagesController().processLoadedAdminsResponse(chatId, (TLRPC.TL_channels_channelParticipants) response);
                    }
                    getMessagesController().putUsers(res.users, false);
                    int selfId = getUserConfig().getClientUserId();
                    if (selectType != 0) {
                        for (int a = 0; a < res.participants.size(); a++) {
                            if (res.participants.get(a).user_id == selfId) {
                                res.participants.remove(a);
                                break;
                            }
                        }
                    }
                    ArrayList<TLObject> objects;
                    SparseArray<TLObject> map;
                    if (type == TYPE_USERS) {
                        delayResults--;
                        if (req.filter instanceof TLRPC.TL_channelParticipantsContacts) {
                            objects = contacts;
                            map = contactsMap;
                        } else if (req.filter instanceof TLRPC.TL_channelParticipantsBots) {
                            objects = bots;
                            map = botsMap;
                        } else {
                            objects = participants;
                            map = participantsMap;
                        }
                        if (delayResults <= 0) {
                            if (emptyView != null) {
                                emptyView.showTextView();
                            }
                        }
                    } else {
                        objects = participants;
                        map = participantsMap;
                        participantsMap.clear();
                        if (emptyView != null) {
                            emptyView.showTextView();
                        }
                    }
                    objects.clear();
                    objects.addAll(res.participants);
                    for (int a = 0, size = res.participants.size(); a < size; a++) {
                        TLRPC.ChannelParticipant participant = res.participants.get(a);
                        map.put(participant.user_id, participant);
                    }
                    if (type == TYPE_USERS) {
                        for (int a = 0, N = participants.size(); a < N; a++) {
                            TLRPC.ChannelParticipant participant = (TLRPC.ChannelParticipant) participants.get(a);
                            if (contactsMap.get(participant.user_id) != null ||
                                    botsMap.get(participant.user_id) != null) {
                                participants.remove(a);
                                participantsMap.remove(participant.user_id);
                                a--;
                                N--;
                            }
                        }
                    }
                    try {
                        if ((type == TYPE_BANNED || type == TYPE_KICKED || type == TYPE_USERS) && currentChat != null && currentChat.megagroup && info instanceof TLRPC.TL_channelFull && info.participants_count <= 200) {
                            int currentTime = getConnectionsManager().getCurrentTime();
                            Collections.sort(objects, (lhs, rhs) -> {
                                TLRPC.ChannelParticipant p1 = (TLRPC.ChannelParticipant) lhs;
                                TLRPC.ChannelParticipant p2 = (TLRPC.ChannelParticipant) rhs;
                                TLRPC.User user1 = getMessagesController().getUser(p1.user_id);
                                TLRPC.User user2 = getMessagesController().getUser(p2.user_id);
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
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (type != TYPE_USERS || delayResults <= 0) {
                    loadingUsers = false;
                    firstLoaded = true;
                }
                updateRows();
                if (listViewAdapter != null) {
                    listViewAdapter.notifyDataSetChanged();
                }
            }));
            getConnectionsManager().bindRequestToGuid(reqId, classGuid);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        //AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (undoView != null) {
            undoView.hide(true, 0);
        }
    }

    @Override
    protected void onBecomeFullyHidden() {
        if (undoView != null) {
            undoView.hide(true, 0);
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
        private Runnable searchRunnable;

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
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            if (TextUtils.isEmpty(query)) {
                searchResult.clear();
                searchResultNames.clear();
                searchAdapterHelper.mergeResults(null);
                searchAdapterHelper.queryServerSearch(null, type != 0, false, true, false, ChatObject.isChannel(currentChat) ? chatId : 0, false, type);
                notifyDataSetChanged();
            } else {
                Utilities.searchQueue.postRunnable(searchRunnable = () -> processSearch(query), 300);
            }
        }

        private void processSearch(final String query) {
            AndroidUtilities.runOnUIThread(() -> {
                searchRunnable = null;

                int kickedType;
                final ArrayList<TLRPC.ChatParticipant> participantsCopy = !ChatObject.isChannel(currentChat) && info != null ? new ArrayList<>(info.participants.participants) : null;
                final ArrayList<TLRPC.TL_contact> contactsCopy = selectType == 1 ? new ArrayList<>(getContactsController().contacts) : null;

                searchAdapterHelper.queryServerSearch(query, selectType != 0, false, true, false, ChatObject.isChannel(currentChat) ? chatId : 0, false, type);
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
                        String[] search = new String[1 + (search2 != null ? 1 : 0)];
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
                                TLRPC.User user = getMessagesController().getUser(participant.user_id);
                                if (user.id == getUserConfig().getClientUserId()) {
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
                                TLRPC.User user = getMessagesController().getUser(contact.user_id);
                                if (user.id == getUserConfig().getClientUserId()) {
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
                        user = getMessagesController().getUser(((TLRPC.ChannelParticipant) object).user_id);
                    } else if (object instanceof TLRPC.ChatParticipant) {
                        user = getMessagesController().getUser(((TLRPC.ChatParticipant) object).user_id);
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
                                    if ((index = AndroidUtilities.indexOfIgnoreCase(un, foundUserName)) != -1) {
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
                        int idx = AndroidUtilities.indexOfIgnoreCase(u, nameSearch);
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
                TLObject object = cell.getCurrentObject();
                if (object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    if (user.self) {
                        return false;
                    }
                }
                return true;
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
                case 7:
                    view = new TextCheckCell2(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 8:
                    view = new GraySectionCell(mContext);
                    break;
                case 9:
                default:
                    view = new ChooseView(mContext);
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
                    int lastRow;

                    if (position >= participantsStartRow && position < participantsEndRow) {
                        lastRow = participantsEndRow;
                    } else if (position >= contactsStartRow && position < contactsEndRow) {
                        lastRow = contactsEndRow;
                    } else {
                        lastRow = botEndRow;
                    }

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
                    TLRPC.User user = getMessagesController().getUser(userId);
                    if (user != null) {
                        if (type == TYPE_KICKED) {
                            userCell.setData(user, null, formatUserPermissions(bannedRights), position != lastRow - 1);
                        } else if (type == TYPE_BANNED) {
                            String role = null;
                            if (banned) {
                                TLRPC.User user1 = getMessagesController().getUser(kickedBy);
                                if (user1 != null) {
                                    role = LocaleController.formatString("UserRemovedBy", R.string.UserRemovedBy, ContactsController.formatName(user1.first_name, user1.last_name));
                                }
                            }
                            userCell.setData(user, null, role, position != lastRow - 1);
                        } else if (type == TYPE_ADMIN) {
                            String role = null;
                            if (creator) {
                                role = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                            } else if (admin) {
                                TLRPC.User user1 = getMessagesController().getUser(promotedBy);
                                if (user1 != null) {
                                    if (user1.id == user.id) {
                                        role = LocaleController.getString("ChannelAdministrator", R.string.ChannelAdministrator);
                                    } else {
                                        role = LocaleController.formatString("EditAdminPromotedBy", R.string.EditAdminPromotedBy, ContactsController.formatName(user1.first_name, user1.last_name));
                                    }
                                }
                            }
                            userCell.setData(user, null, role, position != lastRow - 1);
                        } else if (type == TYPE_USERS) {
                            userCell.setData(user, null, null, position != lastRow - 1);
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
                    } else if (position == slowmodeInfoRow) {
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        if (info == null || info.slowmode_seconds == 0) {
                            privacyCell.setText(LocaleController.getString("SlowmodeInfoOff", R.string.SlowmodeInfoOff));
                        } else {
                            if (info.slowmode_seconds < 60) {
                                privacyCell.setText(LocaleController.formatString("SlowmodeInfoSelected", R.string.SlowmodeInfoSelected, LocaleController.formatPluralString("Seconds", info.slowmode_seconds)));
                            } else if (info.slowmode_seconds < 60 * 60) {
                                privacyCell.setText(LocaleController.formatString("SlowmodeInfoSelected", R.string.SlowmodeInfoSelected, LocaleController.formatPluralString("Minutes", info.slowmode_seconds / 60)));
                            } else {
                                privacyCell.setText(LocaleController.formatString("SlowmodeInfoSelected", R.string.SlowmodeInfoSelected, LocaleController.formatPluralString("Hours", info.slowmode_seconds / 60 / 60)));
                            }
                        }
                    }
                    break;
                case 2:
                    ManageChatTextCell actionCell = (ManageChatTextCell) holder.itemView;
                    actionCell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                    if (position == addNewRow) {
                        if (type == TYPE_KICKED) {
                            actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                            actionCell.setText(LocaleController.getString("ChannelAddException", R.string.ChannelAddException), null, R.drawable.actions_addmember2, participantsStartRow != -1);
                        } else if (type == TYPE_BANNED) {
                            actionCell.setText(LocaleController.getString("ChannelBlockUser", R.string.ChannelBlockUser), null, R.drawable.actions_removed, false);
                        } else if (type == TYPE_ADMIN) {
                            actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                            actionCell.setText(LocaleController.getString("ChannelAddAdmin", R.string.ChannelAddAdmin), null, R.drawable.add_admin, true);
                        } else if (type == TYPE_USERS) {
                            actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                            if (isChannel) {
                                actionCell.setText(LocaleController.getString("AddSubscriber", R.string.AddSubscriber), null, R.drawable.actions_addmember2, membersHeaderRow == -1 && !participants.isEmpty());
                            } else {
                                actionCell.setText(LocaleController.getString("AddMember", R.string.AddMember), null, R.drawable.actions_addmember2, membersHeaderRow == -1 && !participants.isEmpty());
                            }
                        }
                    } else if (position == recentActionsRow) {
                        actionCell.setText(LocaleController.getString("EventLog", R.string.EventLog), null, R.drawable.group_log, false);
                    } else if (position == addNew2Row) {
                        actionCell.setText(LocaleController.getString("ChannelInviteViaLink", R.string.ChannelInviteViaLink), null, R.drawable.profile_link, true);
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
                    } else if (position == slowmodeRow) {
                        headerCell.setText(LocaleController.getString("Slowmode", R.string.Slowmode));
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
                case 8:
                    GraySectionCell sectionCell = (GraySectionCell) holder.itemView;
                    if (position == membersHeaderRow) {
                        if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                            sectionCell.setText(LocaleController.getString("ChannelOtherSubscribers", R.string.ChannelOtherSubscribers));
                        } else {
                            sectionCell.setText(LocaleController.getString("ChannelOtherMembers", R.string.ChannelOtherMembers));
                        }
                    } else if (position == botHeaderRow) {
                        sectionCell.setText(LocaleController.getString("ChannelBots", R.string.ChannelBots));
                    } else if (position == contactsHeaderRow) {
                        if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                            sectionCell.setText(LocaleController.getString("ChannelContacts", R.string.ChannelContacts));
                        } else {
                            sectionCell.setText(LocaleController.getString("GroupContacts", R.string.GroupContacts));
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
            if (position == addNewRow || position == addNew2Row || position == recentActionsRow) {
                return 2;
            } else if (position >= participantsStartRow && position < participantsEndRow ||
                    position >= botStartRow && position < botEndRow ||
                    position >= contactsStartRow && position < contactsEndRow) {
                return 0;
            } else if (position == addNewSectionRow || position == participantsDividerRow || position == participantsDivider2Row) {
                return 3;
            } else if (position == restricted1SectionRow || position == permissionsSectionRow || position == slowmodeRow) {
                return 5;
            } else if (position == participantsInfoRow || position == slowmodeInfoRow) {
                return 1;
            } else if (position == blockedEmptyRow) {
                return 4;
            } else if (position == removedUsersRow) {
                return 6;
            } else if (position == changeInfoRow || position == addUsersRow || position == pinMessagesRow || position == sendMessagesRow ||
                    position == sendMediaRow || position == sendStickersRow || position == embedLinksRow || position == sendPollsRow) {
                return 7;
            } else if (position == membersHeaderRow || position == contactsHeaderRow || position == botHeaderRow) {
                return 8;
            } else if (position == slowmodeSelectRow) {
                return 9;
            }
            return 0;
        }

        public TLObject getItem(int position) {
            if (position >= participantsStartRow && position < participantsEndRow) {
                return participants.get(position - participantsStartRow);
            } else if (position >= contactsStartRow && position < contactsEndRow) {
                return contacts.get(position - contactsStartRow);
            } else if (position >= botStartRow && position < botEndRow) {
                return bots.get(position - botStartRow);
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
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, ManageChatUserCell.class, ManageChatTextCell.class, TextCheckCell2.class, TextSettingsCell.class, ChooseView.class}, null, null, null, Theme.key_windowBackgroundWhite),
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
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, null, new Drawable[]{Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(undoView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_undo_background),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_undo_cancelColor),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_undo_cancelColor),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor),
                new ThemeDescription(undoView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{UndoView.class}, new String[]{"leftImageView"}, null, null, null, Theme.key_undo_infoColor),

                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon),
        };
    }
}
