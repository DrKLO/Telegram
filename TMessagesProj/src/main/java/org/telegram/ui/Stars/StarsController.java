package org.telegram.ui.Stars;

import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getCurrencyExpDivider;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams;

import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PaymentFormActivity;
import org.telegram.ui.bots.BotWebViewSheet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class StarsController {

    public static final String currency = "XTR";

    private static volatile StarsController[] Instance = new StarsController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];
    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }

    public static StarsController getInstance(int num) {
        StarsController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects[num]) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new StarsController(num);
                }
            }
        }
        return localInstance;
    }

    public final int currentAccount;

    private StarsController(int account) {
        currentAccount = account;
    }

    private long lastBalanceLoaded;
    private boolean balanceLoading, balanceLoaded;
    public long balance;
    public long getBalance() {
        return getBalance(null);
    }

    public long getBalance(Runnable loaded) {
        if ((!balanceLoaded || System.currentTimeMillis() - lastBalanceLoaded > 1000 * 60) && !balanceLoading) {
            balanceLoading = true;
            TLRPC.TL_payments_getStarsStatus req = new TLRPC.TL_payments_getStarsStatus();
            req.peer = new TLRPC.TL_inputPeerSelf();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                boolean updatedTransactions = false;
                boolean updatedBalance = !balanceLoaded;
                lastBalanceLoaded = System.currentTimeMillis();
                if (res instanceof TLRPC.TL_payments_starsStatus) {
                    TLRPC.TL_payments_starsStatus r = (TLRPC.TL_payments_starsStatus) res;
                    MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                    MessagesController.getInstance(currentAccount).putChats(r.chats, false);

                    if (transactions[ALL_TRANSACTIONS].isEmpty()) {
                        for (TLRPC.TL_starsTransaction t : r.history) {
                            transactions[ALL_TRANSACTIONS].add(t);
                            transactions[t.stars > 0 ? INCOMING_TRANSACTIONS : OUTGOING_TRANSACTIONS].add(t);
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

                    if (this.balance != r.balance) {
                        updatedBalance = true;
                    }
                    this.balance = r.balance;
                }
                balanceLoading = false;
                balanceLoaded = true;
                if (updatedBalance) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
                }
                if (updatedTransactions) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starTransactionsLoaded);
                }

                if (loaded != null) {
                    loaded.run();
                }
            }));
        }
        return balance;
    }

    public void updateBalance(long balance) {
        if (this.balance != balance) {
            this.balance = balance;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
        }
    }

    public boolean balanceAvailable() {
        return balanceLoaded;
    }

    private static boolean isCollapsed(long stars) {
        return (
            stars != 15 &&
            stars != 75 &&
            stars != 250 &&
            stars != 500 &&
            stars != 1000 &&
            stars != 2500
        );
    }

    private boolean optionsLoading, optionsLoaded;
    private ArrayList<TLRPC.TL_starsTopupOption> options;
    public ArrayList<TLRPC.TL_starsTopupOption> getOptions() {
        if (optionsLoading || optionsLoaded) {
            return options;
        }
        optionsLoading = true;
        ConnectionsManager.getInstance(currentAccount).sendRequest(new TLRPC.TL_payments_getStarsTopupOptions(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            ArrayList<TLRPC.TL_starsTopupOption> loadedOptions = new ArrayList<>();
            ArrayList<TLRPC.TL_starsTopupOption> toLoadStorePrice = new ArrayList<>();
            if (res instanceof TLRPC.Vector) {
                TLRPC.Vector vector = (TLRPC.Vector) res;
                for (Object object : vector.objects) {
                    if (object instanceof TLRPC.TL_starsTopupOption) {
                        TLRPC.TL_starsTopupOption option = (TLRPC.TL_starsTopupOption) object;
                        loadedOptions.add(option);
                        option.collapsed = isCollapsed(option.stars);
                        if (option.store_product != null && !BuildVars.useInvoiceBilling()) {
                            toLoadStorePrice.add(option);
                            option.loadingStorePrice = true;
                        }
                    }
                }
            }
            options = loadedOptions;
            optionsLoaded = true;
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
                                TLRPC.TL_starsTopupOption option = null;
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

    private void bulletinError(TLRPC.TL_error err, String str) {
        bulletinError(err == null ? str : err.text);
    }
    private void bulletinError(String err) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        BulletinFactory b = fragment != null && fragment.visibleDialog == null ? BulletinFactory.of(fragment) : BulletinFactory.global();
        b.createSimpleBulletin(R.raw.error, LocaleController.formatString(R.string.UnknownErrorCode, err)).show();
    }

    public static final int ALL_TRANSACTIONS = 0;
    public static final int INCOMING_TRANSACTIONS = 1;
    public static final int OUTGOING_TRANSACTIONS = 2;

    public final ArrayList<TLRPC.TL_starsTransaction>[] transactions = new ArrayList[] { new ArrayList<>(), new ArrayList<>(), new ArrayList<>() };
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

        TLRPC.TL_payments_getStarsTransactions req = new TLRPC.TL_payments_getStarsTransactions();
        req.peer = new TLRPC.TL_inputPeerSelf();
        req.inbound = type == INCOMING_TRANSACTIONS;
        req.outbound = type == OUTGOING_TRANSACTIONS;
        req.offset = offset[type];
        if (req.offset == null) {
            req.offset = "";
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            loading[type] = false;
            if (res instanceof TLRPC.TL_payments_starsStatus) {
                TLRPC.TL_payments_starsStatus r = (TLRPC.TL_payments_starsStatus) res;
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

    public Theme.ResourcesProvider getResourceProvider() {
        BaseFragment lastFragment = LaunchActivity.getLastFragment();
        if (lastFragment != null) {
            return lastFragment.getResourceProvider();
        }
        return null;
    }

    public void buy(Activity activity, TLRPC.TL_starsTopupOption option, Utilities.Callback2<Boolean, String> whenDone) {
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
            TLRPC.TL_inputStorePaymentStars payload = new TLRPC.TL_inputStorePaymentStars();
            payload.stars = option.stars;
            payload.currency = option.currency;
            payload.amount = option.amount;

            TLRPC.TL_inputInvoiceStars invoice = new TLRPC.TL_inputInvoiceStars();
            invoice.option = option;

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

        TLRPC.TL_inputStorePaymentStars payload = new TLRPC.TL_inputStorePaymentStars();
        payload.stars = option.stars;
        payload.currency = option.currency;
        payload.amount = option.amount;
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
        }));
    }

    public void pay(MessageObject messageObject, Runnable whenShown) {
        final Context context = LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
        final Theme.ResourcesProvider resourcesProvider = getResourceProvider();

        if (context == null) {
            return;
        }

        if (!(MessageObject.getMedia(messageObject) instanceof TLRPC.TL_messageMediaInvoice)) {
            return;
        }

        if (whenShown != null) {
            whenShown.run();
        }

        TLRPC.TL_inputInvoiceMessage inputInvoice = new TLRPC.TL_inputInvoiceMessage();
        inputInvoice.peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());
        inputInvoice.msg_id = messageObject.getId();

        TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
        final JSONObject themeParams = BotWebViewSheet.makeThemeParams(resourcesProvider);
        if (themeParams != null) {
            req.theme_params = new TLRPC.TL_dataJSON();
            req.theme_params.data = themeParams.toString();
            req.flags |= 1;
        }
        req.invoice = inputInvoice;

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.TL_payments_paymentFormStars) {
                openPaymentForm(inputInvoice, (TLRPC.TL_payments_paymentFormStars) res, whenShown, null);
            } else {
                bulletinError(err, "NO_PAYMENT_FORM");
            }
            if (whenShown != null) {
                whenShown.run();
            }
        }));
    }

    private boolean paymentFormOpened;

    public void openPaymentForm(TLRPC.InputInvoice inputInvoice, TLRPC.TL_payments_paymentFormStars form, Runnable whenShown, Utilities.Callback<String> whenAllDone) {
        if (form == null || form.invoice == null || paymentFormOpened) {
            return;
        }

        final Context context = LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
        final Theme.ResourcesProvider resourcesProvider = getResourceProvider();

        if (context == null) {
            return;
        }

        if (!balanceAvailable()) {
            getBalance(() -> {
                if (!balanceAvailable()) {
                    bulletinError("NO_BALANCE");
                    if (whenShown != null) {
                        whenShown.run();
                    }
                    return;
                }
                openPaymentForm(inputInvoice, form, whenShown, whenAllDone);
            });
            return;
        }

        long _stars = 0;
        for (TLRPC.TL_labeledPrice price : form.invoice.prices) {
            _stars += price.amount;
        }
        final long stars = _stars;
        final long dialogId = form.bot_id;
        final String bot;
        if (dialogId >= 0) {
            bot = UserObject.getUserName(MessagesController.getInstance(currentAccount).getUser(dialogId));
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            bot = chat == null ? "" : chat.title;
        }
        final String product = form.title;

        if (whenShown != null) {
            whenShown.run();
        }

        final boolean[] allDone = new boolean[] { false };
        StarsIntroActivity.openConfirmPurchaseSheet(context, resourcesProvider, currentAccount, dialogId, product, stars, form.photo, whenDone -> {
            if (balance < stars) {
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
                StarsIntroActivity.StarsNeededSheet sheet = new StarsIntroActivity.StarsNeededSheet(context, resourcesProvider, stars, bot, () -> {
                    purchased[0] = true;
                    payAfterConfirmed(inputInvoice, form, success -> {
                        allDone[0] = true;
                        if (whenAllDone != null) {
                            whenAllDone.run(success ? "paid" : "failed");
                        }
                        if (whenDone != null) {
                            whenDone.run(true);
                        }
                    });
                });
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
                payAfterConfirmed(inputInvoice, form, success -> {
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

    private void showNoSupportDialog(Context context, Theme.ResourcesProvider resourcesProvider) {
        new AlertDialog.Builder(context, resourcesProvider)
            .setTitle(getString(R.string.StarsNotAvailableTitle))
            .setMessage(getString(R.string.StarsNotAvailableText))
            .setPositiveButton(getString(R.string.OK), null)
            .show();
    }

    private void payAfterConfirmed(TLRPC.InputInvoice inputInvoice, TLRPC.TL_payments_paymentFormStars form, Utilities.Callback<Boolean> whenDone) {
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
        final long dialogId = form.bot_id;
        final String bot;
        if (dialogId >= 0) {
            bot = UserObject.getUserName(MessagesController.getInstance(currentAccount).getUser(dialogId));
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            bot = chat == null ? "" : chat.title;
        }
        final String product = form.title;

        TLRPC.TL_payments_sendStarsForm req2 = new TLRPC.TL_payments_sendStarsForm();
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
                MessagesController.getInstance(currentAccount).processUpdates(result.updates, false);

                Drawable starDrawable = context.getResources().getDrawable(R.drawable.star_small_inner).mutate();
                b.createSimpleBulletin(starDrawable, getString(R.string.StarsPurchaseCompleted), AndroidUtilities.replaceTags(formatPluralString("StarsPurchaseCompletedInfo", (int) stars, product, bot))).show();

                invalidateTransactions(true);
            } else if (err2 != null && "BALANCE_TOO_LOW".equals(err2.text)) {
                if (!MessagesController.getInstance(currentAccount).starsPurchaseAvailable()) {
                    if (whenDone != null) {
                        whenDone.run(false);
                    }
                    showNoSupportDialog(context, resourcesProvider);
                    return;
                }
                final boolean[] purchased = new boolean[] { false };
                StarsIntroActivity.StarsNeededSheet sheet = new StarsIntroActivity.StarsNeededSheet(context, resourcesProvider, stars, bot, () -> {
                    purchased[0] = true;
                    payAfterConfirmed(inputInvoice, form, success -> {
                        if (whenDone != null) {
                            whenDone.run(success);
                        }
                    });
                });
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
                        payAfterConfirmed(inputInvoice, (TLRPC.TL_payments_paymentFormStars) res3, whenDone);
                    } else {
                        if (whenDone != null) {
                            whenDone.run(false);
                        }
                        b.createSimpleBulletin(R.raw.error, LocaleController.formatString(R.string.UnknownErrorCode, err3 != null ? err3.text : "FAILED_GETTING_FORM")).show();
                    }
                }));
            } else {
                if (whenDone != null) {
                    whenDone.run(false);
                }
                b.createSimpleBulletin(R.raw.error, LocaleController.formatString(R.string.UnknownErrorCode, err2 != null ? err2.text : "FAILED_SEND_STARS")).show();
            }
        }));
    }

}
