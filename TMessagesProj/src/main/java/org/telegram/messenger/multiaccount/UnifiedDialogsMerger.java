package org.telegram.messenger.multiaccount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Pure merge logic of the unified chat list (no Android or controller dependencies, unit tested).
 *
 * <p>Each account pages its dialogs independently, newest first. A dialog of account A that is older
 * than the oldest loaded dialog of a not fully loaded account B could later be overtaken by a not yet
 * loaded dialog of B, so such rows are held back until B loads further ({@link #visibleCutoff}).
 */
public final class UnifiedDialogsMerger {

    public static final int FILTER_ALL = 0;
    public static final int FILTER_UNREAD = 1;

    public static final class Entry {
        public final AccountDialogKey key;
        public final int accountOrdinal;
        public final boolean pinned;
        public final int pinnedNum;
        public final int date;
        public final boolean unread;
        public final Object payload;

        public Entry(int account, int accountOrdinal, long dialogId, boolean pinned, int pinnedNum, int date, boolean unread, Object payload) {
            this.key = new AccountDialogKey(account, dialogId);
            this.accountOrdinal = accountOrdinal;
            this.pinned = pinned;
            this.pinnedNum = pinnedNum;
            this.date = date;
            this.unread = unread;
            this.payload = payload;
        }
    }

    /** Paging state of one account's dialog list. */
    public static final class AccountPage {
        public final int account;
        public final boolean endReached;
        /** Date of the oldest non-pinned dialog loaded so far, {@code Integer.MAX_VALUE} when none. */
        public final int oldestLoadedDate;

        public AccountPage(int account, boolean endReached, int oldestLoadedDate) {
            this.account = account;
            this.endReached = endReached;
            this.oldestLoadedDate = oldestLoadedDate;
        }
    }

    public static final Comparator<Entry> ORDER = (e1, e2) -> {
        if (e1.pinned != e2.pinned) {
            return e1.pinned ? -1 : 1;
        }
        if (e1.pinned) {
            if (e1.accountOrdinal != e2.accountOrdinal) {
                return Integer.compare(e1.accountOrdinal, e2.accountOrdinal);
            }
            if (e1.pinnedNum != e2.pinnedNum) {
                return Integer.compare(e2.pinnedNum, e1.pinnedNum);
            }
        }
        if (e1.date != e2.date) {
            return Integer.compare(e2.date, e1.date);
        }
        if (e1.accountOrdinal != e2.accountOrdinal) {
            return Integer.compare(e1.accountOrdinal, e2.accountOrdinal);
        }
        return Long.compare(e1.key.dialogId, e2.key.dialogId);
    };

    private UnifiedDialogsMerger() {
    }

    /**
     * Rows older than the returned date must not be shown yet. Only accounts included by the filter
     * matter: a hidden account cannot reorder visible rows.
     */
    public static int visibleCutoff(List<AccountPage> pages, Set<Integer> accountFilter) {
        int cutoff = 0;
        for (AccountPage page : pages) {
            if (page.endReached || !includes(accountFilter, page.account)) {
                continue;
            }
            if (page.oldestLoadedDate == Integer.MAX_VALUE) {
                continue;
            }
            cutoff = Math.max(cutoff, page.oldestLoadedDate);
        }
        return cutoff;
    }

    /**
     * Merges, filters and orders entries of all accounts.
     *
     * @param accountFilter accounts to show, or {@code null}/empty for all accounts
     */
    public static ArrayList<Entry> merge(List<Entry> entries, List<AccountPage> pages, int filter, Set<Integer> accountFilter) {
        int cutoff = visibleCutoff(pages, accountFilter);
        ArrayList<Entry> result = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (!includes(accountFilter, entry.key.account)) {
                continue;
            }
            if (filter == FILTER_UNREAD && !entry.unread) {
                continue;
            }
            if (!entry.pinned && entry.date < cutoff) {
                continue;
            }
            result.add(entry);
        }
        Collections.sort(result, ORDER);
        return result;
    }

    /** Accounts whose paging currently limits {@link #visibleCutoff}: these should load more. */
    public static ArrayList<Integer> accountsToLoadMore(List<AccountPage> pages, Set<Integer> accountFilter) {
        int cutoff = visibleCutoff(pages, accountFilter);
        ArrayList<Integer> result = new ArrayList<>();
        for (AccountPage page : pages) {
            if (page.endReached || !includes(accountFilter, page.account)) {
                continue;
            }
            if (page.oldestLoadedDate >= cutoff || page.oldestLoadedDate == Integer.MAX_VALUE) {
                result.add(page.account);
            }
        }
        return result;
    }

    private static boolean includes(Set<Integer> accountFilter, int account) {
        return accountFilter == null || accountFilter.isEmpty() || accountFilter.contains(account);
    }
}
