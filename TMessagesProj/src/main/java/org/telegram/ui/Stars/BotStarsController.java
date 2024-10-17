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

    private final HashMap<Long, Long> lastLoadedStats = new HashMap<>();
    private final HashMap<Long, TLRPC.TL_payments_starsRevenueStats> stats = new HashMap<>();

    private final HashMap<Long, Long> lastLoadedChannelStats = new HashMap<>();
    private final HashMap<Long, TL_stats.TL_broadcastRevenueStats> channelStats = new HashMap<>();

    public long getBalance(long did) {
        TLRPC.TL_payments_starsRevenueStats botStats = getRevenueStats(did);
        return botStats == null ? 0 : botStats.status.current_balance;
    }

    public long getChannelBalance(long did) {
        TL_stats.TL_broadcastRevenueStats botStats = getChannelRevenueStats(did, false);
        return botStats == null || botStats.balances == null ? 0 : botStats.balances.current_balance;
    }

    public long getAvailableBalance(long did) {
        TLRPC.TL_payments_starsRevenueStats botStats = getRevenueStats(did);
        return botStats == null ? 0 : botStats.status.available_balance;
    }

    public boolean isBalanceAvailable(long did) {
        return getRevenueStats(did) != null;
    }

    public TLRPC.TL_payments_starsRevenueStats getRevenueStats(long did) {
        return getRevenueStats(did, false);
    }

    public boolean hasStars(long did) {
        TLRPC.TL_payments_starsRevenueStats stats = getRevenueStats(did);
        return stats != null && stats.status != null && (stats.status.available_balance > 0 || stats.status.overall_revenue > 0 || stats.status.current_balance > 0);
    }

    public void preloadRevenueStats(long did) {
        Long lastLoaded = lastLoadedStats.get(did);
        TLRPC.TL_payments_starsRevenueStats botStats = stats.get(did);
        getRevenueStats(did, lastLoaded == null || System.currentTimeMillis() - lastLoaded > 1000 * 30);
    }

    public TLRPC.TL_payments_starsRevenueStats getRevenueStats(long did, boolean force) {
        Long lastLoaded = lastLoadedStats.get(did);
        TLRPC.TL_payments_starsRevenueStats botStats = stats.get(did);
        if (lastLoaded == null || System.currentTimeMillis() - lastLoaded > 1000 * 60 * 5 || force) {
            TLRPC.TL_payments_getStarsRevenueStats req = new TLRPC.TL_payments_getStarsRevenueStats();
            req.dark = Theme.isCurrentThemeDark();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(did);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.TL_payments_starsRevenueStats) {
                    TLRPC.TL_payments_starsRevenueStats r = (TLRPC.TL_payments_starsRevenueStats) res;
                    stats.put(did, r);
                } else {
                    stats.put(did, null);
                }
                lastLoadedStats.put(did, System.currentTimeMillis());
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botStarsUpdated, did);
            }));
        }
        return botStats;
    }

    public TL_stats.TL_broadcastRevenueStats getChannelRevenueStats(long did, boolean force) {
        Long lastLoaded = lastLoadedChannelStats.get(did);
        TL_stats.TL_broadcastRevenueStats botStats = channelStats.get(did);
        if (lastLoaded == null || System.currentTimeMillis() - lastLoaded > 1000 * 60 * 5 || force) {
            TL_stats.TL_getBroadcastRevenueStats req = new TL_stats.TL_getBroadcastRevenueStats();
            req.dark = Theme.isCurrentThemeDark();
            req.channel = MessagesController.getInstance(currentAccount).getInputChannel(-did);
            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-did);
            if (chatFull == null) return botStats;
            final int stats_dc = chatFull.stats_dc;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TL_stats.TL_broadcastRevenueStats) {
                    TL_stats.TL_broadcastRevenueStats r = (TL_stats.TL_broadcastRevenueStats) res;
                    channelStats.put(did, r);
                } else {
                    channelStats.put(did, null);
                }
                lastLoadedChannelStats.put(did, System.currentTimeMillis());
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
            TLRPC.TL_payments_starsRevenueStats s = getRevenueStats(dialogId, true);
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
