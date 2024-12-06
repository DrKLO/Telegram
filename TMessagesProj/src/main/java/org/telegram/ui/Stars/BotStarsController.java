package org.telegram.ui.Stars;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.tgnet.tl.TL_payments;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stats;
import org.telegram.ui.ActionBar.AlertDialog;
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

    public TL_stars.StarsAmount getBotStarsBalance(long did) {
        TLRPC.TL_payments_starsRevenueStats botStats = getStarsRevenueStats(did);
        return botStats == null ? new TL_stars.StarsAmount(0) : botStats.status.current_balance;
    }

    public long getTONBalance(long did) {
        TL_stats.TL_broadcastRevenueStats botStats = getTONRevenueStats(did, false);
        return botStats == null || botStats.balances == null ? 0 : botStats.balances.current_balance;
    }

    public long getAvailableBalance(long did) {
        TLRPC.TL_payments_starsRevenueStats botStats = getStarsRevenueStats(did);
        return botStats == null ? 0 : botStats.status.available_balance.amount;
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
        return stats != null && stats.status != null && (stats.status.available_balance.amount > 0 || stats.status.overall_revenue.amount > 0 || stats.status.current_balance.amount > 0);
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
            if (res instanceof TL_stars.StarsStatus) {
                TL_stars.StarsStatus r = (TL_stars.StarsStatus) res;
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

    private final HashMap<Long, ChannelConnectedBots> connectedBots = new HashMap<>();
    public static class ChannelConnectedBots {

        public final int currentAccount;
        public final long dialogId;
        public int count;
        public boolean endReached;
        public final ArrayList<TL_payments.connectedBotStarRef> bots = new ArrayList<>();
        public long lastRequestTime;

        public ChannelConnectedBots(int currentAccount, long dialogId) {
            this.currentAccount = currentAccount;
            this.dialogId = dialogId;
            check();
        }

        public void clear() {
            count = 0;
            error = false;
            endReached = false;
        }

        public void check() {
            if ((System.currentTimeMillis() - lastRequestTime) > 1000 * 60 * 15) {
                clear();
                cancel();
                load();
            }
        }

        public void cancel() {
            if (reqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                reqId = 0;
            }
            loading = false;
        }

        public boolean isLoading() {
            return loading;
        }

        private boolean loading = false;
        private boolean error = false;
        private int reqId;
        public void load() {
            if (loading || error || endReached) return;

            lastRequestTime = System.currentTimeMillis();
            TL_payments.getConnectedStarRefBots req = new TL_payments.getConnectedStarRefBots();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.limit = 20;
            if (!bots.isEmpty()) {
                TL_payments.connectedBotStarRef bot = bots.get(bots.size() - 1);
                req.flags |= 4;
                req.offset_date = bot.date;
                req.offset_link = bot.url;
            }
            reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                reqId = 0;
                if (res instanceof TL_payments.connectedStarRefBots) {
                    TL_payments.connectedStarRefBots r = (TL_payments.connectedStarRefBots) res;
                    MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                    if (count <= 0) {
                        bots.clear();
                    }
                    count = r.count;
                    bots.addAll(r.connected_bots);
                    endReached = r.connected_bots.isEmpty() || bots.size() >= count;
                } else {
                    error = true;
                    endReached = true;
                }
                loading = false;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.channelConnectedBotsUpdate, dialogId);
            }));
        }

        public void apply(TL_payments.connectedStarRefBots res) {
            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
            clear();
            bots.clear();
            cancel();
            count = res.count;
            bots.addAll(res.connected_bots);
            endReached = res.connected_bots.isEmpty() || bots.size() >= count;
            error = false;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.channelConnectedBotsUpdate, dialogId);
            load();
        }

        public void applyEdit(TL_payments.connectedStarRefBots res) {
            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
            for (int a = 0; a < res.connected_bots.size(); ++a) {
                TL_payments.connectedBotStarRef bot = res.connected_bots.get(a);
                for (int i = 0; i < bots.size(); ++i) {
                    if (bots.get(i).bot_id == bot.bot_id) {
                        if (bot.revoked) {
                            bots.remove(i);
                            count = Math.max(count - 1, 0);
                        } else {
                            bots.set(i, bot);
                        }
                        break;
                    }
                }
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.channelConnectedBotsUpdate, dialogId);
            load();
        }
    }

    public ChannelConnectedBots getChannelConnectedBots(long dialogId) {
        ChannelConnectedBots bots = connectedBots.get(dialogId);
        if (bots == null) {
            connectedBots.put(dialogId, bots = new ChannelConnectedBots(currentAccount, dialogId));
        }
        return bots;
    }

    public boolean channelHasConnectedBots(long dialogId) {
        final ChannelConnectedBots bots = getChannelConnectedBots(dialogId);
        return bots != null && bots.count > 0;
    }


    private final HashMap<Long, ChannelSuggestedBots> suggestedBots = new HashMap<>();
    public static class ChannelSuggestedBots {

        public final int currentAccount;
        public final long dialogId;
        public int count;
        public boolean endReached;
        public final ArrayList<TL_payments.starRefProgram> bots = new ArrayList<>();
        public long lastRequestTime;

        public ChannelSuggestedBots(int currentAccount, long dialogId) {
            this.currentAccount = currentAccount;
            this.dialogId = dialogId;
            check();
        }

        public void clear() {
            count = 0;
            endReached = false;
            error = false;
            lastRequestTime = 0;
            lastOffset = null;
        }

        public void check() {
            if ((System.currentTimeMillis() - lastRequestTime) > 1000 * 60 * 15) {
                clear();
                cancel();
                load();
            }
        }

        public void cancel() {
            if (reqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                reqId = 0;
            }
            loading = false;
        }

        public boolean isLoading() {
            return loading;
        }

        public int getCount() {
            return Math.max(count, bots.size());
        }

        public enum Sort {
            BY_PROFITABILITY,
            BY_REVENUE,
            BY_DATE
        };

        private Sort sorting = Sort.BY_PROFITABILITY;
        public void setSort(Sort sort) {
            if (sorting != sort) {
                sorting = sort;
                reload();
            }
        }

        public Sort getSort() {
            return sorting;
        }

        private boolean loading = false;
        private boolean error = false;
        private String lastOffset = null;
        private int reqId;
        public void load() {
            if (loading || error || endReached) return;

            lastRequestTime = System.currentTimeMillis();
            TL_payments.getSuggestedStarRefBots req = new TL_payments.getSuggestedStarRefBots();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.limit = 20;
            req.order_by_date = sorting == Sort.BY_DATE;
            req.order_by_revenue = sorting == Sort.BY_REVENUE;
            if (!TextUtils.isEmpty(lastOffset)) {
                req.offset = lastOffset;
            } else {
                req.offset = "";
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TL_payments.suggestedStarRefBots) {
                    TL_payments.suggestedStarRefBots r = (TL_payments.suggestedStarRefBots) res;
                    MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                    if (count <= 0) {
                        bots.clear();
                    }
                    count = r.count;
                    bots.addAll(r.suggested_bots);
                    lastOffset = r.next_offset;
                    endReached = r.suggested_bots.isEmpty() || bots.size() >= count;
                } else {
                    error = true;
                    endReached = true;
                }
                loading = false;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.channelSuggestedBotsUpdate, dialogId);
            }));
        }

        public void remove(long did) {
            for (int i = 0; i < bots.size(); ++i) {
                if (bots.get(i).bot_id == did) {
                    bots.remove(i);
                    count--;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.channelSuggestedBotsUpdate, dialogId);
                    break;
                }
            }
        }

        public void reload() {
            clear();
            cancel();
            load();
        }
    }

    public ChannelSuggestedBots getChannelSuggestedBots(long dialogId) {
        ChannelSuggestedBots bots = suggestedBots.get(dialogId);
        if (bots == null) {
            suggestedBots.put(dialogId, bots = new ChannelSuggestedBots(currentAccount, dialogId));
        }
        return bots;
    }

    public boolean channelHasSuggestedBots(long dialogId) {
        final ChannelConnectedBots bots = getChannelConnectedBots(dialogId);
        return bots != null && bots.count > 0;
    }

    private boolean loadingAdminedBots;
    public ArrayList<TLRPC.User> adminedBots;
    private boolean loadingAdminedChannels;
    public ArrayList<TLRPC.Chat> adminedChannels;

    public void loadAdmined() {
        if (!loadingAdminedBots || adminedBots != null) {
            loadingAdminedBots = true;
            TL_bots.getAdminedBots req1 = new TL_bots.getAdminedBots();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req1, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                adminedBots = new ArrayList<>();
                loadingAdminedBots = false;
                if (res instanceof TLRPC.Vector) {
                    TLRPC.Vector vector = (TLRPC.Vector) res;
                    for (int i = 0; i < vector.objects.size(); ++i) {
                        adminedBots.add((TLRPC.User) vector.objects.get(i));
                    }
                    MessagesController.getInstance(currentAccount).putUsers(adminedBots, false);
                }
            }));
        }

        if (!loadingAdminedChannels || adminedChannels != null) {
            loadingAdminedChannels = true;
            TLRPC.TL_channels_getAdminedPublicChannels req2 = new TLRPC.TL_channels_getAdminedPublicChannels();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                adminedChannels = new ArrayList<>();
                loadingAdminedBots = false;
                if (res instanceof TLRPC.messages_Chats) {
                    TLRPC.messages_Chats chats = (TLRPC.messages_Chats) res;
                    MessagesController.getInstance(currentAccount).putChats(chats.chats, false);
                    adminedChannels.addAll(chats.chats);
                }
            }));
        }
    }

    public ArrayList<TLObject> getAdmined() {
        loadAdmined();
        ArrayList<TLObject> list = new ArrayList<>();
        if (adminedBots != null) {
            list.addAll(adminedBots);
        }
        if (adminedChannels != null) {
            list.addAll(adminedChannels);
        }
        return list;
    }

    public void getConnectedBot(Context context, long dialogId, long botId, Utilities.Callback<TL_payments.connectedBotStarRef> whenDone) {
        if (whenDone == null) return;
        ChannelConnectedBots bots = connectedBots.get(dialogId);
        if (bots != null) {
            for (int i = 0; i < bots.bots.size(); ++i) {
                if (!bots.bots.get(i).revoked && bots.bots.get(i).bot_id == botId) {
                    whenDone.run(bots.bots.get(i));
                    return;
                }
            }
        }
        final AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
        TL_payments.getConnectedStarRefBot req = new TL_payments.getConnectedStarRefBot();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            progressDialog.dismiss();
            if (res instanceof TL_payments.connectedStarRefBots) {
                TL_payments.connectedStarRefBots r = (TL_payments.connectedStarRefBots) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                for (int i = 0; i < r.connected_bots.size(); ++i) {
                    if (r.connected_bots.get(i).bot_id == botId && !r.connected_bots.get(i).revoked) {
                        whenDone.run(r.connected_bots.get(i));
                        return;
                    }
                }
            }
            whenDone.run(null);
        }));
        progressDialog.setCanCancel(true);
        progressDialog.setOnCancelListener(d -> {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
        });
        progressDialog.showDelayed(200);
    }

}
