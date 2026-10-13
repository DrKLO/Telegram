package org.telegram.ui.multiaccount;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.multiaccount.MultiAccountController;
import org.telegram.messenger.multiaccount.UnifiedDialogsMerger;
import org.telegram.messenger.multiaccount.UnifiedDialogsStore;
import org.telegram.messenger.multiaccount.UnifiedSearchController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.MainTabsActivityController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * One chat list for all logged-in accounts. Rows keep their owning account: opening, replying,
 * pinning, muting, archiving and deleting always go through that account's controllers, so the
 * same chat id in two accounts stays two independent conversations.
 */
public class UnifiedDialogsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate,
        UnifiedDialogsStore.Listener, UnifiedSearchController.Listener, MainTabsActivity.TabFragmentDelegate {

    private static final String PREF_FILTER = "multiAccountFilter";
    private static final String PREF_FILTER_ACCOUNT = "multiAccountFilterAccount";

    private static final int VIEW_DIALOG = 0;
    private static final int VIEW_ARCHIVE = 1;
    private static final int VIEW_LOADING = 2;
    private static final int VIEW_HEADER = 3;
    private static final int VIEW_PEER = 4;

    private static final int MENU_SEARCH = 1;
    private static final int MENU_OTHER = 2;

    private static final class Item {
        final int viewType;
        final int account;
        final long dialogId;
        final TLRPC.Dialog dialog;
        final UnifiedSearchController.MessageResult message;
        final CharSequence text;

        Item(int viewType, int account, long dialogId, TLRPC.Dialog dialog, UnifiedSearchController.MessageResult message, CharSequence text) {
            this.viewType = viewType;
            this.account = account;
            this.dialogId = dialogId;
            this.dialog = dialog;
            this.message = message;
            this.text = text;
        }
    }

    private final boolean hasMainTabs;
    private final int folderId;
    private final String initialSearch;

    private UnifiedDialogsStore store;
    private UnifiedSearchController searchController;
    private MainTabsActivityController mainTabsActivityController;

    private FrameLayout contentView;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private Adapter adapter;
    private TextView emptyView;
    private HorizontalScrollView chipsScroll;
    private LinearLayout chipsLayout;
    private FragmentFloatingButton floatingButton;
    private ActionBarMenuItem searchItem;

    private final ArrayList<Item> items = new ArrayList<>();
    private boolean searching;
    private String searchQuery;
    private int navigationBarHeight;

    public UnifiedDialogsActivity(Bundle args) {
        super(args);
        hasMainTabs = args != null && args.getBoolean("hasMainTabs", false);
        folderId = args != null ? args.getInt("folderId", 0) : 0;
        initialSearch = args != null ? args.getString("initialSearch") : null;
    }

    public void setMainTabsActivityController(MainTabsActivityController controller) {
        mainTabsActivityController = controller;
    }

    @Override
    public boolean onFragmentCreate() {
        store = new UnifiedDialogsStore(folderId, this);
        searchController = new UnifiedSearchController(this);
        restoreFilter();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.didUpdateConnectionState);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.mainUserInfoChanged);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.multiAccountStateChanged);
        store.attach();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.didUpdateConnectionState);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.mainUserInfoChanged);
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.multiAccountStateChanged);
        if (store != null) {
            store.detach();
        }
        if (searchController != null) {
            searchController.cancel();
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return hasMainTabs;
    }

    @Override
    public View createView(Context context) {
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(folderId == 1 ? getString(R.string.ArchivedChats) : getString(R.string.FilterAllChats));
        if (!hasMainTabs) {
            actionBar.setBackButtonDrawable(new BackDrawable(false));
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
        searchItem = menu.addItem(MENU_SEARCH, R.drawable.outline_header_search).setIsSearchField(true);
        searchItem.setSearchFieldHint(getString(R.string.MultiAccountSearchHint));
        searchItem.setContentDescription(getString(R.string.Search));
        searchItem.setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                setSearching(true);
            }

            @Override
            public void onSearchCollapse() {
                setSearching(false);
            }

            @Override
            public void onTextChanged(EditText editText) {
                searchQuery = editText.getText().toString();
                searchController.search(searchQuery, store.getAccountFilter());
            }
        });
        if (folderId == 0) {
            ActionBarMenuItem other = menu.addItem(MENU_OTHER, R.drawable.ic_ab_other);
            other.setContentDescription(getString(R.string.AccDescrMoreOptions));
            other.setOnClickListener(v -> showOtherOptions(v));
        }

        contentView = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int top = actionBar.getMeasuredHeight();
                ((MarginLayoutParams) chipsScroll.getLayoutParams()).topMargin = top;
                updateListPadding(top);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        fragmentView = contentView;

        listView = new RecyclerListView(context);
        listView.setClipToPadding(false);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter = new Adapter(context));
        listView.setItemAnimator(null);
        listView.setOnItemClickListener((view, position) -> onItemClick(position));
        listView.setOnItemLongClickListener((view, position) -> onItemLongClick(view, position));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (floatingButton != null && dy != 0) {
                    floatingButton.setButtonVisible(dy < 0 && !searching, true);
                }
                checkLoadMore();
            }
        });
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(32), 0, dp(32), 0);
        emptyView.setVisibility(View.GONE);
        contentView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        chipsLayout = new LinearLayout(context);
        chipsLayout.setOrientation(LinearLayout.HORIZONTAL);
        chipsLayout.setPadding(dp(12), dp(6), dp(12), dp(6));
        chipsScroll.addView(chipsLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentView.addView(chipsScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP));

        if (folderId == 0) {
            floatingButton = new FragmentFloatingButton(context, getResourceProvider());
            floatingButton.setImageResource(R.drawable.filled_fab_compose_32);
            floatingButton.setContentDescription(getString(R.string.NewMessageTitle));
            floatingButton.setOnClickListener(v -> startNewMessage(v));
            contentView.addView(floatingButton, FragmentFloatingButton.createDefaultLayoutParams());
        }

        contentView.addView(actionBar);

        if (hasMainTabs) {
            ViewCompat.setOnApplyWindowInsetsListener(contentView, this::onInsetsInternal);
        }

        rebuildChips();
        updateConnectionStatus();
        rebuildItems();

        if (!TextUtils.isEmpty(initialSearch)) {
            AndroidUtilities.runOnUIThread(() -> {
                if (searchItem != null) {
                    searchItem.openSearch(false);
                    searchItem.setSearchFieldText(initialSearch, false);
                }
            });
        }
        return contentView;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        navigationBarHeight = bottom;
        if (actionBar != null) {
            updateListPadding(actionBar.getMeasuredHeight());
        }
    }

    private int bottomOffset() {
        return navigationBarHeight + (hasMainTabs ? dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0);
    }

    private void updateListPadding(int actionBarHeight) {
        if (listView == null) {
            return;
        }
        int top = actionBarHeight + (chipsScroll.getVisibility() == View.VISIBLE ? dp(44) : 0);
        int bottom = bottomOffset();
        if (listView.getPaddingTop() != top || listView.getPaddingBottom() != bottom) {
            listView.setPadding(0, top, 0, bottom);
        }
        emptyView.setPadding(dp(32), top, dp(32), bottom);
        if (floatingButton != null) {
            floatingButton.setTranslationY(-bottomOffset());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        MultiAccountController.getInstance().refreshKeepAlive();
        if (store != null) {
            store.ensureLoaded();
            store.scheduleRebuild();
        }
        updateConnectionStatus();
    }

    @Override
    public void onParentScrollToTop() {
        if (listView != null) {
            listView.smoothScrollToPosition(0);
        }
    }

    // region filters

    private void restoreFilter() {
        SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        int filter = prefs.getInt(PREF_FILTER, UnifiedDialogsMerger.FILTER_ALL);
        int account = prefs.getInt(PREF_FILTER_ACCOUNT, -1);
        HashSet<Integer> accounts = new HashSet<>();
        if (UserConfig.isValidAccount(account)) {
            accounts.add(account);
        }
        store.setFilter(folderId == 0 ? filter : UnifiedDialogsMerger.FILTER_ALL, accounts);
    }

    private void applyFilter(int filter, int account) {
        HashSet<Integer> accounts = new HashSet<>();
        if (account >= 0) {
            accounts.add(account);
        }
        store.setFilter(filter, accounts);
        if (folderId == 0) {
            MessagesController.getGlobalMainSettings().edit()
                    .putInt(PREF_FILTER, filter)
                    .putInt(PREF_FILTER_ACCOUNT, account)
                    .apply();
        }
        rebuildChips();
        if (searching && searchController != null) {
            searchController.search(searchQuery, store.getAccountFilter());
        }
        if (listView != null) {
            listView.scrollToPosition(0);
        }
    }

    private int selectedFilterAccount() {
        Set<Integer> accounts = store.getAccountFilter();
        return accounts.size() == 1 ? accounts.iterator().next() : -1;
    }

    /** Long press on the Chats tab: quick account / unread filter. */
    public void showAccountFilterMenu(View anchor) {
        if (getParentActivity() == null) {
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(this, anchor);
        options.addText(getString(R.string.MultiAccountFilter), 13);
        int selectedAccount = selectedFilterAccount();
        options.add(R.drawable.msg_discussion, getString(R.string.MultiAccountAllAccounts), () -> applyFilter(UnifiedDialogsMerger.FILTER_ALL, -1));
        options.add(R.drawable.msg_markunread, getString(R.string.MultiAccountUnread), () -> applyFilter(UnifiedDialogsMerger.FILTER_UNREAD, selectedAccount));
        options.addGap();
        for (int account : MultiAccountController.getActiveAccounts()) {
            View row = MultiAccountUi.createAccountRow(getParentActivity(), account, account == selectedAccount, getResourceProvider());
            row.setOnClickListener(v -> {
                options.dismiss();
                applyFilter(store.getFilter(), account);
            });
            options.addView(row, LayoutHelper.createLinear(240, 48));
        }
        options.setGravity(Gravity.LEFT);
        options.show();
    }

    private void rebuildChips() {
        if (chipsLayout == null) {
            return;
        }
        Context context = chipsLayout.getContext();
        chipsLayout.removeAllViews();
        ArrayList<Integer> accounts = MultiAccountController.getActiveAccounts();
        int selectedAccount = selectedFilterAccount();
        int filter = store.getFilter();

        addChip(context, null, getString(R.string.MultiAccountAllAccounts), selectedAccount == -1 && filter == UnifiedDialogsMerger.FILTER_ALL,
                () -> applyFilter(UnifiedDialogsMerger.FILTER_ALL, -1));
        if (folderId == 0) {
            addChip(context, null, getString(R.string.MultiAccountUnread), filter == UnifiedDialogsMerger.FILTER_UNREAD,
                    () -> applyFilter(filter == UnifiedDialogsMerger.FILTER_UNREAD ? UnifiedDialogsMerger.FILTER_ALL : UnifiedDialogsMerger.FILTER_UNREAD, selectedAccount));
        }
        if (accounts.size() > 1) {
            for (int account : accounts) {
                addChip(context, account, MultiAccountUi.getAccountShortName(account), account == selectedAccount,
                        () -> applyFilter(filter, account == selectedAccount ? -1 : account));
            }
        }
    }

    private void addChip(Context context, Integer account, CharSequence text, boolean selected, Runnable onClick) {
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(16));
        int accent = getThemedColor(Theme.key_featuredStickers_addButton);
        background.setColor(selected ? accent : Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), 0.06f));
        chip.setBackground(background);
        chip.setPadding(account != null ? dp(4) : dp(12), 0, dp(12), 0);
        if (account != null) {
            BackupImageView avatar = MultiAccountUi.createAccountAvatar(context, account, 22);
            chip.addView(avatar, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
        }
        TextView label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        label.setTypeface(AndroidUtilities.bold());
        label.setTextColor(selected ? getThemedColor(Theme.key_featuredStickers_buttonText) : getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        label.setText(text);
        label.setSingleLine(true);
        chip.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        chip.setOnClickListener(v -> onClick.run());
        chip.setSelected(selected);
        chip.setContentDescription(text);
        chipsLayout.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
    }

    // endregion

    // region content

    @Override
    public void onUnifiedDialogsChanged() {
        if (!searching) {
            rebuildItems();
        }
    }

    @Override
    public void onUnifiedSearchResults(String query) {
        if (searching) {
            rebuildItems();
        }
    }

    private void setSearching(boolean value) {
        if (searching == value) {
            return;
        }
        searching = value;
        if (!value) {
            searchQuery = null;
            searchController.cancel();
        }
        if (mainTabsActivityController != null) {
            mainTabsActivityController.setTabsVisible(!value);
        }
        if (floatingButton != null) {
            floatingButton.setButtonVisible(!value, true);
        }
        rebuildItems();
    }

    private void rebuildItems() {
        if (adapter == null) {
            return;
        }
        items.clear();
        if (searching && !TextUtils.isEmpty(searchQuery)) {
            ArrayList<UnifiedSearchController.ChatResult> chats = searchController.getChats();
            if (!chats.isEmpty()) {
                items.add(new Item(VIEW_HEADER, -1, 0, null, null, getString(R.string.SearchAllChatsShort)));
                for (UnifiedSearchController.ChatResult chat : chats) {
                    items.add(new Item(chat.dialog != null ? VIEW_DIALOG : VIEW_PEER, chat.account, chat.dialogId, chat.dialog, null, null));
                }
            }
            ArrayList<UnifiedSearchController.MessageResult> messages = searchController.getMessages();
            if (!messages.isEmpty()) {
                items.add(new Item(VIEW_HEADER, -1, 0, null, null, getString(R.string.SearchMessages)));
                for (UnifiedSearchController.MessageResult message : messages) {
                    items.add(new Item(VIEW_DIALOG, message.account, message.message.getDialogId(), null, message, null));
                }
            }
            if (searchController.isSearching()) {
                items.add(new Item(VIEW_LOADING, -1, 0, null, null, null));
            }
            emptyView.setText(getString(R.string.NoResult));
        } else {
            if (folderId == 0 && store.getFilter() == UnifiedDialogsMerger.FILTER_ALL && UnifiedDialogsStore.hasArchivedChats()) {
                items.add(new Item(VIEW_ARCHIVE, -1, 0, null, null, null));
            }
            for (UnifiedDialogsStore.Row row : store.getRows()) {
                items.add(new Item(VIEW_DIALOG, row.account, row.dialog.id, row.dialog, null, null));
            }
            if (store.hasMore() || store.isLoading()) {
                items.add(new Item(VIEW_LOADING, -1, 0, null, null, null));
            }
            if (store.getFilter() == UnifiedDialogsMerger.FILTER_UNREAD) {
                emptyView.setText(getString(R.string.MultiAccountNoUnread));
            } else if (selectedFilterAccount() >= 0) {
                emptyView.setText(getString(R.string.MultiAccountNoChatsInAccount));
            } else {
                emptyView.setText(getString(R.string.MultiAccountNoChats));
            }
        }
        boolean empty = items.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
        if (!searching) {
            AndroidUtilities.runOnUIThread(this::checkLoadMore);
        }
    }

    private void checkLoadMore() {
        if (searching || layoutManager == null || store == null) {
            return;
        }
        int last = layoutManager.findLastVisibleItemPosition();
        if (last >= items.size() - 10 && store.hasMore()) {
            store.loadMoreIfNeeded();
        }
    }

    private void updateConnectionStatus() {
        if (actionBar == null) {
            return;
        }
        ArrayList<String> connecting = new ArrayList<>();
        ArrayList<String> updating = new ArrayList<>();
        boolean waitingForNetwork = false;
        ArrayList<Integer> accounts = MultiAccountController.getActiveAccounts();
        for (int account : accounts) {
            int state = ConnectionsManager.getInstance(account).getConnectionState();
            if (state == ConnectionsManager.ConnectionStateWaitingForNetwork) {
                waitingForNetwork = true;
            } else if (state == ConnectionsManager.ConnectionStateConnecting || state == ConnectionsManager.ConnectionStateConnectingToProxy) {
                connecting.add(MultiAccountUi.getAccountShortName(account));
            } else if (state == ConnectionsManager.ConnectionStateUpdating) {
                updating.add(MultiAccountUi.getAccountShortName(account));
            }
        }
        CharSequence subtitle;
        if (waitingForNetwork) {
            subtitle = getString(R.string.WaitingForNetwork);
        } else if (!connecting.isEmpty()) {
            subtitle = LocaleController.formatString(R.string.MultiAccountConnecting, TextUtils.join(", ", connecting));
        } else if (!updating.isEmpty()) {
            subtitle = LocaleController.formatString(R.string.MultiAccountUpdating, TextUtils.join(", ", updating));
        } else if (accounts.size() > 1) {
            subtitle = LocaleController.formatString(R.string.MultiAccountAccountsCount, accounts.size());
        } else {
            subtitle = null;
        }
        actionBar.setSubtitle(subtitle);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didUpdateConnectionState) {
            updateConnectionStatus();
        } else if (id == NotificationCenter.mainUserInfoChanged || id == NotificationCenter.multiAccountStateChanged) {
            rebuildChips();
            updateConnectionStatus();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    // endregion

    // region actions

    private void onItemClick(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        Item item = items.get(position);
        if (item.viewType == VIEW_ARCHIVE) {
            Bundle args = new Bundle();
            args.putInt("folderId", 1);
            presentFragment(new UnifiedDialogsActivity(args));
        } else if (item.viewType == VIEW_DIALOG || item.viewType == VIEW_PEER) {
            int messageId = item.message != null ? item.message.message.getId() : 0;
            MultiAccountUi.openDialog(this, item.account, item.dialogId, messageId);
        }
    }

    private boolean onItemLongClick(View view, int position) {
        if (position < 0 || position >= items.size() || getParentActivity() == null) {
            return false;
        }
        Item item = items.get(position);
        if (item.viewType != VIEW_DIALOG || item.message != null) {
            return false;
        }
        final int account = item.account;
        final long did = item.dialogId;
        final MessagesController controller = MessagesController.getInstance(account);
        final TLRPC.Dialog dialog = controller.dialogs_dict.get(did);
        if (dialog == null) {
            return false;
        }
        ItemOptions options = ItemOptions.makeOptions(this, view);
        if (MultiAccountUi.showAccountBadges()) {
            options.addText(LocaleController.formatString(R.string.MultiAccountAccountLabel, MultiAccountUi.getAccountName(account)), 13);
        }
        boolean isSelf = did == UserConfig.getInstance(account).getClientUserId();
        if (dialog.folder_id == 0 || dialog.folder_id == 1) {
            options.add(dialog.pinned ? R.drawable.msg_unpin : R.drawable.msg_pin, getString(dialog.pinned ? R.string.UnpinFromTop : R.string.PinToTop), () -> togglePin(account, dialog));
        }
        boolean unread = controller.getDialogUnreadCount(dialog) > 0 || dialog.unread_mark || dialog.unread_mentions_count > 0;
        if (unread) {
            options.add(R.drawable.msg_markread, getString(R.string.MarkAsRead), () -> markAsRead(account, dialog));
        } else if (!isSelf) {
            options.add(R.drawable.msg_markunread, getString(R.string.MarkAsUnread), () -> controller.markDialogAsUnread(did, null, 0));
        }
        if (!isSelf) {
            boolean muted = controller.isDialogMuted(did, 0);
            options.add(muted ? R.drawable.msg_unmute : R.drawable.msg_mute, getString(muted ? R.string.Unmute : R.string.Mute), () ->
                    NotificationsController.getInstance(account).setDialogNotificationsSettings(did, 0, muted ? NotificationsController.SETTING_MUTE_UNMUTE : NotificationsController.SETTING_MUTE_FOREVER));
            if (!controller.isPromoDialog(did, false)) {
                boolean archived = dialog.folder_id == 1;
                options.add(archived ? R.drawable.msg_unarchive : R.drawable.msg_archive, getString(archived ? R.string.Unarchive : R.string.Archive), () ->
                        controller.addDialogToFolder(did, archived ? 0 : 1, -1, 0));
            }
        }
        options.add(R.drawable.msg_delete, getString(R.string.Delete), true, () -> confirmDelete(account, did));
        options.show();
        return true;
    }

    private void markAsRead(int account, TLRPC.Dialog dialog) {
        MessagesController controller = MessagesController.getInstance(account);
        long did = dialog.id;
        if (controller.isForum(did) || controller.isMonoForumWithManageRights(did)) {
            controller.markAllTopicsAsRead(did);
        }
        controller.markMentionsAsRead(did, 0);
        controller.markDialogAsRead(did, dialog.top_message, dialog.top_message, dialog.last_message_date, false, 0, 0, true, 0);
    }

    private void togglePin(int account, TLRPC.Dialog dialog) {
        MessagesController controller = MessagesController.getInstance(account);
        boolean pin = !dialog.pinned;
        if (pin) {
            boolean premium = UserConfig.getInstance(account).isPremium();
            int max = dialog.folder_id != 0
                    ? (premium ? controller.maxFolderPinnedDialogsCountPremium : controller.maxFolderPinnedDialogsCountDefault)
                    : (premium ? controller.maxPinnedDialogsCountPremium : controller.maxPinnedDialogsCountDefault);
            boolean secret = DialogObject.isEncryptedDialog(dialog.id);
            int pinned = 0;
            for (TLRPC.Dialog d : controller.getDialogs(dialog.folder_id)) {
                if (d.pinned && !(d instanceof TLRPC.TL_dialogFolder) && DialogObject.isEncryptedDialog(d.id) == secret) {
                    pinned++;
                }
            }
            if (pinned >= max) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip,
                        LocaleController.formatString(R.string.MultiAccountPinLimit, MultiAccountUi.getAccountShortName(account))).show();
                return;
            }
        }
        controller.pinDialog(dialog.id, pin, null, -1);
    }

    private void confirmDelete(int account, long did) {
        if (getParentActivity() == null) {
            return;
        }
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.Chat chat = DialogObject.isChatDialog(did) ? controller.getChat(-did) : null;
        TLRPC.User user = null;
        if (DialogObject.isEncryptedDialog(did)) {
            TLRPC.EncryptedChat encryptedChat = controller.getEncryptedChat(DialogObject.getEncryptedChatId(did));
            if (encryptedChat != null) {
                user = controller.getUser(encryptedChat.user_id);
            }
        } else if (DialogObject.isUserDialog(did)) {
            user = controller.getUser(did);
        }
        String peerName = chat != null ? chat.title : user != null ? UserObject.getUserName(user) : "";
        if (did == UserConfig.getInstance(account).getClientUserId()) {
            peerName = getString(R.string.SavedMessages);
        }
        String accountName = MultiAccountUi.getAccountName(account);
        boolean leave = chat != null && !ChatObject.isNotInChat(chat);
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(getString(R.string.MultiAccountDeleteChat));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(leave ? R.string.MultiAccountLeaveChatText : R.string.MultiAccountDeleteChatText, peerName, accountName)));
        builder.setPositiveButton(getString(R.string.Delete), (d, w) -> performDelete(account, did));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog alert = builder.create();
        showDialog(alert);
        View positive = alert.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (positive instanceof TextView) {
            ((TextView) positive).setTextColor(getThemedColor(Theme.key_text_RedBold));
        }
    }

    private void performDelete(int account, long did) {
        MessagesController controller = MessagesController.getInstance(account);
        if (DialogObject.isChatDialog(did)) {
            TLRPC.Chat chat = controller.getChat(-did);
            if (chat == null || ChatObject.isNotInChat(chat)) {
                controller.deleteDialog(did, 0, false);
            } else {
                TLRPC.User self = controller.getUser(UserConfig.getInstance(account).getClientUserId());
                controller.deleteParticipantFromChat(-did, self, null, false, false);
            }
        } else {
            controller.deleteDialog(did, 0, false);
        }
        controller.checkIfFolderEmpty(folderId);
    }

    private void startNewMessage(View anchor) {
        int primary = MultiAccountController.getInstance().getPrimaryAccount();
        int preselected = selectedFilterAccount() >= 0 ? selectedFilterAccount() : primary;
        MultiAccountUi.chooseAccount(this, anchor, getString(R.string.MultiAccountSendFrom), preselected,
                account -> MultiAccountUi.openNewMessage(this, account));
    }

    private void showOtherOptions(View anchor) {
        if (getParentActivity() == null) {
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(this, anchor);
        options.add(R.drawable.msg_markread, getString(R.string.MarkAllAsRead), this::markAllAsRead);
        options.add(R.drawable.msg_contacts, getString(R.string.MultiAccountSeparate), () -> {
            MultiAccountController.setUnifiedEnabled(false);
            if (LaunchActivity.instance != null) {
                LaunchActivity.instance.rebuildMainInterface();
            }
        });
        options.show();
    }

    private void markAllAsRead() {
        for (UnifiedDialogsStore.Row row : new ArrayList<>(store.getRows())) {
            MessagesController controller = MessagesController.getInstance(row.account);
            if (controller.getDialogUnreadCount(row.dialog) > 0 || row.dialog.unread_mark || row.dialog.unread_mentions_count > 0) {
                markAsRead(row.account, row.dialog);
            }
        }
    }

    // endregion

    private class Adapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == VIEW_DIALOG || type == VIEW_ARCHIVE || type == VIEW_PEER;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_ARCHIVE:
                    view = new TextCell(context, getResourceProvider());
                    break;
                case VIEW_LOADING: {
                    FlickerLoadingView loading = new FlickerLoadingView(context, getResourceProvider());
                    loading.setIsSingleCell(true);
                    loading.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
                    view = loading;
                    break;
                }
                case VIEW_HEADER:
                    view = new GraySectionCell(context, getResourceProvider());
                    break;
                case VIEW_PEER:
                    view = new ProfileSearchCell(context, getResourceProvider());
                    break;
                case VIEW_DIALOG:
                default:
                    view = new UnifiedDialogCell(context, currentAccount, getResourceProvider());
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            switch (item.viewType) {
                case VIEW_ARCHIVE: {
                    TextCell cell = (TextCell) holder.itemView;
                    int unread = UnifiedDialogsStore.getArchiveUnreadCount();
                    cell.setTextAndValueAndIcon(getString(R.string.ArchivedChats), unread > 0 ? LocaleController.formatNumber(unread, ',') : null, R.drawable.msg_archive, true);
                    break;
                }
                case VIEW_HEADER:
                    ((GraySectionCell) holder.itemView).setText(item.text);
                    break;
                case VIEW_PEER: {
                    ProfileSearchCell cell = (ProfileSearchCell) holder.itemView;
                    cell.setCurrentAccount(item.account);
                    MessagesController controller = MessagesController.getInstance(item.account);
                    TLObject peer = item.dialogId > 0 ? controller.getUser(item.dialogId) : controller.getChat(-item.dialogId);
                    CharSequence status = MultiAccountUi.showAccountBadges()
                            ? LocaleController.formatString(R.string.MultiAccountAccountLabel, MultiAccountUi.getAccountShortName(item.account))
                            : null;
                    cell.setData(peer, null, null, status, false, false);
                    break;
                }
                case VIEW_DIALOG: {
                    UnifiedDialogCell cell = (UnifiedDialogCell) holder.itemView;
                    cell.bindAccount(item.account, MultiAccountUi.showAccountBadges());
                    if (item.message != null) {
                        cell.setDialog(item.dialogId, item.message.message, item.message.message.messageOwner.date, false, false);
                    } else if (item.dialog != null) {
                        cell.setDialog(item.dialog, 0, item.dialog.folder_id);
                    }
                    cell.useSeparator = position + 1 < items.size() && items.get(position + 1).viewType == VIEW_DIALOG;
                    break;
                }
            }
        }
    }
}
