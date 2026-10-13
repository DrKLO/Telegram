package org.telegram.messenger.multiaccount;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class UnifiedDialogsMergerTest {

    private static UnifiedDialogsMerger.Entry entry(int account, long dialogId, int date) {
        return new UnifiedDialogsMerger.Entry(account, account, dialogId, false, 0, date, false, account + ":" + dialogId);
    }

    private static UnifiedDialogsMerger.Entry unread(int account, long dialogId, int date) {
        return new UnifiedDialogsMerger.Entry(account, account, dialogId, false, 0, date, true, account + ":" + dialogId);
    }

    private static UnifiedDialogsMerger.Entry pinned(int account, long dialogId, int pinnedNum, int date) {
        return new UnifiedDialogsMerger.Entry(account, account, dialogId, true, pinnedNum, date, false, account + ":" + dialogId);
    }

    private static List<UnifiedDialogsMerger.AccountPage> complete(int... accounts) {
        ArrayList<UnifiedDialogsMerger.AccountPage> pages = new ArrayList<>();
        for (int account : accounts) {
            pages.add(new UnifiedDialogsMerger.AccountPage(account, true, 0));
        }
        return pages;
    }

    private static List<String> keys(List<UnifiedDialogsMerger.Entry> entries) {
        ArrayList<String> result = new ArrayList<>();
        for (UnifiedDialogsMerger.Entry entry : entries) {
            result.add(entry.key.toString());
        }
        return result;
    }

    @Test
    public void mergesAccountsByLatestMessage() {
        List<UnifiedDialogsMerger.Entry> entries = Arrays.asList(
                entry(0, 10, 100), entry(0, 11, 300),
                entry(1, 20, 200), entry(1, 21, 400));
        List<UnifiedDialogsMerger.Entry> merged = UnifiedDialogsMerger.merge(entries, complete(0, 1), UnifiedDialogsMerger.FILTER_ALL, null);
        assertEquals(Arrays.asList("1:21", "0:11", "1:20", "0:10"), keys(merged));
    }

    @Test
    public void sameDialogIdInTwoAccountsStaysTwoRows() {
        List<UnifiedDialogsMerger.Entry> entries = Arrays.asList(entry(0, -500, 100), entry(1, -500, 100));
        List<UnifiedDialogsMerger.Entry> merged = UnifiedDialogsMerger.merge(entries, complete(0, 1), UnifiedDialogsMerger.FILTER_ALL, null);
        assertEquals(2, merged.size());
        assertNotEquals(merged.get(0).key, merged.get(1).key);
        assertEquals(Arrays.asList("0:-500", "1:-500"), keys(merged));
        assertNotEquals(merged.get(0).payload, merged.get(1).payload);
    }

    @Test
    public void keysCompareAccountAndDialog() {
        assertEquals(new AccountDialogKey(1, 42), new AccountDialogKey(1, 42));
        assertNotEquals(new AccountDialogKey(0, 42), new AccountDialogKey(1, 42));
        assertNotEquals(new AccountDialogKey(0, 42).stableId(), new AccountDialogKey(1, 42).stableId());
        HashSet<AccountDialogKey> set = new HashSet<>(Arrays.asList(new AccountDialogKey(0, 42), new AccountDialogKey(1, 42), new AccountDialogKey(1, 42)));
        assertEquals(2, set.size());
    }

    @Test
    public void pinnedFirstGroupedByAccountThenPinOrder() {
        List<UnifiedDialogsMerger.Entry> entries = Arrays.asList(
                entry(0, 1, 999),
                pinned(1, 2, 5, 10),
                pinned(0, 3, 1, 20),
                pinned(0, 4, 2, 5));
        List<UnifiedDialogsMerger.Entry> merged = UnifiedDialogsMerger.merge(entries, complete(0, 1), UnifiedDialogsMerger.FILTER_ALL, null);
        assertEquals(Arrays.asList("0:4", "0:3", "1:2", "0:1"), keys(merged));
    }

    @Test
    public void unreadAndAccountFilters() {
        List<UnifiedDialogsMerger.Entry> entries = Arrays.asList(
                unread(0, 1, 100), entry(0, 2, 200), unread(1, 3, 300), entry(1, 4, 400));
        assertEquals(Arrays.asList("1:3", "0:1"),
                keys(UnifiedDialogsMerger.merge(entries, complete(0, 1), UnifiedDialogsMerger.FILTER_UNREAD, null)));
        assertEquals(Arrays.asList("1:4", "1:3"),
                keys(UnifiedDialogsMerger.merge(entries, complete(0, 1), UnifiedDialogsMerger.FILTER_ALL, Collections.singleton(1))));
        assertEquals(Collections.singletonList("0:1"),
                keys(UnifiedDialogsMerger.merge(entries, complete(0, 1), UnifiedDialogsMerger.FILTER_UNREAD, Collections.singleton(0))));
    }

    @Test
    public void holdsBackRowsOlderThanAnIncompleteAccount() {
        // account 1 has only loaded down to date 250; older rows of account 0 might be overtaken later
        List<UnifiedDialogsMerger.Entry> entries = Arrays.asList(
                entry(0, 1, 500), entry(0, 2, 100),
                entry(1, 3, 300), entry(1, 4, 250));
        List<UnifiedDialogsMerger.AccountPage> pages = Arrays.asList(
                new UnifiedDialogsMerger.AccountPage(0, true, 100),
                new UnifiedDialogsMerger.AccountPage(1, false, 250));
        assertEquals(250, UnifiedDialogsMerger.visibleCutoff(pages, null));
        assertEquals(Arrays.asList("0:1", "1:3", "1:4"),
                keys(UnifiedDialogsMerger.merge(entries, pages, UnifiedDialogsMerger.FILTER_ALL, null)));
        assertEquals(Collections.singletonList(1), UnifiedDialogsMerger.accountsToLoadMore(pages, null));

        // filtering to account 0 alone: account 1 can no longer reorder anything
        assertEquals(0, UnifiedDialogsMerger.visibleCutoff(pages, Collections.singleton(0)));
        assertEquals(Arrays.asList("0:1", "0:2"),
                keys(UnifiedDialogsMerger.merge(entries, pages, UnifiedDialogsMerger.FILTER_ALL, Collections.singleton(0))));
        assertTrue(UnifiedDialogsMerger.accountsToLoadMore(pages, Collections.singleton(0)).isEmpty());
    }

    @Test
    public void pinnedRowsAreNeverHeldBack() {
        List<UnifiedDialogsMerger.Entry> entries = Arrays.asList(pinned(0, 1, 1, 1), entry(1, 2, 900));
        List<UnifiedDialogsMerger.AccountPage> pages = Arrays.asList(
                new UnifiedDialogsMerger.AccountPage(0, true, Integer.MAX_VALUE),
                new UnifiedDialogsMerger.AccountPage(1, false, 900));
        assertEquals(Arrays.asList("0:1", "1:2"),
                keys(UnifiedDialogsMerger.merge(entries, pages, UnifiedDialogsMerger.FILTER_ALL, null)));
    }

    @Test
    public void accountWithoutLoadedPageDoesNotBlankTheList() {
        List<UnifiedDialogsMerger.Entry> entries = Collections.singletonList(entry(0, 1, 50));
        List<UnifiedDialogsMerger.AccountPage> pages = Arrays.asList(
                new UnifiedDialogsMerger.AccountPage(0, true, 50),
                new UnifiedDialogsMerger.AccountPage(1, false, Integer.MAX_VALUE));
        assertEquals(1, UnifiedDialogsMerger.merge(entries, pages, UnifiedDialogsMerger.FILTER_ALL, null).size());
        assertFalse(UnifiedDialogsMerger.accountsToLoadMore(pages, null).isEmpty());
    }
}
