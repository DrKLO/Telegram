package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Components.UniversalAdapter.VIEW_TYPE_EXPANDABLE_SWITCH;
import static org.telegram.ui.Components.UniversalAdapter.VIEW_TYPE_ROUND_CHECKBOX;
import static org.telegram.ui.Components.UniversalAdapter.VIEW_TYPE_SHADOW_COLLAPSE_BUTTON;
import static org.telegram.ui.Components.UniversalAdapter.VIEW_TYPE_SWITCH;
import static org.telegram.ui.Components.UniversalAdapter.VIEW_TYPE_USER_GROUP_CHECKBOX;
import static org.telegram.ui.Components.UniversalAdapter.VIEW_TYPE_USER_CHECKBOX;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CollapseTextCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorBtnCell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class DeleteMessagesBottomSheet extends BottomSheetWithRecyclerListView {
    private UniversalAdapter adapter;

    private SelectorBtnCell buttonContainer;
    private TextView actionButton;

    private TLRPC.Chat inChat;
    private boolean isForum;
    private ArrayList<MessageObject> messages;
    private long mergeDialogId;
    private int topicId;
    private int mode;
    private Runnable onDelete;

    private boolean restrict = false;

    private static final int ACTION_REPORT = 0;
    private static final int ACTION_DELETE_ALL = 1;
    private static final int ACTION_BAN = 2;

    private boolean monoforum;
    private Action report;
    private Action deleteAll;
    private Action banOrRestrict;

    private boolean[] banFilter;
    private boolean[] restrictFilter;
    private boolean canRestrict;

    private int[] participantMessageCounts;
    private boolean participantMessageCountsLoading = false;
    private boolean participantMessageCountsLoaded = false;

    private TLRPC.TL_chatBannedRights defaultBannedRights;
    private TLRPC.TL_chatBannedRights bannedRights;
    private ArrayList<TLRPC.TL_chatBannedRights> participantsBannedRights;
    private boolean sendMediaCollapsed = true;

    private static final int RIGHT_SEND_MESSAGES = 0;
    private static final int RIGHT_SEND_MEDIA = 1;
    private static final int RIGHT_ADD_USERS = 2;
    private static final int RIGHT_PIN_MESSAGES = 3;
    private static final int RIGHT_CHANGE_CHAT_INFO = 4;
    private static final int RIGHT_CREATE_TOPICS = 5;
    private static final int RIGHT_SEND_PHOTOS = 6;
    private static final int RIGHT_SEND_VIDEOS = 7;
    private static final int RIGHT_SEND_FILES = 8;
    private static final int RIGHT_SEND_MUSIC = 9;
    private static final int RIGHT_SEND_VOICE = 10;
    private static final int RIGHT_SEND_ROUND = 11;
    private static final int RIGHT_SEND_STICKERS = 12;
    private static final int RIGHT_SEND_POLLS = 13;
    private static final int RIGHT_SEND_LINKS = 14;

    private class Action {
        int type;
        String title;

        ArrayList<TLObject> options;
        boolean[] checks;
        boolean[] filter;
        boolean collapsed;
        int totalCount;
        int filteredCount;
        int selectedCount;

        Action(int type, ArrayList<TLObject> options) {
            this.type = type;
            totalCount = options.size();
            selectedCount = 0;

            if (totalCount > 0) {
                this.options = options;
                checks = new boolean[totalCount];
                collapsed = true;

                updateTitle();
            }
        }

        int getCount() {
            if (filter != null) {
                return filteredCount;
            } else {
                return totalCount;
            }
        }

        boolean isPresent() {
            return getCount() > 0;
        }

        boolean isExpandable() {
            return getCount() > 1;
        }

        void setFilter(boolean[] filter) {
            if (totalCount == 0) {
                return;
            }
            this.filter = filter;

            updateCounters();
            updateTitle();
        }

        void updateCounters() {
            selectedCount = 0;
            filteredCount = 0;
            for (int i = 0; i < totalCount; i++) {
                if (filter == null) {
                    if (checks[i]) {
                        selectedCount++;
                    }
                } else if (filter[i]) {
                    filteredCount++;
                    if (checks[i]) {
                        selectedCount++;
                    }
                }
            }
        }

        TLObject first() {
            for (int i = 0; i < totalCount; i++) {
                if (filter == null || filter[i]) {
                    return options.get(i);
                }
            }
            return null;
        }

        void updateTitle() {
            if (totalCount == 0) {
                return;
            }

            TLObject userOrChat = first();
            String name;
            if (userOrChat instanceof TLRPC.User) {
                name = UserObject.getForcedFirstName((TLRPC.User) userOrChat);
            } else {
                name = ContactsController.formatName(userOrChat);
            }
            if (type == ACTION_REPORT) {
                title = getString(R.string.DeleteReportSpam);
            } else if (type == ACTION_DELETE_ALL) {
                title = isExpandable() ? getString(R.string.DeleteAllFromUsers) : formatString(R.string.DeleteAllFrom, name);
            } else if (type == ACTION_BAN) {
                if (restrict) {
                    title = isExpandable() ? getString(R.string.DeleteRestrictUsers) : formatString(R.string.DeleteRestrict, name);
                } else {
                    title = isExpandable() ? getString(R.string.DeleteBanUsers) : formatString(R.string.DeleteBan, name);
                }
            }
        }

        void collapseOrExpand() {
            collapsed = !collapsed;
            adapter.update(true);
        }

        void toggleCheck(int index) {
            if (filter != null && !filter[index]) {
                return;
            }

            this.checks[index] = !this.checks[index];
            if (this.checks[index]) {
                selectedCount++;
            } else {
                selectedCount--;
            }
            adapter.update(true);
        }

        boolean areAllSelected() {
            for (int i = 0; i < totalCount; i++) {
                if (!(checks[i] && (filter == null || filter[i]))) {
                    return false;
                }
            }
            return true;
        }

        boolean isOneSelected() {
            for (int i = 0; i < totalCount; i++) {
                if (checks[i] && (filter == null || filter[i])) {
                    return true;
                }
            }
            return false;
        }

        void toggleAllChecks() {
            setAllChecks(!isOneSelected());
        }

        void setAllChecks(boolean value) {
            setAllChecks(value, true);
        }
        void setAllChecks(boolean value, boolean notify) {
            Arrays.fill(checks, value);
            updateCounters();
            if (notify) {
                adapter.update(true);
            }
        }

        void forEachSelected(Utilities.IndexedConsumer<TLObject> action) {
            for (int i = 0; i < totalCount; i++) {
                if (checks[i] && (filter == null || filter[i])) {
                    action.accept(options.get(i), i);
                }
            }
        }

        void forEach(Utilities.IndexedConsumer<TLObject> action) {
            for (int i = 0; i < totalCount; i++) {
                if (filter == null || filter[i]) {
                    action.accept(options.get(i), i);
                }
            }
        }
    }

    public DeleteMessagesBottomSheet(BaseFragment fragment, TLRPC.Chat inChat, ArrayList<MessageObject> messages, ArrayList<TLObject> actionParticipants, TLRPC.ChannelParticipant[] channelParticipants, long mergeDialogId, int topicId, int mode, Runnable onDelete) {
        super(fragment.getContext(), fragment, false, false, false, true, ActionBarType.SLIDING, fragment.getResourceProvider());
        setShowHandle(true);
        fixNavigationBar();
        this.takeTranslationIntoAccount = true;
        recyclerListView.setPadding(backgroundPaddingLeft, headerTotalHeight, backgroundPaddingLeft, dp(68));
        recyclerListView.setOnItemClickListener((view, position, x, y) -> {
            UItem item = adapter.getItem(position - 1);
            if (item == null) return;
            onClick(item, view, position, x, y);
        });
        this.takeTranslationIntoAccount = true;
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                super.onMoveAnimationUpdate(holder);
                containerView.invalidate();
            }
        };
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        recyclerListView.setItemAnimator(itemAnimator);

        buttonContainer = new SelectorBtnCell(getContext(), resourcesProvider, null);
        buttonContainer.setClickable(true);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        buttonContainer.setPadding(dp(10), dp(10), dp(10), dp(10));
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        actionButton = new TextView(getContext());
        actionButton.setLines(1);
        actionButton.setSingleLine(true);
        actionButton.setGravity(Gravity.CENTER_HORIZONTAL);
        actionButton.setEllipsize(TextUtils.TruncateAt.END);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        actionButton.setTypeface(AndroidUtilities.bold());
        actionButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        actionButton.setText(getString(R.string.DeleteProceedBtn));
        actionButton.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton), 6));
        actionButton.setOnClickListener(e -> proceed());
        buttonContainer.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        containerView.addView(buttonContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));

        this.inChat = inChat;
        this.isForum = ChatObject.isForum(inChat);
        this.messages = messages;
        this.mergeDialogId = mergeDialogId;
        this.topicId = topicId;
        this.mode = mode;
        this.onDelete = onDelete;

        this.defaultBannedRights = inChat.default_banned_rights;
        this.bannedRights = new TLRPC.TL_chatBannedRights();
        if (defaultBannedRights.view_messages) {
            bannedRights.view_messages = true;
        }
        if (defaultBannedRights.send_messages) {
            bannedRights.send_messages = true;
        }
        if (defaultBannedRights.send_media) {
            bannedRights.send_media = true;
        }
        if (defaultBannedRights.send_stickers) {
            bannedRights.send_stickers = true;
        }
        if (defaultBannedRights.send_gifs) {
            bannedRights.send_gifs = true;
        }
        if (defaultBannedRights.send_games) {
            bannedRights.send_games = true;
        }
        if (defaultBannedRights.send_inline) {
            bannedRights.send_inline = true;
        }
        if (defaultBannedRights.embed_links) {
            bannedRights.embed_links = true;
        }
        if (defaultBannedRights.send_polls) {
            bannedRights.send_polls = true;
        }
        if (defaultBannedRights.invite_users) {
            bannedRights.invite_users = true;
        }
        if (defaultBannedRights.change_info) {
            bannedRights.change_info = true;
        }
        if (defaultBannedRights.pin_messages) {
            bannedRights.pin_messages = true;
        }
        if (defaultBannedRights.manage_topics) {
            bannedRights.manage_topics = true;
        }
        if (defaultBannedRights.send_photos) {
            bannedRights.send_photos = true;
        }
        if (defaultBannedRights.send_videos) {
            bannedRights.send_videos = true;
        }
        if (defaultBannedRights.send_audios) {
            bannedRights.send_audios = true;
        }
        if (defaultBannedRights.send_docs) {
            bannedRights.send_docs = true;
        }
        if (defaultBannedRights.send_voices) {
            bannedRights.send_voices = true;
        }
        if (defaultBannedRights.send_roundvideos) {
            bannedRights.send_roundvideos = true;
        }
        if (defaultBannedRights.send_plain) {
            bannedRights.send_plain = true;
        }

        final SharedPreferences prefs = MessagesController.getInstance(currentAccount).getMainSettings();

        report = new Action(ACTION_REPORT, actionParticipants);
//        if (prefs.getBoolean("delete_report", false)) {
//            report.setAllChecks(true, false);
//        }
        deleteAll = new Action(ACTION_DELETE_ALL, actionParticipants);
//        if (prefs.getBoolean("delete_deleteAll", false)) {
//            deleteAll.setAllChecks(true, false);
//            onDeleteAllChanged();
//        }

        monoforum = ChatObject.isMonoForum(inChat);
        if (ChatObject.canBlockUsers(inChat)) {
            banFilter = new boolean[actionParticipants.size()];
            for (int i = 0; i < actionParticipants.size(); i++) {
                TLRPC.ChannelParticipant channelParticipant = i < channelParticipants.length ? channelParticipants[i] : null;
                if (!inChat.creator && (channelParticipant instanceof TLRPC.TL_channelParticipantAdmin || channelParticipant instanceof TLRPC.TL_channelParticipantCreator)) {
                    continue;
                }
                if (channelParticipant instanceof TLRPC.TL_channelParticipantBanned && channelParticipant.banned_rights != null && isBanned(channelParticipant.banned_rights)) {
                    continue;
                }

                banFilter[i] = true;
            }

            restrictFilter = new boolean[actionParticipants.size()];
            if (hasAnyDefaultRights()) {
                for (int i = 0; i < actionParticipants.size(); i++) {
                    TLRPC.ChannelParticipant channelParticipant = i < channelParticipants.length ? channelParticipants[i] : null;
                    if (actionParticipants.get(i) instanceof TLRPC.Chat) {
                        continue;
                    }
                    if (channelParticipant instanceof TLRPC.TL_channelParticipantBanned && channelParticipant.banned_rights != null && !canBeRestricted(channelParticipant.banned_rights)) {
                        continue;
                    }

                    if (banFilter[i]) {
                        restrictFilter[i] = true;
                        canRestrict = true;
                    }
                }
            }

            participantsBannedRights = Arrays.stream(channelParticipants)
                    .map(channelParticipant -> channelParticipant == null ? null : channelParticipant.banned_rights)
                    .collect(Collectors.toCollection(ArrayList::new));

            banOrRestrict = new Action(ACTION_BAN, actionParticipants);
            banOrRestrict.setFilter(banFilter);
        } else {
            banOrRestrict = new Action(ACTION_BAN, new ArrayList<>(0));
        }

//        if (banOrRestrict != null && !restrict && prefs.getBoolean("delete_ban", false)) {
//            banOrRestrict.setAllChecks(true, false);
//        }

        adapter.update(false);
        actionBar.setTitle(getTitle());
    }

    private static boolean isBanned(TLRPC.TL_chatBannedRights bannedRights) {
        return bannedRights.view_messages;
    }

    private boolean hasAnyDefaultRights() {
        return !defaultBannedRights.send_messages ||
                !defaultBannedRights.send_media ||
                !defaultBannedRights.send_stickers ||
                !defaultBannedRights.send_gifs ||
                !defaultBannedRights.send_games ||
                !defaultBannedRights.send_inline ||
                !defaultBannedRights.embed_links ||
                !defaultBannedRights.send_polls ||
                !defaultBannedRights.change_info ||
                !defaultBannedRights.invite_users ||
                !defaultBannedRights.pin_messages ||
                !defaultBannedRights.manage_topics && isForum ||
                !defaultBannedRights.send_photos ||
                !defaultBannedRights.send_videos ||
                !defaultBannedRights.send_roundvideos ||
                !defaultBannedRights.send_audios ||
                !defaultBannedRights.send_voices ||
                !defaultBannedRights.send_docs ||
                !defaultBannedRights.send_plain;
    }

    public static TLRPC.TL_chatBannedRights bannedRightsOr(TLRPC.TL_chatBannedRights left, TLRPC.TL_chatBannedRights right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }

        TLRPC.TL_chatBannedRights bannedRights = new TLRPC.TL_chatBannedRights();
        bannedRights.view_messages = left.view_messages || right.view_messages;
        bannedRights.send_messages = left.send_messages || right.send_messages;
        bannedRights.send_media = left.send_media || right.send_media;
        bannedRights.send_stickers = left.send_stickers || right.send_stickers;
        bannedRights.send_gifs = left.send_gifs || right.send_gifs;
        bannedRights.send_games = left.send_games || right.send_games;
        bannedRights.send_inline = left.send_inline || right.send_inline;
        bannedRights.embed_links = left.embed_links || right.embed_links;
        bannedRights.send_polls = left.send_polls || right.send_polls;
        bannedRights.change_info = left.change_info || right.change_info;
        bannedRights.invite_users = left.invite_users || right.invite_users;
        bannedRights.pin_messages = left.pin_messages || right.pin_messages;
        bannedRights.manage_topics = left.manage_topics || right.manage_topics;
        bannedRights.send_photos = left.send_photos || right.send_photos;
        bannedRights.send_videos = left.send_videos || right.send_videos;
        bannedRights.send_roundvideos = left.send_roundvideos || right.send_roundvideos;
        bannedRights.send_audios = left.send_audios || right.send_audios;
        bannedRights.send_voices = left.send_voices || right.send_voices;
        bannedRights.send_docs = left.send_docs || right.send_docs;
        bannedRights.send_plain = left.send_plain || right.send_plain;
        return bannedRights;
    }

    private boolean canBeRestricted(TLRPC.TL_chatBannedRights bannedRights) {
        return !bannedRights.send_stickers && !defaultBannedRights.send_stickers ||
                !bannedRights.send_gifs && !defaultBannedRights.send_gifs ||
                !bannedRights.send_games && !defaultBannedRights.send_games ||
                !bannedRights.send_inline && !defaultBannedRights.send_inline ||
                !bannedRights.embed_links && !bannedRights.send_plain && !defaultBannedRights.embed_links && !defaultBannedRights.send_plain ||
                !bannedRights.send_polls && !defaultBannedRights.send_polls ||
                !bannedRights.change_info && !defaultBannedRights.change_info ||
                !bannedRights.invite_users && !defaultBannedRights.invite_users ||
                !bannedRights.pin_messages && !defaultBannedRights.pin_messages ||
                !bannedRights.manage_topics && !defaultBannedRights.manage_topics && isForum ||
                !bannedRights.send_photos && !defaultBannedRights.send_photos ||
                !bannedRights.send_videos && !defaultBannedRights.send_videos ||
                !bannedRights.send_roundvideos && !defaultBannedRights.send_roundvideos ||
                !bannedRights.send_audios && !defaultBannedRights.send_audios ||
                !bannedRights.send_voices && !defaultBannedRights.send_voices ||
                !bannedRights.send_docs && !defaultBannedRights.send_docs ||
                !bannedRights.send_plain && !defaultBannedRights.send_plain;
    }

    @Override
    protected CharSequence getTitle() {
        final int[] messageCount = {messages != null ? messages.size() : 0};

        if (participantMessageCounts != null && participantMessageCountsLoaded) {
            deleteAll.forEachSelected((o, i) -> {
                messageCount[0] += participantMessageCounts[i];
            });
        }

        return LocaleController.formatPluralString("DeleteOptionsTitle", messageCount[0]);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new UniversalAdapter(listView, getContext(), currentAccount, getBaseFragment().getClassGuid(), true, this::fillItems, resourcesProvider);
    }

    @Override
    public void show() {
        super.show();
        Bulletin.hideVisible();
    }

    @Override
    protected boolean canHighlightChildAt(View child, float x, float y) {
        return !(child instanceof CollapseTextCell);
    }

    private int getSendMediaSelectedCount() {
        int i = 0;
        if (!bannedRights.send_photos && !defaultBannedRights.send_photos) {
            i++;
        }
        if (!bannedRights.send_videos && !defaultBannedRights.send_videos) {
            i++;
        }
        if (!bannedRights.send_stickers && !defaultBannedRights.send_stickers) {
            i++;
        }
        if (!bannedRights.send_audios && !defaultBannedRights.send_audios) {
            i++;
        }
        if (!bannedRights.send_docs && !defaultBannedRights.send_docs) {
            i++;
        }
        if (!bannedRights.send_voices && !defaultBannedRights.send_voices) {
            i++;
        }
        if (!bannedRights.send_roundvideos && !defaultBannedRights.send_roundvideos) {
            i++;
        }
        if (!bannedRights.embed_links && !defaultBannedRights.embed_links && !bannedRights.send_plain && !defaultBannedRights.send_plain) {
            i++;
        }
        if (!bannedRights.send_polls && !defaultBannedRights.send_polls) {
            i++;
        }
        return i;
    }

    private void updateParticipantMessageCounts() {
        if (participantMessageCountsLoading) {
            return;
        }
        participantMessageCountsLoading = true;

        participantMessageCounts = new int[deleteAll.totalCount];

        int[] loadCountdown = new int[]{deleteAll.totalCount};
        for (int a = 0; a < deleteAll.totalCount; a++) {
            final int i = a;

            final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = MessagesController.getInputPeer(inChat);
            req.q = "";
            final TLRPC.InputPeer inputPeer = MessagesController.getInputPeer(deleteAll.options.get(a));
            req.from_id = inputPeer;
            req.flags |= 1;
            req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            req.limit = 1;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_messages_channelMessages) {
                    int totalCount = ((TLRPC.TL_messages_channelMessages) response).count;
                    int alreadyAccountedFor = (int) messages.stream()
                            .filter(msg -> MessageObject.peersEqual(inputPeer, msg.messageOwner.from_id))
                            .count();
                    participantMessageCounts[i] = totalCount - alreadyAccountedFor;
                }

                loadCountdown[0]--;
                if (loadCountdown[0] == 0) {
                    participantMessageCountsLoading = false;
                    participantMessageCountsLoaded = true;
                    updateTitleAnimated();
                }
            }));
        }
    }

    private boolean allDefaultMediaBanned() {
        return defaultBannedRights.send_photos && defaultBannedRights.send_videos && defaultBannedRights.send_stickers
                && defaultBannedRights.send_audios && defaultBannedRights.send_docs && defaultBannedRights.send_voices &&
                defaultBannedRights.send_roundvideos && defaultBannedRights.embed_links && defaultBannedRights.send_polls;
    }

    private void fillAction(ArrayList<UItem> items, Action action) {
        if (!action.isPresent()) {
            return;
        }

        if (!action.isExpandable()) {
            items.add(UItem.asRoundCheckbox(action.type, action.title)
                    .setChecked(action.selectedCount > 0));
        } else {
            items.add(UItem.asUserGroupCheckbox(action.type, action.title, String.valueOf(action.selectedCount > 0 ? action.selectedCount : action.getCount()))
                    .setChecked(action.selectedCount > 0)
                    .setCollapsed(action.collapsed)
                    .setClickCallback((v) -> {
                        saveScrollPosition();
                        action.collapseOrExpand();
                        applyScrolledPosition(true);
                    }));
            if (!action.collapsed) {
                action.forEach((userOrChat, i) -> {
                    items.add(UItem.asUserCheckbox(action.type << 24 | i, userOrChat)
                            .setChecked(action.checks[i])
                            .setPad(1));
                });
            }
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (messages == null) {
            return;
        }

        items.add(UItem.asHeader(getString(R.string.DeleteAdditionalActions)));
        fillAction(items, report);
        fillAction(items, deleteAll);
        fillAction(items, banOrRestrict);

        if (!monoforum && banOrRestrict.isPresent()) {
            if (restrict) {
                items.add(UItem.asShadow(null));
                if (banOrRestrict.isExpandable()) {
                    items.add(UItem.asAnimatedHeader(0, formatPluralString("UserRestrictionsCanDoUsers", banOrRestrict.selectedCount)));
                } else {
                    items.add(UItem.asAnimatedHeader(0, getString(R.string.UserRestrictionsCanDo)));
                }

                items.add(UItem.asSwitch(RIGHT_SEND_MESSAGES, getString(R.string.UserRestrictionsSend))
                        .setChecked(!bannedRights.send_plain && !defaultBannedRights.send_plain)
                        .setLocked(defaultBannedRights.send_plain));

                int sendMediaCount = getSendMediaSelectedCount();
                items.add(UItem.asExpandableSwitch(RIGHT_SEND_MEDIA, getString(R.string.UserRestrictionsSendMedia), String.format(Locale.US, "%d/9", sendMediaCount))
                        .setChecked(sendMediaCount > 0)
                        .setLocked(allDefaultMediaBanned())
                        .setCollapsed(sendMediaCollapsed)
                        .setClickCallback((v) -> {
                            if (allDefaultMediaBanned()) {
                                new AlertDialog.Builder(getContext())
                                        .setTitle(LocaleController.getString(R.string.UserRestrictionsCantModify))
                                        .setMessage(LocaleController.getString(R.string.UserRestrictionsCantModifyDisabled))
                                        .setPositiveButton(LocaleController.getString(R.string.OK), null)
                                        .create()
                                        .show();
                                return;
                            }
                            boolean enabled = !(sendMediaCount > 0);
                            bannedRights.send_media = !enabled;
                            bannedRights.send_photos = !enabled;
                            bannedRights.send_videos = !enabled;
                            bannedRights.send_stickers = !enabled;
                            bannedRights.send_gifs = !enabled;
                            bannedRights.send_inline = !enabled;
                            bannedRights.send_games = !enabled;
                            bannedRights.send_audios = !enabled;
                            bannedRights.send_docs = !enabled;
                            bannedRights.send_voices = !enabled;
                            bannedRights.send_roundvideos = !enabled;
                            bannedRights.embed_links = !enabled;
                            bannedRights.send_polls = !enabled;
                            onRestrictionsChanged();

                            adapter.update(true);
                        }));
                if (!sendMediaCollapsed) {
                    items.add(UItem.asRoundCheckbox(RIGHT_SEND_PHOTOS, getString(R.string.SendMediaPermissionPhotos))
                            .setChecked(!bannedRights.send_photos && !defaultBannedRights.send_photos)
                            .setLocked(defaultBannedRights.send_photos)
                            .setPad(1));
                    items.add(UItem.asRoundCheckbox(RIGHT_SEND_VIDEOS, getString(R.string.SendMediaPermissionVideos))
                            .setChecked(!bannedRights.send_videos && !defaultBannedRights.send_videos)
                            .setLocked(defaultBannedRights.send_videos)
                            .setPad(1));
                    items.add(UItem.asRoundCheckbox(RIGHT_SEND_FILES, getString(R.string.SendMediaPermissionFiles))
                            .setChecked(!bannedRights.send_docs && !defaultBannedRights.send_docs)
                            .setLocked(defaultBannedRights.send_docs)
                            .setPad(1));
                    items.add(UItem.asRoundCheckbox(RIGHT_SEND_MUSIC, getString(R.string.SendMediaPermissionMusic))
                            .setChecked(!bannedRights.send_audios && !defaultBannedRights.send_audios)
                            .setLocked(defaultBannedRights.send_audios)
                            .setPad(1));
                    items.add(UItem.asRoundCheckbox(RIGHT_SEND_VOICE, getString(R.string.SendMediaPermissionVoice))
                            .setChecked(!bannedRights.send_voices && !defaultBannedRights.send_voices)
                            .setLocked(defaultBannedRights.send_voices)
                            .setPad(1));
                    items.add(UItem.asRoundCheckbox(RIGHT_SEND_ROUND, getString(R.string.SendMediaPermissionRound))
                            .setChecked(!bannedRights.send_roundvideos && !defaultBannedRights.send_roundvideos)
                            .setLocked(defaultBannedRights.send_roundvideos)
                            .setPad(1));
                    items.add(UItem.asRoundCheckbox(RIGHT_SEND_STICKERS, getString(R.string.SendMediaPermissionStickersGifs))
                            .setChecked(!bannedRights.send_stickers && !defaultBannedRights.send_stickers)
                            .setLocked(defaultBannedRights.send_stickers)
                            .setPad(1));
                    items.add(UItem.asRoundCheckbox(RIGHT_SEND_POLLS, getString(R.string.SendMediaPolls))
                            .setChecked(!bannedRights.send_polls && !defaultBannedRights.send_polls)
                            .setLocked(defaultBannedRights.send_polls)
                            .setPad(1));
                    items.add(UItem.asRoundCheckbox(RIGHT_SEND_LINKS, getString(R.string.UserRestrictionsEmbedLinks))
                            .setChecked(!bannedRights.embed_links && !defaultBannedRights.embed_links && !bannedRights.send_plain && !defaultBannedRights.send_plain)
                            .setLocked(defaultBannedRights.embed_links)
                            .setPad(1));
                }

                items.add(UItem.asSwitch(RIGHT_ADD_USERS, getString(R.string.UserRestrictionsInviteUsers))
                        .setChecked(!bannedRights.invite_users && !defaultBannedRights.invite_users)
                        .setLocked(defaultBannedRights.invite_users));
                items.add(UItem.asSwitch(RIGHT_PIN_MESSAGES, getString(R.string.UserRestrictionsPinMessages))
                        .setChecked(!bannedRights.pin_messages && !defaultBannedRights.pin_messages)
                        .setLocked(defaultBannedRights.pin_messages));
                items.add(UItem.asSwitch(RIGHT_CHANGE_CHAT_INFO, getString(R.string.UserRestrictionsChangeInfo))
                        .setChecked(!bannedRights.change_info && !defaultBannedRights.change_info)
                        .setLocked(defaultBannedRights.change_info));
                if (isForum) {
                    items.add(UItem.asSwitch(RIGHT_CREATE_TOPICS, getString(R.string.CreateTopicsPermission))
                            .setChecked(!bannedRights.manage_topics && !defaultBannedRights.manage_topics)
                            .setLocked(defaultBannedRights.manage_topics));
                }
            }

            if (canRestrict) {
                items.add(UItem.asShadowCollapseButton(1, getString(getRestrictToggleTextKey()))
                        .setCollapsed(!restrict)
                        .accent());
            }
        }
    }

    private int getRestrictToggleTextKey() {
        if (!banOrRestrict.isExpandable()) {
            if (restrict) {
                return R.string.DeleteToggleBanUser;
            } else {
                return R.string.DeleteToggleRestrictUser;
            }
        } else {
            if (restrict) {
                return R.string.DeleteToggleBanUsers;
            } else {
                return R.string.DeleteToggleRestrictUsers;
            }
        }
    }

    private boolean banChecked;
    private void onRestrictionsChanged() {
        if (restrict && banOrRestrict.isPresent()) {
            banChecked = banOrRestrict.selectedCount > 0;
        }
        if (restrict && banOrRestrict.isPresent() && banOrRestrict.selectedCount == 0) {
            banOrRestrict.toggleAllChecks();
        } else if (!restrict && banOrRestrict.isPresent() && banChecked != (banOrRestrict.selectedCount > 0)) {
            banOrRestrict.toggleAllChecks();
        }
        if (!restrict && banOrRestrict.isPresent()) {
            banChecked = banOrRestrict.selectedCount > 0;
        }
    }

    private void onDeleteAllChanged() {
        if (participantMessageCountsLoaded) {
            updateTitleAnimated();
        } else {
            updateParticipantMessageCounts();
        }
    }

    private float shiftDp = 10.0f;
    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.viewType == VIEW_TYPE_USER_CHECKBOX) {
            int action = item.id >>> 24;
            int index = item.id & 0xffffff;

            if (action == ACTION_REPORT) {
                report.toggleCheck(index);
            } else if (action == ACTION_DELETE_ALL) {
                deleteAll.toggleCheck(index);
                onDeleteAllChanged();
            } else if (action == ACTION_BAN) {
                banOrRestrict.toggleCheck(index);
            }
        } else if (item.viewType == VIEW_TYPE_USER_GROUP_CHECKBOX || item.viewType == VIEW_TYPE_ROUND_CHECKBOX) {
            if (item.id == ACTION_REPORT) {
                report.toggleAllChecks();
            } else if (item.id == ACTION_DELETE_ALL) {
                deleteAll.toggleAllChecks();
                onDeleteAllChanged();
            } else if (item.id == ACTION_BAN) {
                banOrRestrict.toggleAllChecks();
            } else if (item.viewType == VIEW_TYPE_ROUND_CHECKBOX) {
                if (item.locked) {
                    new AlertDialog.Builder(getContext())
                            .setTitle(LocaleController.getString(R.string.UserRestrictionsCantModify))
                            .setMessage(LocaleController.getString(R.string.UserRestrictionsCantModifyDisabled))
                            .setPositiveButton(LocaleController.getString(R.string.OK), null)
                            .create()
                            .show();
                    return;
                }

                if (item.id == RIGHT_SEND_PHOTOS) {
                    bannedRights.send_photos = !bannedRights.send_photos;
                    onRestrictionsChanged();
                } else if (item.id == RIGHT_SEND_VIDEOS) {
                    bannedRights.send_videos = !bannedRights.send_videos;
                    onRestrictionsChanged();
                } else if (item.id == RIGHT_SEND_MUSIC) {
                    bannedRights.send_audios = !bannedRights.send_audios;
                    onRestrictionsChanged();
                } else if (item.id == RIGHT_SEND_FILES) {
                    bannedRights.send_docs = !bannedRights.send_docs;
                    onRestrictionsChanged();
                } else if (item.id == RIGHT_SEND_ROUND) {
                    bannedRights.send_roundvideos = !bannedRights.send_roundvideos;
                    onRestrictionsChanged();
                } else if (item.id == RIGHT_SEND_VOICE) {
                    bannedRights.send_voices = !bannedRights.send_voices;
                    onRestrictionsChanged();
                } else if (item.id == RIGHT_SEND_STICKERS) {
                    bannedRights.send_stickers = bannedRights.send_games = bannedRights.send_gifs = bannedRights.send_inline = !bannedRights.send_stickers;
                    onRestrictionsChanged();
                } else if (item.id == RIGHT_SEND_LINKS) {
                    if (bannedRights.send_plain || defaultBannedRights.send_plain) {
                        for (int i = 0; i < adapter.getItemCount(); i++) {
                            UItem childItem = adapter.getItem(i);
                            if (childItem.viewType == VIEW_TYPE_SWITCH && childItem.id == RIGHT_SEND_MESSAGES) {
                                RecyclerView.ViewHolder holder = recyclerListView.findViewHolderForAdapterPosition(i + 1);
                                if (holder != null) {
                                    AndroidUtilities.shakeViewSpring(holder.itemView, shiftDp = -shiftDp);
                                }
                                break;
                            }
                        }
                        BotWebViewVibrationEffect.APP_ERROR.vibrate();
                        return;
                    }
                    bannedRights.embed_links = !bannedRights.embed_links;
                    onRestrictionsChanged();
                } else if (item.id == RIGHT_SEND_POLLS) {
                    bannedRights.send_polls = !bannedRights.send_polls;
                    onRestrictionsChanged();
                }
                adapter.update(true);
            }
        } else if (item.viewType == VIEW_TYPE_SWITCH) {
            if (item.locked) {
                new AlertDialog.Builder(getContext())
                        .setTitle(LocaleController.getString(R.string.UserRestrictionsCantModify))
                        .setMessage(LocaleController.getString(R.string.UserRestrictionsCantModifyDisabled))
                        .setPositiveButton(LocaleController.getString(R.string.OK), null)
                        .create()
                        .show();
                return;
            }

            if (item.id == RIGHT_ADD_USERS) {
                bannedRights.invite_users = !bannedRights.invite_users;
                onRestrictionsChanged();
            } else if (item.id == RIGHT_PIN_MESSAGES) {
                bannedRights.pin_messages = !bannedRights.pin_messages;
                onRestrictionsChanged();
            } else if (item.id == RIGHT_CHANGE_CHAT_INFO) {
                bannedRights.change_info = !bannedRights.change_info;
                onRestrictionsChanged();
            } else if (item.id == RIGHT_CREATE_TOPICS) {
                bannedRights.manage_topics = !bannedRights.manage_topics;
                onRestrictionsChanged();
            } else if (item.id == RIGHT_SEND_MESSAGES) {
                bannedRights.send_plain = !bannedRights.send_plain;
                onRestrictionsChanged();
            }

            adapter.update(true);
        } else if (item.viewType == VIEW_TYPE_EXPANDABLE_SWITCH) {
            sendMediaCollapsed = !sendMediaCollapsed;
            saveScrollPosition();
            adapter.update(true);
            applyScrolledPosition(true);
        } else if (item.viewType == VIEW_TYPE_SHADOW_COLLAPSE_BUTTON) {
            restrict = !restrict;
            banOrRestrict.setFilter(restrict ? restrictFilter : banFilter);
            adapter.update(true);
            onRestrictionsChanged();
        }
    }

    private void performDelete() {
        ArrayList<Integer> supergroupMessageIds = messages.stream()
                .filter(msg -> msg.messageOwner.peer_id != null && msg.messageOwner.peer_id.chat_id != -mergeDialogId || mergeDialogId == 0)
                .map(MessageObject::getId)
                .collect(Collectors.toCollection(ArrayList::new));

        ArrayList<Integer> groupMessageIds = messages.stream()
                .filter(msg -> msg.messageOwner.peer_id != null && msg.messageOwner.peer_id.chat_id == -mergeDialogId && mergeDialogId != 0)
                .map(MessageObject::getId)
                .collect(Collectors.toCollection(ArrayList::new));

        if (!supergroupMessageIds.isEmpty()) {
            MessagesController.getInstance(currentAccount).deleteMessages(supergroupMessageIds, null, null, -inChat.id, topicId, false, mode);
        }
        if (!groupMessageIds.isEmpty()) {
            MessagesController.getInstance(currentAccount).deleteMessages(groupMessageIds, null, null, mergeDialogId, topicId, true, mode);
        }

        banOrRestrict.forEachSelected((participant, i) -> {
            long chatId = inChat.id;
            if (ChatObject.isMonoForum(inChat) && ChatObject.canManageMonoForum(currentAccount, inChat) && inChat.linked_monoforum_id != 0) {
                chatId = inChat.linked_monoforum_id;
            }
            if (restrict) {
                TLRPC.TL_chatBannedRights rights = bannedRightsOr(bannedRights, participantsBannedRights.get(i));
                if (participant instanceof TLRPC.User) {
                    MessagesController.getInstance(currentAccount).setParticipantBannedRole(chatId, (TLRPC.User) participant, null, rights, false, getBaseFragment());
                } else if (participant instanceof TLRPC.Chat) {
                    MessagesController.getInstance(currentAccount).setParticipantBannedRole(chatId, null, (TLRPC.Chat) participant, rights, false, getBaseFragment());
                }
            } else {
                if (participant instanceof TLRPC.User) {
                    MessagesController.getInstance(currentAccount).deleteParticipantFromChat(chatId, (TLRPC.User) participant, null, false, false);
                } else if (participant instanceof TLRPC.Chat) {
                    MessagesController.getInstance(currentAccount).deleteParticipantFromChat(chatId, null, (TLRPC.Chat) participant, false, false);
                }
            }
        });

        report.forEachSelected((participant, i) -> {
            TLRPC.TL_channels_reportSpam req = new TLRPC.TL_channels_reportSpam();
            req.channel = MessagesController.getInputChannel(inChat);
            if (participant instanceof TLRPC.User) {
                req.participant = MessagesController.getInputPeer((TLRPC.User) participant);
            } else if (participant instanceof TLRPC.Chat) {
                req.participant = MessagesController.getInputPeer((TLRPC.Chat) participant);
            }
            req.id = messages.stream()
                    .filter(msg -> msg.messageOwner.peer_id != null && msg.messageOwner.peer_id.chat_id != -mergeDialogId)
                    .filter(msg -> {
                        if (participant instanceof TLRPC.User) {
                            return msg.messageOwner.from_id.user_id == ((TLRPC.User) participant).id;
                        } else if (participant instanceof TLRPC.Chat) {
                            return msg.messageOwner.from_id.user_id == ((TLRPC.Chat) participant).id;
                        }
                        return false;
                    })
                    .map(MessageObject::getId)
                    .collect(Collectors.toCollection(ArrayList::new));

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
        });

        deleteAll.forEachSelected((participant, i) -> {
            if (participant instanceof TLRPC.User) {
                MessagesController.getInstance(currentAccount).deleteUserChannelHistory(inChat, (TLRPC.User) participant, null, 0);
            } else if (participant instanceof TLRPC.Chat) {
                MessagesController.getInstance(currentAccount).deleteUserChannelHistory(inChat, null, (TLRPC.Chat) participant, 0);
            }
        });
    }

    private void savePreferences() {
        final SharedPreferences.Editor e = MessagesController.getInstance(currentAccount).getMainSettings().edit();
        e.putBoolean("delete_report", report.areAllSelected());
        e.putBoolean("delete_deleteAll", deleteAll.areAllSelected());
        e.putBoolean("delete_ban", !restrict && banOrRestrict.areAllSelected());
        e.apply();
    }

    @Override
    public void dismiss() {
        savePreferences();
        super.dismiss();
    }

    private void proceed() {
        dismiss();
        if (onDelete != null) {
            onDelete.run();
        }

        String subtitle = "";
        if (report.selectedCount > 0) {
            subtitle += formatPluralString("UsersReported", report.selectedCount);
        }
        if (banOrRestrict.selectedCount > 0) {
            if (!TextUtils.isEmpty(subtitle)) {
                subtitle += "\n";
            }
            if (restrict) {
                subtitle += formatPluralString("UsersRestricted", banOrRestrict.selectedCount);
            } else {
                subtitle += formatPluralString("UsersBanned", banOrRestrict.selectedCount);
            }
        }

        int icon = banOrRestrict.selectedCount > 0 ? R.raw.ic_admin : R.raw.contact_check;
        if (TextUtils.isEmpty(subtitle)) {
            BulletinFactory.of(getBaseFragment()).createSimpleBulletin(icon, getString(R.string.MessagesDeleted)).show();
        } else {
            BulletinFactory.of(getBaseFragment()).createSimpleBulletin(icon, getString(R.string.MessagesDeleted), subtitle).show();
        }

        performDelete();
    }
}
