/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.collection.LongSparseArray;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.BotWebViewVibrationEffect;
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
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.GigagroupConvertAlert;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatUsersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int VIEW_TYPE_INNER_CHECK = 13;
    private static final int VIEW_TYPE_EXPANDABLE_SWITCH = 14;
    private static final int VIEW_TYPE_NOT_RESTRICT_BOOSTERS_SLIDER = 15;
    private static final int VIEW_TYPE_CHECK = 16;

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
    private boolean isChannel, isForum;

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
    private int sendMediaPhotosRow;
    private int sendMediaVideosRow;
    private int sendMediaStickerGifsRow;
    private int sendMediaMusicRow;
    private int sendMediaFilesRow;
    private int sendMediaVoiceMessagesRow;
    private int sendMediaVideoMessagesRow;
    private int sendMediaEmbededLinksRow;
    private int sendPollsRow;

    private int sendStickersRow;
    private int embedLinksRow;
    private int changeInfoRow;
    private int addUsersRow;
    private int pinMessagesRow;
    private int manageTopicsRow;

    private int gigaHeaderRow;
    private int gigaConvertRow;
    private int gigaInfoRow;

    private int recentActionsRow;
    private boolean antiSpamToggleLoading;
    private int antiSpamRow;
    private int antiSpamInfoRow;
    private int addNewRow;
    private int addNew2Row;
    private int removedUsersRow;
    private int addNewSectionRow;
    private int restricted1SectionRow;
    private int participantsStartRow;
    private int participantsEndRow;
    private int participantsDividerRow;
    private int participantsDivider2Row;
    private boolean hideMembersToggleLoading;
    private int hideMembersRow;
    private int hideMembersInfoRow;

    private int slowmodeRow;
    private int slowmodeSelectRow;
    private int slowmodeInfoRow;
    private int dontRestrictBoostersRow;
    private int dontRestrictBoostersInfoRow;
    private int dontRestrictBoostersSliderRow;

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

    private int signMessagesRow;
    private int signMessagesProfilesRow;
    private int signMessagesInfoRow;

    private int delayResults;

    private boolean sendMediaExpanded;

    private ChatUsersActivityDelegate delegate;

    private boolean needOpenSearch;

    private boolean searching;

    private int selectedSlowmode;
    private int initialSlowmode;
    private boolean isEnabledNotRestrictBoosters;
    private int notRestrictBoosters;

    private boolean initialSignatures;
    private boolean initialProfiles;
    private boolean signatures, profiles;

    private final static int search_button = 0;
    private final static int done_button = 1;

    public final static int TYPE_BANNED = 0;
    public final static int TYPE_ADMIN = 1;
    public final static int TYPE_USERS = 2;
    public final static int TYPE_KICKED = 3;

    public final static int SELECT_TYPE_MEMBERS = 0; // "Subscribers" / "Members"
    public final static int SELECT_TYPE_ADMIN = 1; // "Add Admin"
    public final static int SELECT_TYPE_BLOCK = 2; // "Remove User"
    public final static int SELECT_TYPE_EXCEPTION = 3; // "Add Exception"

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
            defaultBannedRights.manage_topics = currentChat.default_banned_rights.manage_topics;
            defaultBannedRights.change_info = currentChat.default_banned_rights.change_info;
            defaultBannedRights.send_photos = currentChat.default_banned_rights.send_photos;
            defaultBannedRights.send_videos = currentChat.default_banned_rights.send_videos;
            defaultBannedRights.send_roundvideos = currentChat.default_banned_rights.send_roundvideos;
            defaultBannedRights.send_audios = currentChat.default_banned_rights.send_audios;
            defaultBannedRights.send_voices = currentChat.default_banned_rights.send_voices;
            defaultBannedRights.send_docs = currentChat.default_banned_rights.send_docs;
            defaultBannedRights.send_plain = currentChat.default_banned_rights.send_plain;
            if (!defaultBannedRights.send_media && defaultBannedRights.send_docs && defaultBannedRights.send_voices &&  defaultBannedRights.send_audios && defaultBannedRights.send_roundvideos && defaultBannedRights.send_videos && defaultBannedRights.send_photos) {
                defaultBannedRights.send_photos = false;
                defaultBannedRights.send_videos = false;
                defaultBannedRights.send_roundvideos = false;
                defaultBannedRights.send_audios = false;
                defaultBannedRights.send_voices = false;
                defaultBannedRights.send_docs = false;
            }
        }
        initialBannedRights = ChatObject.getBannedRightsString(defaultBannedRights);
        isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
        isForum = ChatObject.isForum(currentChat);
        if (currentChat != null) {
            initialSignatures = signatures = currentChat.signatures;
            initialProfiles = profiles = currentChat.signature_profiles;
        }
    }

    private void updateRows() {
        currentChat = getMessagesController().getChat(chatId);
        if (currentChat == null) {
            return;
        }
        recentActionsRow = -1;
        antiSpamRow = -1;
        antiSpamInfoRow = -1;
        addNewRow = -1;
        addNew2Row = -1;
        hideMembersRow = -1;
        hideMembersInfoRow = -1;
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
        signMessagesRow = -1;
        signMessagesProfilesRow = -1;
        signMessagesInfoRow = -1;
        blockedEmptyRow = -1;
        permissionsSectionRow = -1;
        sendMessagesRow = -1;
        sendMediaRow = -1;
        sendStickersRow = -1;
        sendPollsRow = -1;
        embedLinksRow = -1;
        addUsersRow = -1;
        manageTopicsRow = -1;
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
        dontRestrictBoostersRow = -1;
        dontRestrictBoostersInfoRow = -1;
        dontRestrictBoostersSliderRow = -1;
        loadingProgressRow = -1;
        loadingUserCellRow = -1;
        loadingHeaderRow = -1;
        sendMediaPhotosRow = -1;
        sendMediaVideosRow = -1;
        sendMediaStickerGifsRow = -1;
        sendMediaMusicRow = -1;
        sendMediaFilesRow = -1;
        sendMediaVoiceMessagesRow = -1;
        sendMediaVideoMessagesRow = -1;
        sendMediaEmbededLinksRow = -1;

        rowCount = 0;
        if (type == TYPE_KICKED) {
            permissionsSectionRow = rowCount++;
            sendMessagesRow = rowCount++;
            sendMediaRow = rowCount++;
            if (sendMediaExpanded) {
                sendMediaPhotosRow = rowCount++;
                sendMediaVideosRow = rowCount++;
                sendMediaStickerGifsRow = rowCount++;
                sendMediaMusicRow = rowCount++;
                sendMediaFilesRow = rowCount++;
                sendMediaVoiceMessagesRow = rowCount++;
                sendMediaVideoMessagesRow = rowCount++;
                sendMediaEmbededLinksRow = rowCount++;
                sendPollsRow = rowCount++;
            }
            addUsersRow = rowCount++;
            pinMessagesRow = rowCount++;
            changeInfoRow = rowCount++;
            if (isForum) {
                manageTopicsRow = rowCount++;
            }

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

            if (isNotRestrictBoostersVisible()) {
                if (participantsDivider2Row == -1) {
                    participantsDivider2Row = rowCount++;
                }
                dontRestrictBoostersRow = rowCount++;
                if (isEnabledNotRestrictBoosters) {
                    dontRestrictBoostersSliderRow = rowCount++;
                }
                dontRestrictBoostersInfoRow = rowCount++;
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
            if (ChatObject.canBlockUsers(currentChat) && getParticipantsCount() > 1 && (ChatObject.isChannel(currentChat) || currentChat.creator)) {
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
            if (ChatObject.isChannel(currentChat) && currentChat.megagroup && !currentChat.gigagroup && (info == null || info.participants_count <= 200 || !isChannel && info.can_set_stickers)) {
//                recentActionsRow = rowCount++;
                if (ChatObject.hasAdminRights(currentChat)) {
                    antiSpamRow = rowCount++;
                    antiSpamInfoRow = rowCount++;
                } else {
                    addNewSectionRow = rowCount++;
                }
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
            if (ChatObject.isChannelAndNotMegaGroup(currentChat) && ChatObject.hasAdminRights(currentChat)) {
                signMessagesRow = rowCount++;
                if (signatures) {
                    signMessagesProfilesRow = rowCount++;
                    signMessagesInfoRow = rowCount++;
                } else {
                    signMessagesInfoRow = rowCount++;
                }
            }
        } else if (type == TYPE_USERS) {
            if (ChatObject.isChannel(currentChat)) {
                if (!ChatObject.isChannelAndNotMegaGroup(currentChat) && !needOpenSearch) {
                    hideMembersRow = rowCount++;
                    hideMembersInfoRow = rowCount++;
                }
            }
            if (selectType == SELECT_TYPE_MEMBERS && ChatObject.canAddUsers(currentChat)) {
                addNewRow = rowCount++;
            }
            if (selectType == SELECT_TYPE_MEMBERS && ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE)) {
                addNew2Row = rowCount++;
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
                if (selectType == SELECT_TYPE_MEMBERS) {
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
            actionBar.setTitle(getString("ChannelPermissions", R.string.ChannelPermissions));
        } else if (type == TYPE_BANNED) {
            actionBar.setTitle(getString("ChannelBlacklist", R.string.ChannelBlacklist));
        } else if (type == TYPE_ADMIN) {
            actionBar.setTitle(getString("ChannelAdministrators", R.string.ChannelAdministrators));
        } else if (type == TYPE_USERS) {
            if (selectType == SELECT_TYPE_MEMBERS) {
                if (isChannel) {
                    actionBar.setTitle(getString("ChannelSubscribers", R.string.ChannelSubscribers));
                } else {
                    actionBar.setTitle(getString("ChannelMembers", R.string.ChannelMembers));
                }
            } else {
                if (selectType == SELECT_TYPE_ADMIN) {
                    actionBar.setTitle(getString("ChannelAddAdmin", R.string.ChannelAddAdmin));
                } else if (selectType == SELECT_TYPE_BLOCK) {
                    actionBar.setTitle(getString("ChannelBlockUser", R.string.ChannelBlockUser));
                } else if (selectType == SELECT_TYPE_EXCEPTION) {
                    actionBar.setTitle(getString("ChannelAddException", R.string.ChannelAddException));
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
        if (selectType != SELECT_TYPE_MEMBERS || type == TYPE_USERS || type == TYPE_BANNED || type == TYPE_KICKED) {
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
            if (type == TYPE_BANNED && !firstLoaded) {
                searchItem.setVisibility(View.GONE);
            }
            if (type == TYPE_KICKED) {
                searchItem.setSearchFieldHint(getString("ChannelSearchException", R.string.ChannelSearchException));
            } else {
                searchItem.setSearchFieldHint(getString("Search", R.string.Search));
            }
            if (!(ChatObject.isChannel(currentChat) || currentChat.creator)) {
                searchItem.setVisibility(View.GONE);
            }

            if (type == TYPE_KICKED) {
                doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_ab_done, AndroidUtilities.dp(56), getString("Done", R.string.Done));
            }
        } else if (type == TYPE_ADMIN && ChatObject.isChannelAndNotMegaGroup(currentChat) && ChatObject.hasAdminRights(currentChat)) {
            ActionBarMenu menu = actionBar.createMenu();
            doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_ab_done, AndroidUtilities.dp(56), getString("Done", R.string.Done));
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
        flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, Theme.key_listSelector);
        progressLayout.addView(flickerLoadingView);

        progressBar = new RadialProgressView(context);
        progressLayout.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        flickerLoadingView.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);

        emptyView = new StickerEmptyView(context, progressLayout, StickerEmptyView.STICKER_TYPE_SEARCH);
        emptyView.title.setText(getString(R.string.NoResult));
        emptyView.subtitle.setText(getString(R.string.SearchEmptyViewFilteredSubtitle2));
        emptyView.setVisibility(View.GONE);
        emptyView.setAnimateLayoutChange(true);
        emptyView.showProgress(true, false);

        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.addView(progressLayout, 0);

        listView = new RecyclerListView(context) {
            @Override
            public void invalidate() {
                super.invalidate();
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (permissionsSectionRow >= 0 && participantsDivider2Row >= 0) {
                    drawSectionBackground(canvas, permissionsSectionRow, Math.max(0, participantsDivider2Row - 1), getThemedColor(Theme.key_windowBackgroundWhite));
                }
                super.dispatchDraw(canvas);
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

            AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();;

            @Override
            protected void onAllAnimationsDone() {
                super.onAllAnimationsDone();
                notificationsLocker.unlock();
            }

            @Override
            public void runPendingAnimations() {
                boolean removalsPending = !mPendingRemovals.isEmpty();
                boolean movesPending = !mPendingMoves.isEmpty();
                boolean changesPending = !mPendingChanges.isEmpty();
                boolean additionsPending = !mPendingAdditions.isEmpty();
                if (removalsPending || movesPending || additionsPending || changesPending) {
                    notificationsLocker.lock();
                }
                super.runPendingAnimations();
            }

            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                super.onMoveAnimationUpdate(holder);
                listView.invalidate();
            }

            @Override
            protected void onChangeAnimationUpdate(RecyclerView.ViewHolder holder) {
                super.onChangeAnimationUpdate(holder);
                listView.invalidate();
            }
        };
        itemAnimator.setDurations(420);
//        itemAnimator.setMoveDelay(0);
//        itemAnimator.setAddDelay(0);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);
        listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position, x, y) -> {
            boolean listAdapter = listView.getAdapter() == listViewAdapter;
            if (position == signMessagesRow) {
                signatures = !signatures;
                ((TextCheckCell) view).setChecked(signatures);

                AndroidUtilities.updateVisibleRows(listView);
                DiffCallback diffCallback = saveState();
                updateRows();
                updateListAnimated(diffCallback);

                listViewAdapter.notifyItemChanged(signMessagesInfoRow);
            } else if (position == signMessagesProfilesRow) {
                profiles = !profiles;
                ((TextCheckCell) view).setChecked(profiles);

                AndroidUtilities.updateVisibleRows(listView);
                DiffCallback diffCallback = saveState();
                updateRows();
                updateListAnimated(diffCallback);

                listViewAdapter.notifyItemChanged(signMessagesInfoRow);
            } else if (listAdapter) {
                if (isExpandableSendMediaRow(position)) {
                    CheckBoxCell checkBoxCell = (CheckBoxCell) view;
                    if (position == sendMediaPhotosRow) {
                        defaultBannedRights.send_photos = !defaultBannedRights.send_photos;
                    } else if (position == sendMediaVideosRow) {
                        defaultBannedRights.send_videos = !defaultBannedRights.send_videos;
                    } else if (position == sendMediaStickerGifsRow) {
                        defaultBannedRights.send_stickers = defaultBannedRights.send_games = defaultBannedRights.send_gifs = defaultBannedRights.send_inline = !defaultBannedRights.send_stickers;
                    } else if (position == sendMediaMusicRow) {
                        defaultBannedRights.send_audios = !defaultBannedRights.send_audios;
                    } else if (position == sendMediaFilesRow) {
                        defaultBannedRights.send_docs = !defaultBannedRights.send_docs;
                    } else if (position == sendMediaVoiceMessagesRow) {
                        defaultBannedRights.send_voices = !defaultBannedRights.send_voices;
                    } else if (position == sendMediaVideoMessagesRow) {
                        defaultBannedRights.send_roundvideos = !defaultBannedRights.send_roundvideos;
                    } else if (position == sendMediaEmbededLinksRow) {
                        if (defaultBannedRights.send_plain) {
                            View senMessagesView = layoutManager.findViewByPosition(sendMessagesRow);
                            if (senMessagesView != null) {
                                AndroidUtilities.shakeViewSpring(senMessagesView);
                                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                                return;
                            }
                        }
                        defaultBannedRights.embed_links = !defaultBannedRights.embed_links;
                    } else if (position == sendPollsRow) {
                        defaultBannedRights.send_polls = !defaultBannedRights.send_polls;
                    }

                    checkBoxCell.setChecked(!checkBoxCell.isChecked(), true);
                    AndroidUtilities.updateVisibleRows(listView);
                    DiffCallback diffCallback = saveState();
                    updateRows();
                    updateListAnimated(diffCallback);
                } else if (position == dontRestrictBoostersRow) {
                    TextCheckCell2 checkBoxCell = (TextCheckCell2) view;
                    isEnabledNotRestrictBoosters = !checkBoxCell.isChecked();
                    checkBoxCell.setChecked(isEnabledNotRestrictBoosters);
                    AndroidUtilities.updateVisibleRows(listView);
                    DiffCallback diffCallback = saveState();
                    updateRows();
                    updateListAnimated(diffCallback);
                } else if (position == addNewRow) {
                    if (type == TYPE_BANNED || type == TYPE_KICKED) {
                        Bundle bundle = new Bundle();
                        bundle.putLong("chat_id", chatId);
                        bundle.putInt("type", ChatUsersActivity.TYPE_USERS);
                        bundle.putInt("selectType", type == TYPE_BANNED ? ChatUsersActivity.SELECT_TYPE_BLOCK : ChatUsersActivity.SELECT_TYPE_EXCEPTION);
                        ChatUsersActivity fragment = new ChatUsersActivity(bundle);
                        fragment.setInfo(info);
                        fragment.setBannedRights(defaultBannedRights);
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
                        bundle.putInt("selectType", ChatUsersActivity.SELECT_TYPE_ADMIN);
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
                        fragment.setDelegate2(new GroupCreateActivity.ContactsAddActivityDelegate() {
                            @Override
                            public void didSelectUsers(ArrayList<TLRPC.User> users, int fwdCount) {
                                if (fragment.getParentActivity() == null) {
                                    return;
                                }
                                getMessagesController().addUsersToChat(currentChat, ChatUsersActivity.this, users, fwdCount, user -> {
                                    ChatUsersActivity.DiffCallback savedState = saveState();
                                    ArrayList<TLObject> array = contactsMap != null && contactsMap.size() != 0 ? contacts : participants;
                                    LongSparseArray<TLObject> map = contactsMap != null && contactsMap.size() != 0 ? contactsMap : participantsMap;
                                    if (map.get(user.id) == null) {
                                        if (ChatObject.isChannel(currentChat)) {
                                            TLRPC.TL_channelParticipant channelParticipant1 = new TLRPC.TL_channelParticipant();
                                            channelParticipant1.inviter_id = getUserConfig().getClientUserId();
                                            channelParticipant1.peer = new TLRPC.TL_peerUser();
                                            channelParticipant1.peer.user_id = user.id;
                                            channelParticipant1.date = getConnectionsManager().getCurrentTime();
                                            array.add(0, channelParticipant1);
                                            map.put(user.id, channelParticipant1);
                                        } else {
                                            TLRPC.ChatParticipant participant = new TLRPC.TL_chatParticipant();
                                            participant.user_id = user.id;
                                            participant.inviter_id = getUserConfig().getClientUserId();
                                            array.add(0, participant);
                                            map.put(user.id, participant);
                                        }
                                    }
                                    if (array == participants) {
                                        sortAdmins(participants);
                                    }
                                    updateListAnimated(savedState);
                                }, user -> {

                                }, null);
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
                } else if (position == antiSpamRow) {
                    final TextCell textCell = (TextCell) view;
                    if (info != null && !info.antispam && getParticipantsCount() < getMessagesController().telegramAntispamGroupSizeMin) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.msg_antispam, AndroidUtilities.replaceTags(LocaleController.formatPluralString("ChannelAntiSpamForbidden", getMessagesController().telegramAntispamGroupSizeMin))).show();
                    } else if (info != null && ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_DELETE_MESSAGES) && !antiSpamToggleLoading) {
                        antiSpamToggleLoading = true;
                        boolean wasAntispam = info.antispam;
                        TLRPC.TL_channels_toggleAntiSpam req = new TLRPC.TL_channels_toggleAntiSpam();
                        req.channel = getMessagesController().getInputChannel(chatId);
                        textCell.setChecked(req.enabled = (info.antispam = !info.antispam));
                        textCell.getCheckBox().setIcon(ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_DELETE_MESSAGES) && (info == null || info.antispam || getParticipantsCount() >= getMessagesController().telegramAntispamGroupSizeMin) ? 0 : R.drawable.permission_locked);
                        getConnectionsManager().sendRequest(req, (res, err) -> {
                            if (res != null) {
                                getMessagesController().processUpdates((TLRPC.Updates) res, false);
                                getMessagesController().putChatFull(info);
                            }
                            if (err != null && !"CHAT_NOT_MODIFIED".equals(err.text)) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (getParentActivity() == null) {
                                        return;
                                    }
                                    textCell.setChecked(info.antispam = wasAntispam);
                                    textCell.getCheckBox().setIcon(ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_DELETE_MESSAGES) && (info == null || !info.antispam || getParticipantsCount() >= getMessagesController().telegramAntispamGroupSizeMin) ? 0 : R.drawable.permission_locked);
                                    BulletinFactory.of(ChatUsersActivity.this).createSimpleBulletin(R.raw.error, getString("UnknownError", R.string.UnknownError)).show();
                                });
                            }
                            antiSpamToggleLoading = false;
                        });
                    }
                    return;
                } else if (position == hideMembersRow) {
                    final TextCell textCell = (TextCell) view;
                    if (getParticipantsCount() < getMessagesController().hiddenMembersGroupSizeMin) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contacts_sync_off, AndroidUtilities.replaceTags(LocaleController.formatPluralString("ChannelHiddenMembersForbidden", getMessagesController().hiddenMembersGroupSizeMin))).show();
                    } else if (info != null && ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_BLOCK_USERS) && !hideMembersToggleLoading) {
                        hideMembersToggleLoading = true;
                        boolean wasParticipantsHidden = info.participants_hidden;
                        TLRPC.TL_channels_toggleParticipantsHidden req = new TLRPC.TL_channels_toggleParticipantsHidden();
                        req.channel = getMessagesController().getInputChannel(chatId);
                        textCell.setChecked(req.enabled = (info.participants_hidden = !info.participants_hidden));
                        textCell.getCheckBox().setIcon(ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_BLOCK_USERS) && (info == null || info.participants_hidden || getParticipantsCount() >= getMessagesController().hiddenMembersGroupSizeMin) ? 0 : R.drawable.permission_locked);
                        getConnectionsManager().sendRequest(req, (res, err) -> {
                            if (res != null) {
                                getMessagesController().processUpdates((TLRPC.Updates) res, false);
                                getMessagesController().putChatFull(info);
                            }
                            if (err != null && !"CHAT_NOT_MODIFIED".equals(err.text)) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (getParentActivity() == null) {
                                        return;
                                    }
                                    textCell.setChecked(info.participants_hidden = wasParticipantsHidden);
                                    textCell.getCheckBox().setIcon(ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_BLOCK_USERS) && (info == null || !info.participants_hidden || getParticipantsCount() >= getMessagesController().hiddenMembersGroupSizeMin) ? 0 : R.drawable.permission_locked);
                                    BulletinFactory.of(ChatUsersActivity.this).createSimpleBulletin(R.raw.error, getString("UnknownError", R.string.UnknownError)).show();
                                });
                            }
                            hideMembersToggleLoading = false;
                        });
                    }
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
                                    BaseFragment editActivity = parentLayout.getFragmentStack().get(parentLayout.getFragmentStack().size() - 2);
                                    if (editActivity instanceof ChatEditActivity) {
                                        editActivity.removeSelfFromStack();

                                        Bundle args = new Bundle();
                                        args.putLong("chat_id", chatId);
                                        ChatEditActivity fragment = new ChatEditActivity(args);
                                        fragment.setInfo(info);
                                        parentLayout.addFragmentToStack(fragment, parentLayout.getFragmentStack().size() - 1);
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
                    if (info != null) {
                        ManageLinksActivity fragment = new ManageLinksActivity(chatId, 0, 0);
                        fragment.setInfo(info, info.exported_invite);
                        presentFragment(fragment);
                    }
                    return;
                } else if (position > permissionsSectionRow && position <= Math.max(manageTopicsRow, changeInfoRow)) {
                    TextCheckCell2 checkCell = (TextCheckCell2) view;
                    if (!checkCell.isEnabled()) {
                        return;
                    }
                    if (checkCell.hasIcon()) {
                        if (ChatObject.isPublic(currentChat) && (position == pinMessagesRow || position == changeInfoRow)) {
                            BulletinFactory.of(this).createErrorBulletin(getString(R.string.EditCantEditPermissionsPublic)).show();
                        } else if (ChatObject.isDiscussionGroup(currentAccount, chatId) && (position == pinMessagesRow || position == changeInfoRow)) {
                            BulletinFactory.of(this).createErrorBulletin(getString(R.string.EditCantEditPermissionsDiscussion)).show();
                        } else {
                            BulletinFactory.of(this).createErrorBulletin(getString("EditCantEditPermissions", R.string.EditCantEditPermissions)).show();
                        }
                        return;
                    }
                    if (position == sendMediaRow) {
                        //defaultBannedRights.send_media = !defaultBannedRights.send_media;
                        DiffCallback diffCallback = saveState();
                        sendMediaExpanded = !sendMediaExpanded;
                        AndroidUtilities.updateVisibleRows(listView);
                        updateListAnimated(diffCallback);
                        return;
                    }
                    checkCell.setChecked(!checkCell.isChecked());
                    if (position == changeInfoRow) {
                        defaultBannedRights.change_info = !defaultBannedRights.change_info;
                    } else if (position == addUsersRow) {
                        defaultBannedRights.invite_users = !defaultBannedRights.invite_users;
                    } else if (position == manageTopicsRow) {
                        defaultBannedRights.manage_topics = !defaultBannedRights.manage_topics;
                    } else if (position == pinMessagesRow) {
                        defaultBannedRights.pin_messages = !defaultBannedRights.pin_messages;
                    } else {
                        if (position == sendMessagesRow) {
                            defaultBannedRights.send_plain = !defaultBannedRights.send_plain;
                            if (sendMediaEmbededLinksRow >= 0) {
                                listViewAdapter.notifyItemChanged(sendMediaEmbededLinksRow);
                            }
                            if (sendMediaRow >= 0) {
                                listViewAdapter.notifyItemChanged(sendMediaRow);
                            }
                            DiffCallback diffCallback = saveState();
                            updateRows();
                            updateListAnimated(diffCallback);
                        } else if (position == sendMediaRow) {
                            DiffCallback diffCallback = saveState();
                            sendMediaExpanded = !sendMediaExpanded;
                            AndroidUtilities.updateVisibleRows(listView);
                            updateListAnimated(diffCallback);
                        } else if (position == sendStickersRow) {
                            defaultBannedRights.send_stickers = defaultBannedRights.send_games = defaultBannedRights.send_gifs = defaultBannedRights.send_inline = !defaultBannedRights.send_stickers;
                        } else if (position == embedLinksRow) {
                            defaultBannedRights.embed_links = !defaultBannedRights.embed_links;
                        } else if (position == sendPollsRow) {
                            defaultBannedRights.send_polls = !defaultBannedRights.send_polls;
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
                                            adminRights.manage_topics = adminRights.pin_messages = adminRights.add_admins = true;
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
                                        adminRights.manage_topics = adminRights.pin_messages = adminRights.add_admins = true;
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
                if (selectType != SELECT_TYPE_MEMBERS) {
                    if (selectType == SELECT_TYPE_EXCEPTION || selectType == SELECT_TYPE_ADMIN) {
                        if (selectType != SELECT_TYPE_ADMIN && canEditAdmin && (participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_chatParticipantAdmin)) {
                            final TLRPC.User user = getMessagesController().getUser(peerId);
                            final TLRPC.TL_chatBannedRights br = bannedRights;
                            final TLRPC.TL_chatAdminRights ar = adminRights;
                            final boolean canEdit = canEditAdmin;
                            final String rankFinal = rank;
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(getString("AppName", R.string.AppName));
                            builder.setMessage(LocaleController.formatString("AdminWillBeRemoved", R.string.AdminWillBeRemoved, UserObject.getUserName(user)));
                            builder.setPositiveButton(getString("OK", R.string.OK), (dialog, which) -> openRightsEdit(user.id, participant, ar, br, rankFinal, canEdit, selectType == SELECT_TYPE_ADMIN ? 0 : 1, false));
                            builder.setNegativeButton(getString("Cancel", R.string.Cancel), null);
                            showDialog(builder.create());
                        } else {
                            openRightsEdit(peerId, participant, adminRights, bannedRights, rank, canEditAdmin, selectType == SELECT_TYPE_ADMIN ? 0 : 1, selectType == SELECT_TYPE_ADMIN || selectType == SELECT_TYPE_EXCEPTION);
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
                    if (type == TYPE_BANNED || type != TYPE_ADMIN && isChannel || type == TYPE_USERS && selectType == SELECT_TYPE_MEMBERS) {
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
                            bannedRights.send_photos = true;
                            bannedRights.send_videos = true;
                            bannedRights.send_roundvideos = true;
                            bannedRights.send_audios = true;
                            bannedRights.send_voices = true;
                            bannedRights.send_docs = true;
                            bannedRights.embed_links = true;
                            bannedRights.send_plain = true;
                            bannedRights.send_messages = true;
                            bannedRights.send_games = true;
                            bannedRights.send_inline = true;
                            bannedRights.send_gifs = true;
                            bannedRights.pin_messages = true;
                            bannedRights.send_polls = true;
                            bannedRights.invite_users = true;
                            bannedRights.manage_topics = true;
                            bannedRights.change_info = true;
                        }
                        ChatRightsEditActivity fragment = new ChatRightsEditActivity(peerId, chatId, adminRights, defaultBannedRights, bannedRights, rank, type == TYPE_ADMIN ? ChatRightsEditActivity.TYPE_ADMIN : ChatRightsEditActivity.TYPE_BANNED, canEdit, participant == null, null);
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

        listView.setOnItemLongClickListener((view, position) -> {
            if (getParentActivity() != null && listView.getAdapter() == listViewAdapter) {
                return createMenuForParticipant(listViewAdapter.getItem(position), false, view);
            }
            return false;
        });
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

    private int getParticipantsCount() {
        if (info == null) {
            return 0;
        }
        int count = info.participants_count;
        if (info.participants != null && info.participants.participants != null) {
            count = Math.max(count, info.participants.participants.size());
        }
        return count;
    }

    private void setBannedRights(TLRPC.TL_chatBannedRights defaultBannedRights) {
        if (defaultBannedRights != null) {
            this.defaultBannedRights = defaultBannedRights;
        }
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
                                admin.admin_rights.manage_topics = admin.admin_rights.pin_messages = admin.admin_rights.add_admins = true;
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
        ChatRightsEditActivity fragment = new ChatRightsEditActivity(peerId, chatId, adminRights, defaultBannedRights, bannedRights, rank, type, true, false, null) {
            @Override
            public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
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
        ChatRightsEditActivity fragment = new ChatRightsEditActivity(user_id, chatId, adminRights, defaultBannedRights, bannedRights, rank, type, canEditAdmin, participant == null, null);
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
        getMessagesController().deleteParticipantFromChat(chatId, user);
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
                if (type == TYPE_BANNED && info != null) {
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

    private boolean createMenuForParticipant(final TLObject participant, boolean resultOnly, View view) {
        if (participant == null || selectType != SELECT_TYPE_MEMBERS) {
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
            boolean canChangePermission = ChatObject.canBlockUsers(currentChat) && canEditAdmin && !isChannel && ChatObject.isChannel(currentChat) && !currentChat.gigagroup;

            if (selectType == SELECT_TYPE_MEMBERS) {
                allowSetAdmin &= !UserObject.isDeleted(user);
            }

            boolean result = allowSetAdmin || (ChatObject.canBlockUsers(currentChat) && canEditAdmin);
            if (resultOnly || !result) {
                return result;
            }

            Utilities.Callback<Integer> openRightsFor = action ->
                openRightsEdit2(peerId, date, participant, adminRights, bannedRights, rank, canEditAdmin, action, false);

            ItemOptions.makeOptions(this, view)
                .setScrimViewBackground(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundWhite)))
                .addIf(allowSetAdmin, R.drawable.msg_admins, editingAdmin ? getString("EditAdminRights", R.string.EditAdminRights) : getString("SetAsAdmin", R.string.SetAsAdmin), () -> openRightsFor.run(0))
                .addIf(canChangePermission, R.drawable.msg_permissions, getString("ChangePermissions", R.string.ChangePermissions), () -> {
                    if (participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_chatParticipantAdmin) {
                        showDialog(
                            new AlertDialog.Builder(getParentActivity())
                                .setTitle(getString("AppName", R.string.AppName))
                                .setMessage(LocaleController.formatString("AdminWillBeRemoved", R.string.AdminWillBeRemoved, UserObject.getUserName(user)))
                                .setPositiveButton(getString("OK", R.string.OK), (dialog, which) -> openRightsFor.run(1))
                                .setNegativeButton(getString("Cancel", R.string.Cancel), null)
                                .create()
                        );
                    } else {
                        openRightsFor.run(1);
                    }
                })
                .addIf(ChatObject.canBlockUsers(currentChat) && canEditAdmin, R.drawable.msg_remove, isChannel ? getString("ChannelRemoveUser", R.string.ChannelRemoveUser) : getString("KickFromGroup", R.string.KickFromGroup), true, () -> {
                    getMessagesController().deleteParticipantFromChat(chatId, user);
                    removeParticipants(peerId);
                    if (currentChat != null && user != null && BulletinFactory.canShowBulletin(this)) {
                        BulletinFactory.createRemoveFromChatBulletin(this, user, currentChat.title).show();
                    }
                })
                .setMinWidth(190)
                .show();
        } else {

            ItemOptions options = ItemOptions.makeOptions(this, view);

            if (type == TYPE_KICKED && ChatObject.canBlockUsers(currentChat)) {
                options.add(R.drawable.msg_permissions, getString("ChannelEditPermissions", R.string.ChannelEditPermissions), () -> {
                    ChatRightsEditActivity fragment = new ChatRightsEditActivity(peerId, chatId, null, defaultBannedRights, bannedRights, rank, ChatRightsEditActivity.TYPE_BANNED, true, false, null);
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
                });
                options.add(R.drawable.msg_delete, getString("ChannelDeleteFromList", R.string.ChannelDeleteFromList), true, () -> deletePeer(peerId));
            } else if (type == TYPE_BANNED && ChatObject.canBlockUsers(currentChat)) {
                if (ChatObject.canAddUsers(currentChat) && peerId > 0) {
                    options.add(R.drawable.msg_contact_add, isChannel ? getString("ChannelAddToChannel", R.string.ChannelAddToChannel) : getString("ChannelAddToGroup", R.string.ChannelAddToGroup), () -> {
                        deletePeer(peerId);
                        TLRPC.User user = getMessagesController().getUser(peerId);
                        getMessagesController().addUserToChat(chatId, user, 0, null, ChatUsersActivity.this, null);
                    });
                }
                options.add(R.drawable.msg_delete, getString("ChannelDeleteFromList", R.string.ChannelDeleteFromList), true, () -> deletePeer(peerId));
            } else if (type == TYPE_ADMIN && ChatObject.canAddAdmins(currentChat) && canEdit) {
                if (currentChat.creator || !(participant instanceof TLRPC.TL_channelParticipantCreator)) {
                    options.add(R.drawable.msg_admins, getString("EditAdminRights", R.string.EditAdminRights), () -> {
                        ChatRightsEditActivity fragment = new ChatRightsEditActivity(peerId, chatId, adminRights, null, null, rank, ChatRightsEditActivity.TYPE_ADMIN, true, false, null);
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
                    });
                }
                options.add(R.drawable.msg_remove, getString("ChannelRemoveUserAdmin", R.string.ChannelRemoveUserAdmin), true, () -> {
                    getMessagesController().setUserAdminRole(chatId, getMessagesController().getUser(peerId), new TLRPC.TL_chatAdminRights(), "", !isChannel, ChatUsersActivity.this, false, false, null, null);
                    removeParticipants(peerId);
                });
            }

            options.setScrimViewBackground(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundWhite)));
            options.setMinWidth(190);

            boolean result = options.getItemsCount() > 0;
            if (resultOnly || !result) {
                return result;
            }

            options.show();
        }
        return true;
    }

    private void deletePeer(long peerId) {
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
                    isEnabledNotRestrictBoosters = info.boosts_unrestrict > 0;
                    notRestrictBoosters = info.boosts_unrestrict;
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
        if (!newBannedRights.equals(initialBannedRights) || initialSlowmode != selectedSlowmode || hasNotRestrictBoostersChanges() || signatures != initialSignatures || (signatures && profiles) != initialProfiles) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(getString("UserRestrictionsApplyChanges", R.string.UserRestrictionsApplyChanges));
            if (isChannel) {
                builder.setMessage(getString("ChannelSettingsChangedAlert", R.string.ChannelSettingsChangedAlert));
            } else {
                builder.setMessage(getString("GroupSettingsChangedAlert", R.string.GroupSettingsChangedAlert));
            }
            builder.setPositiveButton(getString("ApplyTheme", R.string.ApplyTheme), (dialogInterface, i) -> processDone());
            builder.setNegativeButton(getString("PassportDiscard", R.string.PassportDiscard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return true;
    }

    public boolean hasSelectType() {
        return selectType != SELECT_TYPE_MEMBERS;
    }

    private String formatUserPermissions(TLRPC.TL_chatBannedRights rights) {
        if (rights == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (rights.view_messages && defaultBannedRights.view_messages != rights.view_messages) {
            builder.append(getString("UserRestrictionsNoRead", R.string.UserRestrictionsNoRead));
        }
        if (rights.send_messages && defaultBannedRights.send_plain != rights.send_plain) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(getString("UserRestrictionsNoSendText", R.string.UserRestrictionsNoSendText));
        }
        if (rights.send_media && defaultBannedRights.send_media != rights.send_media) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(getString("UserRestrictionsNoSendMedia", R.string.UserRestrictionsNoSendMedia));
        } else {
            if (rights.send_photos && defaultBannedRights.send_photos != rights.send_photos) {
                if (builder.length() != 0) {
                    builder.append(", ");
                }
                builder.append(getString("UserRestrictionsNoSendPhotos", R.string.UserRestrictionsNoSendPhotos));
            }
            if (rights.send_videos && defaultBannedRights.send_videos != rights.send_videos) {
                if (builder.length() != 0) {
                    builder.append(", ");
                }
                builder.append(getString("UserRestrictionsNoSendVideos", R.string.UserRestrictionsNoSendVideos));
            }
            if (rights.send_audios && defaultBannedRights.send_audios != rights.send_audios) {
                if (builder.length() != 0) {
                    builder.append(", ");
                }
                builder.append(getString("UserRestrictionsNoSendMusic", R.string.UserRestrictionsNoSendMusic));
            }
            if (rights.send_docs && defaultBannedRights.send_docs != rights.send_docs) {
                if (builder.length() != 0) {
                    builder.append(", ");
                }
                builder.append(getString("UserRestrictionsNoSendDocs", R.string.UserRestrictionsNoSendDocs));
            }
            if (rights.send_voices && defaultBannedRights.send_voices != rights.send_voices) {
                if (builder.length() != 0) {
                    builder.append(", ");
                }
                builder.append(getString("UserRestrictionsNoSendVoice", R.string.UserRestrictionsNoSendVoice));
            }
            if (rights.send_roundvideos && defaultBannedRights.send_roundvideos != rights.send_roundvideos) {
                if (builder.length() != 0) {
                    builder.append(", ");
                }
                builder.append(getString("UserRestrictionsNoSendRound", R.string.UserRestrictionsNoSendRound));
            }
        }
        if (rights.send_stickers && defaultBannedRights.send_stickers != rights.send_stickers) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(getString("UserRestrictionsNoSendStickers", R.string.UserRestrictionsNoSendStickers));
        }
        if (rights.send_polls && defaultBannedRights.send_polls != rights.send_polls) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(getString("UserRestrictionsNoSendPolls", R.string.UserRestrictionsNoSendPolls));
        }
        if (rights.embed_links && !rights.send_plain && defaultBannedRights.embed_links != rights.embed_links) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(getString("UserRestrictionsNoEmbedLinks", R.string.UserRestrictionsNoEmbedLinks));
        }
        if (rights.invite_users && defaultBannedRights.invite_users != rights.invite_users) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(getString("UserRestrictionsNoInviteUsers", R.string.UserRestrictionsNoInviteUsers));
        }
        if (rights.pin_messages && defaultBannedRights.pin_messages != rights.pin_messages) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(getString("UserRestrictionsNoPinMessages", R.string.UserRestrictionsNoPinMessages));
        }
        if (rights.change_info && defaultBannedRights.change_info != rights.change_info) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(getString("UserRestrictionsNoChangeInfo", R.string.UserRestrictionsNoChangeInfo));
        }
        if (builder.length() != 0) {
            builder.replace(0, 1, builder.substring(0, 1).toUpperCase());
            builder.append('.');
        }
        return builder.toString();
    }

    private void processDone() {
        if (type == TYPE_KICKED) {
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

            if (hasNotRestrictBoostersChanges()) {
                boolean isEnabledNotRestrictBoosters = this.isEnabledNotRestrictBoosters && isNotRestrictBoostersVisible();
                if (isEnabledNotRestrictBoosters && notRestrictBoosters == 0) {
                    getMessagesController().setBoostsToUnblockRestrictions(chatId, 1);
                } else if (!isEnabledNotRestrictBoosters && notRestrictBoosters != 0) {
                    getMessagesController().setBoostsToUnblockRestrictions(chatId, 0);
                } else {
                    getMessagesController().setBoostsToUnblockRestrictions(chatId, notRestrictBoosters);
                }
            }
        } else if (type == TYPE_ADMIN) {
            if (signatures != initialSignatures || (signatures && profiles) != initialProfiles) {
                getMessagesController().toggleChannelSignatures(chatId, signatures, signatures && profiles);
            }
        }

        finishFragment();
    }

    private boolean hasNotRestrictBoostersChanges() {
        boolean isEnabledNotRestrictBoosters = this.isEnabledNotRestrictBoosters && isNotRestrictBoostersVisible();
        return info != null && (info.boosts_unrestrict != notRestrictBoosters
                || (isEnabledNotRestrictBoosters && notRestrictBoosters == 0)
                || (!isEnabledNotRestrictBoosters && notRestrictBoosters != 0));
    }

    private boolean isNotRestrictBoostersVisible() {
        return currentChat.megagroup && !currentChat.gigagroup && ChatObject.canUserDoAdminAction(currentChat,ChatObject.ACTION_DELETE_MESSAGES) &&
                (selectedSlowmode > 0 || defaultBannedRights.send_plain || defaultBannedRights.send_media || defaultBannedRights.send_photos || defaultBannedRights.send_videos
                        || defaultBannedRights.send_stickers || defaultBannedRights.send_audios || defaultBannedRights.send_docs
                        || defaultBannedRights.send_voices || defaultBannedRights.send_roundvideos || defaultBannedRights.embed_links || defaultBannedRights.send_polls);
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
        if (info != null) {
            selectedSlowmode = initialSlowmode = getCurrentSlowmode();
            isEnabledNotRestrictBoosters = info.boosts_unrestrict > 0;
            notRestrictBoosters = info.boosts_unrestrict;
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
        } else {
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

    private ArrayList<TLRPC.TL_channels_getParticipants> loadChatParticipantsRequests(int offset, int count, boolean reset) {
        TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        ArrayList<TLRPC.TL_channels_getParticipants> requests = new ArrayList<>();
        requests.add(req);
        req.channel = getMessagesController().getInputChannel(chatId);
        if (type == TYPE_BANNED) {
            req.filter = new TLRPC.TL_channelParticipantsKicked();
        } else if (type == TYPE_ADMIN) {
            req.filter = new TLRPC.TL_channelParticipantsAdmins();
        } else if (type == TYPE_USERS) {
            if (info != null && info.participants_count <= 200 && currentChat != null && currentChat.megagroup) {
                req.filter = new TLRPC.TL_channelParticipantsRecent();
            } else {
                if (selectType == SELECT_TYPE_ADMIN) {
                    if (!contactsEndReached) {
                        delayResults = 2;
                        req.filter = new TLRPC.TL_channelParticipantsContacts();
                        contactsEndReached = true;
                        requests.addAll(loadChatParticipantsRequests(0, 200, false));
                    } else {
                        req.filter = new TLRPC.TL_channelParticipantsRecent();
                    }
                } else {
                    if (!contactsEndReached) {
                        delayResults = 3;
                        req.filter = new TLRPC.TL_channelParticipantsContacts();
                        contactsEndReached = true;
                        requests.addAll(loadChatParticipantsRequests(0, 200, false));
                    } else if (!botsEndReached) {
                        req.filter = new TLRPC.TL_channelParticipantsBots();
                        botsEndReached = true;
                        requests.addAll(loadChatParticipantsRequests(0, 200, false));
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
        return requests;
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
                        if (selectType != SELECT_TYPE_MEMBERS && participant.user_id == selfUserId) {
                            continue;
                        }
                        if (ignoredUsers != null && ignoredUsers.indexOfKey(participant.user_id) >= 0) {
                            continue;
                        }
                        if (selectType == SELECT_TYPE_ADMIN) {
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
            final ArrayList<TLRPC.TL_channels_getParticipants> requests = loadChatParticipantsRequests(offset, count, reset);
            final ArrayList<TLRPC.TL_channels_channelParticipants> responses = new ArrayList<>();
            final Runnable onRequestsEnd = () -> {
                int objectsCount = 0;
                for (int i = 0; i < requests.size(); ++i) {
                    TLRPC.TL_channels_getParticipants req = requests.get(i);
                    TLRPC.TL_channels_channelParticipants res = responses.get(i);
                    if (req == null || res == null)
                        continue;
                    if (type == TYPE_ADMIN) {
                        getMessagesController().processLoadedAdminsResponse(chatId, res);
                    }
                    getMessagesController().putUsers(res.users, false);
                    getMessagesController().putChats(res.chats, false);
                    long selfId = getUserConfig().getClientUserId();
                    if (selectType != SELECT_TYPE_MEMBERS) {
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
                    objectsCount += objects.size();
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
                            } else if (selectType == SELECT_TYPE_ADMIN && peerId > 0 && UserObject.isDeleted(getMessagesController().getUser(peerId))) {
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
                    if (searchItem != null) {
                        searchItem.setVisibility(type != TYPE_BANNED || firstLoaded && objectsCount > 5 ? View.VISIBLE : View.GONE);
                    }
                }
                updateRows();
                if (listViewAdapter != null) {
                    listView.setAnimateEmptyView(openTransitionStarted, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
                    listViewAdapter.notifyDataSetChanged();

                    if (emptyView != null && listViewAdapter.getItemCount() == 0 && firstLoaded) {
                        emptyView.showProgress(false, true);
                    }
                }
                resumeDelayedFragmentAnimation();
            };
            AtomicInteger responsesReceived = new AtomicInteger(0);
            for (int i = 0; i < requests.size(); ++i) {
                responses.add(null);
                final int index = i;
                int reqId = getConnectionsManager().sendRequest(requests.get(index), (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (error == null && response instanceof TLRPC.TL_channels_channelParticipants)
                        responses.set(index, (TLRPC.TL_channels_channelParticipants) response);
                    responsesReceived.getAndIncrement();
                    if (responsesReceived.get() == requests.size()) {
                        onRequestsEnd.run();
                    }
                }));
                getConnectionsManager().bindRequestToGuid(reqId, classGuid);
            }
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
    public void onBecomeFullyHidden() {
        if (undoView != null) {
            undoView.hide(true, 0);
        }
    }

    public int getSelectType() {
        return selectType;
    }

//    @Override
//    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
//        super.onTransitionAnimationStart(isOpen, backward);
//        if (isOpen) {
//            openTransitionStarted = true;
//        }
//    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            openTransitionStarted = true;
        }
        if (isOpen && !backward && needOpenSearch) {
            searchItem.getSearchField().requestFocus();
            AndroidUtilities.showKeyboard(searchItem.getSearchField());
            searchItem.setVisibility(View.GONE);
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
                final ArrayList<TLRPC.TL_contact> contactsCopy = selectType == SELECT_TYPE_ADMIN ? new ArrayList<>(getContactsController().contacts) : null;

                Runnable addContacts = null;
                if (participantsCopy != null || contactsCopy != null) {
                    addContacts = () -> {
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
                                    username = UserObject.getPublicUsername(user);
                                    firstName = user.first_name;
                                    lastName = user.last_name;
                                } else {
                                    TLRPC.Chat chat = getMessagesController().getChat(-peerId);
                                    name = chat.title.toLowerCase();
                                    username = ChatObject.getPublicUsername(chat);
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
                                String username;
                                for (String q : search) {
                                    if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                        found = 1;
                                    } else if ((username = UserObject.getPublicUsername(user)) != null && username.startsWith(q)) {
                                        found = 2;
                                    }

                                    if (found != 0) {
                                        if (found == 1) {
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                        } else {
                                            resultArrayNames.add(AndroidUtilities.generateSearchName("@" + UserObject.getPublicUsername(user), null, "@" + q));
                                        }
                                        resultArray.add(user);
                                        resultMap.put(user.id, user);
                                        break;
                                    }
                                }
                            }
                        }
                        updateSearchResults(resultArray, resultMap, resultArrayNames, resultArray2);
                    };
                } else {
                    searchInProgress = false;
                }
                searchAdapterHelper.queryServerSearch(query, selectType != SELECT_TYPE_MEMBERS, false, true, false, false, ChatObject.isChannel(currentChat) ? chatId : 0, false, type, 1, 0, addContacts);
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
                listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
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
                    ManageChatUserCell manageChatUserCell = new ManageChatUserCell(mContext, 2, 2, selectType == SELECT_TYPE_MEMBERS);
                    manageChatUserCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    manageChatUserCell.setDelegate((cell, click) -> {
                        TLObject object = getItem((Integer) cell.getTag());
                        if (object instanceof TLRPC.ChannelParticipant) {
                            TLRPC.ChannelParticipant participant = (TLRPC.ChannelParticipant) object;
                            return createMenuForParticipant(participant, !click, cell);
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
                        if (peerId >= 0) {
                            TLRPC.User user = getMessagesController().getUser(peerId);
                            if (user != null) {
                                un = UserObject.getPublicUsername(user);
                            }
                            peerObject = user;
                        } else {
                            TLRPC.Chat chat = getMessagesController().getChat(-peerId);
                            if (chat != null) {
                                un = ChatObject.getPublicUsername(chat);
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
                            sectionCell.setText(getString("ChannelBlockedUsers", R.string.ChannelBlockedUsers));
                        } else if (type == TYPE_KICKED) {
                            sectionCell.setText(getString("ChannelRestrictedUsers", R.string.ChannelRestrictedUsers));
                        } else {
                            if (isChannel) {
                                sectionCell.setText(getString("ChannelSubscribers", R.string.ChannelSubscribers));
                            } else {
                                sectionCell.setText(getString("ChannelMembers", R.string.ChannelMembers));
                            }
                        }
                    } else if (position == globalStartRow) {
                        sectionCell.setText(getString("GlobalSearch", R.string.GlobalSearch));
                    } else if (position == contactsStartRow) {
                        sectionCell.setText(getString("Contacts", R.string.Contacts));
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
            if (viewType == VIEW_TYPE_CHECK) {
                return true;
            }
            if (viewType == 7 || viewType == VIEW_TYPE_EXPANDABLE_SWITCH) {
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
            int position = holder.getAdapterPosition();
            if (viewType == 0 || viewType == 2 || viewType == 6) {
                return true;
            }
            if (viewType == 12) {
                if (position == antiSpamRow) {
                    return ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_DELETE_MESSAGES);
                } else if (position == hideMembersRow) {
                    return ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_BLOCK_USERS);
                }
            }
            if (viewType == VIEW_TYPE_INNER_CHECK) {
                return true;
            }
            return false;
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
                    ManageChatUserCell manageChatUserCell = new ManageChatUserCell(mContext, type == TYPE_BANNED || type == TYPE_KICKED ? 7 : 6, type == TYPE_BANNED || type == TYPE_KICKED ? 6 : 2, selectType == SELECT_TYPE_MEMBERS);
                    manageChatUserCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    manageChatUserCell.setDelegate((cell, click) -> {
                        TLObject participant = listViewAdapter.getItem((Integer) cell.getTag());
                        return createMenuForParticipant(participant, !click, cell);
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
                    view = new TextInfoPrivacyCell(mContext);
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) view;
                    if (isChannel) {
                        privacyCell.setText(getString(R.string.NoBlockedChannel2));
                    } else {
                        privacyCell.setText(getString(R.string.NoBlockedGroup2));
                    }
                    privacyCell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
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
                case VIEW_TYPE_EXPANDABLE_SWITCH:
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
                case 12:
                    TextCell textCell = new TextCell(mContext, 23, false, true, getResourceProvider());
                    textCell.heightDp = 50;
                    textCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = textCell;
                    break;
                case 9:
                default:
                    SlideChooseView chooseView = new SlideChooseView(mContext);
                    view = chooseView;
                    chooseView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    chooseView.setOptions(
                            selectedSlowmode,
                            getString("SlowmodeOff", R.string.SlowmodeOff),
                            LocaleController.formatString("SlowmodeSeconds", R.string.SlowmodeSeconds, 10),
                            LocaleController.formatString("SlowmodeSeconds", R.string.SlowmodeSeconds, 30),
                            LocaleController.formatString("SlowmodeMinutes", R.string.SlowmodeMinutes, 1),
                            LocaleController.formatString("SlowmodeMinutes", R.string.SlowmodeMinutes, 5),
                            LocaleController.formatString("SlowmodeMinutes", R.string.SlowmodeMinutes, 15),
                            LocaleController.formatString("SlowmodeHours", R.string.SlowmodeHours, 1)
                    );
                    chooseView.setCallback(which -> {
                        if (info == null) {
                            return;
                        }
                        boolean needRowsUpdate = (selectedSlowmode > 0 && which == 0) || (selectedSlowmode == 0 && which > 0);
                        selectedSlowmode = which;
                        if (needRowsUpdate) {
                            DiffCallback diffCallback = saveState();
                            updateRows();
                            updateListAnimated(diffCallback);
                        }
                        listViewAdapter.notifyItemChanged(slowmodeInfoRow);
                    });
                    break;
                case VIEW_TYPE_NOT_RESTRICT_BOOSTERS_SLIDER: {
                    SlideChooseView slider = new SlideChooseView(mContext);
                    view = slider;
                    slider.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    Drawable[] drawables = new Drawable[]{
                            ContextCompat.getDrawable(getContext(), R.drawable.mini_boost_profile_badge),
                            ContextCompat.getDrawable(getContext(), R.drawable.mini_boost_profile_badge2),
                            ContextCompat.getDrawable(getContext(), R.drawable.mini_boost_profile_badge2),
                            ContextCompat.getDrawable(getContext(), R.drawable.mini_boost_profile_badge2),
                            ContextCompat.getDrawable(getContext(), R.drawable.mini_boost_profile_badge2)
                    };
                    slider.setOptions(notRestrictBoosters > 0 ? (notRestrictBoosters - 1) : 0, drawables, "1", "2", "3", "4", "5");
                    slider.setCallback(which -> {
                        notRestrictBoosters = which + 1;
                    });
                    break;
                }
                case VIEW_TYPE_INNER_CHECK:
                    CheckBoxCell checkBoxCell = new CheckBoxCell(mContext, 4, 21, getResourceProvider());
                    checkBoxCell.getCheckBoxRound().setDrawBackgroundAsArc(14);
                    checkBoxCell.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked, Theme.key_radioBackground, Theme.key_checkboxCheck);
                    checkBoxCell.setEnabled(true);
                    view = checkBoxCell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_CHECK:
                    view = new TextCheckCell(mContext, getResourceProvider());
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
                                role = getString("ChannelCreator", R.string.ChannelCreator);
                            } else if (admin) {
                                TLRPC.User user1 = getMessagesController().getUser(promotedBy);
                                if (user1 != null) {
                                    if (user1.id == peerId) {
                                        role = getString("ChannelAdministrator", R.string.ChannelAdministrator);
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
                    if (position == antiSpamInfoRow) {
                        privacyCell.setText(getString("ChannelAntiSpamInfo", R.string.ChannelAntiSpamInfo));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == participantsInfoRow) {
                        if (type == TYPE_BANNED || type == TYPE_KICKED) {
                            if (isChannel) {
                                privacyCell.setText(getString("NoBlockedChannel2", R.string.NoBlockedChannel2));
                            } else {
                                privacyCell.setText(getString("NoBlockedGroup2", R.string.NoBlockedGroup2));
                            }
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        } else if (type == TYPE_ADMIN) {
                            if (addNewRow != -1) {
                                if (isChannel) {
                                    privacyCell.setText(getString("ChannelAdminsInfo", R.string.ChannelAdminsInfo));
                                } else {
                                    privacyCell.setText(getString("MegaAdminsInfo", R.string.MegaAdminsInfo));
                                }
                            } else {
                                privacyCell.setText("");
                            }
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        } else if (type == TYPE_USERS) {
                            if (!isChannel || selectType != SELECT_TYPE_MEMBERS) {
                                privacyCell.setText("");
                            } else {
                                privacyCell.setText(getString("ChannelMembersInfo", R.string.ChannelMembersInfo));
                            }
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        }
                    } else if (position == slowmodeInfoRow) {
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        int seconds = getSecondsForIndex(selectedSlowmode);
                        if (info == null || seconds == 0) {
                            privacyCell.setText(getString("SlowmodeInfoOff", R.string.SlowmodeInfoOff));
                        } else {
                            privacyCell.setText(LocaleController.formatString("SlowmodeInfoSelected", R.string.SlowmodeInfoSelected, formatSeconds(seconds)));
                        }
                    } else if (position == hideMembersInfoRow) {
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        privacyCell.setText(getString("ChannelHideMembersInfo", R.string.ChannelHideMembersInfo));
                    } else if (position == gigaInfoRow) {
                        privacyCell.setText(getString("BroadcastGroupConvertInfo", R.string.BroadcastGroupConvertInfo));
                    } else if (position == dontRestrictBoostersInfoRow) {
                        privacyCell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        if (isEnabledNotRestrictBoosters) {
                            privacyCell.setText(getString(R.string.GroupNotRestrictBoostersInfo2));
                        } else {
                            privacyCell.setText(getString(R.string.GroupNotRestrictBoostersInfo));
                        }
                    } else if (position == signMessagesInfoRow) {
                        privacyCell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        privacyCell.setText(getString(signatures ? R.string.ChannelSignProfilesInfo : R.string.ChannelSignInfo));
                    }
                    break;
                case 2:
                    ManageChatTextCell actionCell = (ManageChatTextCell) holder.itemView;
                    actionCell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                    if (position == addNewRow) {
                        if (type == TYPE_KICKED) {
                            actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                            actionCell.setText(getString("ChannelAddException", R.string.ChannelAddException), null, R.drawable.msg_contact_add, participantsStartRow != -1);
                        } else if (type == TYPE_BANNED) {
                            actionCell.setText(getString("ChannelBlockUser", R.string.ChannelBlockUser), null, R.drawable.msg_user_remove, false);
                        } else if (type == TYPE_ADMIN) {
                            actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                            boolean showDivider = !(loadingUsers && !firstLoaded);
                            actionCell.setText(getString("ChannelAddAdmin", R.string.ChannelAddAdmin), null, R.drawable.msg_admin_add, showDivider);
                        } else if (type == TYPE_USERS) {
                            actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                            boolean showDivider = addNew2Row != -1 || (!(loadingUsers && !firstLoaded) && membersHeaderRow == -1 && !participants.isEmpty());
                            if (isChannel) {
                                actionCell.setText(getString("AddSubscriber", R.string.AddSubscriber), null, R.drawable.msg_contact_add, showDivider);
                            } else {
                                actionCell.setText(getString("AddMember", R.string.AddMember), null, R.drawable.msg_contact_add, showDivider);
                            }
                        }
                    } else if (position == recentActionsRow) {
                        actionCell.setText(getString("EventLog", R.string.EventLog), null, R.drawable.msg_log, antiSpamRow > recentActionsRow);
                    } else if (position == addNew2Row) {
                        actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                        boolean showDivider = !(loadingUsers && !firstLoaded) && membersHeaderRow == -1 && !participants.isEmpty();
                        actionCell.setText(getString("ChannelInviteViaLink", R.string.ChannelInviteViaLink), null, R.drawable.msg_link2, showDivider);
                    } else if (position == gigaConvertRow) {
                        actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                        actionCell.setText(getString("BroadcastGroupConvert", R.string.BroadcastGroupConvert), null, R.drawable.msg_channel, false);
                    }
                    break;
                case 3:
                    if (position == addNewSectionRow || type == TYPE_KICKED && position == participantsDividerRow && addNewRow == -1 && participantsStartRow == -1) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
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
                                headerCell.setText(getString("ChannelBlockedUsers", R.string.ChannelBlockedUsers));
                            }
                        } else {
                            headerCell.setText(getString("ChannelRestrictedUsers", R.string.ChannelRestrictedUsers));
                        }
                    } else if (position == permissionsSectionRow) {
                        headerCell.setText(getString("ChannelPermissionsHeader", R.string.ChannelPermissionsHeader));
                    } else if (position == slowmodeRow) {
                        headerCell.setText(getString("Slowmode", R.string.Slowmode));
                    } else if (position == gigaHeaderRow) {
                        headerCell.setText(getString("BroadcastGroup", R.string.BroadcastGroup));
                    }
                    break;
                case 6:
                    TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                    settingsCell.setTextAndValue(getString("ChannelBlacklist", R.string.ChannelBlacklist), String.format("%d", info != null ? info.kicked_count : 0), false);
                    break;
                case VIEW_TYPE_EXPANDABLE_SWITCH:
                case 7:
                    TextCheckCell2 checkCell = (TextCheckCell2) holder.itemView;
                    checkCell.getCheckBox().setDrawIconType(1);
                    checkCell.getCheckBox().setColors(Theme.key_fill_RedNormal, Theme.key_switch2TrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
                    boolean animated = checkCell.getTag() != null && (Integer) checkCell.getTag() == position;
                    checkCell.setTag(position);
                    if (position == changeInfoRow) {
                        checkCell.setTextAndCheck(getString("UserRestrictionsChangeInfo", R.string.UserRestrictionsChangeInfo), !defaultBannedRights.change_info && !ChatObject.isPublic(currentChat), manageTopicsRow != -1, animated);
                    } else if (position == addUsersRow) {
                        checkCell.setTextAndCheck(getString("UserRestrictionsInviteUsers", R.string.UserRestrictionsInviteUsers), !defaultBannedRights.invite_users, true, animated);
                    } else if (position == pinMessagesRow) {
                        checkCell.setTextAndCheck(getString("UserRestrictionsPinMessages", R.string.UserRestrictionsPinMessages), !defaultBannedRights.pin_messages && !ChatObject.isPublic(currentChat), true, animated);
                    } else if (position == sendMessagesRow) {
                        checkCell.setTextAndCheck(getString("UserRestrictionsSendText", R.string.UserRestrictionsSendText), !defaultBannedRights.send_plain, true, animated);
                    } else if(position == dontRestrictBoostersRow) {
                        checkCell.setTextAndCheck(getString(R.string.GroupNotRestrictBoosters), isEnabledNotRestrictBoosters, false, animated);
                        checkCell.getCheckBox().setDrawIconType(0);
                        checkCell.getCheckBox().setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
                    } else if (position == sendMediaRow) {
                        int sentMediaCount = getSendMediaSelectedCount();
                        checkCell.setTextAndCheck(getString("UserRestrictionsSendMedia", R.string.UserRestrictionsSendMedia), sentMediaCount > 0, true, animated);
                        checkCell.setCollapseArrow(String.format(Locale.US, "%d/9", sentMediaCount), !sendMediaExpanded, new Runnable() {
                            @Override
                            public void run() {
                                boolean checked = !checkCell.isChecked();
                                checkCell.setChecked(checked);
                                setSendMediaEnabled(checked);
                            }
                        });
                    } else if (position == sendStickersRow) {
                        checkCell.setTextAndCheck(getString("UserRestrictionsSendStickers", R.string.UserRestrictionsSendStickers), !defaultBannedRights.send_stickers, true, animated);
                    } else if (position == embedLinksRow) {
                        checkCell.setTextAndCheck(getString("UserRestrictionsEmbedLinks", R.string.UserRestrictionsEmbedLinks), !defaultBannedRights.embed_links, true, animated);
                    } else if (position == sendPollsRow) {
                        checkCell.setTextAndCheck(getString("UserRestrictionsSendPollsShort", R.string.UserRestrictionsSendPollsShort), !defaultBannedRights.send_polls, true);
                    } else if (position == manageTopicsRow) {
                        checkCell.setTextAndCheck(getString("CreateTopicsPermission", R.string.CreateTopicsPermission), !defaultBannedRights.manage_topics, false, animated);
                    }
                    if ((position == pinMessagesRow || position == changeInfoRow) && ChatObject.isDiscussionGroup(currentAccount, chatId)) {
                        checkCell.setIcon(R.drawable.permission_locked);
                    } else if (ChatObject.canBlockUsers(currentChat)) {
                        if (position == addUsersRow && !ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE) ||
                                position == pinMessagesRow && !ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_PIN) ||
                                position == changeInfoRow && !ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_CHANGE_INFO) ||
                                position == manageTopicsRow && !ChatObject.canManageTopics(currentChat) ||
                                ChatObject.isPublic(currentChat) && (position == pinMessagesRow || position == changeInfoRow)) {
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
                            sectionCell.setText(getString("ChannelOtherSubscribers", R.string.ChannelOtherSubscribers));
                        } else {
                            sectionCell.setText(getString("ChannelOtherMembers", R.string.ChannelOtherMembers));
                        }
                    } else if (position == botHeaderRow) {
                        sectionCell.setText(getString("ChannelBots", R.string.ChannelBots));
                    } else if (position == contactsHeaderRow) {
                        if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                            sectionCell.setText(getString("ChannelContacts", R.string.ChannelContacts));
                        } else {
                            sectionCell.setText(getString("GroupContacts", R.string.GroupContacts));
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
                case 12:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == antiSpamRow) {
                        textCell.getCheckBox().setIcon(ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_DELETE_MESSAGES) && (info == null || info.antispam || getParticipantsCount() >= getMessagesController().telegramAntispamGroupSizeMin) ? 0 : R.drawable.permission_locked);
                        textCell.setTextAndCheckAndIcon(getString("ChannelAntiSpam", R.string.ChannelAntiSpam), info != null && info.antispam, R.drawable.msg_policy, false);
                    } else if (position == hideMembersRow) {
                        textCell.getCheckBox().setIcon(ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_BLOCK_USERS) && (info == null || info.participants_hidden || getParticipantsCount() >= getMessagesController().hiddenMembersGroupSizeMin) ? 0 : R.drawable.permission_locked);
                        textCell.setTextAndCheck(getString("ChannelHideMembers", R.string.ChannelHideMembers), info != null && info.participants_hidden, false);
                    }
                    break;
                case VIEW_TYPE_INNER_CHECK:
                    CheckBoxCell checkBoxCell = (CheckBoxCell) holder.itemView;
                    animated = checkBoxCell.getTag() != null && (Integer) checkBoxCell.getTag() == position;
                    checkBoxCell.setTag(position);
                    if (position == sendMediaPhotosRow) {
                        checkBoxCell.setText(getString("SendMediaPermissionPhotos", R.string.SendMediaPermissionPhotos), "", !defaultBannedRights.send_photos, true, animated);
                    } else if (position == sendMediaVideosRow) {
                        checkBoxCell.setText(getString("SendMediaPermissionVideos", R.string.SendMediaPermissionVideos), "", !defaultBannedRights.send_videos, true, animated);
                    } else if (position == sendMediaStickerGifsRow) {
                        checkBoxCell.setText(getString("SendMediaPermissionStickersGifs", R.string.SendMediaPermissionStickersGifs), "", !defaultBannedRights.send_stickers, true, animated);
                    } else if (position == sendMediaMusicRow) {
                        checkBoxCell.setText(getString("SendMediaPermissionMusic", R.string.SendMediaPermissionMusic), "", !defaultBannedRights.send_audios, true, animated);
                    } else if (position == sendMediaFilesRow) {
                        checkBoxCell.setText(getString("SendMediaPermissionFiles", R.string.SendMediaPermissionFiles), "", !defaultBannedRights.send_docs, true, animated);
                    } else if (position == sendMediaVoiceMessagesRow) {
                        checkBoxCell.setText(getString("SendMediaPermissionVoice", R.string.SendMediaPermissionVoice), "", !defaultBannedRights.send_voices, true, animated);
                    } else if (position == sendMediaVideoMessagesRow) {
                        checkBoxCell.setText(getString("SendMediaPermissionRound", R.string.SendMediaPermissionRound), "", !defaultBannedRights.send_roundvideos, true, animated);
                    } else if (position == sendMediaEmbededLinksRow) {
                        checkBoxCell.setText(getString("SendMediaEmbededLinks", R.string.SendMediaEmbededLinks), "", !defaultBannedRights.embed_links && !defaultBannedRights.send_plain, false, animated);
                    } else if (position == sendPollsRow) {
                        checkBoxCell.setText(getString("SendMediaPolls", R.string.SendMediaPolls), "", !defaultBannedRights.send_polls, false, animated);
                    } else
                    //  checkBoxCell.setText(getCheckBoxTitle(item.headerName, percents[item.index < 0 ? 8 : item.index], item.index < 0), AndroidUtilities.formatFileSize(item.size), selected, item.index < 0 ? !collapsed : !item.last);
                    checkBoxCell.setPad(1);
                    break;
                case VIEW_TYPE_CHECK:
                    TextCheckCell checkCell2 = (TextCheckCell) holder.itemView;
                    if (position == signMessagesRow) {
                        checkCell2.setTextAndCheck(getString(R.string.ChannelSignMessages), signatures, signatures);
                    } else if (position == signMessagesProfilesRow) {
                        checkCell2.setTextAndCheck(getString(R.string.ChannelSignMessagesWithProfile), profiles, false);
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
            } else if (position == participantsInfoRow || position == slowmodeInfoRow || position == dontRestrictBoostersInfoRow || position == gigaInfoRow || position == antiSpamInfoRow || position == hideMembersInfoRow || position == signMessagesInfoRow) {
                return 1;
            } else if (position == blockedEmptyRow) {
                return 4;
            } else if (position == removedUsersRow) {
                return 6;
            } else if (position == changeInfoRow || position == addUsersRow || position == pinMessagesRow || position == sendMessagesRow ||
                    position == sendStickersRow || position == embedLinksRow || position == manageTopicsRow || position == dontRestrictBoostersRow) {
                return 7;
            } else if (position == membersHeaderRow || position == contactsHeaderRow || position == botHeaderRow || position == loadingHeaderRow) {
                return 8;
            } else if (position == slowmodeSelectRow) {
                return 9;
            } else if (position == loadingProgressRow) {
                return 10;
            } else if (position == loadingUserCellRow) {
                return 11;
            } else if (position == antiSpamRow || position == hideMembersRow) {
                return 12;
            } else if (isExpandableSendMediaRow(position)) {
                return VIEW_TYPE_INNER_CHECK;
            } else if (position == sendMediaRow) {
                return VIEW_TYPE_EXPANDABLE_SWITCH;
            } else if (position == dontRestrictBoostersSliderRow) {
                return VIEW_TYPE_NOT_RESTRICT_BOOSTERS_SLIDER;
            } else if (position == signMessagesRow || position == signMessagesProfilesRow) {
                return VIEW_TYPE_CHECK;
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

    private void setSendMediaEnabled(boolean enabled) {
        defaultBannedRights.send_media = !enabled;
        defaultBannedRights.send_gifs = !enabled;
        defaultBannedRights.send_inline = !enabled;
        defaultBannedRights.send_games = !enabled;
        defaultBannedRights.send_photos = !enabled;
        defaultBannedRights.send_videos = !enabled;
        defaultBannedRights.send_stickers = !enabled;
        defaultBannedRights.send_audios = !enabled;
        defaultBannedRights.send_docs = !enabled;
        defaultBannedRights.send_voices = !enabled;
        defaultBannedRights.send_roundvideos = !enabled;
        defaultBannedRights.embed_links = !enabled;
        defaultBannedRights.send_polls = !enabled;
        AndroidUtilities.updateVisibleRows(listView);
        DiffCallback diffCallback = saveState();
        updateRows();
        updateListAnimated(diffCallback);
    }

    private boolean isExpandableSendMediaRow(int position) {
        return position == sendMediaPhotosRow || position == sendMediaVideosRow || position == sendMediaStickerGifsRow ||
                position == sendMediaMusicRow || position == sendMediaFilesRow || position == sendMediaVoiceMessagesRow ||
                position == sendMediaVideoMessagesRow || position == sendMediaEmbededLinksRow || position == sendPollsRow;
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
            if (isForum) {
                put(++pointer, manageTopicsRow, sparseIntArray);
            }
            put(++pointer, changeInfoRow, sparseIntArray);
            put(++pointer, removedUsersRow, sparseIntArray);
            put(++pointer, contactsHeaderRow, sparseIntArray);
            put(++pointer, botHeaderRow, sparseIntArray);
            put(++pointer, membersHeaderRow, sparseIntArray);
            put(++pointer, slowmodeRow, sparseIntArray);
            put(++pointer, slowmodeSelectRow, sparseIntArray);
            put(++pointer, slowmodeInfoRow, sparseIntArray);
            put(++pointer, dontRestrictBoostersRow, sparseIntArray);
            put(++pointer, dontRestrictBoostersSliderRow, sparseIntArray);
            put(++pointer, dontRestrictBoostersInfoRow, sparseIntArray);
            put(++pointer, loadingProgressRow, sparseIntArray);
            put(++pointer, loadingUserCellRow, sparseIntArray);
            put(++pointer, loadingHeaderRow, sparseIntArray);
            put(++pointer, signMessagesRow, sparseIntArray);
            put(++pointer, signMessagesProfilesRow, sparseIntArray);
            put(++pointer, signMessagesInfoRow, sparseIntArray);
        }

        private void put(int id, int position, SparseIntArray sparseIntArray) {
            if (position >= 0) {
                sparseIntArray.put(position, id);
            }
        }
    }

    private int getSendMediaSelectedCount() {
        return getSendMediaSelectedCount(defaultBannedRights);
    }

    public static int getSendMediaSelectedCount(TLRPC.TL_chatBannedRights bannedRights) {
        int i = 0;
        if (!bannedRights.send_photos) {
            i++;
        }
        if (!bannedRights.send_videos) {
            i++;
        }
        if (!bannedRights.send_stickers) {
            i++;
        }
        if (!bannedRights.send_audios) {
            i++;
        }
        if (!bannedRights.send_docs) {
            i++;
        }
        if (!bannedRights.send_voices) {
            i++;
        }
        if (!bannedRights.send_roundvideos) {
            i++;
        }
        if (!bannedRights.embed_links && !bannedRights.send_plain) {
            i++;
        }
        if (!bannedRights.send_polls) {
            i++;
        }
        return i;
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

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, ManageChatUserCell.class, ManageChatTextCell.class, TextCheckCell2.class, TextSettingsCell.class, SlideChooseView.class}, null, null, null, Theme.key_windowBackgroundWhite));
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
