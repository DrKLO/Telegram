package org.telegram.messenger;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.telegram.messenger.utils.BillingUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stars.StarsController;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class BillingController implements PurchasesUpdatedListener, BillingClientStateListener {
    public final static String PREMIUM_PRODUCT_ID = "telegram_premium";
    public final static QueryProductDetailsParams.Product PREMIUM_PRODUCT = QueryProductDetailsParams.Product.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .setProductId(PREMIUM_PRODUCT_ID)
            .build();

    @Nullable
    public static ProductDetails PREMIUM_PRODUCT_DETAILS;

    private static BillingController instance;

    public static boolean billingClientEmpty;

    private final Map<String, Consumer<BillingResult>> resultListeners = new HashMap<>();
    private final List<String> requestingTokens = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> currencyExpMap = new HashMap<>();
    private final BillingClient billingClient;
    private String lastPremiumTransaction;
    private String lastPremiumToken;
    private boolean isDisconnected;
    private Runnable onCanceled;

    public static BillingController getInstance() {
        if (instance == null) {
            instance = new BillingController(ApplicationLoader.applicationContext);
        }
        return instance;
    }

    private BillingController(Context ctx) {
        billingClient = BillingClient.newBuilder(ctx)
                .enablePendingPurchases()
                .setListener(this)
                .build();
    }

    public void setOnCanceled(Runnable onCanceled) {
        this.onCanceled = onCanceled;
    }

    public String getLastPremiumTransaction() {
        return lastPremiumTransaction;
    }

    public String getLastPremiumToken() {
        return lastPremiumToken;
    }

    public String formatCurrency(long amount, String currency) {
        return formatCurrency(amount, currency, getCurrencyExp(currency));
    }

    public String formatCurrency(long amount, String currency, int exp) {
        return formatCurrency(amount, currency, exp, false);
    }

    private static NumberFormat currencyInstance;
    public String formatCurrency(long amount, String currency, int exp, boolean rounded) {
        if (currency == null || currency.isEmpty()) {
            return String.valueOf(amount);
        }
        if ("TON".equalsIgnoreCase(currency)) {
            return "TON " + (amount / 1_000_000_000.0);
        }
        Currency cur = Currency.getInstance(currency);
        if (cur != null) {
            if (currencyInstance == null) {
                currencyInstance = NumberFormat.getCurrencyInstance();
            }
            currencyInstance.setCurrency(cur);
            if (rounded) {
                return currencyInstance.format(Math.round(amount / Math.pow(10, exp)));
            }
            return currencyInstance.format(amount / Math.pow(10, exp));
        }
        return amount + " " + currency;
    }

    @SuppressWarnings("ConstantConditions")
    public int getCurrencyExp(String currency) {
        BillingUtilities.extractCurrencyExp(currencyExpMap);
        return currencyExpMap.getOrDefault(currency, 0);
    }

    public void startConnection() {
        if (isReady()) {
            return;
        }
        BillingUtilities.extractCurrencyExp(currencyExpMap);
        if (!BuildVars.useInvoiceBilling()) {
            billingClient.startConnection(this);
        }
    }

    private void switchToInvoice() {
        if (billingClientEmpty) {
            return;
        }
        billingClientEmpty = true;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.billingProductDetailsUpdated);
    }

    private void switchBackFromInvoice() {
        if (!billingClientEmpty) {
            return;
        }
        billingClientEmpty = false;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.billingProductDetailsUpdated);
    }

    public boolean isReady() {
        return billingClient.isReady();
    }

    public void queryProductDetails(List<QueryProductDetailsParams.Product> products, ProductDetailsResponseListener responseListener) {
        if (!isReady()) {
            throw new IllegalStateException("Billing: Controller should be ready for this call!");
        }
        billingClient.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build(), responseListener);
    }

    /**
     * {@link BillingClient#queryPurchasesAsync} returns only active subscriptions and not consumed purchases.
     */
    public void queryPurchases(String productType, PurchasesResponseListener responseListener) {
        billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(productType).build(), responseListener);
    }

    public boolean startManageSubscription(Context ctx, String productId) {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format("https://play.google.com/store/account/subscriptions?sku=%s&package=%s", productId, ctx.getPackageName()))));
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    public void addResultListener(String productId, Consumer<BillingResult> listener) {
        resultListeners.put(productId, listener);
    }

    public void launchBillingFlow(Activity activity, AccountInstance accountInstance, TLRPC.InputStorePaymentPurpose paymentPurpose, List<BillingFlowParams.ProductDetailsParams> productDetails) {
        launchBillingFlow(activity, accountInstance, paymentPurpose, productDetails, null, false);
    }

    public void launchBillingFlow(Activity activity, AccountInstance accountInstance, TLRPC.InputStorePaymentPurpose paymentPurpose, List<BillingFlowParams.ProductDetailsParams> productDetails, BillingFlowParams.SubscriptionUpdateParams subscriptionUpdateParams, boolean checkedConsume) {
        if (!isReady() || activity == null) {
            return;
        }

        if ((paymentPurpose instanceof TLRPC.TL_inputStorePaymentGiftPremium || paymentPurpose instanceof TLRPC.TL_inputStorePaymentStarsTopup || paymentPurpose instanceof TLRPC.TL_inputStorePaymentStarsGift) && !checkedConsume) {
            queryPurchases(BillingClient.ProductType.INAPP, (billingResult, list) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Runnable callback = () -> launchBillingFlow(activity, accountInstance, paymentPurpose, productDetails, subscriptionUpdateParams, true);

                    AtomicInteger productsToBeConsumed = new AtomicInteger(0);
                    List<String> productsConsumed = new ArrayList<>();
                    for (Purchase purchase : list) {
                        if (purchase.isAcknowledged()) {
                            for (BillingFlowParams.ProductDetailsParams params : productDetails) {
                                String productId = params.zza().getProductId();
                                if (purchase.getProducts().contains(productId)) {
                                    productsToBeConsumed.incrementAndGet();
                                    billingClient.consumeAsync(ConsumeParams.newBuilder()
                                            .setPurchaseToken(purchase.getPurchaseToken())
                                            .build(), (billingResult1, s) -> {
                                        if (billingResult1.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                            productsConsumed.add(productId);

                                            if (productsToBeConsumed.get() == productsConsumed.size()) {
                                                callback.run();
                                            }
                                        }
                                    });
                                    break;
                                }
                            }
                        } else {
                            onPurchasesUpdated(BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build(), Collections.singletonList(purchase));
                            return;
                        }
                    }

                    if (productsToBeConsumed.get() == 0) {
                        callback.run();
                    }
                }
            });
            return;
        }

        Pair<String, String> payload = BillingUtilities.createDeveloperPayload(paymentPurpose, accountInstance);
        String obfuscatedAccountId = payload.first;
        String obfuscatedData = payload.second;

        BillingFlowParams.Builder flowParams = BillingFlowParams.newBuilder()
                .setObfuscatedAccountId(obfuscatedAccountId)
                .setObfuscatedProfileId(obfuscatedData)
                .setProductDetailsParamsList(productDetails);
        if (subscriptionUpdateParams != null) {
            flowParams.setSubscriptionUpdateParams(subscriptionUpdateParams);
        }
        final BillingResult result = billingClient.launchBillingFlow(activity, flowParams.build());
        int responseCode = result.getResponseCode();
        if (responseCode != BillingClient.BillingResponseCode.OK) {
            FileLog.d("Billing: Launch Error: " + responseCode + ", " + obfuscatedAccountId + ", " + obfuscatedData);
        }
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billing, @Nullable List<Purchase> list) {
        FileLog.d("Billing: Purchases updated: " + billing + ", " + list);
        if (billing.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            if (billing.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                PremiumPreviewFragment.sentPremiumBuyCanceled();
            }
            if (onCanceled != null) {
                onCanceled.run();
                onCanceled = null;
            }
            return;
        }
        if (list == null || list.isEmpty()) {
            return;
        }
        lastPremiumTransaction = null;
        for (Purchase purchase : list) {
            if (purchase.getProducts().contains(PREMIUM_PRODUCT_ID)) {
                lastPremiumTransaction = purchase.getOrderId();
                lastPremiumToken = purchase.getPurchaseToken();
            }

            if (!requestingTokens.contains(purchase.getPurchaseToken()) && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                Pair<AccountInstance, TLRPC.InputStorePaymentPurpose> opayload = BillingUtilities.extractDeveloperPayload(purchase);
                if (opayload == null || opayload.first == null) {
                    continue;
                }
                if (!purchase.isAcknowledged()) {
                    requestingTokens.add(purchase.getPurchaseToken());

                    TLRPC.TL_payments_assignPlayMarketTransaction req = new TLRPC.TL_payments_assignPlayMarketTransaction();
                    req.receipt = new TLRPC.TL_dataJSON();
                    req.receipt.data = purchase.getOriginalJson();
                    req.purpose = opayload.second;

                    final AlertDialog progressDialog = new AlertDialog(ApplicationLoader.applicationContext, AlertDialog.ALERT_TYPE_SPINNER);
                    AndroidUtilities.runOnUIThread(() -> progressDialog.showDelayed(500));

                    AccountInstance acc = opayload.first;
                    acc.getConnectionsManager().sendRequest(req, (response, error) -> {
                        AndroidUtilities.runOnUIThread(progressDialog::dismiss);

                        requestingTokens.remove(purchase.getPurchaseToken());

                        if (response instanceof TLRPC.Updates) {
                            acc.getMessagesController().processUpdates((TLRPC.Updates) response, false);

                            for (String productId : purchase.getProducts()) {
                                Consumer<BillingResult> listener = resultListeners.remove(productId);
                                if (listener != null) {
                                    listener.accept(billing);
                                }
                            }

                            consumeGiftPurchase(purchase, req.purpose);
                            BillingUtilities.cleanupPurchase(purchase);
                        } else if (error != null) {
                            if (onCanceled != null) {
                                onCanceled.run();
                                onCanceled = null;
                            }
                            NotificationCenter.getGlobalInstance().postNotificationNameOnUIThread(NotificationCenter.billingConfirmPurchaseError, req, error);
                        }
                    }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagFailOnServerErrorsExceptFloodWait | ConnectionsManager.RequestFlagInvokeAfter);
                } else {
                    consumeGiftPurchase(purchase, opayload.second);
                }
            }
        }
    }

    /**
     * All consumable purchases must be consumed. For us it is a gift.
     * Without confirmation the user will not be able to buy the product again.
     */
    private void consumeGiftPurchase(Purchase purchase, TLRPC.InputStorePaymentPurpose purpose) {
        if (purpose instanceof TLRPC.TL_inputStorePaymentGiftPremium
                || purpose instanceof TLRPC.TL_inputStorePaymentPremiumGiftCode
                || purpose instanceof TLRPC.TL_inputStorePaymentStarsTopup
                || purpose instanceof TLRPC.TL_inputStorePaymentStarsGift
                || purpose instanceof TLRPC.TL_inputStorePaymentPremiumGiveaway
                || purpose instanceof TLRPC.TL_inputStorePaymentStarsGiveaway) {
            billingClient.consumeAsync(
                    ConsumeParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build(), (r, s) -> {
                    });
        }
    }

    /**
     * May occur in extremely rare cases.
     * For example when Google Play decides to update.
     */
    @SuppressWarnings("Convert2MethodRef")
    @Override
    public void onBillingServiceDisconnected() {
        FileLog.d("Billing: Service disconnected");
        int delay = isDisconnected ? 15000 : 5000;
        isDisconnected = true;
        AndroidUtilities.runOnUIThread(() -> startConnection(), delay);
    }

    private ArrayList<Runnable> setupListeners = new ArrayList<>();
    public void whenSetuped(Runnable listener) {
        setupListeners.add(listener);
    }

    private int triesLeft = 0;

    @Override
    public void onBillingSetupFinished(@NonNull BillingResult setupBillingResult) {
        FileLog.d("Billing: Setup finished with result " + setupBillingResult);
        if (setupBillingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            isDisconnected = false;
            triesLeft = 3;
            try {
                queryProductDetails(Collections.singletonList(PREMIUM_PRODUCT), this::onQueriedPremiumProductDetails);
            } catch (Exception e) {
                FileLog.e(e);
            }
            queryPurchases(BillingClient.ProductType.INAPP, this::onPurchasesUpdated);
            queryPurchases(BillingClient.ProductType.SUBS, this::onPurchasesUpdated);
            if (!setupListeners.isEmpty()) {
                for (int i = 0; i < setupListeners.size(); ++i) {
                    AndroidUtilities.runOnUIThread(setupListeners.get(i));
                }
                setupListeners.clear();
            }
        } else {
            if (!isDisconnected) {
                switchToInvoice();
            }
        }
    }

    private void onQueriedPremiumProductDetails(BillingResult billingResult, List<ProductDetails> list) {
        FileLog.d("Billing: Query product details finished " + billingResult + ", " + list);
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            for (ProductDetails details : list) {
                if (details.getProductId().equals(PREMIUM_PRODUCT_ID)) {
                    PREMIUM_PRODUCT_DETAILS = details;
                }
            }
            if (PREMIUM_PRODUCT_DETAILS == null) {
                switchToInvoice();
            } else {
                switchBackFromInvoice();
                NotificationCenter.getGlobalInstance().postNotificationNameOnUIThread(NotificationCenter.billingProductDetailsUpdated);
            }
        } else {
            switchToInvoice();
            triesLeft--;
            if (triesLeft > 0) {
                long delay;
                if (triesLeft == 2) {
                    delay = 1000;
                } else {
                    delay = 10000;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        queryProductDetails(Collections.singletonList(PREMIUM_PRODUCT), this::onQueriedPremiumProductDetails);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }, delay);
            }
        }
    }

    public static String getResponseCodeString(int code) {
        switch (code) {
            case BillingClient.BillingResponseCode.SERVICE_TIMEOUT:       return "SERVICE_TIMEOUT";
            case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED: return "FEATURE_NOT_SUPPORTED";
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED:  return "SERVICE_DISCONNECTED";
            case BillingClient.BillingResponseCode.OK:                    return "OK";
            case BillingClient.BillingResponseCode.USER_CANCELED:         return "USER_CANCELED";
            case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE:   return "SERVICE_UNAVAILABLE";
            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE:   return "BILLING_UNAVAILABLE";
            case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE:      return "ITEM_UNAVAILABLE";
            case BillingClient.BillingResponseCode.DEVELOPER_ERROR:       return "DEVELOPER_ERROR";
            case BillingClient.BillingResponseCode.ERROR:                 return "ERROR";
            case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED:    return "ITEM_ALREADY_OWNED";
            case BillingClient.BillingResponseCode.ITEM_NOT_OWNED:        return "ITEM_NOT_OWNED";
            case BillingClient.BillingResponseCode.NETWORK_ERROR:         return "NETWORK_ERROR";
        }
        return null;
    }
}
