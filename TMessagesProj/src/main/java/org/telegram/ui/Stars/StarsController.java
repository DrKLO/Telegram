package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.MediaDataController.calcHash;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams;

import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileRefController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.TopicsController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PaymentFormActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.TON.TONIntroActivity;
import org.telegram.ui.bots.BotWebViewSheet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StarsController {

    public static final String currency = "XTR";

    public static final int PERIOD_MONTHLY = 2592000;
    // test backend only:
    public static final int PERIOD_MINUTE = 60;
    public static final int PERIOD_5MINUTES = 300;

    private static volatile StarsController[][] Instance = new StarsController[2][UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[][] lockObjects = new Object[2][UserConfig.MAX_ACCOUNT_COUNT];
    static {
        for (int a = 0; a < 2; ++a) {
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
                lockObjects[a][i] = new Object();
            }
        }
    }

    public static StarsController getTonInstance(int num) {
        return getInstance(num, true);
    }

    public static StarsController getInstance(int num) {
        return getInstance(num, false);
    }

    public static StarsController getInstance(int num, AmountUtils.Currency currency) {
        return getInstance(num, currency == AmountUtils.Currency.TON);
    }

    public static StarsController getInstance(int num, boolean ton) {
        StarsController localInstance = Instance[ton ? 1 : 0][num];
        if (localInstance == null) {
            synchronized (lockObjects[ton ? 1 : 0][num]) {
                localInstance = Instance[ton ? 1 : 0][num];
                if (localInstance == null) {
                    Instance[ton ? 1 : 0][num] = localInstance = new StarsController(num, ton);
                }
            }
        }
        return localInstance;
    }

    public final int currentAccount;
    public final boolean ton;

    private StarsController(int account, boolean ton) {
        this.currentAccount = account;
        this.ton = ton;
    }

    // ===== STAR BALANCE =====

    private long lastBalanceLoaded;
    private boolean balanceLoading, balanceLoaded;
    @NonNull
    public TL_stars.StarsAmount balance = TL_stars.StarsAmount.ofStars(0);
    public long minus;

    public TL_stars.StarsAmount getBalance() {
        return getBalance(null);
    }

    @NonNull
    public AmountUtils.Amount getBalanceAmount() {
        AmountUtils.Amount amount = AmountUtils.Amount.of(getBalance());
        if (amount == null) {
            amount = AmountUtils.Amount.fromNano(0, ton ? AmountUtils.Currency.TON : AmountUtils.Currency.STARS);
        }

        return amount;
    }

    public long getBalance(boolean withMinus) {
        return getBalance(withMinus, null, false).amount;
    }

    public TL_stars.StarsAmount getBalance(Runnable loaded) {
        return getBalance(true, loaded, false);
    }

    public TL_stars.StarsAmount getBalance(boolean withMinus, Runnable loaded, boolean force) {
        if ((!balanceLoaded || System.currentTimeMillis() - lastBalanceLoaded > 1000 * 60) && !balanceLoading || force) {
            balanceLoading = true;
            TL_stars.TL_payments_getStarsStatus req = new TL_stars.TL_payments_getStarsStatus();
            req.ton = ton;
            req.peer = new TLRPC.TL_inputPeerSelf();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                boolean updatedTransactions = false;
                boolean updatedSubscriptions = false;
                boolean updatedBalance = !balanceLoaded;
                lastBalanceLoaded = System.currentTimeMillis();
                if (res instanceof TL_stars.StarsStatus) {
                    TL_stars.StarsStatus r = (TL_stars.StarsStatus) res;
                    MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                    MessagesController.getInstance(currentAccount).putChats(r.chats, false);

                    if (transactions[ALL_TRANSACTIONS].isEmpty()) {
                        for (TL_stars.StarsTransaction t : r.history) {
                            transactions[ALL_TRANSACTIONS].add(t);
                            transactions[t.amount.amount > 0 ? INCOMING_TRANSACTIONS : OUTGOING_TRANSACTIONS].add(t);
                        }
                        for (int i = 0; i < 3; ++i) {
                            transactionsExist[i] = !transactions[i].isEmpty() || transactionsExist[i];
                            endReached[i] = (r.flags & 1) == 0;
                            if (endReached[i]) {
                                loading[i] = false;
                            }
                            offset[i] = endReached[i] ? null : r.next_offset;
                        }
                        updatedTransactions = true;
                    }

                    if (subscriptions.isEmpty()) {
                        subscriptions.addAll(r.subscriptions);
                        subscriptionsLoading = false;
                        subscriptionsOffset = r.subscriptions_next_offset;
                        subscriptionsEndReached = (r.flags & 4) == 0;
                        updatedSubscriptions = true;
                    }

                    if (this.balance.amount != r.balance.amount) {
                        updatedBalance = true;
                    }
                    this.balance = r.balance;
                    this.minus = 0;
                }
                balanceLoading = false;
                balanceLoaded = true;
                if (updatedBalance) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
                }
                if (updatedTransactions) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starTransactionsLoaded);
                }
                if (updatedSubscriptions) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starSubscriptionsLoaded);
                }

                if (loaded != null) {
                    loaded.run();
                }
            }));
        }
        if (withMinus && minus > 0) {
            AmountUtils.Amount b = AmountUtils.Amount.ofSafe(balance);
            return AmountUtils.Amount.fromDecimal(Math.max(0, b.asDecimal() - minus), b.currency).toTl();
        }
        return balance;
    }

    public boolean canUseTon() {
        if (!ton) {
            return false;
        }
        if (TONIntroActivity.allowTopUp()) {
            return true;
        }
        TL_stars.StarsAmount amount = getBalance();
        return amount.nanos != 0 || amount.amount != 0;
    }

    public void invalidateBalance() {
        balanceLoaded = false;
        getBalance();
        balanceLoaded = true;
    }

    public void invalidateBalance(Runnable loaded) {
        balanceLoaded = false;
        getBalance(false, loaded, true);
        balanceLoaded = true;
    }

    public void updateBalance(TL_stars.StarsAmount balance) {
        if (!this.balance.equals(balance)) {
            this.balance = balance;
            this.minus = 0;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
        } else if (this.minus != 0) {
            this.minus = 0;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
        }
    }

    public boolean balanceAvailable() {
        return balanceLoaded;
    }

    private boolean optionsLoading, optionsLoaded;
    private ArrayList<TL_stars.TL_starsTopupOption> options;
    public ArrayList<TL_stars.TL_starsTopupOption> getOptionsCached() {
        return options;
    }

    public ArrayList<TL_stars.TL_starsTopupOption> getOptions() {
        if (optionsLoading || optionsLoaded) {
            return options;
        }
        optionsLoading = true;
        ConnectionsManager.getInstance(currentAccount).sendRequest(new TL_stars.TL_payments_getStarsTopupOptions(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            ArrayList<TL_stars.TL_starsTopupOption> loadedOptions = new ArrayList<>();
            ArrayList<TL_stars.TL_starsTopupOption> toLoadStorePrice = new ArrayList<>();
            if (res instanceof Vector) {
                for (Object object : ((Vector) res).objects) {
                    if (object instanceof TL_stars.TL_starsTopupOption) {
                        TL_stars.TL_starsTopupOption option = (TL_stars.TL_starsTopupOption) object;
                        loadedOptions.add(option);
                        if (option.store_product != null && !BuildVars.useInvoiceBilling()) {
                            toLoadStorePrice.add(option);
                            option.loadingStorePrice = true;
                        }
                    }
                }
                optionsLoaded = true;
            }
            options = loadedOptions;
            optionsLoading = false;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starOptionsLoaded);
            if (!toLoadStorePrice.isEmpty()) {
                Runnable fetchStorePrices = () -> {
                    ArrayList<QueryProductDetailsParams.Product> productQueries = new ArrayList<>();
                    for (int i = 0; i < toLoadStorePrice.size(); ++i) {
                        productQueries.add(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductType(BillingClient.ProductType.INAPP)
                                .setProductId(toLoadStorePrice.get(i).store_product)
                                .build()
                        );
                    }
                    BillingController.getInstance().queryProductDetails(productQueries, (result, list) -> AndroidUtilities.runOnUIThread(() -> {
                        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                            bulletinError("BILLING_" + BillingController.getResponseCodeString(result.getResponseCode()));
                            return;
                        }
                        if (list != null) {
                            for (int i = 0; i < list.size(); ++i) {
                                ProductDetails productDetails = list.get(i);
                                TL_stars.TL_starsTopupOption option = null;
                                for (int j = 0; j < toLoadStorePrice.size(); ++j) {
                                    if (toLoadStorePrice.get(j).store_product.equals(productDetails.getProductId())) {
                                        option = toLoadStorePrice.get(j);
                                        break;
                                    }
                                }
                                if (option == null) continue;

                                ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
                                if (offerDetails != null) {
                                    option.currency = offerDetails.getPriceCurrencyCode();
                                    option.amount = (long) ((offerDetails.getPriceAmountMicros() / Math.pow(10, 6)) * Math.pow(10, BillingController.getInstance().getCurrencyExp(option.currency)));
                                    option.loadingStorePrice = false;
                                }
                            }
                        }
                        if (options != null) {
                            for (int i = 0; i < options.size(); ++i) {
                                TL_stars.TL_starsTopupOption option = options.get(i);
                                if (option != null && option.loadingStorePrice) {
                                    option.missingStorePrice = true;
                                }
                            }
                        }
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starOptionsLoaded);
                    }));
                };
                if (!BillingController.getInstance().isReady()) {
                    BillingController.getInstance().whenSetuped(fetchStorePrices);
                } else {
                    fetchStorePrices.run();
                }
            }
        }));
        return options;
    }

    private boolean giftOptionsLoading, giftOptionsLoaded;
    private ArrayList<TL_stars.TL_starsGiftOption> giftOptions;
    public ArrayList<TL_stars.TL_starsGiftOption> getGiftOptionsCached() {
        return giftOptions;
    }
    public ArrayList<TL_stars.TL_starsGiftOption> getGiftOptions() {
        if (giftOptionsLoading || giftOptionsLoaded) {
            return giftOptions;
        }
        giftOptionsLoading = true;
        TL_stars.TL_payments_getStarsGiftOptions req = new TL_stars.TL_payments_getStarsGiftOptions();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            ArrayList<TL_stars.TL_starsGiftOption> loadedOptions = new ArrayList<>();
            ArrayList<TL_stars.TL_starsGiftOption> toLoadStorePrice = new ArrayList<>();
            if (res instanceof Vector) {
                for (Object object : ((Vector) res).objects) {
                    if (object instanceof TL_stars.TL_starsGiftOption) {
                        TL_stars.TL_starsGiftOption option = (TL_stars.TL_starsGiftOption) object;
                        loadedOptions.add(option);
                        if (option.store_product != null && !BuildVars.useInvoiceBilling()) {
                            toLoadStorePrice.add(option);
                            option.loadingStorePrice = true;
                        }
                    }
                }
                giftOptionsLoaded = true;
            }
            giftOptions = loadedOptions;
            giftOptionsLoading = false;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starGiftOptionsLoaded);
            if (!toLoadStorePrice.isEmpty()) {
                Runnable fetchStorePrices = () -> {
                    ArrayList<QueryProductDetailsParams.Product> productQueries = new ArrayList<>();
                    for (int i = 0; i < toLoadStorePrice.size(); ++i) {
                        productQueries.add(
                                QueryProductDetailsParams.Product.newBuilder()
                                        .setProductType(BillingClient.ProductType.INAPP)
                                        .setProductId(toLoadStorePrice.get(i).store_product)
                                        .build()
                        );
                    }
                    BillingController.getInstance().queryProductDetails(productQueries, (result, list) -> AndroidUtilities.runOnUIThread(() -> {
                        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                            bulletinError("BILLING_" + BillingController.getResponseCodeString(result.getResponseCode()));
                            return;
                        }
                        if (list != null) {
                            for (int i = 0; i < list.size(); ++i) {
                                ProductDetails productDetails = list.get(i);
                                TL_stars.TL_starsGiftOption option = null;
                                for (int j = 0; j < toLoadStorePrice.size(); ++j) {
                                    if (toLoadStorePrice.get(j).store_product.equals(productDetails.getProductId())) {
                                        option = toLoadStorePrice.get(j);
                                        break;
                                    }
                                }
                                if (option == null) continue;

                                ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
                                if (offerDetails != null) {
                                    option.currency = offerDetails.getPriceCurrencyCode();
                                    option.amount = (long) ((offerDetails.getPriceAmountMicros() / Math.pow(10, 6)) * Math.pow(10, BillingController.getInstance().getCurrencyExp(option.currency)));
                                    option.loadingStorePrice = false;
                                }
                            }
                        }
                        if (giftOptions != null) {
                            for (int i = 0; i < giftOptions.size(); ++i) {
                                TL_stars.TL_starsGiftOption option = giftOptions.get(i);
                                if (option != null && option.loadingStorePrice) {
                                    option.missingStorePrice = true;
                                }
                            }
                        }
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starGiftOptionsLoaded);
                    }));
                };
                if (!BillingController.getInstance().isReady()) {
                    BillingController.getInstance().whenSetuped(fetchStorePrices);
                } else {
                    fetchStorePrices.run();
                }
            }
        }));
        return giftOptions;
    }

    private boolean giveawayOptionsLoading, giveawayOptionsLoaded;
    private ArrayList<TL_stars.TL_starsGiveawayOption> giveawayOptions;
    public ArrayList<TL_stars.TL_starsGiveawayOption> getGiveawayOptionsCached() {
        return giveawayOptions;
    }
    public ArrayList<TL_stars.TL_starsGiveawayOption> getGiveawayOptions() {
        if (giveawayOptionsLoading || giveawayOptionsLoaded) {
            return giveawayOptions;
        }
        giveawayOptionsLoading = true;
        TL_stars.TL_payments_getStarsGiveawayOptions req = new TL_stars.TL_payments_getStarsGiveawayOptions();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            ArrayList<TL_stars.TL_starsGiveawayOption> loadedOptions = new ArrayList<>();
            ArrayList<TL_stars.TL_starsGiveawayOption> toLoadStorePrice = new ArrayList<>();
            if (res instanceof Vector) {
                for (Object object : ((Vector) res).objects) {
                    if (object instanceof TL_stars.TL_starsGiveawayOption) {
                        TL_stars.TL_starsGiveawayOption option = (TL_stars.TL_starsGiveawayOption) object;
                        loadedOptions.add(option);
                        if (option.store_product != null && !BuildVars.useInvoiceBilling()) {
                            toLoadStorePrice.add(option);
                            option.loadingStorePrice = true;
                        }
                    }
                }
                giveawayOptionsLoaded = true;
            }
            giveawayOptions = loadedOptions;
            giveawayOptionsLoading = false;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starGiveawayOptionsLoaded);
            if (!toLoadStorePrice.isEmpty()) {
                Runnable fetchStorePrices = () -> {
                    ArrayList<QueryProductDetailsParams.Product> productQueries = new ArrayList<>();
                    for (int i = 0; i < toLoadStorePrice.size(); ++i) {
                        productQueries.add(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductType(BillingClient.ProductType.INAPP)
                                .setProductId(toLoadStorePrice.get(i).store_product)
                                .build()
                        );
                    }
                    BillingController.getInstance().queryProductDetails(productQueries, (result, list) -> AndroidUtilities.runOnUIThread(() -> {
                        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                            bulletinError("BILLING_" + BillingController.getResponseCodeString(result.getResponseCode()));
                            return;
                        }
                        if (list != null) {
                            for (int i = 0; i < list.size(); ++i) {
                                ProductDetails productDetails = list.get(i);
                                TL_stars.TL_starsGiveawayOption option = null;
                                for (int j = 0; j < toLoadStorePrice.size(); ++j) {
                                    if (toLoadStorePrice.get(j).store_product.equals(productDetails.getProductId())) {
                                        option = toLoadStorePrice.get(j);
                                        break;
                                    }
                                }
                                if (option == null) continue;

                                ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
                                if (offerDetails != null) {
                                    option.currency = offerDetails.getPriceCurrencyCode();
                                    option.amount = (long) ((offerDetails.getPriceAmountMicros() / Math.pow(10, 6)) * Math.pow(10, BillingController.getInstance().getCurrencyExp(option.currency)));
                                    option.loadingStorePrice = false;
                                }
                            }
                        }
                        if (giveawayOptions != null) {
                            for (int i = 0; i < giveawayOptions.size(); ++i) {
                                TL_stars.TL_starsGiveawayOption option = giveawayOptions.get(i);
                                if (option != null && option.loadingStorePrice) {
                                    option.missingStorePrice = true;
                                }
                            }
                        }
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starGiveawayOptionsLoaded);
                    }));
                };
                if (!BillingController.getInstance().isReady()) {
                    BillingController.getInstance().whenSetuped(fetchStorePrices);
                } else {
                    fetchStorePrices.run();
                }
            }
        }));
        return giveawayOptions;
    }

    private void bulletinError(TLRPC.TL_error err, String str) {
        bulletinError(err == null ? str : err.text);
    }
    private void bulletinError(String err) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        BulletinFactory b = fragment != null && fragment.visibleDialog == null ? BulletinFactory.of(fragment) : BulletinFactory.global();
        b.createSimpleBulletin(R.raw.error, formatString(R.string.UnknownErrorCode, err)).show();
    }



    // ===== STAR TRANSACTIONS =====

    public static final int ALL_TRANSACTIONS = 0;
    public static final int INCOMING_TRANSACTIONS = 1;
    public static final int OUTGOING_TRANSACTIONS = 2;

    public final ArrayList<TL_stars.StarsTransaction>[] transactions = new ArrayList[] { new ArrayList<>(), new ArrayList<>(), new ArrayList<>() };
    public final boolean[] transactionsExist = new boolean[3];
    private final String[] offset = new String[3];
    private final boolean[] loading = new boolean[3];
    private final boolean[] endReached = new boolean[3];

    public void invalidateTransactions(boolean load) {
        for (int i = 0; i < 3; ++i) {
            if (loading[i]) continue;
            transactions[i].clear();
            offset[i] = null;
            loading[i] = false;
            endReached[i] = false;
            if (load)
                loadTransactions(i);
        }
    }
    public void preloadTransactions() {
        for (int i = 0; i < 3; ++i) {
            if (!loading[i] && !endReached[i] && offset[i] == null) {
                loadTransactions(i);
            }
        }
    }
    public void loadTransactions(int type) {
        if (loading[type] || endReached[type]) {
            return;
        }

        loading[type] = true;

        TL_stars.TL_payments_getStarsTransactions req = new TL_stars.TL_payments_getStarsTransactions();
        req.ton = ton;
        req.peer = new TLRPC.TL_inputPeerSelf();
        req.inbound = type == INCOMING_TRANSACTIONS;
        req.outbound = type == OUTGOING_TRANSACTIONS;
        req.offset = offset[type];
        if (req.offset == null) {
            req.offset = "";
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            loading[type] = false;
            if (res instanceof TL_stars.StarsStatus) {
                TL_stars.StarsStatus r = (TL_stars.StarsStatus) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);

                transactions[type].addAll(r.history);
                transactionsExist[type] = !transactions[type].isEmpty() || transactionsExist[type];
                endReached[type] = (r.flags & 1) == 0;
                offset[type] = endReached[type] ? null : r.next_offset;

                updateBalance(r.balance);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starTransactionsLoaded);
            }
        }));
    }
    public boolean isLoadingTransactions(int type) {
        return loading[type];
    }
    public boolean didFullyLoadTransactions(int type) {
        return endReached[type];
    }
    public boolean hasTransactions() {
        return hasTransactions(ALL_TRANSACTIONS);
    }
    public boolean hasTransactions(int type) {
        return balanceAvailable() && !transactions[type].isEmpty();
    }



    // ===== STAR SUBSCRIPTIONS =====

    public final ArrayList<TL_stars.StarsSubscription> subscriptions = new ArrayList<>();
    public String subscriptionsOffset;
    public boolean subscriptionsLoading, subscriptionsEndReached;

    public boolean hasSubscriptions() {
        return balanceAvailable() && !subscriptions.isEmpty();
    }

    public void invalidateSubscriptions(boolean load) {
        if (subscriptionsLoading) return;
        subscriptions.clear();
        subscriptionsOffset = null;
        subscriptionsLoading = false;
        subscriptionsEndReached = false;
        if (load) loadSubscriptions();
    }

    public void loadSubscriptions() {
        if (ton || subscriptionsLoading || subscriptionsEndReached) return;
        subscriptionsLoading = true;
        final TL_stars.TL_getStarsSubscriptions req = new TL_stars.TL_getStarsSubscriptions();
        req.peer = new TLRPC.TL_inputPeerSelf();
        req.offset = subscriptionsOffset;
        if (req.offset == null) {
            req.offset = "";
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            subscriptionsLoading = false;
            if (res instanceof TL_stars.StarsStatus) {
                TL_stars.StarsStatus r = (TL_stars.StarsStatus) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);

                subscriptions.addAll(r.subscriptions);
                subscriptionsEndReached = (r.flags & 4) == 0;
                subscriptionsOffset = r.subscriptions_next_offset;

                updateBalance(r.balance);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starSubscriptionsLoaded);
            }
        }));
    }
    public boolean isLoadingSubscriptions() {
        return subscriptionsLoading;
    }
    public boolean didFullyLoadSubscriptions() {
        return subscriptionsEndReached;
    }

    public final ArrayList<TL_stars.StarsSubscription> insufficientSubscriptions = new ArrayList<>();
    private boolean insufficientSubscriptionsLoading;
    public void loadInsufficientSubscriptions() {
        if (insufficientSubscriptionsLoading) return;
        insufficientSubscriptionsLoading = true;
        TL_stars.TL_getStarsSubscriptions req = new TL_stars.TL_getStarsSubscriptions();
        req.peer = new TLRPC.TL_inputPeerSelf();
        req.missing_balance = true;
        req.offset = "";
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            insufficientSubscriptionsLoading = false;
            if (res instanceof TL_stars.StarsStatus) {
                TL_stars.StarsStatus r = (TL_stars.StarsStatus) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                insufficientSubscriptions.addAll(r.subscriptions);
                updateBalance(r.balance);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starSubscriptionsLoaded);
            }
        }));
    }
    public void invalidateInsufficientSubscriptions(boolean load) {
        if (insufficientSubscriptionsLoading) return;
        insufficientSubscriptions.clear();
        insufficientSubscriptionsLoading = false;
        if (load) loadInsufficientSubscriptions();
    }
    public boolean hasInsufficientSubscriptions() {
        return !insufficientSubscriptions.isEmpty();
    }


    public Theme.ResourcesProvider getResourceProvider() {
        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (lastFragment != null) {
            return lastFragment.getResourceProvider();
        }
        return null;
    }

    public void showStarsTopup(Activity activity, long amount, String purpose) {
        if (!balanceAvailable()) {
            getBalance(() -> {
                showStarsTopupInternal(activity, amount, purpose);
            });
            return;
        }
        showStarsTopupInternal(activity, amount, purpose);
    }

    private void showStarsTopupInternal(Activity activity, long amount, String purpose) {
        if (getBalance().amount >= amount || amount <= 0) {
            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment == null) return;
            BulletinFactory.of(lastFragment).createSimpleBulletin(R.raw.stars_topup, getString(R.string.StarsTopupLinkEnough), getString(R.string.StarsTopupLinkTopupAnyway), () -> {
                BaseFragment lastFragment2 = LaunchActivity.getSafeLastFragment();
                if (lastFragment2 == null) return;
                lastFragment2.presentFragment(new StarsIntroActivity());
            }).setDuration(Bulletin.DURATION_PROLONG).show(true);
            return;
        }
        new StarsIntroActivity.StarsNeededSheet(activity, null, amount, StarsIntroActivity.StarsNeededSheet.TYPE_LINK, purpose, () -> {

        }, 0).show();
    }

    public void buy(
        Activity activity,
        TL_stars.TL_starsTopupOption option,
        Utilities.Callback2<Boolean, String> whenDone,
        TLRPC.InputPeer purposePeer
    ) {
        if (activity == null) {
            return;
        }

        if (!MessagesController.getInstance(currentAccount).starsPurchaseAvailable()) {
            BaseFragment lastFragment = LaunchActivity.getLastFragment();
            if (lastFragment != null && lastFragment.getContext() != null) {
                showNoSupportDialog(lastFragment.getContext(), lastFragment.getResourceProvider());
            } else {
                showNoSupportDialog(activity, null);
            }
            return;
        }

        if (BuildVars.useInvoiceBilling() || !BillingController.getInstance().isReady()) {
            final TLRPC.TL_inputStorePaymentStarsTopup purpose = new TLRPC.TL_inputStorePaymentStarsTopup();
            purpose.stars = option.stars;
            purpose.amount = option.amount;
            purpose.currency = option.currency;
            purpose.spend_purpose_peer = purposePeer;

            TLRPC.TL_inputInvoiceStars invoice = new TLRPC.TL_inputInvoiceStars();
            invoice.purpose = purpose;

            TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
            final JSONObject themeParams = BotWebViewSheet.makeThemeParams(getResourceProvider());
            if (themeParams != null) {
                req.theme_params = new TLRPC.TL_dataJSON();
                req.theme_params.data = themeParams.toString();
                req.flags |= 1;
            }
            req.invoice = invoice;

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error != null) {
                    if (whenDone != null) {
                        whenDone.run(false, error.text);
                    }
                    return;
                }
                PaymentFormActivity paymentFormActivity = null;
                if (response instanceof TLRPC.PaymentForm) {
                    TLRPC.PaymentForm form = (TLRPC.PaymentForm) response;
                    form.invoice.recurring = true;
                    MessagesController.getInstance(currentAccount).putUsers(form.users, false);
                    paymentFormActivity = new PaymentFormActivity(form, invoice, null);
                } else if (response instanceof TLRPC.PaymentReceipt) {
                    paymentFormActivity = new PaymentFormActivity((TLRPC.PaymentReceipt) response);
                }
                if (paymentFormActivity != null) {
                    paymentFormActivity.setPaymentFormCallback(status -> {
                        if (status == PaymentFormActivity.InvoiceStatus.PAID) {
                            if (whenDone != null) {
                                whenDone.run(true, null);
                            }
                        } else if (status != PaymentFormActivity.InvoiceStatus.PENDING) {
                            if (whenDone != null) {
                                whenDone.run(false, null);
                            }
                        }
                    });
                    BaseFragment lastFragment = LaunchActivity.getLastFragment();
                    if (lastFragment == null) return;
                    if (AndroidUtilities.hasDialogOnTop(lastFragment)) {
                        BaseFragment.BottomSheetParams bottomSheetParams = new BaseFragment.BottomSheetParams();
                        bottomSheetParams.transitionFromLeft = true;
                        bottomSheetParams.allowNestedScroll = false;
                        lastFragment.showAsSheet(paymentFormActivity, bottomSheetParams);
                    } else {
                        lastFragment.presentFragment(paymentFormActivity);
                    }
                } else {
                    if (whenDone != null) {
                        whenDone.run(false, "UNKNOWN_RESPONSE");
                    }
                }
            }));

            return;
        }

        final TLRPC.TL_inputStorePaymentStarsTopup payload = new TLRPC.TL_inputStorePaymentStarsTopup();
        payload.stars = option.stars;
        payload.currency = option.currency;
        payload.amount = option.amount;
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .setProductId(option.store_product)
                .build();
        FileLog.d("StarsController.buy starts queryProductDetails");
        BillingController.getInstance().queryProductDetails(Arrays.asList(product), (billingResult, list) -> AndroidUtilities.runOnUIThread(() -> {
            if (list.isEmpty()) {
                FileLog.d("StarsController.buy queryProductDetails done: no products");
                AndroidUtilities.runOnUIThread(() -> whenDone.run(false, "PRODUCT_NOT_FOUND"));
                return;
            }

            ProductDetails productDetails = list.get(0);
            ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
            if (offerDetails == null) {
                FileLog.d("StarsController.buy queryProductDetails done: no details");
                AndroidUtilities.runOnUIThread(() -> whenDone.run(false, "PRODUCT_NO_ONETIME_OFFER_DETAILS"));
                return;
            }

            payload.currency = offerDetails.getPriceCurrencyCode();
            payload.amount = (long) ((offerDetails.getPriceAmountMicros() / Math.pow(10, 6)) * Math.pow(10, BillingController.getInstance().getCurrencyExp(option.currency)));

            BillingController.getInstance().addResultListener(productDetails.getProductId(), billingResult1 -> {
                final boolean success = billingResult1.getResponseCode() == BillingClient.BillingResponseCode.OK;
                final String error = success ? null : BillingController.getResponseCodeString(billingResult1.getResponseCode());
                FileLog.d("StarsController.buy onResult " + success + " " + error);
                AndroidUtilities.runOnUIThread(() -> whenDone.run(success, error));
            });
            BillingController.getInstance().setOnCanceled(() -> {
                FileLog.d("StarsController.buy onCanceled");
                AndroidUtilities.runOnUIThread(() -> whenDone.run(false, null));
            });
            FileLog.d("StarsController.buy launchBillingFlow");
            BillingController.getInstance().launchBillingFlow(
                    activity, AccountInstance.getInstance(UserConfig.selectedAccount), payload,
                    Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(list.get(0))
                            .build())
            );
        }));
    }

    public void buyGift(Activity activity, TL_stars.TL_starsGiftOption option, long user_id, Utilities.Callback2<Boolean, String> whenDone) {
        if (activity == null) {
            return;
        }

        if (!MessagesController.getInstance(currentAccount).starsPurchaseAvailable()) {
            BaseFragment lastFragment = LaunchActivity.getLastFragment();
            if (lastFragment != null && lastFragment.getContext() != null) {
                showNoSupportDialog(lastFragment.getContext(), lastFragment.getResourceProvider());
            } else {
                showNoSupportDialog(activity, null);
            }
            return;
        }

        if (BuildVars.useInvoiceBilling() || !BillingController.getInstance().isReady()) {
            TLRPC.TL_inputStorePaymentStarsGift purpose = new TLRPC.TL_inputStorePaymentStarsGift();
            purpose.stars = option.stars;
            purpose.amount = option.amount;
            purpose.currency = option.currency;
            purpose.user_id = MessagesController.getInstance(currentAccount).getInputUser(user_id);

            TLRPC.TL_inputInvoiceStars invoice = new TLRPC.TL_inputInvoiceStars();
            invoice.purpose = purpose;

            TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
            final JSONObject themeParams = BotWebViewSheet.makeThemeParams(getResourceProvider());
            if (themeParams != null) {
                req.theme_params = new TLRPC.TL_dataJSON();
                req.theme_params.data = themeParams.toString();
                req.flags |= 1;
            }
            req.invoice = invoice;

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error != null) {
                    if (whenDone != null) {
                        whenDone.run(false, error.text);
                    }
                    return;
                }
                PaymentFormActivity paymentFormActivity = null;
                if (response instanceof TLRPC.PaymentForm) {
                    TLRPC.PaymentForm form = (TLRPC.PaymentForm) response;
                    form.invoice.recurring = true;
                    MessagesController.getInstance(currentAccount).putUsers(form.users, false);
                    paymentFormActivity = new PaymentFormActivity(form, invoice, null);
                } else if (response instanceof TLRPC.PaymentReceipt) {
                    paymentFormActivity = new PaymentFormActivity((TLRPC.PaymentReceipt) response);
                }
                if (paymentFormActivity != null) {
                    paymentFormActivity.setPaymentFormCallback(status -> {
                        if (status == PaymentFormActivity.InvoiceStatus.PAID) {
                            if (whenDone != null) {
                                whenDone.run(true, null);
                            }
                        } else if (status != PaymentFormActivity.InvoiceStatus.PENDING) {
                            if (whenDone != null) {
                                whenDone.run(false, null);
                            }
                        }
                    });
                    BaseFragment lastFragment = LaunchActivity.getLastFragment();
                    if (lastFragment == null) return;
                    if (AndroidUtilities.hasDialogOnTop(lastFragment)) {
                        BaseFragment.BottomSheetParams bottomSheetParams = new BaseFragment.BottomSheetParams();
                        bottomSheetParams.transitionFromLeft = true;
                        bottomSheetParams.allowNestedScroll = false;
                        lastFragment.showAsSheet(paymentFormActivity, bottomSheetParams);
                    } else {
                        lastFragment.presentFragment(paymentFormActivity);
                    }
                } else {
                    if (whenDone != null) {
                        whenDone.run(false, "UNKNOWN_RESPONSE");
                    }
                }
            }));

            return;
        }

        TLRPC.TL_inputStorePaymentStarsGift payload = new TLRPC.TL_inputStorePaymentStarsGift();
        payload.stars = option.stars;
        payload.currency = option.currency;
        payload.amount = option.amount;
        payload.user_id = MessagesController.getInstance(currentAccount).getInputUser(user_id);

        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .setProductId(option.store_product)
                .build();
        BillingController.getInstance().queryProductDetails(Arrays.asList(product), (billingResult, list) -> AndroidUtilities.runOnUIThread(() -> {
            if (list.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> whenDone.run(false, "PRODUCT_NOT_FOUND"));
                return;
            }

            ProductDetails productDetails = list.get(0);
            ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
            if (offerDetails == null) {
                AndroidUtilities.runOnUIThread(() -> whenDone.run(false, "PRODUCT_NO_ONETIME_OFFER_DETAILS"));
                return;
            }

            payload.currency = offerDetails.getPriceCurrencyCode();
            payload.amount = (long) ((offerDetails.getPriceAmountMicros() / Math.pow(10, 6)) * Math.pow(10, BillingController.getInstance().getCurrencyExp(option.currency)));

            TLRPC.TL_payments_canPurchaseStore checkReq = new TLRPC.TL_payments_canPurchaseStore();
            checkReq.purpose = payload;
            ConnectionsManager.getInstance(currentAccount).sendRequest(checkReq, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.TL_boolTrue) {
                    BillingController.getInstance().addResultListener(productDetails.getProductId(), billingResult1 -> {
                        final boolean success = billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK;
                        final String error = success ? null : BillingController.getResponseCodeString(billingResult.getResponseCode());
                        AndroidUtilities.runOnUIThread(() -> whenDone.run(success, error));
                    });
                    BillingController.getInstance().setOnCanceled(() -> {
                        AndroidUtilities.runOnUIThread(() -> whenDone.run(false, null));
                    });
                    BillingController.getInstance().launchBillingFlow(
                            activity, AccountInstance.getInstance(UserConfig.selectedAccount), payload,
                            Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(list.get(0))
                                    .build())
                    );
                } else if (res instanceof TLRPC.TL_boolFalse) {
                    if (whenDone != null) {
                        whenDone.run(false, "PURCHASE_FORBIDDEN");
                    }
                } else {
                    if (whenDone != null) {
                        whenDone.run(false, err != null ? err.text : "SERVER_ERROR");
                    }
                }
            }));
        }));
    }

    public void buyGiveaway(
            Activity activity,
            TLRPC.Chat chat, List<TLObject> chats,
            TL_stars.TL_starsGiveawayOption option, int users,
            List<TLObject> countries,
            int date,
            boolean winnersVisible,
            boolean onlyNewSubscribers,
            boolean withAdditionPrize, String prizeDescription,
            Utilities.Callback2<Boolean, String> whenDone
    ) {
        if (activity == null) {
            return;
        }

        if (!MessagesController.getInstance(currentAccount).starsPurchaseAvailable()) {
            BaseFragment lastFragment = LaunchActivity.getLastFragment();
            if (lastFragment != null && lastFragment.getContext() != null) {
                showNoSupportDialog(lastFragment.getContext(), lastFragment.getResourceProvider());
            } else {
                showNoSupportDialog(activity, null);
            }
            return;
        }

        TLRPC.TL_inputStorePaymentStarsGiveaway payload = new TLRPC.TL_inputStorePaymentStarsGiveaway();
        payload.only_new_subscribers = onlyNewSubscribers;
        payload.winners_are_visible = winnersVisible;
        payload.stars = option.stars;
        payload.boost_peer = MessagesController.getInstance(currentAccount).getInputPeer(chat);
        if (chats != null && !chats.isEmpty()) {
            payload.flags |= 2;
            for (TLObject obj : chats) {
                payload.additional_peers.add(MessagesController.getInstance(currentAccount).getInputPeer(obj));
            }
        }
        for (TLObject object : countries) {
            TLRPC.TL_help_country country = (TLRPC.TL_help_country) object;
            payload.countries_iso2.add(country.iso2);
        }
        if (!payload.countries_iso2.isEmpty()) {
            payload.flags |= 4;
        }
        if (withAdditionPrize) {
            payload.flags |= 16;
            payload.prize_description = prizeDescription;
        }
        payload.random_id = SendMessagesHelper.getInstance(currentAccount).getNextRandomId();
        payload.until_date = date;
        payload.currency = option.currency;
        payload.amount = option.amount;
        payload.users = users;

        if (BuildVars.useInvoiceBilling() || !BillingController.getInstance().isReady() || option.store_product == null) {

            TLRPC.TL_inputInvoiceStars invoice = new TLRPC.TL_inputInvoiceStars();
            invoice.purpose = payload;

            TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
            final JSONObject themeParams = BotWebViewSheet.makeThemeParams(getResourceProvider());
            if (themeParams != null) {
                req.theme_params = new TLRPC.TL_dataJSON();
                req.theme_params.data = themeParams.toString();
                req.flags |= 1;
            }
            req.invoice = invoice;

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error != null) {
                    if (whenDone != null) {
                        whenDone.run(false, error.text);
                    }
                    return;
                }
                PaymentFormActivity paymentFormActivity = null;
                if (response instanceof TLRPC.PaymentForm) {
                    TLRPC.PaymentForm form = (TLRPC.PaymentForm) response;
                    form.invoice.recurring = true;
                    MessagesController.getInstance(currentAccount).putUsers(form.users, false);
                    paymentFormActivity = new PaymentFormActivity(form, invoice, null);
                } else if (response instanceof TLRPC.PaymentReceipt) {
                    paymentFormActivity = new PaymentFormActivity((TLRPC.PaymentReceipt) response);
                }
                if (paymentFormActivity != null) {
                    paymentFormActivity.setPaymentFormCallback(status -> {
                        if (status == PaymentFormActivity.InvoiceStatus.PAID) {
                            if (whenDone != null) {
                                whenDone.run(true, null);
                            }
                        } else if (status != PaymentFormActivity.InvoiceStatus.PENDING) {
                            if (whenDone != null) {
                                whenDone.run(false, null);
                            }
                        }
                    });
                    BaseFragment lastFragment = LaunchActivity.getLastFragment();
                    if (lastFragment == null) return;
                    if (AndroidUtilities.hasDialogOnTop(lastFragment)) {
                        BaseFragment.BottomSheetParams bottomSheetParams = new BaseFragment.BottomSheetParams();
                        bottomSheetParams.transitionFromLeft = true;
                        bottomSheetParams.allowNestedScroll = false;
                        lastFragment.showAsSheet(paymentFormActivity, bottomSheetParams);
                    } else {
                        lastFragment.presentFragment(paymentFormActivity);
                    }
                } else {
                    if (whenDone != null) {
                        whenDone.run(false, "UNKNOWN_RESPONSE");
                    }
                }
            }));

            return;
        }

        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .setProductId(option.store_product)
                .build();
        BillingController.getInstance().queryProductDetails(Arrays.asList(product), (billingResult, list) -> AndroidUtilities.runOnUIThread(() -> {
            if (list.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> whenDone.run(false, "PRODUCT_NOT_FOUND"));
                return;
            }

            ProductDetails productDetails = list.get(0);
            ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
            if (offerDetails == null) {
                AndroidUtilities.runOnUIThread(() -> whenDone.run(false, "PRODUCT_NO_ONETIME_OFFER_DETAILS"));
                return;
            }

            TLRPC.TL_payments_canPurchaseStore checkReq = new TLRPC.TL_payments_canPurchaseStore();
            checkReq.purpose = payload;
            ConnectionsManager.getInstance(currentAccount).sendRequest(checkReq, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.TL_boolTrue) {
                    BillingController.getInstance().addResultListener(productDetails.getProductId(), billingResult1 -> {
                        final boolean success = billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK;
                        final String error = success ? null : BillingController.getResponseCodeString(billingResult.getResponseCode());
                        AndroidUtilities.runOnUIThread(() -> whenDone.run(success, error));
                    });
                    BillingController.getInstance().setOnCanceled(() -> {
                        AndroidUtilities.runOnUIThread(() -> whenDone.run(false, null));
                    });
                    BillingController.getInstance().launchBillingFlow(
                            activity, AccountInstance.getInstance(UserConfig.selectedAccount), payload,
                            Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(list.get(0))
                                    .build())
                    );
                } else if (res instanceof TLRPC.TL_boolFalse) {
                    if (whenDone != null) {
                        whenDone.run(false, "PURCHASE_FORBIDDEN");
                    }
                } else {
                    if (whenDone != null) {
                        whenDone.run(false, err != null ? err.text : "SERVER_ERROR");
                    }
                }
            }));
        }));
    }

    public Runnable pay(MessageObject messageObject, Runnable whenShown) {
        final Context context = LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
        final Theme.ResourcesProvider resourcesProvider = getResourceProvider();

        if (messageObject == null || context == null) {
            return null;
        }

//        if (!(MessageObject.getMedia(messageObject) instanceof TLRPC.TL_messageMediaInvoice)) {
//            return;
//        }

        long did = messageObject.getDialogId();
        int msg_id = messageObject.getId();
//        if (messageObject.messageOwner != null && messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.from_id != null) {
//            did = DialogObject.getPeerDialogId(messageObject.messageOwner.fwd_from.from_id);
//        }

        TLRPC.TL_inputInvoiceMessage inputInvoice = new TLRPC.TL_inputInvoiceMessage();
        inputInvoice.peer = MessagesController.getInstance(currentAccount).getInputPeer(did);
        inputInvoice.msg_id = msg_id;

        TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
        final JSONObject themeParams = BotWebViewSheet.makeThemeParams(resourcesProvider);
        if (themeParams != null) {
            req.theme_params = new TLRPC.TL_dataJSON();
            req.theme_params.data = themeParams.toString();
            req.flags |= 1;
        }
        req.invoice = inputInvoice;

        final int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.TL_payments_paymentFormStars) {
                openPaymentForm(messageObject, inputInvoice, (TLRPC.TL_payments_paymentFormStars) res, whenShown, null);
            } else {
                bulletinError(err, "NO_PAYMENT_FORM");
            }
            if (whenShown != null) {
                whenShown.run();
            }
        }));

        return () -> ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
    }

    private boolean paymentFormOpened;

    public void openPaymentForm(MessageObject messageObject, TLRPC.InputInvoice inputInvoice, TLRPC.TL_payments_paymentFormStars form, Runnable whenShown, Utilities.Callback<String> whenAllDone) {
        if (form == null || form.invoice == null || paymentFormOpened) return;
        MessagesController.getInstance(currentAccount).putUsers(form.users, false);

        final Context context = LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
        final Theme.ResourcesProvider resourcesProvider = getResourceProvider();

        if (context == null) return;

        if (!balanceAvailable()) {
            getBalance(() -> {
                if (!balanceAvailable()) {
                    bulletinError("NO_BALANCE");
                    if (whenShown != null) {
                        whenShown.run();
                    }
                    return;
                }
                openPaymentForm(messageObject, inputInvoice, form, whenShown, whenAllDone);
            });
            return;
        }

        long _stars = 0;
        for (TLRPC.TL_labeledPrice price : form.invoice.prices) {
            _stars += price.amount;
        }
        final long stars = _stars;
        final long dialogId = messageObject != null && messageObject.type == MessageObject.TYPE_PAID_MEDIA ? (
            (messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.from_id != null) ?
                DialogObject.getPeerDialogId(messageObject.messageOwner.fwd_from.from_id) :
                messageObject.getDialogId()
        ) : form.bot_id;
        final String bot;
        final boolean isBot, isBiz;
        if (dialogId >= 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            bot = UserObject.getUserName(user);
            isBot = UserObject.isBot(user);
            isBiz = !UserObject.isBot(user);
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            bot = chat == null ? "" : chat.title;
            isBot = false;
            isBiz = false;
        }
        final String product = form.title;

        if (whenShown != null) {
            whenShown.run();
        }

        final int subscription_period = form.invoice.subscription_period;
        final boolean[] allDone = new boolean[] { false };
        StarsIntroActivity.openConfirmPurchaseSheet(context, resourcesProvider, currentAccount, messageObject, dialogId, product, stars, form.photo, subscription_period, whenDone -> {
            if (balance.amount < stars) {
                if (!MessagesController.getInstance(currentAccount).starsPurchaseAvailable()) {
                    paymentFormOpened = false;
                    if (whenDone != null) {
                        whenDone.run(false);
                    }
                    if (!allDone[0] && whenAllDone != null) {
                        whenAllDone.run("cancelled");
                        allDone[0] = true;
                    }
                    showNoSupportDialog(context, resourcesProvider);
                    return;
                }
                final boolean[] purchased = new boolean[] { false };
                StarsIntroActivity.StarsNeededSheet sheet = new StarsIntroActivity.StarsNeededSheet(context, resourcesProvider, stars, isBiz ? StarsIntroActivity.StarsNeededSheet.TYPE_BIZ : StarsIntroActivity.StarsNeededSheet.TYPE_BOT, bot, () -> {
                    purchased[0] = true;
                    payAfterConfirmed(messageObject, inputInvoice, form, success -> {
                        allDone[0] = true;
                        if (subscription_period > 0) {
                            invalidateSubscriptions(true);
                        }
                        if (whenAllDone != null) {
                            whenAllDone.run(success ? "paid" : "failed");
                        }
                        if (whenDone != null) {
                            whenDone.run(true);
                        }
                    });
                }, dialogId);
                sheet.setOnDismissListener(d -> {
                    if (whenDone != null && !purchased[0]) {
                        whenDone.run(false);
                        paymentFormOpened = false;
                        if (!allDone[0] && whenAllDone != null) {
                            whenAllDone.run("cancelled");
                            allDone[0] = true;
                        }
                    }
                });
                sheet.show();
            } else {
                payAfterConfirmed(messageObject, inputInvoice, form, success -> {
                    if (subscription_period > 0) {
                        invalidateSubscriptions(true);
                    }
                    if (whenDone != null) {
                        whenDone.run(true);
                    }
                    allDone[0] = true;
                    if (whenAllDone != null) {
                        whenAllDone.run(success ? "paid" : "failed");
                    }
                });
            }
        }, () -> {
            paymentFormOpened = false;
            if (!allDone[0] && whenAllDone != null) {
                whenAllDone.run("cancelled");
                allDone[0] = true;
            }
        });
    }

    public void subscribeTo(String hash, TLRPC.ChatInvite chatInvite, Utilities.Callback2<String, Long> whenAllDone) {
        if (chatInvite == null || chatInvite.subscription_pricing == null) return;

        final Context context = LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
        final Theme.ResourcesProvider resourcesProvider = getResourceProvider();
        final long stars = chatInvite.subscription_pricing.amount;

        if (context == null) return;

        final int currentAccount = UserConfig.selectedAccount;

        final boolean[] allDone = new boolean[] { false };
        StarsIntroActivity.openStarsChannelInviteSheet(context, resourcesProvider, currentAccount, chatInvite, whenDone -> {
            if (balance.amount < stars) {
                if (!MessagesController.getInstance(currentAccount).starsPurchaseAvailable()) {
                    paymentFormOpened = false;
                    if (whenDone != null) {
                        whenDone.run(false);
                    }
                    if (!allDone[0] && whenAllDone != null) {
                        whenAllDone.run("cancelled", 0L);
                        allDone[0] = true;
                    }
                    showNoSupportDialog(context, resourcesProvider);
                    return;
                }
                final boolean[] purchased = new boolean[] { false };
                StarsIntroActivity.StarsNeededSheet sheet = new StarsIntroActivity.StarsNeededSheet(context, resourcesProvider, stars, StarsIntroActivity.StarsNeededSheet.TYPE_SUBSCRIPTION_BUY, chatInvite.title, () -> {
                    purchased[0] = true;
                    payAfterConfirmed(hash, chatInvite, (did, success) -> {
                        allDone[0] = true;
                        if (whenAllDone != null) {
                            whenAllDone.run(success ? "paid" : "failed", did);
                        }
                        if (whenDone != null) {
                            whenDone.run(true);
                        }
                    });
                }, 0); // TODO: purpose peer for chat invite?
                sheet.setOnDismissListener(d -> {
                    if (whenDone != null && !purchased[0]) {
                        whenDone.run(false);
                        paymentFormOpened = false;
                        if (!allDone[0] && whenAllDone != null) {
                            whenAllDone.run("cancelled", 0L);
                            allDone[0] = true;
                        }
                    }
                });
                sheet.show();
            } else {
                payAfterConfirmed(hash, chatInvite, (did, success) -> {
                    if (whenDone != null) {
                        whenDone.run(true);
                    }
                    allDone[0] = true;
                    if (whenAllDone != null) {
                        whenAllDone.run(success ? "paid" : "failed", did);
                    }
                });
            }
        }, () -> {
            paymentFormOpened = false;
            if (!allDone[0] && whenAllDone != null) {
                whenAllDone.run("cancelled", 0L);
                allDone[0] = true;
            }
        });
    }

    public static void showNoSupportDialog(Context context, Theme.ResourcesProvider resourcesProvider) {
        new AlertDialog.Builder(context, resourcesProvider)
            .setTitle(getString(R.string.StarsNotAvailableTitle))
            .setMessage(getString(R.string.StarsNotAvailableText))
            .setPositiveButton(getString(R.string.OK), null)
            .show();
    }

    public void payAfterConfirmed(MessageObject messageObject, TLRPC.InputInvoice inputInvoice, TLRPC.TL_payments_paymentFormStars form, Utilities.Callback<Boolean> whenDone) {
        if (form == null) {
            return;
        }

        final Context context = ApplicationLoader.applicationContext;
        final Theme.ResourcesProvider resourcesProvider = getResourceProvider();

        if (context == null) {
            return;
        }

        long _stars = 0;
        for (TLRPC.TL_labeledPrice price : form.invoice.prices) {
            _stars += price.amount;
        }
        final long stars = _stars;
        final long dialogId;
        if (messageObject != null) {
            long did;
            if (messageObject.messageOwner != null && messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.from_id != null) {
                did = DialogObject.getPeerDialogId(messageObject.messageOwner.fwd_from.from_id);
            } else {
                did = messageObject.getDialogId();
            }
            if (did < 0 && messageObject.getFromChatId() > 0) {
                final TLRPC.User _user = MessagesController.getInstance(currentAccount).getUser(messageObject.getFromChatId());
                if (_user != null && _user.bot) {
                    did = _user.id;
                }
            }
            dialogId = did;
        } else {
            dialogId = form.bot_id;
        }
        final String bot;
        if (dialogId >= 0) {
            bot = UserObject.getUserName(MessagesController.getInstance(currentAccount).getUser(dialogId));
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            bot = chat == null ? "" : chat.title;
        }
        final String product = form.title;
        final int subscription_period = form.invoice.subscription_period;

        TL_stars.TL_payments_sendStarsForm req2 = new TL_stars.TL_payments_sendStarsForm();
        req2.form_id = form.form_id;
        req2.invoice = inputInvoice;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
            paymentFormOpened = false;
            BaseFragment fragment = LaunchActivity.getLastFragment();
            BulletinFactory b = fragment != null && fragment.visibleDialog == null ? BulletinFactory.of(fragment) : BulletinFactory.global();
            if (res2 instanceof TLRPC.TL_payments_paymentResult) {
                if (whenDone != null) {
                    whenDone.run(true);
                }

                TLRPC.TL_payments_paymentResult result = (TLRPC.TL_payments_paymentResult) res2;
                Utilities.stageQueue.postRunnable(() -> {
                    MessagesController.getInstance(currentAccount).processUpdates(result.updates, false);
                });

                final boolean media = messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPaidMedia;
                if (media) {
                    Drawable starDrawable = context.getResources().getDrawable(R.drawable.star_small_inner).mutate();
                    b.createSimpleBulletin(starDrawable, getString(R.string.StarsMediaPurchaseCompleted), AndroidUtilities.replaceTags(formatPluralString("StarsMediaPurchaseCompletedInfo", (int) stars, bot))).show();
                } else if (subscription_period > 0) {
                    b.createSimpleBulletin(R.raw.stars_send, getString(R.string.StarsBotSubscriptionCompleted), AndroidUtilities.replaceTags(formatPluralString("StarsBotSubscriptionCompletedInfo", (int) stars, product, bot))).show();
                } else {
                    b.createSimpleBulletin(R.raw.stars_send, getString(R.string.StarsPurchaseCompleted), AndroidUtilities.replaceTags(formatPluralString("StarsPurchaseCompletedInfo", (int) stars, product, bot))).show();
                }
                if (LaunchActivity.instance != null && LaunchActivity.instance.getFireworksOverlay() != null) {
                    LaunchActivity.instance.getFireworksOverlay().start(true);
                }

                final boolean isStarsGift = inputInvoice instanceof TLRPC.TL_inputInvoiceStars && ((TLRPC.TL_inputInvoiceStars) inputInvoice).purpose instanceof TLRPC.TL_inputStorePaymentStarsGift;
                if (!isStarsGift) {
                    invalidateTransactions(true);
                }

                if (messageObject != null) {
                    TLRPC.TL_messages_getExtendedMedia req = new TLRPC.TL_messages_getExtendedMedia();
                    req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                    req.id.add(messageObject.getId());
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
                }
            } else if (err2 != null && "BALANCE_TOO_LOW".equals(err2.text)) {
                if (!MessagesController.getInstance(currentAccount).starsPurchaseAvailable()) {
                    if (whenDone != null) {
                        whenDone.run(false);
                    }
                    showNoSupportDialog(context, resourcesProvider);
                    return;
                }
                final boolean[] purchased = new boolean[] { false };
                StarsIntroActivity.StarsNeededSheet sheet = new StarsIntroActivity.StarsNeededSheet(context, resourcesProvider, stars, StarsIntroActivity.StarsNeededSheet.TYPE_BOT, bot, () -> {
                    purchased[0] = true;
                    payAfterConfirmed(messageObject, inputInvoice, form, success -> {
                        if (whenDone != null) {
                            whenDone.run(success);
                        }
                    });
                }, dialogId);
                sheet.setOnDismissListener(d -> {
                    if (whenDone != null && !purchased[0]) {
                        whenDone.run(false);
                    }
                });
                sheet.show();
            } else if (err2 != null && "FORM_EXPIRED".equals(err2.text)) {
                TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
                final JSONObject themeParams = BotWebViewSheet.makeThemeParams(resourcesProvider);
                if (themeParams != null) {
                    req.theme_params = new TLRPC.TL_dataJSON();
                    req.theme_params.data = themeParams.toString();
                    req.flags |= 1;
                }
                req.invoice = inputInvoice;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res3, err3) -> AndroidUtilities.runOnUIThread(() -> {
                    if (res3 instanceof TLRPC.TL_payments_paymentFormStars) {
                        payAfterConfirmed(messageObject, inputInvoice, (TLRPC.TL_payments_paymentFormStars) res3, whenDone);
                    } else {
                        if (whenDone != null) {
                            whenDone.run(false);
                        }
                        b.createSimpleBulletin(R.raw.error, formatString(R.string.UnknownErrorCode, err3 != null ? err3.text : "FAILED_GETTING_FORM")).show();
                    }
                }));
            } else {
                if (whenDone != null) {
                    whenDone.run(false);
                }
                b.createSimpleBulletin(R.raw.error, formatString(R.string.UnknownErrorCode, err2 != null ? err2.text : "FAILED_SEND_STARS")).show();

                if (messageObject != null) {
                    TLRPC.TL_messages_getExtendedMedia req = new TLRPC.TL_messages_getExtendedMedia();
                    req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                    req.id.add(messageObject.getId());
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
                }
            }
        }));
    }

    private void payAfterConfirmed(String hash, TLRPC.ChatInvite chatInvite, Utilities.Callback2<Long, Boolean> whenDone) {
        if (chatInvite == null || chatInvite.subscription_pricing == null) {
            return;
        }

        final Context context = ApplicationLoader.applicationContext;
        final Theme.ResourcesProvider resourcesProvider = getResourceProvider();

        if (context == null) {
            return;
        }

        final long stars = chatInvite.subscription_pricing.amount;
        final String channel = chatInvite.title;

        TLRPC.TL_inputInvoiceChatInviteSubscription inputInvoice = new TLRPC.TL_inputInvoiceChatInviteSubscription();
        inputInvoice.hash = hash;

        TL_stars.TL_payments_sendStarsForm req2 = new TL_stars.TL_payments_sendStarsForm();
        req2.form_id = chatInvite.subscription_form_id;
        req2.invoice = inputInvoice;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
            paymentFormOpened = false;
            BaseFragment fragment = LaunchActivity.getLastFragment();
            BulletinFactory b = !AndroidUtilities.hasDialogOnTop(fragment) ? BulletinFactory.of(fragment) : BulletinFactory.global();
            if (res2 instanceof TLRPC.TL_payments_paymentResult) {
                TLRPC.TL_payments_paymentResult result = (TLRPC.TL_payments_paymentResult) res2;
                Utilities.stageQueue.postRunnable(() -> {
                    MessagesController.getInstance(currentAccount).processUpdates(result.updates, false);
                });

                long dialogId = 0;
                if (result.updates.update instanceof TLRPC.TL_updateChannel) {
                    TLRPC.TL_updateChannel upd = (TLRPC.TL_updateChannel) result.updates.update;
                    dialogId = -upd.channel_id;
                }
                if (result.updates.updates != null) {
                    for (int i = 0; i < result.updates.updates.size(); ++i) {
                        if (result.updates.updates.get(i) instanceof TLRPC.TL_updateChannel) {
                            TLRPC.TL_updateChannel upd = (TLRPC.TL_updateChannel) result.updates.updates.get(i);
                            dialogId = -upd.channel_id;
                        }
                    }
                }

                if (whenDone != null) {
                    whenDone.run(dialogId, true);
                }

                if (dialogId == 0) {
                    b.createSimpleBulletin(R.raw.stars_send, getString(R.string.StarsSubscriptionCompleted), AndroidUtilities.replaceTags(formatPluralString("StarsSubscriptionCompletedText", (int) stars, channel))).show();
                }
                if (LaunchActivity.instance != null && LaunchActivity.instance.getFireworksOverlay() != null) {
                    LaunchActivity.instance.getFireworksOverlay().start(true);
                }

                invalidateTransactions(true);
                invalidateSubscriptions(true);
            } else if (err2 != null && "BALANCE_TOO_LOW".equals(err2.text)) {
                if (!MessagesController.getInstance(currentAccount).starsPurchaseAvailable()) {
                    if (whenDone != null) {
                        whenDone.run(0L, false);
                    }
                    showNoSupportDialog(context, resourcesProvider);
                    return;
                }
                final boolean[] purchased = new boolean[] { false };
                StarsIntroActivity.StarsNeededSheet sheet = new StarsIntroActivity.StarsNeededSheet(context, resourcesProvider, stars, StarsIntroActivity.StarsNeededSheet.TYPE_SUBSCRIPTION_BUY, chatInvite.title, () -> {
                    purchased[0] = true;
                    payAfterConfirmed(hash, chatInvite, (did, success) -> {
                        if (whenDone != null) {
                            whenDone.run(did, success);
                        }
                    });
                }, 0); // TODO: purpose peer for chat invite?
                sheet.setOnDismissListener(d -> {
                    if (whenDone != null && !purchased[0]) {
                        whenDone.run(0L, false);
                    }
                });
                sheet.show();
            } else {
                if (whenDone != null) {
                    whenDone.run(0L, false);
                }
                b.createSimpleBulletin(R.raw.error, formatString(R.string.UnknownErrorCode, err2 != null ? err2.text : "FAILED_SEND_STARS")).show();
            }
        }));
    }

    public void updateMediaPrice(MessageObject msg, long price, Runnable done) {
        updateMediaPrice(msg, price, done, false);
    }

    private void updateMediaPrice(MessageObject msg, long price, Runnable done, boolean afterFileRef) {
        if (msg == null) {
            done.run();
            return;
        }

        final long dialog_id = msg.getDialogId();
        final int msg_id = msg.getId();

        TLRPC.TL_messageMediaPaidMedia paidMedia = (TLRPC.TL_messageMediaPaidMedia) msg.messageOwner.media;

        TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialog_id);
        req.flags |= 32768;
        req.schedule_date = msg.messageOwner.date;
        req.id = msg_id;
        req.flags |= 16384;

        TLRPC.TL_inputMediaPaidMedia media = new TLRPC.TL_inputMediaPaidMedia();
        media.stars_amount = price;
        for (int i = 0; i < paidMedia.extended_media.size(); ++i) {
            TLRPC.MessageExtendedMedia emedia = paidMedia.extended_media.get(i);
            if (!(emedia instanceof TLRPC.TL_messageExtendedMedia)) {
                done.run();
                return;
            }
            TLRPC.MessageMedia imedia = ((TLRPC.TL_messageExtendedMedia) emedia).media;
            if (imedia instanceof TLRPC.TL_messageMediaPhoto) {
                TLRPC.TL_messageMediaPhoto mediaPhoto = (TLRPC.TL_messageMediaPhoto) imedia;
                TLRPC.TL_inputMediaPhoto inputMedia = new TLRPC.TL_inputMediaPhoto();
                TLRPC.TL_inputPhoto photo = new TLRPC.TL_inputPhoto();
                photo.id = mediaPhoto.photo.id;
                photo.access_hash = mediaPhoto.photo.access_hash;
                photo.file_reference = mediaPhoto.photo.file_reference;
                inputMedia.id = photo;
                media.extended_media.add(inputMedia);
            } else if (imedia instanceof TLRPC.TL_messageMediaDocument) {
                TLRPC.TL_messageMediaDocument mediaDocument = (TLRPC.TL_messageMediaDocument) imedia;
                TLRPC.TL_inputMediaDocument inputMedia = new TLRPC.TL_inputMediaDocument();
                TLRPC.TL_inputDocument doc = new TLRPC.TL_inputDocument();
                doc.id = mediaDocument.document.id;
                doc.access_hash = mediaDocument.document.access_hash;
                doc.file_reference = mediaDocument.document.file_reference;
                inputMedia.id = doc;
                media.extended_media.add(inputMedia);
            }
        }
        req.media = media;

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.Updates) {
                Utilities.stageQueue.postRunnable(() -> {
                    MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res, false);
                });
                done.run();
            } else if (err != null && FileRefController.isFileRefError(err.text) && !afterFileRef) {
                TLRPC.TL_messages_getScheduledMessages req2 = new TLRPC.TL_messages_getScheduledMessages();
                req2.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialog_id);
                req2.id.add(msg_id);
                ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
                    if (res2 instanceof TLRPC.TL_messages_messages) {
                        TLRPC.TL_messages_messages m = (TLRPC.TL_messages_messages) res2;
                        MessagesController.getInstance(currentAccount).putUsers(m.users, false);
                        MessagesController.getInstance(currentAccount).putChats(m.chats, false);

                        if (m.messages.size() == 1 && m.messages.get(0) instanceof TLRPC.TL_message && m.messages.get(0).media instanceof TLRPC.TL_messageMediaPaidMedia) {
                            msg.messageOwner = m.messages.get(0);
                            updateMediaPrice(msg, price, done, true);
                        } else {
                            done.run();
                        }
                    } else {
                        done.run();
                    }
                }));
            } else {
                done.run();
            }
        }));
    }



    // ===== STAR REACTIONS =====

    public static final long REACTIONS_TIMEOUT = 5_000;
    public PendingPaidReactions currentPendingReactions;

    public static class MessageId {
        public long did;
        public int mid;
        private MessageId(long did, int mid) {
            this.did = did;
            this.mid = mid;
        }
        public static MessageId from(long did, int mid) {
            return new MessageId(did, mid);
        }
        public static MessageId from(MessageObject msg) {
            if (msg == null) return null;
            if (msg.messageOwner != null && (msg.messageOwner.isThreadMessage || msg.isForwardedChannelPost()) && msg.messageOwner.fwd_from != null) {
                return new MessageId(msg.getFromChatId(), msg.messageOwner.fwd_from.saved_from_msg_id);
            } else {
                return new MessageId(msg.getDialogId(), msg.getId());
            }
        }
        @Override
        public int hashCode() {
            return Objects.hash(did, mid);
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof MessageId) {
                MessageId id = (MessageId) obj;
                return id.did == did && id.mid == mid;
            }
            return false;
        }
    }

    public long getPaidReactionsDialogId(MessageObject messageObject) {
        if (currentPendingReactions != null && currentPendingReactions.message.equals(MessageId.from(messageObject)) && currentPendingReactions.peer != null) {
            return currentPendingReactions.peer;
        }
        Long messageSettings = messageObject == null ? null : messageObject.getMyPaidReactionPeer();
        if (messageSettings != null) {
            return messageSettings;
        }
        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
        Long peer = messagesController.getPaidReactionsDialogId();
        return peer != null ? peer : 0;
    }

    public long getPaidReactionsDialogId(MessageId id, TLRPC.MessageReactions reactions) {
        if (currentPendingReactions != null && currentPendingReactions.message.equals(id) && currentPendingReactions.peer != null) {
            return currentPendingReactions.peer;
        }
        Long messageSettings = MessageObject.getMyPaidReactionPeer(reactions);
        if (messageSettings != null) {
            return messageSettings;
        }
        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
        Long peer = messagesController.getPaidReactionsDialogId();
        return peer != null ? peer : 0;
    }

    public class PendingPaidReactions {

        public MessageId message;
        public MessageObject messageObject;
        public long random_id;
        public ChatActivity chatActivity;
        public Bulletin bulletin;
        public Bulletin.TwoLineAnimatedLottieLayout bulletinLayout;
        public Bulletin.UndoButton bulletinButton;
        public Bulletin.TimerView timerView;

        public boolean wasChosen;

        public long amount;
        public long lastTime;
        public boolean committed = false;
        public boolean cancelled = false;

        public long not_added;
        public boolean applied;
        public boolean shownBulletin;

        public Long peer = null;
        public long getPeerId() {
            if (peer != null) return peer;
            return getPaidReactionsDialogId(messageObject);
        }

        public boolean isAnonymous() {
            return getPeerId() == UserObject.ANONYMOUS;
        }

        public StarReactionsOverlay overlay;
        public void setOverlay(StarReactionsOverlay overlay) {
            this.overlay = overlay;
        }

        public String getToastTitle() {
            if (isAnonymous()) {
                return getString(R.string.StarsSentAnonymouslyTitle);
            } else if (getPeerId() != 0 && getPeerId() != UserConfig.getInstance(currentAccount).getClientUserId()) {
                return formatString(R.string.StarsSentTitleChannel, DialogObject.getShortName(getPeerId()));
            } else {
                return getString(R.string.StarsSentTitle);
            }
        }

        public PendingPaidReactions(
            MessageId message,
            MessageObject messageObject,
            ChatActivity chatActivity,
            long currentTime,
            boolean affect
        ) {
            this.message = message;
            this.messageObject = messageObject;
            this.random_id = Utilities.random.nextLong() & 0xFFFFFFFFL | (currentTime << 32);
            this.chatActivity = chatActivity;

            final Context context = getContext(chatActivity);
            bulletinLayout = new Bulletin.TwoLineAnimatedLottieLayout(context, chatActivity.themeDelegate);
            bulletinLayout.setAnimation(R.raw.stars_topup);
            bulletinLayout.titleTextView.setText(getToastTitle());
            bulletinButton = new Bulletin.UndoButton(context, true, false, chatActivity.themeDelegate);
            bulletinButton.setText(LocaleController.getString(R.string.StarsSentUndo));
            bulletinButton.setUndoAction(this::cancel);
            timerView = new Bulletin.TimerView(context, chatActivity.themeDelegate);
            timerView.timeLeft = REACTIONS_TIMEOUT;
            timerView.setColor(Theme.getColor(Theme.key_undo_cancelColor, chatActivity.themeDelegate));
            bulletinButton.addView(timerView, LayoutHelper.createFrame(20, 20, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
            bulletinButton.undoTextView.setPadding(dp(12), dp(8), dp(20 + 10), dp(8));
            bulletinLayout.setButton(bulletinButton);
            bulletin = BulletinFactory.of(chatActivity).create(bulletinLayout, -1);
            bulletin.hideAfterBottomSheet = false;
            if (affect) {
                bulletin.show(true);
                shownBulletin = true;
            }
            bulletin.setOnHideListener(closeRunnable);

            this.amount = 0;
            this.lastTime = System.currentTimeMillis();

            wasChosen = messageObject.isPaidReactionChosen();
        }

        public void add(long amount, boolean affect) {
            if (committed || cancelled) {
                if (BuildVars.DEBUG_PRIVATE_VERSION) {
                    throw new RuntimeException("adding more amount to committed reactions");
                } else {
                    return;
                }
            }
            this.amount += amount;
            this.lastTime = System.currentTimeMillis();

            bulletinLayout.subtitleTextView.cancelAnimation();
            bulletinLayout.subtitleTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatPluralString("StarsSentText", (int) this.amount)), true);

            if (shownBulletin) {
                timerView.timeLeft = REACTIONS_TIMEOUT;
                AndroidUtilities.cancelRunOnUIThread(closeRunnable);
                AndroidUtilities.runOnUIThread(closeRunnable, REACTIONS_TIMEOUT);
            }

            if (affect) {
                applied = true;
                messageObject.addPaidReactions((int) +amount, true, getPeerId());
                minus += amount;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didUpdateReactions, messageObject.getDialogId(), messageObject.getId(), messageObject.messageOwner.reactions);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
            } else {
                applied = false;
                if (messageObject.ensurePaidReactionsExist(true)) {
                    not_added--;
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didUpdateReactions, messageObject.getDialogId(), messageObject.getId(), messageObject.messageOwner.reactions);
                not_added += amount;
            }

            bulletinLayout.titleTextView.setText(getToastTitle());
        }

        public void apply() {
            if (!applied) {
                applied = true;
                messageObject.addPaidReactions((int) +not_added, true, getPeerId());
                minus += not_added;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
                not_added = 0;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didUpdateReactions, messageObject.getDialogId(), messageObject.getId(), messageObject.messageOwner.reactions);
            }
            if (!shownBulletin) {
                shownBulletin = true;

                timerView.timeLeft = REACTIONS_TIMEOUT;
                AndroidUtilities.cancelRunOnUIThread(closeRunnable);
                AndroidUtilities.runOnUIThread(closeRunnable, REACTIONS_TIMEOUT);

                bulletin.show(true);
                bulletin.setOnHideListener(closeRunnable);
            }

            bulletinLayout.titleTextView.setText(getToastTitle());
        }

        public final Runnable closeRunnable = this::close;
        public void close() {
            AndroidUtilities.cancelRunOnUIThread(closeRunnable);

            if (applied) {
                commit();
            } else {
                cancelled = true;
                messageObject.addPaidReactions((int) -amount, wasChosen, getPeerId());
                minus -= amount;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
            }
            bulletin.hide();
            if (overlay != null && overlay.isShowing(messageObject)) {
                overlay.hide();
            }

            if (currentPendingReactions == this) {
                currentPendingReactions = null;
            }
        }

        public final Runnable cancelRunnable = this::cancel;
        public void cancel() {
            AndroidUtilities.cancelRunOnUIThread(closeRunnable);

            cancelled = true;
            bulletin.hide();
            if (overlay != null) {
                overlay.hide();
            }

            messageObject.addPaidReactions((int) -amount, wasChosen, getPeerId());
            minus -= amount;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didUpdateReactions, messageObject.getDialogId(), messageObject.getId(), messageObject.messageOwner.reactions);

            if (currentPendingReactions == this) {
                currentPendingReactions = null;
            }
        }

        public void commit() {
            if (committed || cancelled) {
                return;
            }

            final StarsController starsController = StarsController.getInstance(currentAccount);
            final MessagesController messagesController = MessagesController.getInstance(currentAccount);
            final ConnectionsManager connectionsManager = ConnectionsManager.getInstance(currentAccount);

            final long totalStars = amount;
            if (starsController.balanceAvailable() && starsController.getBalance(false) < totalStars) {
                cancelled = true;

                messageObject.addPaidReactions((int) -amount, wasChosen, getPeerId());
                minus = 0;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didUpdateReactions, messageObject.getDialogId(), messageObject.getId(), messageObject.messageOwner.reactions);

                String name;
                if (message.did >= 0) {
                    TLRPC.User user = chatActivity.getMessagesController().getUser(message.did);
                    name = UserObject.getForcedFirstName(user);
                } else {
                    TLRPC.Chat chat = chatActivity.getMessagesController().getChat(-message.did);
                    name = chat == null ? "" : chat.title;
                }
                Context context = chatActivity.getContext();
                if (context == null) context = LaunchActivity.instance;
                if (context == null) context = ApplicationLoader.applicationContext;
                new StarsIntroActivity.StarsNeededSheet(context, chatActivity.getResourceProvider(), totalStars, StarsIntroActivity.StarsNeededSheet.TYPE_REACTIONS, name, () -> {
                    sendPaidReaction(messageObject, chatActivity, totalStars, true, true, peer);
                }, 0).show();

                return;
            }

            committed = true;

            final TLRPC.TL_messages_sendPaidReaction req = new TLRPC.TL_messages_sendPaidReaction();
            req.peer = messagesController.getInputPeer(message.did);
            req.msg_id = message.mid;
            req.random_id = random_id;
            req.count = (int) amount;
            req.flags |= 1;
            final long privacyDialogId = getPeerId();
            if (privacyDialogId == 0 || privacyDialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                req.privacy = new TL_stars.paidReactionPrivacyDefault();
            } else if (privacyDialogId == UserObject.ANONYMOUS) {
                req.privacy = new TL_stars.paidReactionPrivacyAnonymous();
            } else {
                req.privacy = new TL_stars.paidReactionPrivacyPeer();
                req.privacy.peer = messagesController.getInputPeer(privacyDialogId);
            }

            invalidateBalance();

            connectionsManager.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response != null) {
                    Utilities.stageQueue.postRunnable(() -> {
                        messagesController.processUpdates((TLRPC.Updates) response, false);
                    });
                } else if (error != null) {
                    messageObject.addPaidReactions((int) -amount, wasChosen, getPeerId());
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didUpdateReactions, messageObject.getDialogId(), messageObject.getId(), messageObject.messageOwner.reactions);

                    if ("BALANCE_TOO_LOW".equals(error.text)) {
                        String name;
                        if (message.did >= 0) {
                            TLRPC.User user = chatActivity.getMessagesController().getUser(message.did);
                            name = UserObject.getForcedFirstName(user);
                        } else {
                            TLRPC.Chat chat = chatActivity.getMessagesController().getChat(-message.did);
                            name = chat == null ? "" : chat.title;
                        }
                        Context context = chatActivity.getContext();
                        if (context == null) context = LaunchActivity.instance;
                        if (context == null) context = ApplicationLoader.applicationContext;
                        new StarsIntroActivity.StarsNeededSheet(context, chatActivity.getResourceProvider(), totalStars, StarsIntroActivity.StarsNeededSheet.TYPE_REACTIONS, name, () -> {
                            sendPaidReaction(messageObject, chatActivity, totalStars, true, true, peer);
                        }, 0).show();
                    }

                    invalidateTransactions(false);
                    invalidateBalance();
                }
            }));
        }
    }

    public StarsController.PendingPaidReactions sendPaidReaction(MessageObject messageObject, ChatActivity chatActivity) {
        return sendPaidReaction(messageObject, chatActivity, +1, true, true, null);
    }

    public Context getContext(BaseFragment fragment) {
        if (fragment != null && fragment.getContext() != null)
            return fragment.getContext();
        if (LaunchActivity.instance != null && !LaunchActivity.instance.isFinishing())
            return LaunchActivity.instance;
        if (ApplicationLoader.applicationContext != null)
            return ApplicationLoader.applicationContext;
        return null;
    }

    public StarsController.PendingPaidReactions sendPaidReaction(
        MessageObject messageObject,
        ChatActivity chatActivity,
        long amount,
        boolean affect,
        boolean checkBalance,
        Long peer
    ) {
        final MessageId key = MessageId.from(messageObject);
        final StarsController s = StarsController.getInstance(currentAccount);
        final long totalStars = amount;
        final Context context = getContext(chatActivity);
        if (context == null) return null;
        if (checkBalance && s.balanceAvailable() && s.getBalance(false) <= 0) {
            final long dialogId = chatActivity.getDialogId();
            String name;
            if (dialogId >= 0) {
                TLRPC.User user = chatActivity.getMessagesController().getUser(dialogId);
                name = UserObject.getForcedFirstName(user);
            } else {
                TLRPC.Chat chat = chatActivity.getMessagesController().getChat(-dialogId);
                name = chat == null ? "" : chat.title;
            }
            if (context == null) return null;
            new StarsIntroActivity.StarsNeededSheet(context, chatActivity.getResourceProvider(), totalStars, StarsIntroActivity.StarsNeededSheet.TYPE_REACTIONS, name, () -> {
                sendPaidReaction(messageObject, chatActivity, totalStars, true, true, peer);
            }, 0).show();
            return null;
        }
        if (currentPendingReactions == null || !currentPendingReactions.message.equals(key)) {
            if (currentPendingReactions != null) {
                currentPendingReactions.close();
            }
            currentPendingReactions = new PendingPaidReactions(key, messageObject, chatActivity, ConnectionsManager.getInstance(currentAccount).getCurrentTime(), affect);
            currentPendingReactions.peer = peer;
        }
        if (currentPendingReactions.amount + amount > MessagesController.getInstance(currentAccount).starsPaidReactionAmountMax) {
            currentPendingReactions.close();
            currentPendingReactions = new PendingPaidReactions(key, messageObject, chatActivity, ConnectionsManager.getInstance(currentAccount).getCurrentTime(), affect);
        }
        final long totalStars2 = currentPendingReactions.amount + amount;
        if (checkBalance && s.balanceAvailable() && s.getBalance(false) < totalStars2) {
            currentPendingReactions.cancel();
            final long dialogId = chatActivity.getDialogId();
            String name;
            if (dialogId >= 0) {
                TLRPC.User user = chatActivity.getMessagesController().getUser(dialogId);
                name = UserObject.getForcedFirstName(user);
            } else {
                TLRPC.Chat chat = chatActivity.getMessagesController().getChat(-dialogId);
                name = chat == null ? "" : chat.title;
            }
            new StarsIntroActivity.StarsNeededSheet(context, chatActivity.getResourceProvider(), totalStars2, StarsIntroActivity.StarsNeededSheet.TYPE_REACTIONS, name, () -> {
                sendPaidReaction(messageObject, chatActivity, totalStars2, true, true, peer);
            }, 0).show();
            return null;
        }
        currentPendingReactions.add(amount, (messageObject != null && !messageObject.doesPaidReactionExist()) || affect);
        currentPendingReactions.peer = peer;
        return currentPendingReactions;
    }

    public void undoPaidReaction() {
        if (currentPendingReactions != null) {
            currentPendingReactions.cancel();
        }
    }

    public void commitPaidReaction() {
        if (currentPendingReactions != null) {
            currentPendingReactions.close();
        }
    }

    public boolean hasPendingPaidReactions(MessageObject messageObject) {
        if (currentPendingReactions == null) return false;
        if (messageObject == null) return false;
        final MessageId key = MessageId.from(messageObject);
        if (currentPendingReactions.message.did != key.did || currentPendingReactions.message.mid != key.mid) return false;
        if (!currentPendingReactions.applied) return false;
        return true;
    }

    public long getPendingPaidReactions(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) return 0;
        if ((messageObject.messageOwner.isThreadMessage || messageObject.isForwardedChannelPost()) && messageObject.messageOwner.fwd_from != null) {
            return getPendingPaidReactions(messageObject.getFromChatId(), messageObject.messageOwner.fwd_from.saved_from_msg_id);
        } else {
            return getPendingPaidReactions(messageObject.getDialogId(), messageObject.getId());
        }
    }

    public long getPendingPaidReactions(long dialogId, int messageId) {
        if (currentPendingReactions == null) return 0;
        if (currentPendingReactions.message.did != dialogId || currentPendingReactions.message.mid != messageId) return 0;
        if (!currentPendingReactions.applied) return 0;
        return currentPendingReactions.amount;
    }


    // ===== STAR GIFTS =====

    public boolean giftsLoading, giftsLoaded;
    private boolean giftsCacheLoaded;
    public int giftsHash;
    public long giftsRemoteTime;
    public final ArrayList<TL_stars.StarGift> gifts = new ArrayList<>();
    public final ArrayList<TL_stars.StarGift> sortedGifts = new ArrayList<>();
    public final ArrayList<TL_stars.StarGift> birthdaySortedGifts = new ArrayList<>();

    public void invalidateStarGifts() {
        giftsLoaded = false;
        giftsCacheLoaded = true;
        giftsRemoteTime = 0;
        loadStarGifts();
    }

    public void loadStarGifts() {
        if (giftsLoading || giftsLoaded && (System.currentTimeMillis() - giftsRemoteTime) < 1000 * 60) return;
        giftsLoading = true;

        if (!giftsCacheLoaded) {
            getStarGiftsCached((giftsCached, hash, time, users, chats) -> {
                MessagesController.getInstance(currentAccount).putUsers(users, true);
                MessagesController.getInstance(currentAccount).putChats(chats, true);

                giftsCacheLoaded = true;
                gifts.clear();
                gifts.addAll(giftsCached);
                birthdaySortedGifts.clear();
                birthdaySortedGifts.addAll(gifts);
                Collections.sort(birthdaySortedGifts, Comparator.comparingInt((TL_stars.StarGift a) -> (a.sold_out ? 1 : 0)).thenComparingInt((TL_stars.StarGift a) -> (a.birthday ? -1 : 0)));
                sortedGifts.clear();
                sortedGifts.addAll(gifts);
                Collections.sort(sortedGifts, Comparator.comparingInt((TL_stars.StarGift a) -> (a.sold_out ? 1 : 0)));
                giftsHash = hash;
                giftsRemoteTime = time;
                giftsLoading = false;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starGiftsLoaded);

                loadStarGifts();
            });
        } else {
            getStarGiftsRemote(giftsHash, giftsRemote -> {
                giftsLoading = false;
                giftsLoaded = true;
                if (giftsRemote instanceof TL_stars.TL_starGifts) {
                    final TL_stars.TL_starGifts res = (TL_stars.TL_starGifts) giftsRemote;
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                    gifts.clear();
                    gifts.addAll(res.gifts);
                    birthdaySortedGifts.clear();
                    birthdaySortedGifts.addAll(gifts);
                    Collections.sort(birthdaySortedGifts, Comparator.comparingInt((TL_stars.StarGift a) -> (a.sold_out ? 1 : 0)).thenComparingInt((TL_stars.StarGift a) -> (a.birthday ? -1 : 0)));
                    sortedGifts.clear();
                    sortedGifts.addAll(gifts);
                    Collections.sort(sortedGifts, Comparator.comparingInt((TL_stars.StarGift a) -> (a.sold_out ? 1 : 0)));
                    giftsHash = res.hash;
                    giftsRemoteTime = System.currentTimeMillis();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starGiftsLoaded);
                    saveStarGiftsCached(res.gifts, giftsHash, giftsRemoteTime);
                } else if (giftsRemote instanceof TL_stars.TL_starGiftsNotModified) {
                    saveStarGiftsCached(gifts, giftsHash, giftsRemoteTime = System.currentTimeMillis());
                }
            });
        }
    }

    public void makeStarGiftSoldOut(TL_stars.StarGift starGift) {
        if (starGift == null || !giftsLoaded) return;
        starGift.availability_remains = 0;
        saveStarGiftsCached(gifts, giftsHash, giftsRemoteTime);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starGiftSoldOut, starGift);
    }

    private void getStarGiftsCached(Utilities.Callback5<ArrayList<TL_stars.StarGift>, Integer, Long, ArrayList<TLRPC.User>, ArrayList<TLRPC.Chat>> whenDone) {
        if (whenDone == null) return;
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        final ArrayList<TL_stars.StarGift> result = new ArrayList<>();
        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            final SQLiteDatabase db = storage.getDatabase();
            int hash = 0;
            long time = 0;
            SQLiteCursor cursor = null;
            try {
                cursor = db.queryFinalized("SELECT data, hash, time FROM star_gifts2 ORDER BY pos ASC");
                while (cursor.next()) {
                    final NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TL_stars.StarGift gift = TL_stars.StarGift.TLdeserialize(data, data.readInt32(false), false);
                        if (gift != null) {
                            result.add(gift);
                        }
                        data.reuse();
                        hash = (int) cursor.longValue(1);
                        time = cursor.longValue(2);
                    }
                }


                final ArrayList<Long> usersToLoad = new ArrayList<>();
                final ArrayList<Long> chatsToLoad = new ArrayList<>();
                for (TL_stars.StarGift starGift : result) {
                    if (starGift.released_by != null) {
                        final long dialogId = DialogObject.getPeerDialogId(starGift.released_by);
                        if (dialogId > 0) {
                            usersToLoad.add(dialogId);
                        } else if (dialogId < 0) {
                            chatsToLoad.add(-dialogId);
                        }
                    }
                }

                if (!chatsToLoad.isEmpty()) {
                    storage.getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                }
                if (!usersToLoad.isEmpty()) {
                    storage.getUsersInternal(usersToLoad, users);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            final int finalHash = hash;
            final long finalTime = time;
            AndroidUtilities.runOnUIThread(() -> {
                whenDone.run(result, finalHash, finalTime, users, chats);
            });
        });
    }
    private void saveStarGiftsCached(ArrayList<TL_stars.StarGift> gifts, int hash, long time) {
        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            final SQLiteDatabase db = storage.getDatabase();
            SQLitePreparedStatement state = null;
            try {
                db.executeFast("DELETE FROM star_gifts2").stepThis().dispose();
                if (gifts != null) {
                    state = db.executeFast("REPLACE INTO star_gifts2 VALUES(?, ?, ?, ?, ?)");
                    for (int i = 0; i < gifts.size(); ++i) {
                        final TL_stars.StarGift gift = gifts.get(i);
                        state.requery();
                        state.bindLong(1, gift.id);
                        NativeByteBuffer data = new NativeByteBuffer(gift.getObjectSize());
                        gift.serializeToStream(data);
                        state.bindByteBuffer(2, data);
                        state.bindLong(3, hash);
                        state.bindLong(4, time);
                        state.bindInteger(5, i);
                        state.step();
                        data.reuse();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }
    private void getStarGiftsRemote(int hash, Utilities.Callback<TL_stars.StarGifts> whenDone) {
        if (whenDone == null) return;
        TL_stars.getStarGifts req = new TL_stars.getStarGifts();
        req.hash = hash;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
           if (res instanceof TL_stars.StarGifts) {
               whenDone.run((TL_stars.StarGifts) res);
           } else {
               whenDone.run(null);
           }
        }));
    }

    @Nullable
    public TL_stars.StarGift getStarGift(long gift_id) {
        loadStarGifts();
        for (int i = 0; i < gifts.size(); ++i) {
            final TL_stars.StarGift gift = gifts.get(i);
            if (gift.id == gift_id)
                return gift;
        }
        return null;
    }

    public Runnable getStarGift(long gift_id, Utilities.Callback<TL_stars.StarGift> whenDone) {
//        final AlertDialog progressDialog = new AlertDialog(ApplicationLoader.applicationContext, AlertDialog.ALERT_TYPE_SPINNER);
//        progressDialog.showDelayed(500);

        final boolean[] done = new boolean[] { false };
        NotificationCenter.NotificationCenterDelegate[] observer = new NotificationCenter.NotificationCenterDelegate[1];
        observer[0] = (id, account, args) -> {
            if (done[0]) return;
            if (id == NotificationCenter.starGiftsLoaded) {
                TL_stars.StarGift gift = getStarGift(gift_id);
                if (gift != null) {
//                    progressDialog.dismissUnless(500);
                    done[0] = true;
                    NotificationCenter.getInstance(currentAccount).removeObserver(observer[0], NotificationCenter.starGiftsLoaded);
                    whenDone.run(gift);
                }
            }
        };
        NotificationCenter.getInstance(currentAccount).addObserver(observer[0], NotificationCenter.starGiftsLoaded);
        TL_stars.StarGift gift = getStarGift(gift_id);
        if (gift != null) {
            done[0] = true;
//            progressDialog.dismissUnless(500);
            NotificationCenter.getInstance(currentAccount).removeObserver(observer[0], NotificationCenter.starGiftsLoaded);
            whenDone.run(gift);
        }
        return () -> {
            done[0] = true;
//            progressDialog.dismissUnless(500);
            NotificationCenter.getInstance(currentAccount).removeObserver(observer[0], NotificationCenter.starGiftsLoaded);
        };
    }

    public void buyPremiumGift(long dialogId, Object option, TLRPC.TL_textWithEntities text, Utilities.Callback2<Boolean, String> whenDone) {
        final Context context = LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
        final Theme.ResourcesProvider resourcesProvider = getResourceProvider();

        if (!(option instanceof TLRPC.TL_premiumGiftOption || option instanceof TLRPC.TL_premiumGiftCodeOption) || context == null) {
            return;
        }

        if (!balanceAvailable()) {
            getBalance(() -> {
                if (!balanceAvailable()) {
                    bulletinError("NO_BALANCE");
                    if (whenDone != null) {
                        whenDone.run(false, null);
                    }
                    return;
                }
                buyPremiumGift(dialogId, option, text, whenDone);
            });
            return;
        }

        final int months;

        if (option instanceof TLRPC.TL_premiumGiftOption) {
            final TLRPC.TL_premiumGiftOption o = (TLRPC.TL_premiumGiftOption) option;
            months = o.months;
        } else if (option instanceof TLRPC.TL_premiumGiftCodeOption) {
            final TLRPC.TL_premiumGiftCodeOption o = (TLRPC.TL_premiumGiftCodeOption) option;
            months = o.months;
        } else return;

        final String name = DialogObject.getName(currentAccount, dialogId);

        final TLRPC.TL_inputInvoicePremiumGiftStars inputInvoice = new TLRPC.TL_inputInvoicePremiumGiftStars();
        inputInvoice.user_id = MessagesController.getInstance(currentAccount).getInputUser(dialogId);
        inputInvoice.months = months;
        if (text != null && !TextUtils.isEmpty(text.text)) {
            inputInvoice.flags |= 1;
            inputInvoice.message = text;
        }

        final TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
        final JSONObject themeParams = BotWebViewSheet.makeThemeParams(resourcesProvider);
        if (themeParams != null) {
            req.theme_params = new TLRPC.TL_dataJSON();
            req.theme_params.data = themeParams.toString();
            req.flags |= 1;
        }
        req.invoice = inputInvoice;

        final int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(res instanceof TLRPC.TL_payments_paymentFormStars)) {
                bulletinError(err, "NO_PAYMENT_FORM");
                whenDone.run(false, null);
                return;
            }

            final TLRPC.TL_payments_paymentFormStars form = (TLRPC.TL_payments_paymentFormStars) res;
            TL_stars.TL_payments_sendStarsForm req2 = new TL_stars.TL_payments_sendStarsForm();
            req2.form_id = form.form_id;
            req2.invoice = inputInvoice;
            long _stars = 0;
            for (TLRPC.TL_labeledPrice price : form.invoice.prices) {
                _stars += price.amount;
            }
            final long stars = _stars;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
                final BaseFragment fragment = LaunchActivity.getLastFragment();
                BulletinFactory b = fragment != null && fragment.visibleDialog == null ? BulletinFactory.of(fragment) : BulletinFactory.global();

                if (!(res2 instanceof TLRPC.TL_payments_paymentResult)) {
                    if (err2 != null && "BALANCE_TOO_LOW".equals(err2.text)) {
                        if (!MessagesController.getInstance(currentAccount).starsPurchaseAvailable()) {
                            if (whenDone != null) {
                                whenDone.run(false, null);
                            }
                            showNoSupportDialog(context, resourcesProvider);
                            return;
                        }
                        final boolean[] purchased = new boolean[] { false };
                        StarsIntroActivity.StarsNeededSheet sheet = new StarsIntroActivity.StarsNeededSheet(context, resourcesProvider, stars, StarsIntroActivity.StarsNeededSheet.TYPE_STAR_GIFT_BUY, name, () -> {
                            purchased[0] = true;
                            buyPremiumGift(dialogId, option, text, whenDone);
                        }, 0);
                        sheet.setOnDismissListener(d -> {
                            if (whenDone != null && !purchased[0]) {
                                whenDone.run(false, null);
                            }
                        });
                        sheet.show();
                    } else if (err2 != null && "STARGIFT_USAGE_LIMITED".equals(err2.text)) {
                        if (whenDone != null) {
                            whenDone.run(false, "STARGIFT_USAGE_LIMITED");
                        }
                    } else {
                        if (whenDone != null) {
                            whenDone.run(false, null);
                        }
                        b.createSimpleBulletin(R.raw.error, formatString(R.string.UnknownErrorCode, err2 != null ? err2.text : "FAILED_SEND_STARS")).show();
                    }
                    return;
                }

                final TLRPC.TL_payments_paymentResult result = (TLRPC.TL_payments_paymentResult) res2;
                Utilities.stageQueue.postRunnable(() -> {
                    MessagesController.getInstance(currentAccount).processUpdates(result.updates, false);
                });

                invalidateTransactions(true);

                if (whenDone != null) {
                    whenDone.run(true, null);
                }

                if (BirthdayController.getInstance(currentAccount).contains(dialogId)) {
                    MessagesController.getInstance(currentAccount).getMainSettings().edit().putBoolean(Calendar.getInstance().get(Calendar.YEAR) + "bdayhint_" + dialogId, false).apply();
                }

                if (dialogId < 0) {
//                    TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
//                    if (chatFull != null) {
//                        chatFull.stargifts_count++;
//                        chatFull.flags2 |= 262144;
//                        MessagesController.getInstance(currentAccount).putChatFull(chatFull);
//                    }
//                    if (fragment instanceof ProfileActivity && ((ProfileActivity) fragment).getDialogId() == dialogId) {
//                        if (((ProfileActivity) fragment).sharedMediaLayout != null) {
//                            ((ProfileActivity) fragment).sharedMediaLayout.updateTabs(true);
//                            ((ProfileActivity) fragment).sharedMediaLayout.scrollToPage(SharedMediaLayout.TAB_GIFTS);
//                            ((ProfileActivity) fragment).scrollToSharedMedia();
//                        }
//                        BulletinFactory.of(fragment).createEmojiBulletin(gift.sticker, getString(R.string.StarsGiftCompleted), AndroidUtilities.replaceTags(formatPluralString("StarsGiftCompletedChannelText", (int) stars, name))).show(false);
//                    } else {
//                        final Bundle args = new Bundle();
//                        args.putLong("chat_id", -dialogId);
//                        args.putBoolean("open_gifts", true);
//                        final ProfileActivity profileActivity = new ProfileActivity(args);
//                        profileActivity.whenFullyVisible(() -> {
//                            AndroidUtilities.runOnUIThread(() -> {
//                                if (profileActivity.sharedMediaLayout != null) {
//                                    profileActivity.sharedMediaLayout.scrollToPage(SharedMediaLayout.TAB_GIFTS);
//                                    profileActivity.scrollToSharedMedia();
//                                }
//                            }, 200);
//                            BulletinFactory.of(profileActivity).createEmojiBulletin(gift.sticker, getString(R.string.StarsGiftCompleted), AndroidUtilities.replaceTags(formatPluralString("StarsGiftCompletedChannelText", (int) stars, name))).show(false);
//                        });
//                        fragment.presentFragment(profileActivity);
//                    }
                } else {
//                    if (fragment instanceof ChatActivity && ((ChatActivity) fragment).getDialogId() == dialogId) {
//                        BulletinFactory.of(fragment).createEmojiBulletin(gift.sticker, getString(R.string.StarsGiftCompleted), AndroidUtilities.replaceTags(formatPluralString("StarsGiftCompletedText", (int) stars/*, UserObject.getForcedFirstName(user)*/))).show(true);
//                    } else {
//                        final ChatActivity chatActivity = ChatActivity.of(dialogId);
//                        chatActivity.whenFullyVisible(() -> {
//                            BulletinFactory.of(chatActivity).createEmojiBulletin(gift.sticker, getString(R.string.StarsGiftCompleted), AndroidUtilities.replaceTags(formatPluralString("StarsGiftCompletedText", (int) stars/*, UserObject.getForcedFirstName(user)*/))).show(true);
//                        });
//                        fragment.presentFragment(chatActivity);
//                    }
                }

                MessagesController.getInstance(currentAccount).getMainSettings().edit()
                    .putBoolean("show_gift_for_" + dialogId, true)
                    .putBoolean(Calendar.getInstance().get(Calendar.YEAR) + "show_gift_for_" + dialogId, true)
                    .apply();
                if (LaunchActivity.instance != null && LaunchActivity.instance.getFireworksOverlay() != null) {
                    LaunchActivity.instance.getFireworksOverlay().start(true);
                }
            }));
        }));
    }

    public void buyStarGift(TL_stars.StarGift gift, boolean anonymous, boolean upgraded, long dialogId, TLRPC.TL_textWithEntities text, Utilities.Callback2<Boolean, String> whenDone) {
        final Context context = LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
        final Theme.ResourcesProvider resourcesProvider = getResourceProvider();

        if (gift == null || context == null) {
            return;
        }

        if (!balanceAvailable()) {
            getBalance(() -> {
                if (!balanceAvailable()) {
                    bulletinError("NO_BALANCE");
                    if (whenDone != null) {
                        whenDone.run(false, null);
                    }
                    return;
                }
                buyStarGift(gift, anonymous, upgraded, dialogId, text, whenDone);
            });
            return;
        }

        final String name = DialogObject.getName(currentAccount, dialogId);

        final TLRPC.TL_inputInvoiceStarGift inputInvoice = new TLRPC.TL_inputInvoiceStarGift();
        inputInvoice.hide_name = anonymous;
        inputInvoice.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        inputInvoice.gift_id = gift.id;
        inputInvoice.include_upgrade = upgraded;
        if (text != null && !TextUtils.isEmpty(text.text)) {
            inputInvoice.flags |= 2;
            inputInvoice.message = text;
        }

        final TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
        final JSONObject themeParams = BotWebViewSheet.makeThemeParams(resourcesProvider);
        if (themeParams != null) {
            req.theme_params = new TLRPC.TL_dataJSON();
            req.theme_params.data = themeParams.toString();
            req.flags |= 1;
        }
        req.invoice = inputInvoice;

        final int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(res instanceof TLRPC.TL_payments_paymentFormStarGift)) {
                bulletinError(err, "NO_PAYMENT_FORM");
                whenDone.run(false, null);
                return;
            }

            final TLRPC.TL_payments_paymentFormStarGift form = (TLRPC.TL_payments_paymentFormStarGift) res;
            TL_stars.TL_payments_sendStarsForm req2 = new TL_stars.TL_payments_sendStarsForm();
            req2.form_id = form.form_id;
            req2.invoice = inputInvoice;
            long _stars = 0;
            for (TLRPC.TL_labeledPrice price : form.invoice.prices) {
                _stars += price.amount;
            }
            final long stars = _stars;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
                final BaseFragment fragment = LaunchActivity.getLastFragment();
                BulletinFactory b = fragment != null && fragment.visibleDialog == null ? BulletinFactory.of(fragment) : BulletinFactory.global();

                if (!(res2 instanceof TLRPC.TL_payments_paymentResult)) {
                    if (err2 != null && "BALANCE_TOO_LOW".equals(err2.text)) {
                        if (!MessagesController.getInstance(currentAccount).starsPurchaseAvailable()) {
                            if (whenDone != null) {
                                whenDone.run(false, null);
                            }
                            showNoSupportDialog(context, resourcesProvider);
                            return;
                        }
                        final boolean[] purchased = new boolean[] { false };
                        StarsIntroActivity.StarsNeededSheet sheet = new StarsIntroActivity.StarsNeededSheet(context, resourcesProvider, stars, StarsIntroActivity.StarsNeededSheet.TYPE_STAR_GIFT_BUY, name, () -> {
                            purchased[0] = true;
                            buyStarGift(gift, anonymous, upgraded, dialogId, text, whenDone);
                        }, 0);
                        sheet.setOnDismissListener(d -> {
                            if (whenDone != null && !purchased[0]) {
                                whenDone.run(false, null);
                            }
                        });
                        sheet.show();
                    } else if (err2 != null && "STARGIFT_USAGE_LIMITED".equals(err2.text)) {
                        if (whenDone != null) {
                            whenDone.run(false, "STARGIFT_USAGE_LIMITED");
                        }
                    } else if (err2 != null && "STARGIFT_USER_USAGE_LIMITED".equals(err2.text)) {
                        if (whenDone != null) {
                            whenDone.run(false, "STARGIFT_USER_USAGE_LIMITED");
                        }
                    } else {
                        if (whenDone != null) {
                            whenDone.run(false, null);
                        }
                        b.createSimpleBulletin(R.raw.error, formatString(R.string.UnknownErrorCode, err2 != null ? err2.text : "FAILED_SEND_STARS")).show();
                    }
                    return;
                }

                final TLRPC.TL_payments_paymentResult result = (TLRPC.TL_payments_paymentResult) res2;
                Utilities.stageQueue.postRunnable(() -> {
                    MessagesController.getInstance(currentAccount).processUpdates(result.updates, false);
                });

                invalidateStarGifts();
                invalidateProfileGifts(dialogId);
                invalidateTransactions(true);

                if (whenDone != null) {
                    whenDone.run(true, null);
                }

                if (BirthdayController.getInstance(currentAccount).contains(dialogId)) {
                    MessagesController.getInstance(currentAccount).getMainSettings().edit().putBoolean(Calendar.getInstance().get(Calendar.YEAR) + "bdayhint_" + dialogId, false).apply();
                }

                CharSequence _overrideToastSubtitle = null;
                if (gift != null && gift.limited_per_user) {
                    gift.per_user_remains--;
                    _overrideToastSubtitle = AndroidUtilities.replaceTags(formatPluralStringComma("Gift2SentRemainsLimit", Math.max(0, gift.per_user_remains)));
                }
                final CharSequence overrideToastSubtitle = _overrideToastSubtitle;

                if (dialogId < 0) {
                    TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
                    if (chatFull != null) {
                        chatFull.stargifts_count++;
                        chatFull.flags2 |= 262144;
                        MessagesController.getInstance(currentAccount).putChatFull(chatFull);
                    }
                    if (fragment instanceof ProfileActivity && ((ProfileActivity) fragment).getDialogId() == dialogId) {
                        if (((ProfileActivity) fragment).sharedMediaLayout != null) {
                            ((ProfileActivity) fragment).sharedMediaLayout.updateTabs(true);
                            ((ProfileActivity) fragment).sharedMediaLayout.scrollToPage(SharedMediaLayout.TAB_GIFTS);
                            ((ProfileActivity) fragment).scrollToSharedMedia();
                        }
                        BulletinFactory.of(fragment).createEmojiBulletin(gift.sticker, getString(R.string.StarsGiftCompleted), overrideToastSubtitle != null ? overrideToastSubtitle : AndroidUtilities.replaceTags(formatPluralString("StarsGiftCompletedChannelText", (int) stars, name))).show(false);
                    } else {
                        final Bundle args = new Bundle();
                        args.putLong("chat_id", -dialogId);
                        args.putBoolean("open_gifts", true);
                        final ProfileActivity profileActivity = new ProfileActivity(args);
                        profileActivity.whenFullyVisible(() -> {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (profileActivity.sharedMediaLayout != null) {
                                    profileActivity.sharedMediaLayout.scrollToPage(SharedMediaLayout.TAB_GIFTS);
                                    profileActivity.scrollToSharedMedia();
                                }
                            }, 200);
                            BulletinFactory.of(profileActivity).createEmojiBulletin(gift.sticker, getString(R.string.StarsGiftCompleted), overrideToastSubtitle != null ? overrideToastSubtitle : AndroidUtilities.replaceTags(formatPluralString("StarsGiftCompletedChannelText", (int) stars, name))).show(false);
                        });
                        fragment.presentFragment(profileActivity);
                    }
                } else {
                    if (fragment instanceof ChatActivity && ((ChatActivity) fragment).getDialogId() == dialogId) {
                        BulletinFactory.of(fragment).createEmojiBulletin(gift.sticker, getString(R.string.StarsGiftCompleted), overrideToastSubtitle != null ? overrideToastSubtitle : AndroidUtilities.replaceTags(formatPluralString("StarsGiftCompletedText", (int) stars/*, UserObject.getForcedFirstName(user)*/))).show(true);
                    } else {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeProfileActivity, dialogId, false);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChatActivity, dialogId, false);
                        final ChatActivity chatActivity = ChatActivity.of(dialogId);
                        chatActivity.whenFullyVisible(() -> {
                            BulletinFactory.of(chatActivity).createEmojiBulletin(gift.sticker, getString(R.string.StarsGiftCompleted), overrideToastSubtitle != null ? overrideToastSubtitle : AndroidUtilities.replaceTags(formatPluralString("StarsGiftCompletedText", (int) stars/*, UserObject.getForcedFirstName(user)*/))).show(true);
                        });
                        fragment.presentFragment(chatActivity);
                    }
                }

                MessagesController.getInstance(currentAccount).getMainSettings().edit()
                    .putBoolean("show_gift_for_" + dialogId, true)
                    .putBoolean(Calendar.getInstance().get(Calendar.YEAR) + "show_gift_for_" + dialogId, true)
                    .apply();
                if (LaunchActivity.instance != null && LaunchActivity.instance.getFireworksOverlay() != null) {
                    LaunchActivity.instance.getFireworksOverlay().start(true);
                }
            }));
        }));
    }

    public void getResellingGiftForm(TL_stars.StarGift gift, long dialogId, Utilities.Callback<TLRPC.TL_payments_paymentFormStarGift> whenDone) {
        final Context context = LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
        final Theme.ResourcesProvider resourcesProvider = getResourceProvider();

        if (gift == null || context == null) {
            return;
        }

        if (!balanceAvailable()) {
            getBalance(() -> {
                if (!balanceAvailable()) {
                    bulletinError("NO_BALANCE");
                    if (whenDone != null) {
                        whenDone.run(null);
                    }
                    return;
                }
                getResellingGiftForm(gift, dialogId, whenDone);
            });
            return;
        }

        final TLRPC.TL_inputInvoiceStarGiftResale inputInvoice = new TLRPC.TL_inputInvoiceStarGiftResale();
        inputInvoice.slug = gift.slug;
        inputInvoice.to_id = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        inputInvoice.ton = ton;

        final TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
        final JSONObject themeParams = BotWebViewSheet.makeThemeParams(resourcesProvider);
        if (themeParams != null) {
            req.theme_params = new TLRPC.TL_dataJSON();
            req.theme_params.data = themeParams.toString();
            req.flags |= 1;
        }
        req.invoice = inputInvoice;

        final int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(res instanceof TLRPC.TL_payments_paymentFormStarGift)) {
                bulletinError(err, "NO_PAYMENT_FORM");
                whenDone.run(null);
            } else {
                whenDone.run((TLRPC.TL_payments_paymentFormStarGift) res);
            }
        }));
    }

    public static long getFormStarsPrice(TLRPC.PaymentForm form) {
        long stars = 0;
        if (form != null) {
            for (TLRPC.TL_labeledPrice price : form.invoice.prices) {
                stars += price.amount;
            }
        }
        return stars;
    }

    public void buyResellingGift(TLRPC.TL_payments_paymentFormStarGift form, TL_stars.StarGift gift, long dialogId, Utilities.Callback2<Boolean, String> whenDone) {
        final Context context = LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
        final Theme.ResourcesProvider resourcesProvider = getResourceProvider();

        if (gift == null || context == null) {
            return;
        }

        if (!balanceAvailable()) {
            getBalance(() -> {
                if (!balanceAvailable()) {
                    bulletinError("NO_BALANCE");
                    if (whenDone != null) {
                        whenDone.run(false, null);
                    }
                    return;
                }
                buyResellingGift(form, gift, dialogId, whenDone);
            });
            return;
        }

        final String name = DialogObject.getName(currentAccount, dialogId);

        final TLRPC.TL_inputInvoiceStarGiftResale inputInvoice = new TLRPC.TL_inputInvoiceStarGiftResale();
        inputInvoice.slug = gift.slug;
        inputInvoice.to_id = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        inputInvoice.ton = ton;

        final TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
        final JSONObject themeParams = BotWebViewSheet.makeThemeParams(resourcesProvider);
        if (themeParams != null) {
            req.theme_params = new TLRPC.TL_dataJSON();
            req.theme_params.data = themeParams.toString();
            req.flags |= 1;
        }
        req.invoice = inputInvoice;

        TL_stars.TL_payments_sendStarsForm req2 = new TL_stars.TL_payments_sendStarsForm();
        req2.form_id = form.form_id;
        req2.invoice = inputInvoice;
        long _stars = 0;
        for (TLRPC.TL_labeledPrice price : form.invoice.prices) {
            _stars += price.amount;
        }
        final long stars = _stars;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
            final BaseFragment fragment = LaunchActivity.getLastFragment();
            BulletinFactory b = fragment != null && fragment.visibleDialog == null ? BulletinFactory.of(fragment) : BulletinFactory.global();

            if (!(res2 instanceof TLRPC.TL_payments_paymentResult)) {
                if (err2 != null && "BALANCE_TOO_LOW".equals(err2.text)) {
                    if (!MessagesController.getInstance(currentAccount).starsPurchaseAvailable()) {
                        if (whenDone != null) {
                            whenDone.run(false, null);
                        }
                        showNoSupportDialog(context, resourcesProvider);
                        return;
                    }
                    final boolean[] purchased = new boolean[] { false };
                    StarsIntroActivity.StarsNeededSheet sheet = new StarsIntroActivity.StarsNeededSheet(context, resourcesProvider, stars, StarsIntroActivity.StarsNeededSheet.TYPE_STAR_GIFT_BUY, name, () -> {
                        purchased[0] = true;
                        buyResellingGift(form, gift, dialogId, whenDone);
                    }, 0);
                    sheet.setOnDismissListener(d -> {
                        if (whenDone != null && !purchased[0]) {
                            whenDone.run(false, null);
                        }
                    });
                    sheet.show();
                } else if (err2 != null && "STARGIFT_USAGE_LIMITED".equals(err2.text)) {
                    if (whenDone != null) {
                        whenDone.run(false, "STARGIFT_USAGE_LIMITED");
                    }
                } else {
                    if (whenDone != null) {
                        whenDone.run(false, null);
                    }
                    b.createSimpleBulletin(R.raw.error, formatString(R.string.UnknownErrorCode, err2 != null ? err2.text : "FAILED_SEND_STARS")).show();
                }
                return;
            }

            final TLRPC.TL_payments_paymentResult result = (TLRPC.TL_payments_paymentResult) res2;
            Utilities.stageQueue.postRunnable(() -> {
                MessagesController.getInstance(currentAccount).processUpdates(result.updates, false);
            });

            invalidateStarGifts();
            invalidateProfileGifts(dialogId);
            invalidateTransactions(true);

            if (whenDone != null) {
                whenDone.run(true, null);
            }

            if (BirthdayController.getInstance(currentAccount).contains(dialogId)) {
                MessagesController.getInstance(currentAccount).getMainSettings().edit().putBoolean(Calendar.getInstance().get(Calendar.YEAR) + "bdayhint_" + dialogId, false).apply();
            }

            MessagesController.getInstance(currentAccount).getMainSettings().edit()
                .putBoolean("show_gift_for_" + dialogId, true)
                .putBoolean(Calendar.getInstance().get(Calendar.YEAR) + "show_gift_for_" + dialogId, true)
                .apply();
            if (LaunchActivity.instance != null && LaunchActivity.instance.getFireworksOverlay() != null) {
                LaunchActivity.instance.getFireworksOverlay().start(true);
            }
        }));
    }

    public final LongSparseArray<GiftsCollections> giftCollections = new LongSparseArray<>();
    public final LongSparseArray<GiftsList> giftLists = new LongSparseArray<>();
    public GiftsList getProfileGiftsList(long dialogId) {
        return getProfileGiftsList(dialogId, true);
    }
    public GiftsList getProfileGiftsList(long dialogId, boolean create) {
        GiftsList list = giftLists.get(dialogId);
        if (list == null && create) {
            giftLists.put(dialogId, list = new GiftsList(currentAccount, dialogId));
        }
        return list;
    }
    public GiftsCollections getProfileGiftCollectionsList(long dialogId, boolean create) {
        GiftsCollections list = giftCollections.get(dialogId);
        if (list == null && create) {
            giftCollections.put(dialogId, list = new GiftsCollections(currentAccount, dialogId));
        }
        return list;
    }

    public void invalidateProfileGifts(long dialogId) {
        GiftsList list = getProfileGiftsList(dialogId, false);
        if (list != null) {
            list.invalidate(false);
        }
        GiftsCollections collections = giftCollections.get(dialogId);
        if (collections != null) {
            collections.invalidate(false);
        }
    }

    public void invalidateProfileGifts(TLRPC.UserFull userFull) {
        if (userFull == null) return;
        long dialogId = userFull.id;
        GiftsList list = getProfileGiftsList(dialogId, false);
        if (list != null && list.totalCount != userFull.stargifts_count) {
            list.invalidate(false);
        }
        GiftsCollections collections = giftCollections.get(dialogId);
        if (collections != null) {
            collections.invalidate(false);
        }
    }

    public static class GiftsCollections {

        public final int currentAccount;
        public final long dialogId;

        public GiftsCollections(int currentAccount, long dialogId) {
            this(currentAccount, dialogId, true);
        }
        public GiftsCollections(int currentAccount, long dialogId, boolean load) {
            this.currentAccount = currentAccount;
            this.dialogId = dialogId;
            if (load) load();
        }

        public boolean loading;
        public boolean loaded;
        private ArrayList<TL_stars.TL_starGiftCollection> collections = new ArrayList<>();
        private ArrayList<TL_stars.TL_starGiftCollection> filteredCollections = new ArrayList<>();
        public GiftsList all;
        public HashMap<Integer, GiftsList> gifts = new HashMap<>();
        public int currentRequestId = -1;

        public boolean isMine() {
            if (dialogId >= 0)
                return dialogId == 0 || dialogId == UserConfig.getInstance(currentAccount).getClientUserId();
            return ChatObject.canUserDoAction(MessagesController.getInstance(currentAccount).getChat(-dialogId), ChatObject.ACTION_POST);
        }

        public ArrayList<TL_stars.TL_starGiftCollection> getCollections() {
            return isMine() ? collections : filteredCollections;
        }

        private void refilterCollections() {
            filteredCollections.clear();
            for (int i = 0; i < collections.size(); ++i) {
                final TL_stars.TL_starGiftCollection collection = collections.get(i);
                if (collection.gifts_count <= 0)
                    continue;
                filteredCollections.add(collection);
            }
        }

        public void updateGiftsCollections(TL_stars.SavedStarGift gift, int collection_id, boolean included) {
            for (GiftsList list : gifts.values()) {
                list.updateGiftsCollections(gift, collection_id, included);
            }
            if (all != null) {
                all.updateGiftsCollections(gift, collection_id, included);
            }
        }

        public void updateGiftsUnsaved(TL_stars.SavedStarGift gift, boolean unsaved) {
            for (GiftsList list : gifts.values()) {
                list.updateGiftsUnsaved(gift, unsaved);
            }
            if (all != null) {
                all.updateGiftsUnsaved(gift, unsaved);
            }
        }

        private long getHash(ArrayList<TL_stars.TL_starGiftCollection> array) {
            long hash = 0;
            for (TL_stars.TL_starGiftCollection collection : array) {
                hash = calcHash(hash, collection.hash);
            }
            return hash;
        }

        public GiftsList getListByIndex(int index) {
            if (index < 0 || index >= getCollections().size())
                return null;
            final TL_stars.TL_starGiftCollection collection = getCollections().get(index);
            return getListById(collection.collection_id);
        }

        public GiftsList getListById(int collection_id) {
            return gifts.get(collection_id);
        }

        public void load() {
            if (loading || loaded) return;

            loading = true;

            final TL_stars.getStarGiftCollections req = new TL_stars.getStarGiftCollections();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.hash = getHash(collections);
            currentRequestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TL_stars.TL_starGiftCollections) {
                    final TL_stars.TL_starGiftCollections r = (TL_stars.TL_starGiftCollections) res;

                    collections.clear();
                    collections.addAll(r.collections);
                    refilterCollections();

                    for (TL_stars.TL_starGiftCollection collection : collections) {
                        GiftsList list = getListById(collection.collection_id);
                        if (list != null) continue;
                        list = new GiftsList(currentAccount, dialogId, false);
                        list.setCollectionId(collection.collection_id);
                        gifts.put(collection.collection_id, list);
                    }

                    loaded = true;
                    loading = false;

                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starUserGiftCollectionsLoaded, dialogId, GiftsCollections.this);

                } else if (res instanceof TL_stars.TL_starGiftCollectionsNotModified) {
                    refilterCollections();

                    loaded = true;
                    loading = false;

                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starUserGiftCollectionsLoaded, dialogId, GiftsCollections.this);
                }
            }));
        }

        public boolean shown;

        public void invalidate(boolean load) {
            if (currentRequestId != -1) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(currentRequestId, true);
                currentRequestId = -1;
            }
            loading = false;
            loaded = false;
            if (load || shown) load();
        }

        public boolean creating;
        public void createCollection(String title, Utilities.Callback<TL_stars.TL_starGiftCollection> created) {
            if (creating) return;

            creating = true;

            final TL_stars.TL_starGiftCollection tempCollection = new TL_stars.TL_starGiftCollection();
            tempCollection.collection_id = -1;
            tempCollection.title = title;
            collections.add(tempCollection);
            refilterCollections();

            final GiftsList list = new GiftsList(currentAccount, dialogId, false);
            list.setCollectionId(-1);
            list.totalCount = 0;
            list.endReached = true;
            gifts.put(-1, list);

            final TL_stars.createStarGiftCollection req = new TL_stars.createStarGiftCollection();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.title = title;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                creating = false;
                if (res instanceof TL_stars.TL_starGiftCollection) {
                    final TL_stars.TL_starGiftCollection loaded = (TL_stars.TL_starGiftCollection) res;
                    collections.remove(tempCollection);
                    collections.add(loaded);
                    gifts.remove(-1);
                    list.collectionId = loaded.collection_id;
                    gifts.put(loaded.collection_id, list);
                    refilterCollections();

                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starUserGiftCollectionsLoaded, dialogId, GiftsCollections.this);

                    if (created != null) {
                        created.run(loaded);
                    }
                } else {
                    if (err != null) {
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            BulletinFactory.of(lastFragment).showForError(err);
                        }
                    }
                    collections.remove(tempCollection);
                    gifts.remove(-1);
                    refilterCollections();

                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starUserGiftCollectionsLoaded, dialogId, GiftsCollections.this);
                }
            }));
        }

        public TL_stars.TL_starGiftCollection findById(int id) {
            for (int i = 0; i < collections.size(); ++i) {
                TL_stars.TL_starGiftCollection collection = collections.get(i);
                if (id == collection.collection_id) {
                    return collection;
                }
            }
            return null;
        }

        public int indexOf(int id) {
            for (int i = 0; i < collections.size(); ++i) {
                final TL_stars.TL_starGiftCollection collection = collections.get(i);
                if (id == collection.collection_id) {
                    return i;
                }
            }
            return -1;
        }

        public void removeCollection(int id) {
            final int index = indexOf(id);
            if (index == -1) return;

            final TL_stars.TL_starGiftCollection collection = collections.remove(index);
            gifts.remove(collection.collection_id);

            final TL_stars.deleteStarGiftCollection req = new TL_stars.deleteStarGiftCollection();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.collection_id = collection.collection_id;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
        }

        public void updateIcon(int id) {
            final GiftsList list = getListById(id);
            final TL_stars.TL_starGiftCollection collection = findById(id);
            if (list == null || collection == null) return;
            final TL_stars.SavedStarGift firstGift = list.gifts.isEmpty() ? null : list.gifts.get(0);
            if (firstGift == null) {
                collection.flags &=~ 1;
                collection.icon = null;
            } else {
                collection.flags |= 1;
                collection.icon = firstGift.gift.getDocument();
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starUserGiftCollectionsLoaded, dialogId, this);
        }

        public void rename(int id, String newName) {
            final TL_stars.updateStarGiftCollection req = new TL_stars.updateStarGiftCollection();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.collection_id = id;
            req.flags |= 1;
            req.title = newName;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
        }

        public void addGift(int id, TL_stars.SavedStarGift gift, boolean insert) {
            final ArrayList<TL_stars.SavedStarGift> gifts = new ArrayList<>();
            gifts.add(gift);
            addGifts(id, gifts, insert);
        }

        public void addGifts(int id, ArrayList<TL_stars.SavedStarGift> gifts, boolean insert) {
            if (gifts.isEmpty()) return;
            final GiftsList list = getListById(id);
            if (list != null && insert) {
                list.gifts.addAll(0, gifts);
                list.totalCount += gifts.size();
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starUserGiftsLoaded, dialogId, list);
                updateIcon(id);
            }
            final TL_stars.updateStarGiftCollection req = new TL_stars.updateStarGiftCollection();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.collection_id = id;
            req.flags |= 4;
            for (TL_stars.SavedStarGift gift : gifts) {
                updateGiftsCollections(gift, id, true);
                if (gift.msg_id > 0) {
                    final TL_stars.TL_inputSavedStarGiftUser inputGift = new TL_stars.TL_inputSavedStarGiftUser();
                    inputGift.msg_id = gift.msg_id;
                    req.add_stargift.add(inputGift);
                } else if (gift.saved_id != 0) {
                    final TL_stars.TL_inputSavedStarGiftChat inputGift = new TL_stars.TL_inputSavedStarGiftChat();
                    inputGift.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                    inputGift.saved_id = gift.saved_id;
                    req.add_stargift.add(inputGift);
                } else {
                    FileLog.w("can't convert gift to inputgift to add into the collection");
                }
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TL_stars.TL_starGiftCollection) {
                    final TL_stars.TL_starGiftCollection loaded = (TL_stars.TL_starGiftCollection) res;
                    int index = indexOf(loaded.collection_id);
                    if (index >= 0) {
                        collections.set(index, loaded);
                    }
                }
            }));
        }

        public void removeGift(int id, final TL_stars.SavedStarGift gift) {
            final ArrayList<TL_stars.SavedStarGift> gifts = new ArrayList<>();
            gifts.add(gift);
            removeGifts(id, gifts);
        }

        public void removeGifts(int id, final ArrayList<TL_stars.SavedStarGift> gifts) {
            if (gifts.isEmpty()) return;
            final GiftsList list = getListById(id);
            if (list != null && !list.gifts.isEmpty()) {
                for (int i = 0; i < list.gifts.size(); ++i) {
                    final TL_stars.SavedStarGift g = list.gifts.get(i);
                    boolean remove = false;
                    for (int j = 0; j < gifts.size(); ++j) {
                        if (eq(g, gifts.get(j))) {
                            remove = true;
                            break;
                        }
                    }
                    if (remove) {
                        list.gifts.remove(i);
                        list.totalCount = Math.max(0, list.totalCount - 1);
                        i--;
                    }
                }
            }
            updateIcon(id);
            final TL_stars.updateStarGiftCollection req = new TL_stars.updateStarGiftCollection();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.collection_id = id;
            req.flags |= 2;
            for (TL_stars.SavedStarGift gift : gifts) {
                updateGiftsCollections(gift, id, false);
                if (gift.msg_id > 0) {
                    final TL_stars.TL_inputSavedStarGiftUser inputGift = new TL_stars.TL_inputSavedStarGiftUser();
                    inputGift.msg_id = gift.msg_id;
                    req.delete_stargift.add(inputGift);
                } else if (gift.saved_id != 0) {
                    final TL_stars.TL_inputSavedStarGiftChat inputGift = new TL_stars.TL_inputSavedStarGiftChat();
                    inputGift.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                    inputGift.saved_id = gift.saved_id;
                    req.delete_stargift.add(inputGift);
                } else {
                    FileLog.w("can't convert gift to inputgift to add into the collection");
                }
            }
            final int count = req.delete_stargift.size();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TL_stars.TL_starGiftCollection) {
                    final TL_stars.TL_starGiftCollection loaded = (TL_stars.TL_starGiftCollection) res;
                    int index = indexOf(loaded.collection_id);
                    if (index >= 0) {
                        collections.set(index, loaded);
                    }
                }
            }));
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starUserGiftsLoaded, dialogId, list);
        }

        public void reorder(ArrayList<Integer> collectionIds) {
            final HashMap<Integer, TL_stars.TL_starGiftCollection> map = new HashMap<>();
            for (final TL_stars.TL_starGiftCollection collection : collections) {
                map.put(collection.collection_id, collection);
            }
            final ArrayList<TL_stars.TL_starGiftCollection> newCollections = new ArrayList<>();
            for (final int id : collectionIds) {
                final TL_stars.TL_starGiftCollection collection = map.get(id);
                if (collection != null) {
                    newCollections.add(collection);
                }
            }

            collections.clear();
            collections.addAll(newCollections);
            refilterCollections();
        }

        public void sendOrder() {
            final TL_stars.reorderStarGiftCollections req = new TL_stars.reorderStarGiftCollections();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            for (final TL_stars.TL_starGiftCollection collection : collections) {
                req.order.add(collection.collection_id);
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
            refilterCollections();
        }
    }

    public interface IGiftsList {
        void notifyUpdate();

        int getLoadedCount();
        void set(int index, Object obj);
        Object get(int index);
        int indexOf(Object object);
        int getTotalCount();
        void load();

        int findGiftToUpgrade(int from);
    }

    public static class GiftsList implements IGiftsList {

        public final int currentAccount;
        public final long dialogId;

        public GiftsList(int currentAccount, long dialogId) {
            this(currentAccount, dialogId, true);
        }
        public GiftsList(int currentAccount, long dialogId, boolean load) {
            this.currentAccount = currentAccount;
            this.dialogId = dialogId;
            if (load) load();
        }

        public boolean isCollection = false;
        public int collectionId;
        public void setCollectionId(int collection_id) {
            isCollection = true;
            collectionId = collection_id;
        }

        @Override
        public void notifyUpdate() {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starUserGiftsLoaded, dialogId, GiftsList.this);
        }

        public void updateGiftsCollections(TL_stars.SavedStarGift gift, int collection_id, boolean included) {
            for (final TL_stars.SavedStarGift g : gifts) {
                if (StarsController.eq(g, gift)) {
                    if (included) {
                        if (!g.collection_id.contains(collection_id))
                            g.collection_id.add((Integer) collection_id);
                    } else {
                        g.collection_id.remove((Integer) collection_id);
                    }
                }
            }
        }

        public void updateGiftsUnsaved(TL_stars.SavedStarGift gift, boolean unsaved) {
            boolean changed = false;
            for (final TL_stars.SavedStarGift g : gifts) {
                if (StarsController.eq(g, gift) && g.unsaved != unsaved) {
                    g.unsaved = unsaved;
                    changed = true;
                }
            }
            if (changed) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starUserGiftsLoaded, dialogId, GiftsList.this);
            }
        }

        public int findGiftToUpgrade(int fromIndex) {
            if (!StarGiftSheet.isMineWithActions(currentAccount, dialogId)) return -1;
            for (int index = fromIndex + 1; index < gifts.size(); ++index) {
                final TL_stars.SavedStarGift gift = gifts.get(index);
                if (gift.can_upgrade) {
                    return index;
                }
            }
            for (int index = fromIndex - 1; index >= 0; --index) {
                final TL_stars.SavedStarGift gift = gifts.get(index);
                if (gift.can_upgrade) {
                    return index;
                }
            }
            return -1;
        }

        public void set(int index, Object obj) {
            if (obj instanceof TL_stars.SavedStarGift) {
                try {
                    this.gifts.set(index, (TL_stars.SavedStarGift) obj);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        public boolean sort_by_date = true; // false => sort_by_value
        public boolean peer_color_available = false;

        public static final int INCLUDE_TYPE_UNLIMITED_FLAG = 1;
        public static final int INCLUDE_TYPE_LIMITED_FLAG = 1 << 1;
        public static final int INCLUDE_TYPE_UPGRADABLE_FLAG = 1 << 2;
        public static final int INCLUDE_TYPE_UNIQUE_FLAG = 1 << 3;
        private static final int INCLUDE_TYPE_MASK = INCLUDE_TYPE_UNLIMITED_FLAG | INCLUDE_TYPE_LIMITED_FLAG | INCLUDE_TYPE_UPGRADABLE_FLAG | INCLUDE_TYPE_UNIQUE_FLAG;

        public static final int INCLUDE_VISIBILITY_DISPLAYED_FLAG = 1 << 8;
        public static final int INCLUDE_VISIBILITY_HIDDEN_FLAG = 1 << 9;
        private static final int INCLUDE_VISIBILITY_MASK = INCLUDE_VISIBILITY_DISPLAYED_FLAG | INCLUDE_VISIBILITY_HIDDEN_FLAG;

        private int includeFlags = INCLUDE_TYPE_MASK | INCLUDE_VISIBILITY_MASK;

        private int getMask(int flag) {
            if ((flag & INCLUDE_TYPE_MASK) != 0) {
                return INCLUDE_TYPE_MASK;
            }
            if ((flag & INCLUDE_VISIBILITY_MASK) != 0) {
                return INCLUDE_VISIBILITY_MASK;
            }
            return 0;
        }

        public void forceTypeIncludeFlag(int flag, boolean invalidate) {
            final int mask = getMask(flag);

            int newFlags = includeFlags & ~mask | flag;
            if (this.includeFlags != newFlags) {
                this.includeFlags = newFlags;
                if (invalidate) {
                    invalidate(true);
                }
            }
        }

        public void toggleTypeIncludeFlag(int flag) {
            final int mask = getMask(flag);

            int flags = includeFlags & mask;
            flags = TLObject.setFlag(flags, flag, !TLObject.hasFlag(flags, flag));
            if (flags == 0) {
                flags = mask & ~flag;
            }

            int newFlags = includeFlags & ~mask | flags;

            if (this.includeFlags != newFlags) {
                this.includeFlags = newFlags;
                invalidate(true);
            }
        }

        public Boolean chat_notifications_enabled;

        public void resetFilters() {
            if (!hasFilters()) return;
            includeFlags = INCLUDE_TYPE_MASK | INCLUDE_VISIBILITY_MASK;
            sort_by_date = true;
            invalidate(true);
        }

        public void setFilters(int flags) {
            includeFlags = flags;
            sort_by_date = true;
            invalidate(true);
        }

        public boolean hasFilters() {
            return !(sort_by_date && includeFlags == (INCLUDE_TYPE_MASK | INCLUDE_VISIBILITY_MASK));
        }

        public boolean isInclude_unlimited() {
            return TLObject.hasFlag(includeFlags, INCLUDE_TYPE_UNLIMITED_FLAG);
        }

        public boolean isInclude_limited() {
            return TLObject.hasFlag(includeFlags, INCLUDE_TYPE_LIMITED_FLAG);
        }

        public boolean isInclude_upgradable() {
            return TLObject.hasFlag(includeFlags, INCLUDE_TYPE_UPGRADABLE_FLAG);
        }

        public boolean isInclude_unique() {
            return TLObject.hasFlag(includeFlags, INCLUDE_TYPE_UNIQUE_FLAG);
        }

        public boolean isInclude_displayed() {
            return TLObject.hasFlag(includeFlags, INCLUDE_VISIBILITY_DISPLAYED_FLAG);
        }

        public boolean isInclude_hidden() {
            return TLObject.hasFlag(includeFlags, INCLUDE_VISIBILITY_HIDDEN_FLAG);
        }

        public boolean loading;
        public boolean endReached;
        public String lastOffset;
        public ArrayList<TL_stars.SavedStarGift> gifts = new ArrayList<>();
        public int currentRequestId = -1;
        public int totalCount;

        public int getTotalCount() {
            return totalCount;
        }

        public int getLoadedCount() {
            return gifts.size();
        }

        public Object get(int index) {
            if (index < 0 || index >= gifts.size())
                return null;
            return gifts.get(index);
        }

        public int indexOf(Object object) {
            return gifts.indexOf(object);
        }

        public boolean shown;

        public void invalidate(boolean load) {
            if (currentRequestId != -1) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(currentRequestId, true);
                currentRequestId = -1;
            }
            loading = false;
            gifts.clear();
            lastOffset = null;
            endReached = false;
            if (load || shown) load();
        }

        public void load() {
            if (loading || endReached) return;

            boolean first = lastOffset == null;
            loading = true;
            final TL_stars.getSavedStarGifts req = new TL_stars.getSavedStarGifts();
            req.sort_by_value = !sort_by_date;
            req.exclude_unupgradable = !isInclude_limited();
            req.exclude_upgradable = !isInclude_upgradable();
            req.exclude_unlimited = !isInclude_unlimited();
            req.exclude_unique = !isInclude_unique();
            req.exclude_saved = !isInclude_displayed();
            req.exclude_unsaved = !isInclude_hidden();
            req.peer_color_available = peer_color_available;
            if (dialogId == 0) {
                req.peer = new TLRPC.TL_inputPeerSelf();
            } else {
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            }
            req.offset = first ? "" : lastOffset;
            req.limit = first ? Math.max(MessagesController.getInstance(currentAccount).stargiftsPinnedToTopLimit, 15) : 30;
            final int[] reqId = new int[1];
            if (isCollection) {
                req.flags |= 64;
                req.collection_id = collectionId;
            }
            reqId[0] = currentRequestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (reqId[0] != currentRequestId) return;
                loading = false;
                currentRequestId = -1;
                if (res instanceof TL_stars.TL_payments_savedStarGifts) {
                    final TL_stars.TL_payments_savedStarGifts rez = (TL_stars.TL_payments_savedStarGifts) res;
                    MessagesController.getInstance(currentAccount).putUsers(rez.users, false);
                    MessagesController.getInstance(currentAccount).putChats(rez.chats, false);

                    if (first) {
                        gifts.clear();
                    }
                    gifts.addAll(rez.gifts);
                    lastOffset = rez.next_offset;
                    totalCount = rez.count;
                    chat_notifications_enabled = (rez.flags & 2) != 0 ? rez.chat_notifications_enabled : null;
                    endReached = gifts.size() > totalCount || lastOffset == null;
                } else {
                    endReached = true;
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starUserGiftsLoaded, dialogId, GiftsList.this);
            }));
        }

        public void cancel() {
            if (currentRequestId != -1) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(currentRequestId, true);
                currentRequestId = -1;
            }
            loading = false;
        }

        public int getCount() {
            return totalCount;
        }

        public ArrayList<TL_stars.SavedStarGift> getPinned() {
            final ArrayList<TL_stars.SavedStarGift> pinned = new ArrayList<>();
            for (int i = 0; i < gifts.size(); ++i) {
                final TL_stars.SavedStarGift gift = gifts.get(i);
                if (gift.pinned_to_top && !gift.unsaved) {
                    pinned.add(gift);
                }
            }
            return pinned;
        }

        public boolean eq(ArrayList<TL_stars.SavedStarGift> a, ArrayList<TL_stars.SavedStarGift> b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); ++i) {
                if (a.get(i) != b.get(i)) return false;
            }
            return true;
        }

        public TL_stars.InputSavedStarGift getInput(TL_stars.SavedStarGift gift) {
            if (gift == null) return null;
            if ((gift.flags & 8) != 0) {
                TL_stars.TL_inputSavedStarGiftUser input = new TL_stars.TL_inputSavedStarGiftUser();
                input.msg_id = gift.msg_id;
                return input;
            } else {
                TL_stars.TL_inputSavedStarGiftChat input = new TL_stars.TL_inputSavedStarGiftChat();
                input.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                input.saved_id = gift.saved_id;
                return input;
            }
        }

        public void setPinned(ArrayList<TL_stars.SavedStarGift> newPinned) {
            gifts.removeAll(newPinned);
            if (sort_by_date && !isCollection) {
                Collections.sort(gifts, (a, b) -> b.date - a.date);
            }
            gifts.addAll(0, newPinned);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starUserGiftsLoaded, dialogId, GiftsList.this);
            sendPinnedOrder();
        }

        public boolean togglePinned(TL_stars.SavedStarGift gift, boolean pin, boolean fixLimit) {
            if (gift == null) {
                return false;
            }
            boolean hitLimit = false;
            final ArrayList<TL_stars.SavedStarGift> pinned = getPinned();
            if (pinned.contains(gift)) {
                if (pin) {
                    return false;
                }
                pinned.remove(gift);
            } else {
                if (!pin) {
                    return false;
                }
                if (pinned.size() + 1 > MessagesController.getInstance(currentAccount).stargiftsPinnedToTopLimit) {
                    if (fixLimit) {
                        hitLimit = true;
                        while (pinned.size() > 0 && pinned.size() + 1 > MessagesController.getInstance(currentAccount).stargiftsPinnedToTopLimit) {
                            TL_stars.SavedStarGift pinnedGift = pinned.remove(pinned.size() - 1);
                            pinnedGift.pinned_to_top = false;
                        }
                    } else {
                        return true;
                    }
                }
                pinned.add(gift);
            }
            gift.pinned_to_top = pin;
            gifts.removeAll(pinned);
            if (sort_by_date && !isCollection) {
                Collections.sort(gifts, (a, b) -> b.date - a.date);
            }
            gifts.addAll(0, pinned);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starUserGiftsLoaded, dialogId, GiftsList.this);
            sendPinnedOrder();
            return hitLimit;
        }

        private ArrayList<TL_stars.SavedStarGift> savedPinnedState;

        public void reorderPinned(int fromPosition, int toPosition) {
            if (savedPinnedState == null) {
                savedPinnedState = getPinned();
            }
            reorder(fromPosition, toPosition);
        }

        public void reorder(int fromPosition, int toPosition) {
            fromPosition = Utilities.clamp(fromPosition, gifts.size() - 1, 0);
            if (fromPosition < 0 || fromPosition >= gifts.size()) return;

            final TL_stars.SavedStarGift g = gifts.remove(fromPosition);

            toPosition = Utilities.clamp(toPosition, gifts.size() - 1, 0);
            if (toPosition < 0 || toPosition >= gifts.size()) return;

            gifts.add(toPosition, g);
        }

        public void reorderDone() {
            if (savedPinnedState == null || eq(savedPinnedState, getPinned())) {
                savedPinnedState = null;
                return;
            }
            sendPinnedOrder();
            savedPinnedState = null;
        }

        public void sendPinnedOrder() {
            if (isCollection) {
                final TL_stars.updateStarGiftCollection req = new TL_stars.updateStarGiftCollection();
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                req.collection_id = collectionId;
                req.flags |= 8;
                for (TL_stars.SavedStarGift g : gifts) {
                    req.order.add(getInput(g));
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, null, ConnectionsManager.RequestFlagInvokeAfter);
            } else {
                final TL_stars.toggleStarGiftsPinnedToTop req = new TL_stars.toggleStarGiftsPinnedToTop();
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                for (TL_stars.SavedStarGift pinnedGift : getPinned()) {
                    req.stargift.add(getInput(pinnedGift));
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {

                }, ConnectionsManager.RequestFlagInvokeAfter);
            }
        }

        public boolean contains(final TL_stars.SavedStarGift gift) {
            for (final TL_stars.SavedStarGift g : gifts) {
                if (StarsController.eq(g, gift)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean eq(final TL_stars.SavedStarGift a, final TL_stars.SavedStarGift b) {
        return (
            (a.flags & 2048) != 0 && (b.flags & 2048) != 0 && a.saved_id == b.saved_id ||
            (a.flags & 8) != 0 && (b.flags & 8) != 0 && a.msg_id == b.msg_id
        );
    }

    public TL_stars.SavedStarGift findUserStarGift(long collection_id) {
        for (int i = 0; i < giftLists.size(); ++i) {
            final GiftsList list = giftLists.valueAt(i);
            for (int j = 0; j < list.gifts.size(); ++j) {
                final TL_stars.SavedStarGift gift = list.gifts.get(j);
                if (gift != null && gift.gift != null && gift.gift.id == collection_id) {
                    return gift;
                }
            }
        }
        return null;
    }

    public static <T extends TL_stars.StarGiftAttribute> T findAttribute(ArrayList<TL_stars.StarGiftAttribute> attributes, Class<T> clazz) {
        if (attributes == null) {
            return null;
        }
        for (TL_stars.StarGiftAttribute attribute : attributes) {
            if (clazz.isInstance(attribute)) {
                return clazz.cast(attribute);
            }
        }
        return null;
    }

    public static <T extends TL_stars.StarGiftAttribute> ArrayList<T> findAttributes(ArrayList<TL_stars.StarGiftAttribute> attributes, Class<T> clazz) {
        final ArrayList<T> result = new ArrayList<>();
        for (TL_stars.StarGiftAttribute attribute : attributes) {
            if (clazz.isInstance(attribute)) {
                result.add(clazz.cast(attribute));
            }
        }
        return result;
    }

    private ConcurrentHashMap<Long, TL_stars.starGiftUpgradePreview> giftPreviews = new ConcurrentHashMap<>();

    public void getStarGiftPreview(long gift_id, Utilities.Callback<TL_stars.starGiftUpgradePreview> got) {
        if (got == null) return;
        TL_stars.starGiftUpgradePreview cached = giftPreviews.get(gift_id);
        if (cached != null) {
            got.run(cached);
            return;
        }

        TL_stars.getStarGiftUpgradePreview req = new TL_stars.getStarGiftUpgradePreview();
        req.gift_id = gift_id;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TL_stars.starGiftUpgradePreview) {
                giftPreviews.put(gift_id, (TL_stars.starGiftUpgradePreview) res);
                got.run((TL_stars.starGiftUpgradePreview) res);
            } else {
                got.run(null);
            }
        }));
    }

    public void getUserStarGift(TL_stars.InputSavedStarGift inputSavedStarGift, Utilities.Callback<TL_stars.SavedStarGift> got) {
        if (got == null) return;
        final AlertDialog progressDialog = new AlertDialog(ApplicationLoader.applicationContext, AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.showDelayed(200);
        final TL_stars.getSavedStarGift req = new TL_stars.getSavedStarGift();
        req.stargift.add(inputSavedStarGift);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            progressDialog.dismiss();
            TL_stars.SavedStarGift upgradedGift = null;
            if (res instanceof TL_stars.TL_payments_savedStarGifts) {
                TL_stars.TL_payments_savedStarGifts r = (TL_stars.TL_payments_savedStarGifts) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                for (int i = 0; i < r.gifts.size(); ++i) {
                    TL_stars.SavedStarGift savedStarGift = r.gifts.get(i);
                    if (
                        inputSavedStarGift instanceof TL_stars.TL_inputSavedStarGiftUser && ((TL_stars.TL_inputSavedStarGiftUser) inputSavedStarGift).msg_id == savedStarGift.msg_id ||
                        inputSavedStarGift instanceof TL_stars.TL_inputSavedStarGiftChat && ((TL_stars.TL_inputSavedStarGiftChat) inputSavedStarGift).saved_id == savedStarGift.saved_id
                    ) {
                        upgradedGift = savedStarGift;
                        break;
                    }
                }
            }
            got.run(upgradedGift);
        }));
    }

    public void getPaidRevenue(long user_id, long parent_id, Utilities.Callback<Long> got) {
        final TL_account.getPaidMessagesRevenue req = new TL_account.getPaidMessagesRevenue();
        req.user_id = MessagesController.getInstance(currentAccount).getInputUser(user_id);
        if (parent_id != 0) {
            req.parent_peer = MessagesController.getInstance(currentAccount).getInputPeer(parent_id);
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TL_account.paidMessagesRevenue) {
                got.run(((TL_account.paidMessagesRevenue) res).stars_amount);
            } else {
                got.run(0L);
            }
        }));
    }

    public void stopPaidMessages(long user_id, long parent_id, boolean refund, boolean stop) {
        TL_account.toggleNoPaidMessagesException req = new TL_account.toggleNoPaidMessagesException();
        req.user_id = MessagesController.getInstance(currentAccount).getInputUser(user_id);
        if (parent_id != 0) {
            req.parent_peer = MessagesController.getInstance(currentAccount).getInputPeer(parent_id);
        }
        req.refund_charged = refund;
        req.require_payment = !stop;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.TL_boolTrue) {
                if (parent_id != 0) {
                    processUpdateMonoForumNoPaidException(-parent_id, user_id, stop);
                } else {
                    TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(user_id);
                    if (userFull != null && userFull.settings != null) {
                        userFull.settings.flags &= ~16384;
                        userFull.settings.charge_paid_message_stars = 0;
                    }
                    MessagesController.getNotificationsSettings(currentAccount).edit().putLong("dialog_bar_paying_" + user_id, 0L).apply();
                    MessagesController.getInstance(currentAccount).loadPeerSettings(
                            MessagesController.getInstance(currentAccount).getUser(user_id),
                            MessagesController.getInstance(currentAccount).getChat(-user_id),
                            true
                    );
                    ContactsController.getInstance(currentAccount).loadPrivacySettings(true);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesFeeUpdated, user_id);
                }
            }
        }));
    }

    public void processUpdateMonoForumNoPaidException(long channelId, long userId, boolean nopaidMessagesException) {
        TopicsController topicsController = MessagesController.getInstance(currentAccount).getTopicsController();
        TLRPC.TL_forumTopic topic = topicsController.findTopic(channelId, userId);
        if (topic != null) {
            topic.nopaid_messages_exception = nopaidMessagesException;
            topicsController.saveTopics(channelId);

            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesFeeUpdated, userId);
        }
    }



    public static final long PAID_MESSAGES_TIMEOUT = 3_000;
    private class PaidMessagesToast {

        public final BaseFragment fragment;
        public final long dialogId;

        public final Bulletin bulletin;
        public final Bulletin.TwoLineAnimatedLottieLayout bulletinLayout;
        public final Bulletin.UndoButton bulletinButton;
        public final Bulletin.TimerView timerView;

        public int totalMessagesCount;
        public long totalStars;
        public Utilities.Callback<HashSet<MessageObject>> undoListener;
        public final ArrayList<Runnable> totalSendListeners = new ArrayList<>();
        public final HashSet<MessageObject> messages = new HashSet<>();

        public long startTime = System.currentTimeMillis();
        public boolean undoRunning = true;

        public PaidMessagesToast(
            BaseFragment fragment,
            long dialogId
        ) {
            this.fragment = fragment;
            this.dialogId = dialogId;

            final Context context = getContext(fragment);
            bulletinLayout = new Bulletin.TwoLineAnimatedLottieLayout(context, fragment.getResourceProvider());
            bulletinLayout.setAnimation(R.raw.stars_topup);

            timerView = new Bulletin.TimerView(context, fragment.getResourceProvider());
            timerView.timeLeft = PAID_MESSAGES_TIMEOUT;
            timerView.setColor(Theme.getColor(Theme.key_undo_cancelColor, fragment.getResourceProvider()));
            bulletinButton = new Bulletin.UndoButton(context, true, false, fragment.getResourceProvider());
            bulletinButton.setText(LocaleController.getString(R.string.StarsSentUndo));
            bulletinButton.setUndoAction(this::undo);
            bulletinButton.addView(timerView, LayoutHelper.createFrame(20, 20, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
            bulletinButton.undoTextView.setPadding(dp(12), dp(8), dp(20 + 10), dp(8));
            bulletinLayout.setButton(bulletinButton);

            bulletin = BulletinFactory.of(fragment).create(bulletinLayout, -1);
            bulletin.hideAfterBottomSheet = false;
            bulletin.show(true);
            bulletin.setOnHideListener(this::send);

            AndroidUtilities.cancelRunOnUIThread(sendRunnable);
            AndroidUtilities.runOnUIThread(sendRunnable, PAID_MESSAGES_TIMEOUT);
        }

        public CharSequence getTitle() {
            if (totalMessagesCount == 1) {
                return LocaleController.getString(R.string.PaidMessageSentTitleOne);
            } else {
                return LocaleController.formatPluralString("PaidMessageSentTitle", totalMessagesCount);
            }
        }

        public CharSequence getSubtitle() {
            return AndroidUtilities.replaceTags(LocaleController.formatPluralStringComma("PaidMessageSentSubtitle", Math.max(0, (int) totalStars)));
        }

        public boolean isUndoRunning() {
            return totalMessagesCount > 0 && undoRunning;
        }

        public boolean isVisible() {
            return !undone && !sent;
        }

        public boolean push(MessageObject messageObject, long payStars, Utilities.Callback<HashSet<MessageObject>> undo, Runnable send, boolean needsUndo) {
            if (undone || sent) return false;

            totalMessagesCount += 1;
            messages.add(messageObject);

            totalStars += payStars;
            undoListener = undo;
            if (send != null) totalSendListeners.add(send);

            if (undoRunning && !needsUndo) {
                undoRunning = false;
                AndroidUtilities.cancelRunOnUIThread(sendRunnable);
                bulletin.setDuration(Bulletin.DURATION_PROLONG);
                bulletin.setCanHide(true);

                if (System.currentTimeMillis() - startTime > 500) {
                    bulletinButton.animate().alpha(0.0f).scaleX(0.3f).scaleY(0.3f).start();
                } else {
                    bulletinButton.setAlpha(0.0f);
                    bulletinButton.setVisibility(View.GONE);
                }
            }

            if (timerView != null && undoRunning) {
                timerView.timeLeft = PAID_MESSAGES_TIMEOUT;
                AndroidUtilities.cancelRunOnUIThread(sendRunnable);
                AndroidUtilities.runOnUIThread(sendRunnable, PAID_MESSAGES_TIMEOUT);
            }

            bulletinLayout.titleTextView.setText(getTitle());
            bulletinLayout.subtitleTextView.setText(getSubtitle());
            bulletinLayout.imageView.playAnimation();

            return true;
        }

        public boolean pop(int messageId) {
            if (undone || sent) return false;

            MessageObject removedMessageObject = null;
            for (MessageObject messageObject : messages) {
                if (messageObject.getId() == messageId) {
                    messages.remove(removedMessageObject = messageObject);
                    break;
                }
            }

            if (messages.isEmpty()) {
                undone = true;
                bulletin.hide();
                return true;
            }

            totalMessagesCount--;
            if (removedMessageObject != null && removedMessageObject.messageOwner != null) {
                totalStars -= removedMessageObject.messageOwner.paid_message_stars;
            }

            if (timerView != null) {
                timerView.timeLeft = PAID_MESSAGES_TIMEOUT;
                AndroidUtilities.cancelRunOnUIThread(sendRunnable);
                AndroidUtilities.runOnUIThread(sendRunnable, PAID_MESSAGES_TIMEOUT);
            }

            bulletinLayout.titleTextView.setText(getTitle());
            bulletinLayout.subtitleTextView.setText(getSubtitle());
            bulletinLayout.imageView.playAnimation();

            return false;
        }

        private boolean undone, sent;

        public void undo() {
            if (undone || sent || !undoRunning) return;
            undone = true;

            if (undoListener != null) {
                undoListener.run(messages);
            }
            if (bulletinButton != null) {
                bulletin.hide();
            }
        }

        private final Runnable sendRunnable = this::send;
        public void send() {
            if (undone || sent) return;
            sent = true;

            for (Runnable listener : totalSendListeners) {
                listener.run();
            }
            if (bulletinButton != null) {
                bulletin.hide();
            }
        }
    }
    private PaidMessagesToast currentPaidMessagesToast;

    public void showPaidMessageToast(
        long dialogId,
        MessageObject messageObject,
        long payStars,
        Utilities.Callback<HashSet<MessageObject>> undo,
        Runnable send,
        boolean needsUndo
    ) {
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (currentPaidMessagesToast != null && (currentPaidMessagesToast.sent || currentPaidMessagesToast.undone)) {
            currentPaidMessagesToast = null;
        }
        if (currentPaidMessagesToast != null) {
            if (fragment == null || fragment.isRemovingFromStack() || currentPaidMessagesToast.dialogId != dialogId || currentPaidMessagesToast.fragment != fragment) {
                currentPaidMessagesToast.send();
                currentPaidMessagesToast = null;
            }
        }
        if (fragment == null || fragment.isRemovingFromStack()) {
            if (send != null) {
                send.run();
            }
            return;
        }

        if (currentPaidMessagesToast == null) {
            currentPaidMessagesToast = new PaidMessagesToast(fragment, dialogId);
        }
        if (!currentPaidMessagesToast.push(messageObject, payStars, undo, send, needsUndo)) {
            if (send != null) {
                send.run();
            }
        }
    }

    public void hidePaidMessageToast(MessageObject messageObject) {
        if (messageObject == null) return;
        if (currentPaidMessagesToast != null && currentPaidMessagesToast.dialogId == messageObject.getDialogId()) {
            if (currentPaidMessagesToast.pop(messageObject.getId())) {
                currentPaidMessagesToast = null;
            }
        }
    }

    public final ConcurrentHashMap<Long, Long> justAgreedToNotAskDialogs = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<Long, Integer> sendingMessagesCount = new ConcurrentHashMap<>();

    private final Set<Integer> sendingPaidMessagesIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ConcurrentHashMap<Integer, Runnable> postponedPaidMessages = new ConcurrentHashMap<>();

    private boolean needsUndoButton(MessageObject msg, long payStars) {
        if (currentPaidMessagesToast != null && currentPaidMessagesToast.isUndoRunning() && currentPaidMessagesToast.isVisible()) {
            // toast with undo already ticking
            return true;
        }

        if (AlertsCreator.needsPaidMessageAlert(currentAccount, msg.getDialogId())) {
            // we already shown an alert
            return false;
        }
        final Long agreedTime = justAgreedToNotAskDialogs.get(msg.getDialogId());
        if (agreedTime != null && System.currentTimeMillis() - agreedTime > 1000 * 5) {
            // we already shown an alert: and agreed to not show it again
            return false;
        }

        final Integer count = sendingMessagesCount.get(msg.getDialogId());
        if (count != null && count >= 3) {
            return true;
        }

        if (payStars < 100) {
            return false;
        }

        return true;
    }

    public void beforeSendingMessage(MessageObject msg) {
        if (msg == null) return;
        if (msg.messageOwner == null) return;
        final long price = msg.messageOwner.paid_message_stars;
        if (price <= 0) return;

        final boolean needsUndo = needsUndoButton(msg, price);

        final int id = msg.getId();
        if (needsUndo) {
            sendingPaidMessagesIds.add(id);
        }
        showPaidMessageToast(
            msg.getDialogId(),
            msg,
            price,
            /* undo */ (messages) -> {
                if (!needsUndo) {
                    return;
                }
                SendMessagesHelper.getInstance(currentAccount).cancelSendingMessage(new ArrayList<>(messages));
            },
            /* send */ () -> {
                if (!needsUndo) {
                    return;
                }
                sendingPaidMessagesIds.remove(id);
                Runnable send = postponedPaidMessages.remove(id);
                if (send != null) {
                    send.run();
                }
            },
            needsUndo
        );
    }

    // returns true if request should be sent
    public boolean beforeSendingFinalRequest(TLObject req, MessageObject msg, Runnable send) {
        if (msg == null) return true;
        if (msg.messageOwner == null) return true;

        final int id = msg.getId();
        final long requestPrice = getAllowedPaidStars(req);

        if (requestPrice <= 0) return true;

        if (sendingPaidMessagesIds.remove(id)) {
            postponedPaidMessages.put(id, send);
            return false;
        }

        return true;
    }

    public boolean beforeSendingFinalRequest(TLObject req, ArrayList<MessageObject> messages, Runnable send) {
        if (messages == null || messages.isEmpty()) return true;

        final long requestPrice = getAllowedPaidStars(req);
        if (requestPrice <= 0) return true;

        final HashSet<Integer> finalIds = new HashSet<>();

        boolean postponing = false;
        for (MessageObject msg : messages) {
            final int id = msg.getId();
            finalIds.add(id);
            if (sendingPaidMessagesIds.remove(id)) {
                postponedPaidMessages.put(id, () -> {
                    for (int id2 : finalIds) {
                        sendingPaidMessagesIds.remove(id2);
                        postponedPaidMessages.remove(id2);
                    }
                    send.run();
                });
                postponing = true;
            }
        }
        return !postponing;
    }

    public static long getAllowedPaidStars(TLObject req) {
        if (req instanceof TLRPC.TL_messages_sendMessage) {
            return ((TLRPC.TL_messages_sendMessage) req).allow_paid_stars;
        } else if (req instanceof TLRPC.TL_messages_sendMultiMedia) {
            return ((TLRPC.TL_messages_sendMultiMedia) req).allow_paid_stars;
        } else if (req instanceof TLRPC.TL_messages_sendInlineBotResult) {
            return ((TLRPC.TL_messages_sendInlineBotResult) req).allow_paid_stars;
        } else if (req instanceof TLRPC.TL_messages_forwardMessages) {
            return ((TLRPC.TL_messages_forwardMessages) req).allow_paid_stars / ((TLRPC.TL_messages_forwardMessages) req).id.size();
        } else if (req instanceof TLRPC.TL_messages_sendMedia) {
            return ((TLRPC.TL_messages_sendMedia) req).allow_paid_stars;
        }
        return 0;
    }

    public static long getPeer(TLObject req) {
        if (req instanceof TLRPC.TL_messages_sendMessage) {
            return DialogObject.getPeerDialogId(((TLRPC.TL_messages_sendMessage) req).peer);
        } else if (req instanceof TLRPC.TL_messages_sendMultiMedia) {
            return DialogObject.getPeerDialogId(((TLRPC.TL_messages_sendMultiMedia) req).peer);
        } else if (req instanceof TLRPC.TL_messages_sendInlineBotResult) {
            return DialogObject.getPeerDialogId(((TLRPC.TL_messages_sendInlineBotResult) req).peer);
        } else if (req instanceof TLRPC.TL_messages_forwardMessages) {
            return DialogObject.getPeerDialogId(((TLRPC.TL_messages_forwardMessages) req).to_peer);
        } else if (req instanceof TLRPC.TL_messages_sendMedia) {
            return DialogObject.getPeerDialogId(((TLRPC.TL_messages_sendMedia) req).peer);
        }
        return 0;
    }

    public void showPriceChangedToast(List<MessageObject> msgs) {
        if (msgs == null || msgs.isEmpty()) return;
        final MessageObject msg = msgs.get(0);
        final long dialogId = msg.getDialogId();
        if (dialogId >= 0) {
            MessagesController.getInstance(currentAccount).loadFullUser(MessagesController.getInstance(currentAccount).getUser(dialogId), 0, true);
        } else {
            MessagesController.getInstance(currentAccount).loadFullChat(-dialogId, 0, true);
        }
        final CharSequence text = StarsIntroActivity.replaceStars(TextUtils.concat(
            LocaleController.formatPluralString("PaidMessagesSendErrorToast1", (int) msg.messageOwner.errorAllowedPriceStars),
            " ",
            LocaleController.formatPluralString("PaidMessagesSendErrorToast2", (int) msg.messageOwner.errorNewPriceStars)
        ));
        BulletinFactory.of(LaunchActivity.getSafeLastFragment())
            .createSimpleBulletin(R.raw.error, text)
            .show();
    }

    public static boolean isEnoughAmount(int currentAccount, AmountUtils.Amount amount) {
        if (amount == null) {
            return true;
        }

        AmountUtils.Amount balance = getInstance(currentAccount, amount.currency).getBalanceAmount();
        return balance.asNano() >= amount.asNano();
    }
}
