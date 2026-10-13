package org.telegram.messenger.multiaccount;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Live, merged view over the dialog lists of all logged-in accounts for one folder
 * (0 = main list, 1 = archive). Each row keeps its owning account; nothing is shared between
 * accounts except the presentation order.
 */
public class UnifiedDialogsStore implements NotificationCenter.NotificationCenterDelegate {

    public interface Listener {
        void onUnifiedDialogsChanged();
    }

    public static final class Row {
        public final int account;
        public final TLRPC.Dialog dialog;
        public final AccountDialogKey key;

        Row(int account, TLRPC.Dialog dialog, AccountDialogKey key) {
            this.account = account;
            this.dialog = dialog;
            this.key = key;
        }
    }

    private static final int[] ACCOUNT_EVENTS = {
        NotificationCenter.dialogsNeedReload,
        NotificationCenter.updateInterfaces,
        NotificationCenter.dialogsUnreadCounterChanged,
        NotificationCenter.notificationsSettingsUpdated,
        NotificationCenter.messagesDidLoad,
        NotificationCenter.messagesRead,
        NotificationCenter.newDraftReceived,
        NotificationCenter.mainUserInfoChanged,
        NotificationCenter.appDidLogout,
        NotificationCenter.folderBecomeEmpty,
    };

    private final int folderId;
    private final Listener listener;
    private final HashSet<Integer> accountFilter = new HashSet<>();
    private int filter = UnifiedDialogsMerger.FILTER_ALL;
    private ArrayList<Row> rows = new ArrayList<>();
    private boolean attached;
    private boolean rebuildScheduled;
    private int lastAccountsHash;

    private final Runnable rebuildRunnable = () -> {
        rebuildScheduled = false;
        rebuild();
    };

    public UnifiedDialogsStore(int folderId, Listener listener) {
        this.folderId = folderId;
        this.listener = listener;
    }

    public int getFolderId() {
        return folderId;
    }

    public void attach() {
        if (attached) {
            return;
        }
        attached = true;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter center = NotificationCenter.getInstance(a);
            for (int event : ACCOUNT_EVENTS) {
                center.addObserver(this, event);
            }
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.multiAccountStateChanged);
        ensureLoaded();
        rebuild();
    }

    public void detach() {
        if (!attached) {
            return;
        }
        attached = false;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter center = NotificationCenter.getInstance(a);
            for (int event : ACCOUNT_EVENTS) {
                center.removeObserver(this, event);
            }
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.multiAccountStateChanged);
        AndroidUtilities.cancelRunOnUIThread(rebuildRunnable);
        rebuildScheduled = false;
    }

    public ArrayList<Row> getRows() {
        return rows;
    }

    public int getFilter() {
        return filter;
    }

    public Set<Integer> getAccountFilter() {
        return accountFilter;
    }

    public void setFilter(int filter, Set<Integer> accounts) {
        this.filter = filter;
        accountFilter.clear();
        if (accounts != null) {
            accountFilter.addAll(accounts);
        }
        rebuild();
        loadMoreIfNeeded();
    }

    /** Starts the first load of every logged-in account whose list was not requested yet. */
    public void ensureLoaded() {
        for (int account : MultiAccountController.getActiveAccounts()) {
            MessagesController controller = MessagesController.getInstance(account);
            if (folderId == 0) {
                DialogsActivity.loadDialogs(AccountInstance.getInstance(account));
            } else if (controller.getDialogs(folderId).isEmpty() && !controller.isDialogsEndReached(folderId) && !controller.isLoadingDialogs(folderId)) {
                controller.loadDialogs(folderId, 0, 100, true);
            }
        }
    }

    /** Requests the next page from every account that currently holds back older rows. */
    public void loadMoreIfNeeded() {
        if (!attached) {
            return;
        }
        ensureLoaded();
        ArrayList<UnifiedDialogsMerger.AccountPage> pages = new ArrayList<>();
        for (int account : MultiAccountController.getActiveAccounts()) {
            pages.add(page(account, collectEntries(account, null)));
        }
        for (int account : UnifiedDialogsMerger.accountsToLoadMore(pages, accountFilter)) {
            MessagesController controller = MessagesController.getInstance(account);
            if (controller.isLoadingDialogs(folderId) || folderId == 0 && !DialogsActivity.dialogsLoaded[account]) {
                continue;
            }
            boolean fromCache = !controller.isDialogsEndReached(folderId);
            if (fromCache || !controller.isServerDialogsEndReached(folderId)) {
                controller.loadDialogs(folderId, -1, 100, fromCache);
            }
        }
    }

    public boolean isLoading() {
        for (int account : MultiAccountController.getActiveAccounts()) {
            MessagesController controller = MessagesController.getInstance(account);
            if (controller.isLoadingDialogs(folderId) || folderId == 0 && !controller.dialogsLoaded) {
                return true;
            }
        }
        return false;
    }

    public boolean hasMore() {
        for (int account : MultiAccountController.getActiveAccounts()) {
            if (accountFilter.isEmpty() || accountFilter.contains(account)) {
                if (!isEndReached(MessagesController.getInstance(account))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether any account has chats in the archive (folder 1). */
    public static boolean hasArchivedChats() {
        for (int account : MultiAccountController.getActiveAccounts()) {
            MessagesController controller = MessagesController.getInstance(account);
            if (controller.dialogs_dict.get(DialogObject.makeFolderDialogId(1)) != null || !controller.getDialogs(1).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static int getArchiveUnreadCount() {
        int count = 0;
        for (int account : MultiAccountController.getActiveAccounts()) {
            count += MessagesStorage.getInstance(account).getArchiveUnreadCount();
        }
        return count;
    }

    public void scheduleRebuild() {
        if (rebuildScheduled) {
            return;
        }
        rebuildScheduled = true;
        AndroidUtilities.runOnUIThread(rebuildRunnable, 16);
    }

    public void rebuild() {
        ArrayList<Integer> accounts = MultiAccountController.getActiveAccounts();
        accountFilter.retainAll(accounts);
        ArrayList<UnifiedDialogsMerger.Entry> entries = new ArrayList<>();
        ArrayList<UnifiedDialogsMerger.AccountPage> pages = new ArrayList<>();
        for (int account : accounts) {
            ArrayList<UnifiedDialogsMerger.Entry> accountEntries = collectEntries(account, accounts);
            entries.addAll(accountEntries);
            pages.add(page(account, accountEntries));
        }
        ArrayList<UnifiedDialogsMerger.Entry> merged = UnifiedDialogsMerger.merge(entries, pages, filter, accountFilter);
        ArrayList<Row> newRows = new ArrayList<>(merged.size());
        for (UnifiedDialogsMerger.Entry entry : merged) {
            newRows.add(new Row(entry.key.account, (TLRPC.Dialog) entry.payload, entry.key));
        }
        rows = newRows;
        lastAccountsHash = accounts.hashCode();
        if (listener != null) {
            listener.onUnifiedDialogsChanged();
        }
    }

    private ArrayList<UnifiedDialogsMerger.Entry> collectEntries(int account, ArrayList<Integer> order) {
        MessagesController controller = MessagesController.getInstance(account);
        MediaDataController mediaDataController = MediaDataController.getInstance(account);
        ArrayList<TLRPC.Dialog> dialogs = controller.getDialogs(folderId);
        int ordinal = order != null ? Math.max(0, order.indexOf(account)) : 0;
        ArrayList<UnifiedDialogsMerger.Entry> result = new ArrayList<>(dialogs.size());
        for (int i = 0, n = dialogs.size(); i < n; i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (dialog == null || dialog instanceof TLRPC.TL_dialogFolder || DialogObject.isFolderDialogId(dialog.id)) {
                continue;
            }
            int date = (int) DialogObject.getLastMessageOrDraftDate(dialog, mediaDataController.getDraft(dialog.id, 0));
            boolean unread = controller.getDialogUnreadCount(dialog) > 0 || dialog.unread_mark || dialog.unread_mentions_count > 0;
            result.add(new UnifiedDialogsMerger.Entry(account, ordinal, dialog.id, dialog.pinned, dialog.pinnedNum, date, unread, dialog));
        }
        return result;
    }

    private UnifiedDialogsMerger.AccountPage page(int account, ArrayList<UnifiedDialogsMerger.Entry> entries) {
        int oldest = Integer.MAX_VALUE;
        for (UnifiedDialogsMerger.Entry entry : entries) {
            if (!entry.pinned && entry.date > 0) {
                oldest = Math.min(oldest, entry.date);
            }
        }
        return new UnifiedDialogsMerger.AccountPage(account, isEndReached(MessagesController.getInstance(account)), oldest);
    }

    private boolean isEndReached(MessagesController controller) {
        return controller.isDialogsEndReached(folderId) && controller.isServerDialogsEndReached(folderId);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (!attached) {
            return;
        }
        if (id == NotificationCenter.appDidLogout || id == NotificationCenter.mainUserInfoChanged || id == NotificationCenter.multiAccountStateChanged) {
            if (MultiAccountController.getActiveAccounts().hashCode() != lastAccountsHash) {
                ensureLoaded();
            }
        }
        scheduleRebuild();
    }
}
