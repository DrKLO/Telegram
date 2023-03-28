package org.telegram.messenger;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

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
import com.google.android.exoplayer2.util.Util;

import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.PremiumPreviewFragment;

import java.io.InputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
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

    private Map<String, Consumer<BillingResult>> resultListeners = new HashMap<>();
    private List<String> requestingTokens = new ArrayList<>();
    private String lastPremiumTransaction;
    private String lastPremiumToken;

    private Map<String, Integer> currencyExpMap = new HashMap<>();

    public static BillingController getInstance() {
        if (instance == null) {
            instance = new BillingController(ApplicationLoader.applicationContext);
        }
        return instance;
    }

    private BillingClient billingClient;

    private BillingController(Context ctx) {
        billingClient = BillingClient.newBuilder(ctx)
                .enablePendingPurchases()
                .setListener(this)
                .build();
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
        if (currency.isEmpty()) {
            return String.valueOf(amount);
        }
        Currency cur = Currency.getInstance(currency);
        if (cur != null) {
            NumberFormat numberFormat = NumberFormat.getCurrencyInstance();
            numberFormat.setCurrency(cur);

            return numberFormat.format(amount / Math.pow(10, exp));
        }
        return amount + " " + currency;
    }

    public int getCurrencyExp(String currency) {
        Integer exp = currencyExpMap.get(currency);
        if (exp == null) {
            return 0;
        }
        return exp;
    }

    public void startConnection() {
        if (isReady()) {
            return;
        }
        try {
            Context ctx = ApplicationLoader.applicationContext;
            InputStream in = ctx.getAssets().open("currencies.json");
            JSONObject obj = new JSONObject(new String(Util.toByteArray(in), "UTF-8"));
            parseCurrencies(obj);
            in.close();
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (!BuildVars.useInvoiceBilling()) {
            billingClient.startConnection(this);
        }
    }

    private void parseCurrencies(JSONObject obj) {
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            String key = it.next();
            JSONObject currency = obj.optJSONObject(key);
            currencyExpMap.put(key, currency.optInt("exp"));
        }
    }

    public boolean isReady() {
        return billingClient.isReady();
    }

    public void queryProductDetails(List<QueryProductDetailsParams.Product> products, ProductDetailsResponseListener responseListener) {
        if (!isReady()) {
            throw new IllegalStateException("Billing controller should be ready for this call!");
        }
        billingClient.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build(), responseListener);
    }

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

        if (paymentPurpose instanceof TLRPC.TL_inputStorePaymentGiftPremium && !checkedConsume) {
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

        BillingFlowParams.Builder flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetails);
        if (subscriptionUpdateParams != null) {
            flowParams.setSubscriptionUpdateParams(subscriptionUpdateParams);
        }
        boolean ok = billingClient.launchBillingFlow(activity, flowParams.build()).getResponseCode() == BillingClient.BillingResponseCode.OK;

        if (ok) {
            for (BillingFlowParams.ProductDetailsParams params : productDetails) {
                accountInstance.getUserConfig().billingPaymentPurpose = paymentPurpose;
                accountInstance.getUserConfig().awaitBillingProductIds.add(params.zza().getProductId()); // params.getProductDetails().getProductId()
            }
            accountInstance.getUserConfig().saveConfig(false);
        }
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> list) {
        FileLog.d("Billing purchases updated: " + billingResult + ", " + list);
        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                PremiumPreviewFragment.sentPremiumBuyCanceled();
            }

            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
                AccountInstance acc = AccountInstance.getInstance(i);
                if (!acc.getUserConfig().awaitBillingProductIds.isEmpty()) {
                    acc.getUserConfig().awaitBillingProductIds.clear();
                    acc.getUserConfig().billingPaymentPurpose = null;
                    acc.getUserConfig().saveConfig(false);
                }
            }

            return;
        }
        if (list == null) {
            return;
        }
        lastPremiumTransaction = null;
        for (Purchase purchase : list) {
            if (purchase.getProducts().contains(PREMIUM_PRODUCT_ID)) {
                lastPremiumTransaction = purchase.getOrderId();
                lastPremiumToken = purchase.getPurchaseToken();
            }

            if (!requestingTokens.contains(purchase.getPurchaseToken())) {
                for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
                    AccountInstance acc = AccountInstance.getInstance(i);
                    if (acc.getUserConfig().awaitBillingProductIds.containsAll(purchase.getProducts()) && purchase.getPurchaseState() != Purchase.PurchaseState.PENDING) {
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            if (!purchase.isAcknowledged()) {
                                requestingTokens.add(purchase.getPurchaseToken());
                                TLRPC.TL_payments_assignPlayMarketTransaction req = new TLRPC.TL_payments_assignPlayMarketTransaction();
                                req.receipt = new TLRPC.TL_dataJSON();
                                req.receipt.data = purchase.getOriginalJson();
                                req.purpose = acc.getUserConfig().billingPaymentPurpose;
                                acc.getConnectionsManager().sendRequest(req, (response, error) -> {
                                    if (response instanceof TLRPC.Updates) {
                                        acc.getMessagesController().processUpdates((TLRPC.Updates) response, false);
                                        requestingTokens.remove(purchase.getPurchaseToken());

                                        for (String productId : purchase.getProducts()) {
                                            Consumer<BillingResult> listener = resultListeners.remove(productId);
                                            if (listener != null) {
                                                listener.accept(billingResult);
                                            }
                                        }

                                        if (req.purpose instanceof TLRPC.TL_inputStorePaymentGiftPremium) {
                                            billingClient.consumeAsync(ConsumeParams.newBuilder()
                                                            .setPurchaseToken(purchase.getPurchaseToken())
                                                    .build(), (billingResult1, s) -> {});
                                        }
                                    }
                                    if (response != null || (ApplicationLoader.isNetworkOnline() && error != null && error.code != -1000)) {
                                        acc.getUserConfig().awaitBillingProductIds.removeAll(purchase.getProducts());
                                        acc.getUserConfig().saveConfig(false);
                                    }
                                }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagInvokeAfter);
                            } else {
                                acc.getUserConfig().awaitBillingProductIds.removeAll(purchase.getProducts());
                                acc.getUserConfig().saveConfig(false);
                            }
                        } else {
                            acc.getUserConfig().awaitBillingProductIds.removeAll(purchase.getProducts());
                            acc.getUserConfig().saveConfig(false);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onBillingServiceDisconnected() {
        FileLog.d("Billing service disconnected");
    }

    @Override
    public void onBillingSetupFinished(@NonNull BillingResult setupBillingResult) {
        if (setupBillingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            queryProductDetails(Collections.singletonList(PREMIUM_PRODUCT), (billingResult, list) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    for (ProductDetails details : list) {
                        if (details.getProductId().equals(PREMIUM_PRODUCT_ID)) {
                            PREMIUM_PRODUCT_DETAILS = details;
                        }
                    }

                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.billingProductDetailsUpdated));
                }
            });

            queryPurchases(BillingClient.ProductType.SUBS, this::onPurchasesUpdated);
        }
    }
}
