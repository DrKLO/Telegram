package org.telegram.ui.Stars;

import static org.telegram.messenger.LocaleController.getString;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stats;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChannelMonetizationLayout;

import java.util.ArrayList;
import java.util.HashMap;

public class BotStarsController {

    private static volatile BotStarsController[] Instance = new BotStarsController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];
    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }

    public static BotStarsController getInstance(int num) {
        BotStarsController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects[num]) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new BotStarsController(num);
                }
            }
        }
        return localInstance;
    }

    public final int currentAccount;

    private BotStarsController(int account) {
        currentAccount = account;
    }

    private final HashMap<Long, Long> lastLoadedBotStarsStats = new HashMap<>();
    private final HashMap<Long, TLRPC.TL_payments_starsRevenueStats> botStarsStats = new HashMap<>();

    private final HashMap<Long, Long> lastLoadedTonStats = new HashMap<>();
    private final HashMap<Long, TL_stats.TL_broadcastRevenueStats> tonStats = new HashMap<>();

    public long getBotStarsBalance(long did) {
        TLRPC.TL_payments_starsRevenueStats botStats = getStarsRevenueStats(did);
        return botStats == null ? 0 : botStats.status.current_balance;
    }

    public long getTONBalance(long did) {
        TL_stats.TL_broadcastRevenueStats botStats = getTONRevenueStats(did, false);
        return botStats == null || botStats.balances == null ? 0 : botStats.balances.current_balance;
    }

    public long getAvailableBalance(long did) {
        TLRPC.TL_payments_starsRevenueStats botStats = getStarsRevenueStats(did);
        return botStats == null ? 0 : botStats.status.available_balance;
    }

    public boolean isStarsBalanceAvailable(long did) {
        return getStarsRevenueStats(did) != null;
    }

    public boolean isTONBalanceAvailable(long did) {
        return getTONRevenueStats(did, false) != null;
    }

    public TLRPC.TL_payments_starsRevenueStats getStarsRevenueStats(long did) {
        return getStarsRevenueStats(did, false);
    }

    public boolean botHasStars(long did) {
        TLRPC.TL_payments_starsRevenueStats stats = getStarsRevenueStats(did);
        return stats != null && stats.status != null && (stats.status.available_balance > 0 || stats.status.overall_revenue > 0 || stats.status.current_balance > 0);
    }

    public boolean botHasTON(long did) {
        TL_stats.TL_broadcastRevenueStats stats = getTONRevenueStats(did, false);
        return stats != null && (stats.balances.current_balance > 0 || stats.balances.available_balance > 0 || stats.balances.overall_revenue > 0);
    }

    public void preloadStarsStats(long did) {
        Long lastLoaded = lastLoadedBotStarsStats.get(did);
        getStarsRevenueStats(did, lastLoaded == null || System.currentTimeMillis() - lastLoaded > 1000 * 30);
    }

    public void preloadTonStats(long did) {
        Long lastLoaded = lastLoadedTonStats.get(did);
        getTONRevenueStats(did, lastLoaded == null || System.currentTimeMillis() - lastLoaded > 1000 * 30);
    }

    public TLRPC.TL_payments_starsRevenueStats getStarsRevenueStats(long did, boolean force) {
        Long lastLoaded = lastLoadedBotStarsStats.get(did);
        TLRPC.TL_payments_starsRevenueStats botStats = botStarsStats.get(did);
        if (lastLoaded == null || System.currentTimeMillis() - lastLoaded > 1000 * 60 * 5 || force) {
            TLRPC.TL_payments_getStarsRevenueStats req = new TLRPC.TL_payments_getStarsRevenueStats();
            req.dark = Theme.isCurrentThemeDark();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(did);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.TL_payments_starsRevenueStats) {
                    TLRPC.TL_payments_starsRevenueStats r = (TLRPC.TL_payments_starsRevenueStats) res;
                    botStarsStats.put(did, r);
                } else {
                    botStarsStats.put(did, null);
                }
                lastLoadedBotStarsStats.put(did, System.currentTimeMillis());
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botStarsUpdated, did);
            }));
        }
        return botStats;
    }

    public TL_stats.TL_broadcastRevenueStats getTONRevenueStats(long did, boolean force) {
        Long lastLoaded = lastLoadedTonStats.get(did);
        TL_stats.TL_broadcastRevenueStats botStats = tonStats.get(did);
        if (lastLoaded == null || System.currentTimeMillis() - lastLoaded > 1000 * 60 * 5 || force) {
            TL_stats.TL_getBroadcastRevenueStats req = new TL_stats.TL_getBroadcastRevenueStats();
            req.dark = Theme.isCurrentThemeDark();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(did);
            final int stats_dc;
            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-did);
            if (chatFull != null) {
                stats_dc = chatFull.stats_dc;
            } else {
                stats_dc = ConnectionsManager.DEFAULT_DATACENTER_ID;
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TL_stats.TL_broadcastRevenueStats) {
                    TL_stats.TL_broadcastRevenueStats r = (TL_stats.TL_broadcastRevenueStats) res;
                    tonStats.put(did, r);
                } else {
                    tonStats.put(did, null);
                }
                lastLoadedTonStats.put(did, System.currentTimeMillis());
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botStarsUpdated, did);
            }), null, null, 0, stats_dc, ConnectionsManager.ConnectionTypeGeneric, true);
        }
        return botStats;
    }

    public void onUpdate(TLRPC.TL_updateStarsRevenueStatus update) {
        if (update == null) return;
        long dialogId = DialogObject.getPeerDialogId(update.peer);
        if (dialogId < 0) {
            if (ChannelMonetizationLayout.instance != null && ChannelMonetizationLayout.instance.dialogId == DialogObject.getPeerDialogId(update.peer)) {
                ChannelMonetizationLayout.instance.setupBalances(update.status);
                ChannelMonetizationLayout.instance.reloadTransactions();
            }
        } else {
            TLRPC.TL_payments_starsRevenueStats s = getStarsRevenueStats(dialogId, true);
            if (s != null) {
                s.status = update.status;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botStarsUpdated, dialogId);
            }
            invalidateTransactions(dialogId, true);
        }
    }


    public static final int ALL_TRANSACTIONS = 0;
    public static final int INCOMING_TRANSACTIONS = 1;
    public static final int OUTGOING_TRANSACTIONS = 2;

    private class TransactionsState {
        public final ArrayList<TL_stars.StarsTransaction>[] transactions = new ArrayList[] { new ArrayList<>(), new ArrayList<>(), new ArrayList<>() };
        public final boolean[] transactionsExist = new boolean[3];
        private final String[] offset = new String[3];
        private final boolean[] loading = new boolean[3];
        private final boolean[] endReached = new boolean[3];
    }

    private final HashMap<Long, TransactionsState> transactions = new HashMap<>();

    @NonNull
    private TransactionsState getTransactionsState(long did) {
        TransactionsState state = transactions.get(did);
        if (state == null) {
            transactions.put(did, state = new TransactionsState());
        }
        return state;
    }

    @NonNull
    public ArrayList<TL_stars.StarsTransaction> getTransactions(long did, int type) {
        TransactionsState state = getTransactionsState(did);
        return state.transactions[type];
    }

    public void invalidateTransactions(long did, boolean load) {
        final TransactionsState state = getTransactionsState(did);
        for (int i = 0; i < 3; ++i) {
            if (state.loading[i]) continue;
            state.transactions[i].clear();
            state.offset[i] = null;
            state.loading[i] = false;
            state.endReached[i] = false;
            if (load)
                loadTransactions(did, i);
        }
    }

    public void preloadTransactions(long did) {
        final TransactionsState state = getTransactionsState(did);
        for (int i = 0; i < 3; ++i) {
            if (!state.loading[i] && !state.endReached[i] && state.offset[i] == null) {
                loadTransactions(did, i);
            }
        }
    }

    public void loadTransactions(long did, int type) {
        final TransactionsState state = getTransactionsState(did);
        if (state.loading[type] || state.endReached[type]) {
            return;
        }

        state.loading[type] = true;

        TL_stars.TL_payments_getStarsTransactions req = new TL_stars.TL_payments_getStarsTransactions();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(did);
        req.inbound = type == INCOMING_TRANSACTIONS;
        req.outbound = type == OUTGOING_TRANSACTIONS;
        req.offset = state.offset[type];
        if (req.offset == null) {
            req.offset = "";
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            state.loading[type] = false;
            if (res instanceof TL_stars.TL_payments_starsStatus) {
                TL_stars.TL_payments_starsStatus r = (TL_stars.TL_payments_starsStatus) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);

                state.transactions[type].addAll(r.history);
                state.transactionsExist[type] = !state.transactions[type].isEmpty() || state.transactionsExist[type];
                state.endReached[type] = (r.flags & 1) == 0;
                state.offset[type] = state.endReached[type] ? null : r.next_offset;

//                state.updateBalance(r.balance);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botStarsTransactionsLoaded, did);
            }
        }));
    }

    public boolean isLoadingTransactions(long did, int type) {
        final TransactionsState state = getTransactionsState(did);
        return state.loading[type];
    }

    public boolean didFullyLoadTransactions(long did, int type) {
        final TransactionsState state = getTransactionsState(did);
        return state.endReached[type];
    }

    public boolean hasTransactions(long did) {
        return hasTransactions(did, ALL_TRANSACTIONS);
    }

    public boolean hasTransactions(long did, int type) {
        final TransactionsState state = getTransactionsState(did);
        return !state.transactions[type].isEmpty();
    }

}
