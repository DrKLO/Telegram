/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
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
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.GigagroupConvertAlert;
import org.telegram.ui.Components.IntSeekBarAccessibilityDelegate;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarAccessibilityDelegate;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Collections;

public class ChatUsersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listViewAdapter;
    private StickerEmptyView emptyView;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
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
    private LongSparseArray<TLObject> participantsMap = new LongSparseArray<>();
    private LongSparseArray<TLObject> botsMap = new LongSparseArray<>();
    private LongSparseArray<TLObject> contactsMap = new LongSparseArray<>();
    private long chatId;
    private int type;
    private boolean loadingUsers;
    private boolean firstLoaded;

    private LongSparseArray<TLRPC.TL_groupCallParticipant> ignoredUsers;

    private int permissionsSectionRow;
    private int sendMessagesRow;
    private int sendMediaRow;
    private int sendStickersRow;
    private int sendPollsRow;
    private int embedLinksRow;
    private int changeInfoRow;
    private int addUsersRow;
    private int pinMessagesRow;

    private int gigaHeaderRow;
    private int gigaConvertRow;
    private int gigaInfoRow;

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
    private int loadingProgressRow;

    private int participantsInfoRow;
    private int blockedEmptyRow;
    private int rowCount;
    private int selectType;
    private int loadingUserCellRow;
    private int loadingHeaderRow;

    private int delayResults;

    private ChatUsersActivityDelegate delegate;

    private boolean needOpenSearch;

    private boolean searching;

    private int selectedSlowmode;
    private int initialSlowmode;

    private final static int search_button = 0;
    private final static int done_button = 1;

    public final static int TYPE_BANNED = 0;
    public final static int TYPE_ADMIN = 1;
    public final static int TYPE_USERS = 2;
    public final static int TYPE_KICKED = 3;
    private boolean openTransitionStarted;
    private FlickerLoadingView flickerLoadingView;
    private View progressBar;

    public interface ChatUsersActivityDelegate {
        default void didAddParticipantToList(long uid, TLObject participant) {

        }

        default void didChangeOwner(TLRPC.User user) {

        }

        default void didSelectUser(long uid) {

        }

        default void didKickParticipant(long userId) {

        }
    }

    private class ChooseView extends View {

        private final Paint paint;
        private final TextPaint textPaint;
        private final SeekBarAccessibilityDelegate accessibilityDelegate;

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

            accessibilityDelegate = new IntSeekBarAccessibilityDelegate() {
                @Override
                public int getProgress() {
                    return selectedSlowmode;
                }

                @Override
                public void setProgress(int progress) {
                    setItem(progress);
                }

                @Override
                public int getMaxValue() {
                    return strings.size() - 1;
                }

                @Override
                protected CharSequence getContentDescription(View host) {
                    if (selectedSlowmode == 0) {
                        return LocaleController.getString("SlowmodeOff", R.string.SlowmodeOff);
                    } else {
                        return formatSeconds(getSecondsForIndex(selectedSlowmode));
                    }
                }
            };
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            accessibilityDelegate.onInitializeAccessibilityNodeInfoInternal(this, info);
        }

        @Override
        public boolean performAccessibilityAction(int action, Bundle arguments) {
            return super.performAccessibilityAction(action, arguments) || accessibilityDelegate.performAccessibilityActionInternal(this, action, arguments);
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
            listViewAdapter.notifyItemChanged(slowmodeInfoRow);
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(74), MeasureSpec.EXACTLY));
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
        chatId = arguments.getLong("chat_id");
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
        gigaInfoRow = -1;
        gigaConvertRow = -1;
        gigaHeaderRow = -1;
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
        loadingProgressRow = -1;
        loadingUserCellRow = -1;
        loadingHeaderRow = -1;

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

            if (ChatObject.isChannel(currentChat) && currentChat.creator && currentChat.megagroup && !currentChat.gigagroup) {
                int count = Math.max(currentChat.participants_count, info != null ? info.participants_count : 0);
                if (count >= getMessagesController().maxMegagroupCount - 1000) {
                    participantsDivider2Row = rowCount++;
                    gigaHeaderRow = rowCount++;
                    gigaConvertRow = rowCount++;
                    gigaInfoRow = rowCount++;
                }
            }

            if (!ChatObject.isChannel(currentChat) && currentChat.creator || currentChat.megagroup && !currentChat.gigagroup && ChatObject.canBlockUsers(currentChat)) {
                if (participantsDivider2Row == -1) {
                    participantsDivider2Row = rowCount++;
                }
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
            if (slowmodeInfoRow == -1 && gigaHeaderRow == -1 || removedUsersRow != -1) {
                participantsDividerRow = rowCount++;
            }
            if (ChatObject.canBlockUsers(currentChat) && (ChatObject.isChannel(currentChat) || currentChat.creator)) {
                addNewRow = rowCount++;
            }

            if (loadingUsers && !firstLoaded) {
                if (!firstLoaded) {
                    if (info != null && info.banned_count > 0) {
                        loadingUserCellRow = rowCount++;
                    }
                }
            } else {
                if (!participants.isEmpty()) {
                    participantsStartRow = rowCount;
                    rowCount += participants.size();
                    participantsEndRow = rowCount;
                }
                if (addNewRow != -1 || participantsStartRow != -1) {
                    addNewSectionRow = rowCount++;
                }
            }
        } else if (type == TYPE_BANNED) {
            if (ChatObject.canBlockUsers(currentChat)) {
                addNewRow = rowCount++;
                if (!participants.isEmpty() || (loadingUsers && !firstLoaded && (info != null && info.kicked_count > 0))) {
                    participantsInfoRow = rowCount++;
                }
            }
            if (!(loadingUsers && !firstLoaded)) {
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
                    //restricted1SectionRow = rowCount++;
                    blockedEmptyRow = rowCount++;
                }
            } else if (!firstLoaded) {
                restricted1SectionRow = rowCount++;
                loadingUserCellRow = rowCount++;
            }
        } else if (type == TYPE_ADMIN) {
            if (ChatObject.isChannel(currentChat) && currentChat.megagroup && !currentChat.gigagroup && (info == null || info.participants_count <= 200)) {
                recentActionsRow = rowCount++;
                addNewSectionRow = rowCount++;
            }

            if (ChatObject.canAddAdmins(currentChat)) {
                addNewRow = rowCount++;
            }
            if (!(loadingUsers && !firstLoaded)) {
                if (!participants.isEmpty()) {
                    participantsStartRow = rowCount;
                    rowCount += participants.size();
                    participantsEndRow = rowCount;
                }
                participantsInfoRow = rowCount++;
            } else if (!firstLoaded) {
                loadingUserCellRow = rowCount++;
            }
        } else if (type == TYPE_USERS) {
            if (selectType == 0 && ChatObject.canAddUsers(currentChat)) {
                addNewRow = rowCount++;
            }
            if (!(loadingUsers && !firstLoaded)) {
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
            } else if (!firstLoaded) {
                if (selectType == 0) {
                    loadingHeaderRow = rowCount++;
                }
                loadingUserCellRow = rowCount++;
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
                    if (doneItem != null) {
                        doneItem.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onSearchCollapse() {
                    searchListViewAdapter.searchUsers(null);
                    searching = false;
                    listView.setAnimateEmptyView(false, 0);
                    listView.setAdapter(listViewAdapter);
                    listViewAdapter.notifyDataSetChanged();
                    listView.setFastScrollVisible(true);
                    listView.setVerticalScrollBarEnabled(false);
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
                    int oldItemsCount = listView.getAdapter() == null ? 0 : listView.getAdapter().getItemCount();
                    searchListViewAdapter.searchUsers(text);
                    if (TextUtils.isEmpty(text) && listView != null && listView.getAdapter() != listViewAdapter) {
                        listView.setAnimateEmptyView(false, 0);
                        listView.setAdapter(listViewAdapter);
                        if (oldItemsCount == 0) {
                            showItemsAnimated(0);
                        }
                    }
                    progressBar.setVisibility(View.GONE);
                    flickerLoadingView.setVisibility(View.VISIBLE);
                }
            });
            if (type == TYPE_KICKED) {
                searchItem.setSearchFieldHint(LocaleController.getString("ChannelSearchException", R.string.ChannelSearchException));
            } else {
                searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
            }
            if (!(ChatObject.isChannel(currentChat) || currentChat.creator)) {
                searchItem.setVisibility(View.GONE);
            }

            if (type == TYPE_KICKED) {
                doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));
            }
        }

        fragmentView = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.drawColor(Theme.getColor(listView.getAdapter() == searchListViewAdapter ? Theme.key_windowBackgroundWhite : Theme.key_windowBackgroundGray));
                super.dispatchDraw(canvas);
            }
        };
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        FrameLayout progressLayout = new FrameLayout(context);
        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE);
        flickerLoadingView.showDate(false);
        flickerLoadingView.setUseHeaderOffset(true);
        progressLayout.addView(flickerLoadingView);

        progressBar = new RadialProgressView(context);
        progressLayout.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        flickerLoadingView.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);

        emptyView = new StickerEmptyView(context, progressLayout, StickerEmptyView.STICKER_TYPE_SEARCH);
        emptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
        emptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
        emptyView.setVisibility(View.GONE);
        emptyView.setAnimateLayoutChange(true);
        emptyView.showProgress(true, false);

        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.addView(progressLayout,0);

        listView = new RecyclerListView(context) {
            @Override
            public void invalidate() {
                super.invalidate();
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            }
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                if (!firstLoaded && type == TYPE_BANNED && participants.size() == 0) {
                    return 0;
                }
                return super.scrollVerticallyBy(dy, recycler, state);
            }
        });
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {

            @Override
            protected long getAddAnimationDelay(long removeDuration, long moveDuration, long changeDuration) {
                return 0;
            }

            @Override
            protected long getMoveAnimationDelay() {
                return 0;
            }

            @Override
            public long getMoveDuration() {
                return 220;
            }

            @Override
            public long getRemoveDuration() {
                return 220;
            }

            @Override
            public long getAddDuration() {
                return 220;
            }

            int animationIndex = -1;

            @Override
            protected void onAllAnimationsDone() {
                super.onAllAnimationsDone();
                getNotificationCenter().onAnimationFinish(animationIndex);
            }

            @Override
            public void runPendingAnimations() {
                boolean removalsPending = !mPendingRemovals.isEmpty();
                boolean movesPending = !mPendingMoves.isEmpty();
                boolean changesPending = !mPendingChanges.isEmpty();
                boolean additionsPending = !mPendingAdditions.isEmpty();
                if (removalsPending || movesPending || additionsPending || changesPending) {
                    animationIndex = getNotificationCenter().setAnimationInProgress(animationIndex, null);
                }
                super.runPendingAnimations();
            }
        };
        listView.setItemAnimator(itemAnimator);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setAnimateEmptyView(true, 0);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            boolean listAdapter = listView.getAdapter() == listViewAdapter;
            if (listAdapter) {
                if (position == addNewRow) {
                    if (type == TYPE_BANNED || type == TYPE_KICKED) {
                        Bundle bundle = new Bundle();
                        bundle.putLong("chat_id", chatId);
                        bundle.putInt("type", ChatUsersActivity.TYPE_USERS);
                        bundle.putInt("selectType", type == TYPE_BANNED ? 2 : 3);
                        ChatUsersActivity fragment = new ChatUsersActivity(bundle);
                        fragment.setInfo(info);
                        fragment.setDelegate(new ChatUsersActivityDelegate() {

                            @Override
                            public void didAddParticipantToList(long uid, TLObject participant) {
                                if (participantsMap.get(uid) == null) {
                                    DiffCallback diffCallback = saveState();
                                    participants.add(participant);
                                    participantsMap.put(uid, participant);
                                    sortUsers(participants);
                                    updateListAnimated(diffCallback);
                                }
                            }

                            @Override
                            public void didKickParticipant(long uid) {
                                if (participantsMap.get(uid) == null) {
                                    DiffCallback diffCallback = saveState();
                                    TLRPC.TL_channelParticipantBanned chatParticipant = new TLRPC.TL_channelParticipantBanned();
                                    if (uid > 0) {
                                        chatParticipant.peer = new TLRPC.TL_peerUser();
                                        chatParticipant.peer.user_id = uid;
                                    } else {
                                        chatParticipant.peer = new TLRPC.TL_peerChannel();
                                        chatParticipant.peer.channel_id = -uid;
                                    }
                                    chatParticipant.date = getConnectionsManager().getCurrentTime();
                                    chatParticipant.kicked_by = getAccountInstance().getUserConfig().clientUserId;
                                    info.kicked_count++;
                                    participants.add(chatParticipant);
                                    participantsMap.put(uid, chatParticipant);
                                    sortUsers(participants);
                                    updateListAnimated(diffCallback);
                                }
                            }
                        });
                        presentFragment(fragment);
                    } else if (type == TYPE_ADMIN) {
                        Bundle bundle = new Bundle();
                        bundle.putLong("chat_id", chatId);
                        bundle.putInt("type", ChatUsersActivity.TYPE_USERS);
                        bundle.putInt("selectType", 1);
                        ChatUsersActivity fragment = new ChatUsersActivity(bundle);
                        fragment.setDelegate(new ChatUsersActivityDelegate() {
                            @Override
                            public void didAddParticipantToList(long uid, TLObject participant) {
                                if (participant != null && participantsMap.get(uid) == null) {
                                    DiffCallback diffCallback = saveState();
                                    participants.add(participant);
                                    participantsMap.put(uid, participant);
                                    sortAdmins(participants);
                                    updateListAnimated(diffCallback);
                                }
                            }

                            @Override
                            public void didChangeOwner(TLRPC.User user) {
                                onOwnerChaged(user);
                            }

                            @Override
                            public void didSelectUser(long uid) {
                                final TLRPC.User user = getMessagesController().getUser(uid);
                                if (user != null) {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        if (BulletinFactory.canShowBulletin(ChatUsersActivity.this)) {
                                            BulletinFactory.createPromoteToAdminBulletin(ChatUsersActivity.this, user.first_name).show();
                                        }
                                    }, 200);
                                }


                                if (participantsMap.get(uid) == null) {
                                    DiffCallback diffCallback = saveState();
                                    TLRPC.TL_channelParticipantAdmin chatParticipant = new TLRPC.TL_channelParticipantAdmin();
                                    chatParticipant.peer = new TLRPC.TL_peerUser();
                                    chatParticipant.peer.user_id = user.id;
                                    chatParticipant.date = getConnectionsManager().getCurrentTime();
                                    chatParticipant.promoted_by = getAccountInstance().getUserConfig().clientUserId;
                                    participants.add(chatParticipant);
                                    participantsMap.put(user.id, chatParticipant);

                                    sortAdmins(participants);
                                    updateListAnimated(diffCallback);
                                }
                            }
                        });
                        fragment.setInfo(info);
                        presentFragment(fragment);
                    } else if (type == TYPE_USERS) {
                        Bundle args = new Bundle();
                        args.putBoolean("addToGroup", true);
                        args.putLong(isChannel ? "channelId" : "chatId", currentChat.id);
                        GroupCreateActivity fragment = new GroupCreateActivity(args);
                        fragment.setInfo(info);
                        fragment.setIgnoreUsers(contactsMap != null && contactsMap.size() != 0 ? contactsMap : participantsMap);
                        fragment.setDelegate(new GroupCreateActivity.ContactsAddActivityDelegate() {
                            @Override
                            public void didSelectUsers(ArrayList<TLRPC.User> users, int fwdCount) {
                                DiffCallback savedState = saveState();
                                ArrayList<TLObject> array =  contactsMap != null && contactsMap.size() != 0 ? contacts : participants;
                                LongSparseArray<TLObject> map = contactsMap != null && contactsMap.size() != 0 ? contactsMap : participantsMap;
                                int k = 0;
                                for (int a = 0, N = users.size(); a < N; a++) {
                                    TLRPC.User user = users.get(a);
                                    getMessagesController().addUserToChat(chatId, user, fwdCount, null, ChatUsersActivity.this, null);
                                    getMessagesController().putUser(user, false);

                                    if (map.get(user.id) == null) {
                                        if (ChatObject.isChannel(currentChat)) {
                                            TLRPC.TL_channelParticipant channelParticipant1 = new TLRPC.TL_channelParticipant();
                                            channelParticipant1.inviter_id = getUserConfig().getClientUserId();
                                            channelParticipant1.peer = new TLRPC.TL_peerUser();
                                            channelParticipant1.peer.user_id = user.id;
                                            channelParticipant1.date = getConnectionsManager().getCurrentTime();
                                            array.add(k, channelParticipant1);
                                            k++;
                                            map.put(user.id, channelParticipant1);
                                        } else {
                                            TLRPC.ChatParticipant participant = new TLRPC.TL_chatParticipant();
                                            participant.user_id = user.id;
                                            participant.inviter_id = getUserConfig().getClientUserId();
                                            array.add(k, participant);
                                            k++;
                                            map.put(user.id, participant);
                                        }

                                    }
                                }
                                if (array == participants) {
                                    sortAdmins(participants);
                                }
                                updateListAnimated(savedState);
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
                    args.putLong("chat_id", chatId);
                    args.putInt("type", ChatUsersActivity.TYPE_BANNED);
                    ChatUsersActivity fragment = new ChatUsersActivity(args);
                    fragment.setInfo(info);
                    presentFragment(fragment);
                    return;
                } else if (position == gigaConvertRow) {
                    showDialog(new GigagroupConvertAlert(getParentActivity(), ChatUsersActivity.this) {
                        @Override
                        protected void onCovert() {
                            getMessagesController().convertToGigaGroup(getParentActivity(), currentChat, ChatUsersActivity.this, (result) -> {
                                if (result && parentLayout != null) {
                                    BaseFragment editActivity = parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 2);
                                    if (editActivity instanceof ChatEditActivity) {
                                        editActivity.removeSelfFromStack();

                                        Bundle args = new Bundle();
                                        args.putLong("chat_id", chatId);
                                        ChatEditActivity fragment = new ChatEditActivity(args);
                                        fragment.setInfo(info);
                                        parentLayout.addFragmentToStack(fragment, parentLayout.fragmentsStack.size() - 1);
                                        finishFragment();
                                        fragment.showConvertTooltip();
                                    } else {
                                        finishFragment();
                                    }
                                }
                            });
                        }

                        @Override
                        protected void onCancel() {

                        }
                    });
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
                            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString("EditCantEditPermissionsPublic", R.string.EditCantEditPermissionsPublic)).show();
                        } else {
                            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString("EditCantEditPermissions", R.string.EditCantEditPermissions)).show();
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
            long peerId = 0;
            long promoted_by = 0;
            boolean canEditAdmin = false;
            if (listAdapter) {
                participant = listViewAdapter.getItem(position);
                if (participant instanceof TLRPC.ChannelParticipant) {
                    TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                    peerId = MessageObject.getPeerId(channelParticipant.peer);
                    bannedRights = channelParticipant.banned_rights;
                    adminRights = channelParticipant.admin_rights;
                    rank = channelParticipant.rank;
                    canEditAdmin = !(channelParticipant instanceof TLRPC.TL_channelParticipantAdmin || channelParticipant instanceof TLRPC.TL_channelParticipantCreator) || channelParticipant.can_edit;
                    if (participant instanceof TLRPC.TL_channelParticipantCreator) {
                        adminRights = ((TLRPC.TL_channelParticipantCreator) participant).admin_rights;
                        if (adminRights == null) {
                            adminRights = new TLRPC.TL_chatAdminRights();
                            adminRights.change_info = adminRights.post_messages = adminRights.edit_messages =
                            adminRights.delete_messages = adminRights.ban_users = adminRights.invite_users =
                            adminRights.pin_messages = adminRights.add_admins = true;
                            if (!isChannel) {
                                adminRights.manage_call = true;
                            }
                        }
                    }
                } else if (participant instanceof TLRPC.ChatParticipant) {
                    TLRPC.ChatParticipant chatParticipant = (TLRPC.ChatParticipant) participant;
                    peerId = chatParticipant.user_id;
                    canEditAdmin = currentChat.creator;
                    if (participant instanceof TLRPC.TL_chatParticipantCreator) {
                        adminRights = new TLRPC.TL_chatAdminRights();
                        adminRights.change_info = adminRights.post_messages = adminRights.edit_messages =
                        adminRights.delete_messages = adminRights.ban_users = adminRights.invite_users =
                        adminRights.pin_messages = adminRights.add_admins = true;
                        if (!isChannel) {
                            adminRights.manage_call = true;
                        }
                    }
                }
            } else {
                TLObject object = searchListViewAdapter.getItem(position);
                if (object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    getMessagesController().putUser(user, false);
                    participant = getAnyParticipant(peerId = user.id);
                } else if (object instanceof TLRPC.ChannelParticipant || object instanceof TLRPC.ChatParticipant) {
                    participant = object;
                } else {
                    participant = null;
                }
                if (participant instanceof TLRPC.ChannelParticipant) {
                    TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                    peerId = MessageObject.getPeerId(channelParticipant.peer);
                    canEditAdmin = !(channelParticipant instanceof TLRPC.TL_channelParticipantAdmin || channelParticipant instanceof TLRPC.TL_channelParticipantCreator) || channelParticipant.can_edit;
                    bannedRights = channelParticipant.banned_rights;
                    adminRights = channelParticipant.admin_rights;
                    rank = channelParticipant.rank;
                } else if (participant instanceof TLRPC.ChatParticipant) {
                    TLRPC.ChatParticipant chatParticipant = (TLRPC.ChatParticipant) participant;
                    peerId = chatParticipant.user_id;
                    canEditAdmin = currentChat.creator;
                    bannedRights = null;
                    adminRights = null;
                } else if (participant == null) {
                    canEditAdmin = true;
                }
            }
            if (peerId != 0) {
                if (selectType != 0) {
                    if (selectType == 3 || selectType == 1) {
                        if (selectType != 1 && canEditAdmin && (participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_chatParticipantAdmin)) {
                            final TLRPC.User user = getMessagesController().getUser(peerId);
                            final TLRPC.TL_chatBannedRights br = bannedRights;
                            final TLRPC.TL_chatAdminRights ar = adminRights;
                            final boolean canEdit = canEditAdmin;
                            final String rankFinal = rank;
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setMessage(LocaleController.formatString("AdminWillBeRemoved", R.string.AdminWillBeRemoved, UserObject.getUserName(user)));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> openRightsEdit(user.id, participant, ar, br, rankFinal, canEdit, selectType == 1 ? 0 : 1, false));
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showDialog(builder.create());
                        } else {
                            openRightsEdit(peerId, participant, adminRights, bannedRights, rank, canEditAdmin, selectType == 1 ? 0 : 1, selectType == 1 || selectType == 3);
                        }
                    } else {
                        removeParticipant(peerId);
                    }
                } else {
                    boolean canEdit = false;
                    if (type == TYPE_ADMIN) {
                        canEdit = peerId != getUserConfig().getClientUserId() && (currentChat.creator || canEditAdmin);
                    } else if (type == TYPE_BANNED || type == TYPE_KICKED) {
                        canEdit = ChatObject.canBlockUsers(currentChat);
                    }
                    if (type == TYPE_BANNED || type != TYPE_ADMIN && isChannel || type == TYPE_USERS && selectType == 0) {
                        if (peerId == getUserConfig().getClientUserId()) {
                            return;
                        }
                        Bundle args = new Bundle();
                        if (peerId > 0) {
                            args.putLong("user_id", peerId);
                        } else {
                            args.putLong("chat_id", -peerId);
                        }
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
                        ChatRightsEditActivity fragment = new ChatRightsEditActivity(peerId, chatId, adminRights, defaultBannedRights, bannedRights, rank, type == TYPE_ADMIN ? ChatRightsEditActivity.TYPE_ADMIN : ChatRightsEditActivity.TYPE_BANNED, canEdit, participant == null);
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

        updateRows();

        listView.setEmptyView(emptyView);
        listView.setAnimateEmptyView(false, 0);

        if (needOpenSearch) {
            searchItem.openSearch(false);
        }

        return fragmentView;
    }

    private void sortAdmins(ArrayList<TLObject> participants) {
        Collections.sort(participants, (lhs, rhs) -> {
            int type1 = getChannelAdminParticipantType(lhs);
            int type2 = getChannelAdminParticipantType(rhs);
            if (type1 > type2) {
                return 1;
            } else if (type1 < type2) {
                return -1;
            }
            if (lhs instanceof TLRPC.ChannelParticipant && rhs instanceof TLRPC.ChannelParticipant) {
                return (int) (MessageObject.getPeerId(((TLRPC.ChannelParticipant) lhs).peer) - MessageObject.getPeerId(((TLRPC.ChannelParticipant) rhs).peer));
            }
            return 0;
        });
    }

    private void showItemsAnimated(int from) {
        if (isPaused || !openTransitionStarted || (listView.getAdapter() == listViewAdapter && firstLoaded)) {
            return;
        }
        View progressView = null;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof FlickerLoadingView) {
                progressView = child;
            }
        }
        final View finalProgressView = progressView;
        if (progressView != null) {
            listView.removeView(progressView);
            from--;
        }
        int finalFrom = from;

        listView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                listView.getViewTreeObserver().removeOnPreDrawListener(this);
                int n = listView.getChildCount();
                AnimatorSet animatorSet = new AnimatorSet();
                for (int i = 0; i < n; i++) {
                    View child = listView.getChildAt(i);
                    if (child == finalProgressView || listView.getChildAdapterPosition(child) < finalFrom) {
                        continue;
                    }
                    child.setAlpha(0);
                    int s = Math.min(listView.getMeasuredHeight(), Math.max(0, child.getTop()));
                    int delay = (int) ((s / (float) listView.getMeasuredHeight()) * 100);
                    ObjectAnimator a = ObjectAnimator.ofFloat(child, View.ALPHA, 0, 1f);
                    a.setStartDelay(delay);
                    a.setDuration(200);
                    animatorSet.playTogether(a);
                }

                if (finalProgressView != null && finalProgressView.getParent() == null) {
                    listView.addView(finalProgressView);
                    RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
                    if (layoutManager != null) {
                        layoutManager.ignoreView(finalProgressView);
                        Animator animator = ObjectAnimator.ofFloat(finalProgressView, View.ALPHA, finalProgressView.getAlpha(), 0);
                        animator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                finalProgressView.setAlpha(1f);
                                layoutManager.stopIgnoringView(finalProgressView);
                                listView.removeView(finalProgressView);
                            }
                        });
                        animator.start();
                    }
                }

                animatorSet.start();
                return true;
            }
        });
    }

    public void setIgnoresUsers(LongSparseArray<TLRPC.TL_groupCallParticipant> participants) {
        ignoredUsers = participants;
    }
    
    private void onOwnerChaged(TLRPC.User user) {
        undoView.showWithAction(-chatId, isChannel ? UndoView.ACTION_OWNER_TRANSFERED_CHANNEL : UndoView.ACTION_OWNER_TRANSFERED_GROUP, user);
        boolean foundAny = false;
        currentChat.creator = false;
        for (int a = 0; a < 3; a++) {
            LongSparseArray<TLObject> map;
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
                creator.peer = new TLRPC.TL_peerUser();
                creator.peer.user_id = user.id;
                map.put(user.id, creator);
                int index = arrayList.indexOf(object);
                if (index >= 0) {
                    arrayList.set(index, creator);
                }
                found = true;
                foundAny = true;
            }
            long selfUserId = getUserConfig().getClientUserId();
            object = map.get(selfUserId);
            if (object instanceof TLRPC.ChannelParticipant) {
                TLRPC.TL_channelParticipantAdmin admin = new TLRPC.TL_channelParticipantAdmin();
                admin.peer = new TLRPC.TL_peerUser();
                admin.peer.user_id = selfUserId;
                admin.self = true;
                admin.inviter_id = selfUserId;
                admin.promoted_by = selfUserId;
                admin.date = (int) (System.currentTimeMillis() / 1000);
                admin.admin_rights = new TLRPC.TL_chatAdminRights();
                admin.admin_rights.change_info = admin.admin_rights.post_messages = admin.admin_rights.edit_messages =
                admin.admin_rights.delete_messages = admin.admin_rights.ban_users = admin.admin_rights.invite_users =
                admin.admin_rights.pin_messages = admin.admin_rights.add_admins = true;
                if (!isChannel) {
                    admin.admin_rights.manage_call = true;
                }
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
            creator.peer = new TLRPC.TL_peerUser();
            creator.peer.user_id = user.id;
            participantsMap.put(user.id, creator);
            participants.add(creator);
            sortAdmins(participants);
            updateRows();
        }
        listViewAdapter.notifyDataSetChanged();
        if (delegate != null) {
            delegate.didChangeOwner(user);
        }
    }

    private void openRightsEdit2(long peerId, int date, TLObject participant, TLRPC.TL_chatAdminRights adminRights, TLRPC.TL_chatBannedRights bannedRights, String rank, boolean canEditAdmin, int type, boolean removeFragment) {
        boolean[] needShowBulletin = new boolean[1];
        final boolean isAdmin = participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_chatParticipantAdmin;
        ChatRightsEditActivity fragment = new ChatRightsEditActivity(peerId, chatId, adminRights, defaultBannedRights, bannedRights, rank, type, true, false) {
            @Override
            protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
                if (!isOpen && backward && needShowBulletin[0] && BulletinFactory.canShowBulletin(ChatUsersActivity.this)) {
                    if (peerId > 0) {
                        TLRPC.User user = getMessagesController().getUser(peerId);
                        if (user != null) {
                            BulletinFactory.createPromoteToAdminBulletin(ChatUsersActivity.this, user.first_name).show();
                        }
                    } else {
                        TLRPC.Chat chat = getMessagesController().getChat(-peerId);
                        if (chat != null) {
                            BulletinFactory.createPromoteToAdminBulletin(ChatUsersActivity.this, chat.title).show();
                        }
                    }
                }
            }
        };
        fragment.setDelegate(new ChatRightsEditActivity.ChatRightsEditActivityDelegate() {
            @Override
            public void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank) {
                if (type == 0) {
                    for (int a = 0; a < participants.size(); a++) {
                        TLObject p = participants.get(a);
                        if (p instanceof TLRPC.ChannelParticipant) {
                            TLRPC.ChannelParticipant p2 = (TLRPC.ChannelParticipant) p;
                            if (MessageObject.getPeerId(p2.peer) == peerId) {
                                TLRPC.ChannelParticipant newPart;
                                if (rights == 1) {
                                    newPart = new TLRPC.TL_channelParticipantAdmin();
                                } else {
                                    newPart = new TLRPC.TL_channelParticipant();
                                }
                                newPart.admin_rights = rightsAdmin;
                                newPart.banned_rights = rightsBanned;
                                newPart.inviter_id = getUserConfig().getClientUserId();
                                if (peerId > 0) {
                                    newPart.peer = new TLRPC.TL_peerUser();
                                    newPart.peer.user_id = peerId;
                                } else {
                                    newPart.peer = new TLRPC.TL_peerChannel();
                                    newPart.peer.channel_id = -peerId;
                                }
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
                    if (rights == 1 && !isAdmin) {
                        needShowBulletin[0] = true;
                    }
                } else if (type == 1) {
                    if (rights == 0) {
                        removeParticipants(peerId);
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

    @Override
    public boolean canBeginSlide() {
        return checkDiscard();
    }

    private void openRightsEdit(long user_id, TLObject participant, TLRPC.TL_chatAdminRights adminRights, TLRPC.TL_chatBannedRights bannedRights, String rank, boolean canEditAdmin, int type, boolean removeFragment) {
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
                if (delegate != null && rights == 1) {
                    delegate.didSelectUser(user_id);
                } else if (delegate != null) {
                    delegate.didAddParticipantToList(user_id, participant);
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

    private void removeParticipant(long userId) {
        if (!ChatObject.isChannel(currentChat)) {
            return;
        }
        TLRPC.User user = getMessagesController().getUser(userId);
        getMessagesController().deleteParticipantFromChat(chatId, user, null);
        if (delegate != null) {
            delegate.didKickParticipant(userId);
        }
        finishFragment();
    }

    private TLObject getAnyParticipant(long userId) {
        for (int a = 0; a < 3; a++) {
            LongSparseArray<TLObject> map;
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
            removeParticipants(MessageObject.getPeerId(channelParticipant.peer));
        }
    }

    private void removeParticipants(long peerId) {
        boolean updated = false;
        DiffCallback savedState = saveState();
        for (int a = 0; a < 3; a++) {
            LongSparseArray<TLObject> map;
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
            TLObject p = map.get(peerId);
            if (p != null) {
                map.remove(peerId);
                arrayList.remove(p);
                updated = true;
                if (type == TYPE_BANNED) {
                    info.kicked_count--;
                }
            }
        }
        if (updated) {
            updateListAnimated(savedState);
        }
        if (listView.getAdapter() == searchListViewAdapter) {
            searchListViewAdapter.removeUserId(peerId);
        }
    }

    private void updateParticipantWithRights(TLRPC.ChannelParticipant channelParticipant, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, long user_id, boolean withDelegate) {
        boolean delegateCalled = false;
        for (int a = 0; a < 3; a++) {
            LongSparseArray<TLObject> map;
            if (a == 0) {
                map = contactsMap;
            } else if (a == 1) {
                map = botsMap;
            } else {
                map = participantsMap;
            }
            TLObject p = map.get(MessageObject.getPeerId(channelParticipant.peer));
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
        long peerId;
        boolean canEdit;
        int date;
        TLRPC.TL_chatBannedRights bannedRights;
        TLRPC.TL_chatAdminRights adminRights;
        String rank;
        if (participant instanceof TLRPC.ChannelParticipant) {
            TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
            peerId = MessageObject.getPeerId(channelParticipant.peer);
            canEdit = channelParticipant.can_edit;
            bannedRights = channelParticipant.banned_rights;
            adminRights = channelParticipant.admin_rights;
            date = channelParticipant.date;
            rank = channelParticipant.rank;
        } else if (participant instanceof TLRPC.ChatParticipant) {
            TLRPC.ChatParticipant chatParticipant = (TLRPC.ChatParticipant) participant;
            peerId = chatParticipant.user_id;
            date = chatParticipant.date;
            canEdit = ChatObject.canAddAdmins(currentChat);
            bannedRights = null;
            adminRights = null;
            rank = "";
        } else {
            peerId = 0;
            canEdit = false;
            bannedRights = null;
            adminRights = null;
            date = 0;
            rank = null;
        }
        if (peerId == 0 || peerId == getUserConfig().getClientUserId()) {
            return false;
        }
        if (type == TYPE_USERS) {
            final TLRPC.User user = getMessagesController().getUser(peerId);
            boolean allowSetAdmin = ChatObject.canAddAdmins(currentChat) && (participant instanceof TLRPC.TL_channelParticipant || participant instanceof TLRPC.TL_channelParticipantBanned || participant instanceof TLRPC.TL_chatParticipant || canEdit);
            boolean canEditAdmin = !(participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_channelParticipantCreator || participant instanceof TLRPC.TL_chatParticipantCreator || participant instanceof TLRPC.TL_chatParticipantAdmin) || canEdit;
            boolean editingAdmin = participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_chatParticipantAdmin;

            if (selectType == 0) {
                allowSetAdmin &= !UserObject.isDeleted(user);
            }

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
                    if (ChatObject.isChannel(currentChat) && !currentChat.gigagroup) {
                        items.add(LocaleController.getString("ChangePermissions", R.string.ChangePermissions));
                        icons.add(R.drawable.actions_permissions);
                        actions.add(1);
                    }
                    items.add(LocaleController.getString("KickFromGroup", R.string.KickFromGroup));
                } else {
                    items.add(LocaleController.getString("ChannelRemoveUser", R.string.ChannelRemoveUser));
                }
                icons.add(R.drawable.actions_remove_user);
                actions.add(2);
                hasRemove = true;
            }
            if (actions == null || actions.isEmpty()) {
                return false;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setItems(items.toArray(new CharSequence[actions.size()]), AndroidUtilities.toIntArray(icons), (dialogInterface, i) -> {
                if (actions.get(i) == 2) {
                    getMessagesController().deleteParticipantFromChat(chatId, user, null);
                    removeParticipants(peerId);
                    if (currentChat != null && user != null && BulletinFactory.canShowBulletin(this)) {
                        BulletinFactory.createRemoveFromChatBulletin(this, user, currentChat.title).show();
                    }
                } else {
                    if (actions.get(i) == 1 && canEditAdmin && (participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_chatParticipantAdmin)) {
                        AlertDialog.Builder builder2 = new AlertDialog.Builder(getParentActivity());
                        builder2.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder2.setMessage(LocaleController.formatString("AdminWillBeRemoved", R.string.AdminWillBeRemoved, UserObject.getUserName(user)));
                        builder2.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> openRightsEdit2(peerId, date, participant, adminRights, bannedRights, rank, canEditAdmin, actions.get(i), false));
                        builder2.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder2.create());
                    } else {
                        openRightsEdit2(peerId, date, participant, adminRights, bannedRights, rank, canEditAdmin, actions.get(i), false);
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
                        ChatObject.canAddUsers(currentChat) && peerId > 0 ? (isChannel ? LocaleController.getString("ChannelAddToChannel", R.string.ChannelAddToChannel) : LocaleController.getString("ChannelAddToGroup", R.string.ChannelAddToGroup)) : null,
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
                        ChatRightsEditActivity fragment = new ChatRightsEditActivity(peerId, chatId, adminRights, null, null, rank, ChatRightsEditActivity.TYPE_ADMIN, true, false);
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
                        getMessagesController().setUserAdminRole(chatId, getMessagesController().getUser(peerId), new TLRPC.TL_chatAdminRights(), "", !isChannel, ChatUsersActivity.this, false);
                        removeParticipants(peerId);
                    }
                } else if (type == TYPE_BANNED || type == TYPE_KICKED) {
                    if (i == 0) {
                        if (type == TYPE_KICKED) {
                            ChatRightsEditActivity fragment = new ChatRightsEditActivity(peerId, chatId, null, defaultBannedRights, bannedRights, rank, ChatRightsEditActivity.TYPE_BANNED, true, false);
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
                            if (peerId > 0) {
                                TLRPC.User user = getMessagesController().getUser(peerId);
                                getMessagesController().addUserToChat(chatId, user, 0, null, ChatUsersActivity.this, null);
                            }
                        }
                    } else if (i == 1) {
                        TLRPC.TL_channels_editBanned req = new TLRPC.TL_channels_editBanned();
                        req.participant = getMessagesController().getInputPeer(peerId);
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
                    }
                    if (i == 0 && type == TYPE_BANNED || i == 1) {
                        removeParticipants(participant);
                    }
                } else {
                    if (i == 0) {
                        TLRPC.User user;
                        TLRPC.Chat chat;
                        if (peerId > 0) {
                            user = getMessagesController().getUser(peerId);
                            chat = null;
                        } else {
                            user = null;
                            chat = getMessagesController().getChat(-peerId);
                        }
                        getMessagesController().deleteParticipantFromChat(chatId, user, chat, null, false, false);
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

    private String formatSeconds(int seconds) {
        if (seconds < 60) {
            return LocaleController.formatPluralString("Seconds", seconds);
        } else if (seconds < 60 * 60) {
            return LocaleController.formatPluralString("Minutes", seconds / 60);
        } else {
            return LocaleController.formatPluralString("Hours", seconds / 60 / 60);
        }
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
        if (currentChat.creator && !ChatObject.isChannel(currentChat) && selectedSlowmode != initialSlowmode && info != null) {
            MessagesController.getInstance(currentAccount).convertToMegaGroup(getParentActivity(), chatId, this, param -> {
                if (param != 0) {
                    chatId = param;
                    currentChat = MessagesController.getInstance(currentAccount).getChat(param);
                    processDone();
                }
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
            info.slowmode_seconds = getSecondsForIndex(selectedSlowmode);
            info.flags |= 131072;
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

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
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
                    long selfUserId = getUserConfig().clientUserId;
                    for (int a = 0, size = info.participants.participants.size(); a < size; a++) {
                        TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                        if (selectType != 0 && participant.user_id == selfUserId) {
                            continue;
                        }
                        if (ignoredUsers != null && ignoredUsers.indexOfKey(participant.user_id) >= 0) {
                            continue;
                        }
                        if (selectType == 1) {
                            if (getContactsController().isContact(participant.user_id)) {
                                contacts.add(participant);
                                contactsMap.put(participant.user_id, participant);
                            } else if (!UserObject.isDeleted(getMessagesController().getUser(participant.user_id))) {
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
            if (emptyView != null) {
                emptyView.showProgress(true, false);
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
                    getMessagesController().putChats(res.chats, false);
                    long selfId = getUserConfig().getClientUserId();
                    if (selectType != 0) {
                        for (int a = 0; a < res.participants.size(); a++) {
                            if (MessageObject.getPeerId(res.participants.get(a).peer) == selfId) {
                                res.participants.remove(a);
                                break;
                            }
                        }
                    }
                    ArrayList<TLObject> objects;
                    LongSparseArray<TLObject> map;
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
                    } else {
                        objects = participants;
                        map = participantsMap;
                        participantsMap.clear();
                    }
                    objects.clear();
                    objects.addAll(res.participants);
                    for (int a = 0, size = res.participants.size(); a < size; a++) {
                        TLRPC.ChannelParticipant participant = res.participants.get(a);
                        if (participant.user_id == selfId) {
                            objects.remove(participant);
                        } else {
                            map.put(MessageObject.getPeerId(participant.peer), participant);
                        }
                    }
                    if (type == TYPE_USERS) {
                        for (int a = 0, N = participants.size(); a < N; a++) {
                            TLObject object = participants.get(a);
                            if (!(object instanceof TLRPC.ChannelParticipant)) {
                                participants.remove(a);
                                a--;
                                N--;
                                continue;
                            }
                            TLRPC.ChannelParticipant participant = (TLRPC.ChannelParticipant) object;
                            long peerId = MessageObject.getPeerId(participant.peer);
                            boolean remove = false;
                            if (contactsMap.get(peerId) != null || botsMap.get(peerId) != null) {
                                remove = true;
                            } else if (selectType == 1 && peerId > 0 && UserObject.isDeleted(getMessagesController().getUser(peerId))) {
                                remove = true;
                            } else if (ignoredUsers != null && ignoredUsers.indexOfKey(peerId) >= 0) {
                                remove = true;
                            }
                            if (remove) {
                                participants.remove(a);
                                participantsMap.remove(peerId);
                                a--;
                                N--;
                            }
                        }
                    }
                    try {
                        if ((type == TYPE_BANNED || type == TYPE_KICKED || type == TYPE_USERS) && currentChat != null && currentChat.megagroup && info instanceof TLRPC.TL_channelFull && info.participants_count <= 200) {
                            sortUsers(objects);
                        } else if (type == TYPE_ADMIN) {
                            sortAdmins(participants);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (type != TYPE_USERS || delayResults <= 0) {
                    showItemsAnimated(listViewAdapter != null ? listViewAdapter.getItemCount() : 0);
                    loadingUsers = false;
                    firstLoaded = true;
                }
                updateRows();
                if (listViewAdapter != null) {
                    listView.setAnimateEmptyView(openTransitionStarted, 0);
                    listViewAdapter.notifyDataSetChanged();

                    if (emptyView != null && listViewAdapter.getItemCount() == 0 && firstLoaded) {
                        emptyView.showProgress(false, true);
                    }
                }
                resumeDelayedFragmentAnimation();
            }));
            getConnectionsManager().bindRequestToGuid(reqId, classGuid);
        }
    }

    private void sortUsers(ArrayList<TLObject> objects) {
        int currentTime = getConnectionsManager().getCurrentTime();
        Collections.sort(objects, (lhs, rhs) -> {
            TLRPC.ChannelParticipant p1 = (TLRPC.ChannelParticipant) lhs;
            TLRPC.ChannelParticipant p2 = (TLRPC.ChannelParticipant) rhs;
            long peer1 = MessageObject.getPeerId(p1.peer);
            long peer2 = MessageObject.getPeerId(p2.peer);
            int status1 = 0;
            if (peer1 > 0) {
                TLRPC.User user1 = getMessagesController().getUser(MessageObject.getPeerId(p1.peer));
                if (user1 != null && user1.status != null) {
                    if (user1.self) {
                        status1 = currentTime + 50000;
                    } else {
                        status1 = user1.status.expires;
                    }
                }
            } else {
                status1 = -100;
            }
            int status2 = 0;
            if (peer2 > 0) {
                TLRPC.User user2 = getMessagesController().getUser(MessageObject.getPeerId(p2.peer));
                if (user2 != null && user2.status != null) {
                    if (user2.self) {
                        status2 = currentTime + 50000;
                    } else {
                        status2 = user2.status.expires;
                    }
                }
            } else {
                status2 = -100;
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

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
        if (emptyView != null) {
            emptyView.requestLayout();
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

    public int getSelectType() {
        return selectType;
    }

//    @Override
//    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
//        super.onTransitionAnimationStart(isOpen, backward);
//        if (isOpen) {
//            openTransitionStarted = true;
//        }
//    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            openTransitionStarted = true;
        }
        if (isOpen && !backward && needOpenSearch) {
            searchItem.getSearchField().requestFocus();
            AndroidUtilities.showKeyboard(searchItem.getSearchField());
        }
    }

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<Object> searchResult = new ArrayList<>();
        private LongSparseArray<TLObject> searchResultMap = new LongSparseArray<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private SearchAdapterHelper searchAdapterHelper;
        private Runnable searchRunnable;
        private int totalCount = 0;
        private boolean searchInProgress;

        private int groupStartRow;
        private int contactsStartRow;
        private int globalStartRow;

        public SearchAdapter(Context context) {
            mContext = context;
            searchAdapterHelper = new SearchAdapterHelper(true);
            searchAdapterHelper.setDelegate(searchId -> {
                if (!searchAdapterHelper.isSearchInProgress()) {
                    int oldItemCount = getItemCount();
                    notifyDataSetChanged();
                    if (getItemCount() > oldItemCount) {
                        showItemsAnimated(oldItemCount);
                    }
                    if (!searchInProgress) {
                        if (getItemCount() == 0 && searchId != 0) {
                            emptyView.showProgress(false, true);
                        }
                    }
                }
            });
        }

        public void searchUsers(final String query) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            searchResult.clear();
            searchResultMap.clear();
            searchResultNames.clear();
            searchAdapterHelper.mergeResults(null);
            searchAdapterHelper.queryServerSearch(null, type != 0, false, true, false, false, ChatObject.isChannel(currentChat) ? chatId : 0, false, type, 0);
            notifyDataSetChanged();

            if (!TextUtils.isEmpty(query)) {
                searchInProgress = true;
                emptyView.showProgress(true, true);
                Utilities.searchQueue.postRunnable(searchRunnable = () -> processSearch(query), 300);
            }
        }

        private void processSearch(final String query) {
            AndroidUtilities.runOnUIThread(() -> {
                searchRunnable = null;

                final ArrayList<TLObject> participantsCopy = !ChatObject.isChannel(currentChat) && info != null ? new ArrayList<>(info.participants.participants) : null;
                final ArrayList<TLRPC.TL_contact> contactsCopy = selectType == 1 ? new ArrayList<>(getContactsController().contacts) : null;

                if (participantsCopy != null || contactsCopy != null) {
                    Utilities.searchQueue.postRunnable(() -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<>(), new LongSparseArray<>(), new ArrayList<>(), new ArrayList<>());
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
                        ArrayList<Object> resultArray = new ArrayList<>();
                        LongSparseArray<TLObject> resultMap = new LongSparseArray<>();
                        ArrayList<CharSequence> resultArrayNames = new ArrayList<>();
                        ArrayList<TLObject> resultArray2 = new ArrayList<>();

                        if (participantsCopy != null) {
                            for (int a = 0, N = participantsCopy.size(); a < N; a++) {
                                long peerId;
                                TLObject o = participantsCopy.get(a);
                                if (o instanceof TLRPC.ChatParticipant) {
                                    peerId = ((TLRPC.ChatParticipant) o).user_id;
                                } else if (o instanceof TLRPC.ChannelParticipant) {
                                    peerId = MessageObject.getPeerId(((TLRPC.ChannelParticipant) o).peer);
                                } else {
                                    continue;
                                }
                                String name;
                                String username;
                                String firstName;
                                String lastName;
                                if (peerId > 0) {
                                    TLRPC.User user = getMessagesController().getUser(peerId);
                                    if (user.id == getUserConfig().getClientUserId()) {
                                        continue;
                                    }
                                    name = UserObject.getUserName(user).toLowerCase();
                                    username = user.username;
                                    firstName = user.first_name;
                                    lastName = user.last_name;
                                } else {
                                    TLRPC.Chat chat = getMessagesController().getChat(-peerId);
                                    name = chat.title.toLowerCase();
                                    username = chat.username;
                                    firstName = chat.title;
                                    lastName = null;
                                }

                                String tName = LocaleController.getInstance().getTranslitString(name);
                                if (name.equals(tName)) {
                                    tName = null;
                                }

                                int found = 0;
                                for (String q : search) {
                                    if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                        found = 1;
                                    } else if (username != null && username.startsWith(q)) {
                                        found = 2;
                                    }

                                    if (found != 0) {
                                        if (found == 1) {
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(firstName, lastName, q));
                                        } else {
                                            resultArrayNames.add(AndroidUtilities.generateSearchName("@" + username, null, "@" + q));
                                        }
                                        resultArray2.add(o);
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

                                String name = UserObject.getUserName(user).toLowerCase();
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
                                        resultMap.put(user.id, user);
                                        break;
                                    }
                                }
                            }
                        }
                        updateSearchResults(resultArray, resultMap, resultArrayNames, resultArray2);
                    });
                } else {
                    searchInProgress = false;
                }
                searchAdapterHelper.queryServerSearch(query, selectType != 0, false, true, false, false, ChatObject.isChannel(currentChat) ? chatId : 0, false, type, 1);
            });
        }

        private void updateSearchResults(final ArrayList<Object> users, final LongSparseArray<TLObject> usersMap, final ArrayList<CharSequence> names, final ArrayList<TLObject> participants) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchInProgress = false;
                searchResult = users;
                searchResultMap = usersMap;
                searchResultNames = names;
                searchAdapterHelper.mergeResults(searchResult);
                if (!ChatObject.isChannel(currentChat)) {
                    ArrayList<TLObject> search = searchAdapterHelper.getGroupSearch();
                    search.clear();
                    search.addAll(participants);
                }
                int oldItemCount = getItemCount();
                notifyDataSetChanged();
                if (getItemCount() > oldItemCount) {
                    showItemsAnimated(oldItemCount);
                }
                if (!searchAdapterHelper.isSearchInProgress()) {
                    if (getItemCount() == 0) {
                        emptyView.showProgress(false, true);
                    }
                }
            });
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 1;
        }

        @Override
        public int getItemCount() {
            return totalCount;
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
            if (searching && listView != null && listView.getAdapter() != searchListViewAdapter) {
                listView.setAnimateEmptyView(true, 0);
                listView.setAdapter(searchListViewAdapter);
                listView.setFastScrollVisible(false);
                listView.setVerticalScrollBarEnabled(true);
            }
            super.notifyDataSetChanged();
        }

        public void removeUserId(long userId) {
            searchAdapterHelper.removeUserId(userId);
            Object object = searchResultMap.get(userId);
            if (object != null) {
                searchResult.remove(object);
            }
            notifyDataSetChanged();
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
                        return (TLObject) searchResult.get(i - 1);
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
                    ManageChatUserCell manageChatUserCell = new ManageChatUserCell(mContext, 2, 2, selectType == 0);
                    manageChatUserCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    manageChatUserCell.setDelegate((cell, click) -> {
                        TLObject object = getItem((Integer) cell.getTag());
                        if (object instanceof TLRPC.ChannelParticipant) {
                            TLRPC.ChannelParticipant participant = (TLRPC.ChannelParticipant) object;
                            return createMenuForParticipant(participant, !click);
                        } else {
                            return false;
                        }
                    });
                    view = manageChatUserCell;
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
                    TLObject peerObject;
                    String un = null;
                    if (object instanceof TLRPC.User) {
                        peerObject = object;
                    } else if (object instanceof TLRPC.ChannelParticipant) {
                        long peerId = MessageObject.getPeerId(((TLRPC.ChannelParticipant) object).peer);
                        if (peerId > 0) {
                            TLRPC.User user = getMessagesController().getUser(peerId);
                            un = user.username;
                            peerObject = user;
                        } else {
                            TLRPC.Chat chat = getMessagesController().getChat(-peerId);
                            if (chat != null) {
                                un = chat.username;
                            }
                            peerObject = chat;
                        }
                    } else if (object instanceof TLRPC.ChatParticipant) {
                        peerObject = getMessagesController().getUser(((TLRPC.ChatParticipant) object).user_id);
                    } else {
                        return;
                    }

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

                    if (nameSearch != null && un != null) {
                        name = new SpannableStringBuilder(un);
                        int idx = AndroidUtilities.indexOfIgnoreCase(un, nameSearch);
                        if (idx != -1) {
                            ((SpannableStringBuilder) name).setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), idx, idx + nameSearch.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }

                    ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
                    userCell.setTag(position);
                    userCell.setData(peerObject, name, username, false);

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
            int viewType = holder.getItemViewType();
            if (viewType == 7) {
                return ChatObject.canBlockUsers(currentChat);
            } else if (viewType == 0) {
                ManageChatUserCell cell = (ManageChatUserCell) holder.itemView;
                Object object = cell.getCurrentObject();
                if (type != TYPE_ADMIN && object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    if (user.self) {
                        return false;
                    }
                }
                return true;
            }
            return viewType == 0 || viewType == 2 || viewType == 6;
        }

        @Override
        public int getItemCount() {
          /*  if (type == TYPE_KICKED && loadingUsers && !firstLoaded) {
                return 0;
            }*/
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    ManageChatUserCell manageChatUserCell = new ManageChatUserCell(mContext, type == TYPE_BANNED || type == TYPE_KICKED ? 7 : 6, type == TYPE_BANNED || type == TYPE_KICKED ? 6 : 2, selectType == 0);
                    manageChatUserCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    manageChatUserCell.setDelegate((cell, click) -> {
                        TLObject participant = listViewAdapter.getItem((Integer) cell.getTag());
                        return createMenuForParticipant(participant, !click);
                    });
                    view = manageChatUserCell;
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
                    HeaderCell headerCell = new HeaderCell(mContext, Theme.key_windowBackgroundWhiteBlueHeader, 21, 11, false);
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
                    view.setBackground(null);
                    break;
                case 10:
                    view = new LoadingCell(mContext, AndroidUtilities.dp(40), AndroidUtilities.dp(120));
                    break;
                case 11:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                    flickerLoadingView.setIsSingleCell(true);
                    flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE);
                    flickerLoadingView.showDate(false);
                    flickerLoadingView.setPaddingLeft(AndroidUtilities.dp(5));
                    flickerLoadingView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    flickerLoadingView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    view = flickerLoadingView;
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

                    boolean showJoined = false;
                    if (position >= participantsStartRow && position < participantsEndRow) {
                        lastRow = participantsEndRow;
                        showJoined = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
                    } else if (position >= contactsStartRow && position < contactsEndRow) {
                        lastRow = contactsEndRow;
                        showJoined = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
                    } else {
                        lastRow = botEndRow;
                    }

                    long peerId;
                    long kickedBy;
                    long promotedBy;
                    TLRPC.TL_chatBannedRights bannedRights;
                    boolean banned;
                    boolean creator;
                    boolean admin;
                    int joined;
                    if (item instanceof TLRPC.ChannelParticipant) {
                        TLRPC.ChannelParticipant participant = (TLRPC.ChannelParticipant) item;
                        peerId = MessageObject.getPeerId(participant.peer);
                        kickedBy = participant.kicked_by;
                        promotedBy = participant.promoted_by;
                        bannedRights = participant.banned_rights;
                        joined = participant.date;
                        banned = participant instanceof TLRPC.TL_channelParticipantBanned;
                        creator = participant instanceof TLRPC.TL_channelParticipantCreator;
                        admin = participant instanceof TLRPC.TL_channelParticipantAdmin;
                    } else if (item instanceof TLRPC.ChatParticipant) {
                        TLRPC.ChatParticipant participant = (TLRPC.ChatParticipant) item;
                        peerId = participant.user_id;
                        joined = participant.date;
                        kickedBy = 0;
                        promotedBy = 0;
                        bannedRights = null;
                        banned = false;
                        creator = participant instanceof TLRPC.TL_chatParticipantCreator;
                        admin = participant instanceof TLRPC.TL_chatParticipantAdmin;
                    } else {
                        return;
                    }
                    TLObject object;
                    if (peerId > 0) {
                        object = getMessagesController().getUser(peerId);
                    } else {
                        object = getMessagesController().getChat(-peerId);
                    }
                    if (object != null) {
                        if (type == TYPE_KICKED) {
                            userCell.setData(object, null, formatUserPermissions(bannedRights), position != lastRow - 1);
                        } else if (type == TYPE_BANNED) {
                            String role = null;
                            if (banned) {
                                TLRPC.User user1 = getMessagesController().getUser(kickedBy);
                                if (user1 != null) {
                                    role = LocaleController.formatString("UserRemovedBy", R.string.UserRemovedBy, UserObject.getUserName(user1));
                                }
                            }
                            userCell.setData(object, null, role, position != lastRow - 1);
                        } else if (type == TYPE_ADMIN) {
                            String role = null;
                            if (creator) {
                                role = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                            } else if (admin) {
                                TLRPC.User user1 = getMessagesController().getUser(promotedBy);
                                if (user1 != null) {
                                    if (user1.id == peerId) {
                                        role = LocaleController.getString("ChannelAdministrator", R.string.ChannelAdministrator);
                                    } else {
                                        role = LocaleController.formatString("EditAdminPromotedBy", R.string.EditAdminPromotedBy, UserObject.getUserName(user1));
                                    }
                                }
                            }
                            userCell.setData(object, null, role, position != lastRow - 1);
                        } else if (type == TYPE_USERS) {
                            CharSequence status;
                            if (showJoined && joined != 0) {
                                status = LocaleController.formatJoined(joined);
                            } else {
                                status = null;
                            }
                            userCell.setData(object, null, status, position != lastRow - 1);
                        }
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == participantsInfoRow) {
                        if (type == TYPE_BANNED || type == TYPE_KICKED) {
                            if (isChannel) {
                                privacyCell.setText(LocaleController.getString("NoBlockedChannel2", R.string.NoBlockedChannel2));
                            } else {
                                privacyCell.setText(LocaleController.getString("NoBlockedGroup2", R.string.NoBlockedGroup2));
                            }
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        } else if (type == TYPE_ADMIN) {
                            if (addNewRow != -1) {
                                if (isChannel) {
                                    privacyCell.setText(LocaleController.getString("ChannelAdminsInfo", R.string.ChannelAdminsInfo));
                                } else {
                                    privacyCell.setText(LocaleController.getString("MegaAdminsInfo", R.string.MegaAdminsInfo));
                                }
                            } else {
                                privacyCell.setText("");
                            }
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
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
                        int seconds = getSecondsForIndex(selectedSlowmode);
                        if (info == null || seconds == 0) {
                            privacyCell.setText(LocaleController.getString("SlowmodeInfoOff", R.string.SlowmodeInfoOff));
                        } else {
                            privacyCell.setText(LocaleController.formatString("SlowmodeInfoSelected", R.string.SlowmodeInfoSelected, formatSeconds(seconds)));
                        }
                    } else if (position == gigaInfoRow) {
                        privacyCell.setText(LocaleController.getString("BroadcastGroupConvertInfo", R.string.BroadcastGroupConvertInfo));
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
                            boolean showDivider = !(loadingUsers && !firstLoaded);
                            actionCell.setText(LocaleController.getString("ChannelAddAdmin", R.string.ChannelAddAdmin), null, R.drawable.add_admin, showDivider);
                        } else if (type == TYPE_USERS) {
                            actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                            boolean showDivider = !(loadingUsers && !firstLoaded) && membersHeaderRow == -1 && !participants.isEmpty();
                            if (isChannel) {
                                actionCell.setText(LocaleController.getString("AddSubscriber", R.string.AddSubscriber), null, R.drawable.actions_addmember2, showDivider);
                            } else {
                                actionCell.setText(LocaleController.getString("AddMember", R.string.AddMember), null, R.drawable.actions_addmember2, showDivider);
                            }
                        }
                    } else if (position == recentActionsRow) {
                        actionCell.setText(LocaleController.getString("EventLog", R.string.EventLog), null, R.drawable.group_log, false);
                    } else if (position == addNew2Row) {
                        actionCell.setText(LocaleController.getString("ChannelInviteViaLink", R.string.ChannelInviteViaLink), null, R.drawable.profile_link, true);
                    } else if (position == gigaConvertRow) {
                        actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                        actionCell.setText(LocaleController.getString("BroadcastGroupConvert", R.string.BroadcastGroupConvert), null, R.drawable.msg_channel, false);
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
                    } else if (position == gigaHeaderRow) {
                        headerCell.setText(LocaleController.getString("BroadcastGroup", R.string.BroadcastGroup));
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
                    } else if (position == loadingHeaderRow) {
                        sectionCell.setText("");
                    }
                    break;
                case 11:
                    FlickerLoadingView flickerLoadingView = (FlickerLoadingView) holder.itemView;
                    if (type == TYPE_BANNED) {
                        flickerLoadingView.setItemsCount(info == null ? 1 : info.kicked_count);
                    } else {
                        flickerLoadingView.setItemsCount(1);
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
            if (position == addNewRow || position == addNew2Row || position == recentActionsRow || position == gigaConvertRow) {
                return 2;
            } else if (position >= participantsStartRow && position < participantsEndRow ||
                    position >= botStartRow && position < botEndRow ||
                    position >= contactsStartRow && position < contactsEndRow) {
                return 0;
            } else if (position == addNewSectionRow || position == participantsDividerRow || position == participantsDivider2Row) {
                return 3;
            } else if (position == restricted1SectionRow || position == permissionsSectionRow || position == slowmodeRow || position == gigaHeaderRow) {
                return 5;
            } else if (position == participantsInfoRow || position == slowmodeInfoRow || position == gigaInfoRow) {
                return 1;
            } else if (position == blockedEmptyRow) {
                return 4;
            } else if (position == removedUsersRow) {
                return 6;
            } else if (position == changeInfoRow || position == addUsersRow || position == pinMessagesRow || position == sendMessagesRow ||
                    position == sendMediaRow || position == sendStickersRow || position == embedLinksRow || position == sendPollsRow) {
                return 7;
            } else if (position == membersHeaderRow || position == contactsHeaderRow || position == botHeaderRow || position == loadingHeaderRow) {
                return 8;
            } else if (position == slowmodeSelectRow) {
                return 9;
            } else if (position == loadingProgressRow) {
                return 10;
            } else if (position == loadingUserCellRow) {
                return 11;
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

    public DiffCallback saveState() {
        DiffCallback diffCallback = new DiffCallback();
        diffCallback.oldRowCount = rowCount;

        diffCallback.oldBotStartRow = botStartRow;
        diffCallback.oldBotEndRow = botEndRow;
        diffCallback.oldBots.clear();
        diffCallback.oldBots.addAll(bots);

        diffCallback.oldContactsEndRow = contactsEndRow;
        diffCallback.oldContactsStartRow = contactsStartRow;
        diffCallback.oldContacts.clear();
        diffCallback.oldContacts.addAll(contacts);

        diffCallback.oldParticipantsStartRow = participantsStartRow;
        diffCallback.oldParticipantsEndRow = participantsEndRow;
        diffCallback.oldParticipants.clear();
        diffCallback.oldParticipants.addAll(participants);

        diffCallback.fillPositions(diffCallback.oldPositionToItem);
        return diffCallback;
    }

    public void updateListAnimated(DiffCallback savedState) {
        if (listViewAdapter == null) {
            updateRows();
            return;
        }
        updateRows();
        savedState.fillPositions(savedState.newPositionToItem);
        DiffUtil.calculateDiff(savedState).dispatchUpdatesTo(listViewAdapter);
        if (listView != null && layoutManager != null && listView.getChildCount() > 0) {
            View view = null;
            int position = -1;
            for (int i = 0; i < listView.getChildCount(); i++) {
                position = listView.getChildAdapterPosition(listView.getChildAt(i));
                if (position != RecyclerListView.NO_POSITION) {
                    view = listView.getChildAt(i);
                    break;
                }
            }
            if (view != null) {
                layoutManager.scrollToPositionWithOffset(position, view.getTop() - listView.getPaddingTop());
            }
        }
    }

    private class DiffCallback extends DiffUtil.Callback {

        int oldRowCount;
        SparseIntArray oldPositionToItem = new SparseIntArray();
        SparseIntArray newPositionToItem = new SparseIntArray();

        int oldParticipantsStartRow;
        int oldParticipantsEndRow;
        int oldContactsStartRow;
        int oldContactsEndRow;
        int oldBotStartRow;
        int oldBotEndRow;

        private ArrayList<TLObject> oldParticipants = new ArrayList<>();
        private ArrayList<TLObject> oldBots = new ArrayList<>();
        private ArrayList<TLObject> oldContacts = new ArrayList<>();

        @Override
        public int getOldListSize() {
            return oldRowCount;
        }

        @Override
        public int getNewListSize() {
            return rowCount;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            if (oldItemPosition >= oldBotStartRow && oldItemPosition < oldBotEndRow && newItemPosition >= botStartRow && newItemPosition < botEndRow) {
                return oldBots.get(oldItemPosition - oldBotStartRow).equals(bots.get(newItemPosition - botStartRow));
            } else if (oldItemPosition >= oldContactsStartRow && oldItemPosition < oldContactsEndRow && newItemPosition >= contactsStartRow && newItemPosition < contactsEndRow) {
                return oldContacts.get(oldItemPosition - oldContactsStartRow).equals(contacts.get(newItemPosition - contactsStartRow));
            } else if (oldItemPosition >= oldParticipantsStartRow && oldItemPosition < oldParticipantsEndRow && newItemPosition >= participantsStartRow && newItemPosition < participantsEndRow) {
                return oldParticipants.get(oldItemPosition - oldParticipantsStartRow).equals(participants.get(newItemPosition - participantsStartRow));
            }
            return oldPositionToItem.get(oldItemPosition) == newPositionToItem.get(newItemPosition);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            if (areItemsTheSame(oldItemPosition, newItemPosition)) {
                if (restricted1SectionRow == newItemPosition) {
                    return false;
                }
                return true;
            }
            return false;
        }

        public void fillPositions(SparseIntArray sparseIntArray) {
            sparseIntArray.clear();
            int pointer = 0;
            put(++pointer, recentActionsRow, sparseIntArray);
            put(++pointer, addNewRow, sparseIntArray);
            put(++pointer, addNew2Row, sparseIntArray);
            put(++pointer, addNewSectionRow, sparseIntArray);
            put(++pointer, restricted1SectionRow, sparseIntArray);
            put(++pointer, participantsDividerRow, sparseIntArray);
            put(++pointer, participantsDivider2Row, sparseIntArray);
            put(++pointer, gigaHeaderRow, sparseIntArray);
            put(++pointer, gigaConvertRow, sparseIntArray);
            put(++pointer, gigaInfoRow, sparseIntArray);
            put(++pointer, participantsInfoRow, sparseIntArray);
            put(++pointer, blockedEmptyRow, sparseIntArray);
            put(++pointer, permissionsSectionRow, sparseIntArray);
            put(++pointer, sendMessagesRow, sparseIntArray);
            put(++pointer, sendMediaRow, sparseIntArray);
            put(++pointer, sendStickersRow, sparseIntArray);
            put(++pointer, sendPollsRow, sparseIntArray);
            put(++pointer, embedLinksRow, sparseIntArray);
            put(++pointer, addUsersRow, sparseIntArray);
            put(++pointer, pinMessagesRow, sparseIntArray);
            put(++pointer, changeInfoRow, sparseIntArray);
            put(++pointer, removedUsersRow, sparseIntArray);
            put(++pointer, contactsHeaderRow, sparseIntArray);
            put(++pointer, botHeaderRow, sparseIntArray);
            put(++pointer, membersHeaderRow, sparseIntArray);
            put(++pointer, slowmodeRow, sparseIntArray);
            put(++pointer, slowmodeSelectRow, sparseIntArray);
            put(++pointer, slowmodeInfoRow, sparseIntArray);
            put(++pointer, loadingProgressRow, sparseIntArray);
            put(++pointer, loadingUserCellRow, sparseIntArray);
            put(++pointer, loadingHeaderRow, sparseIntArray);
        }

        private void put(int id, int position, SparseIntArray sparseIntArray) {
            if (position >= 0) {
                sparseIntArray.put(position, id);
            }
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

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, ManageChatUserCell.class, ManageChatTextCell.class, TextCheckCell2.class, TextSettingsCell.class, ChooseView.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2Track));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2TrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText));

        themeDescriptions.add(new ThemeDescription(undoView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_undo_background));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_undo_cancelColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_undo_cancelColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{UndoView.class}, new String[]{"leftImageView"}, null, null, null, Theme.key_undo_infoColor));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerEmptyView.class}, new String[]{"title"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerEmptyView.class}, new String[]{"subtitle"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(emptyView.title, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(emptyView.subtitle, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        return themeDescriptions;
    }
}
