/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.android.billingclient.api.ProductDetails;

import java.util.Objects;

import ru.tusco.messenger.Extra;

public class BuildVars {

    public static boolean DEBUG_VERSION = BuildConfig.DEBUG_VERSION;
    public static boolean LOGS_ENABLED = BuildConfig.DEBUG_VERSION;
    public static boolean DEBUG_PRIVATE_VERSION = BuildConfig.DEBUG_PRIVATE_VERSION;
    public static boolean USE_CLOUD_STRINGS = true;
    public static boolean CHECK_UPDATES = true;
    public static boolean NO_SCOPED_STORAGE = Build.VERSION.SDK_INT <= 29;
    public static String BUILD_VERSION_STRING = BuildConfig.BUILD_VERSION_STRING;
    public static int APP_ID = Extra.APP_ID;
    public static String APP_HASH = Extra.APP_HASH;

    // SafetyNet key for Google Identity SDK, set it to empty to disable
    public static String SAFETYNET_KEY = "";
    public static String PLAYSTORE_APP_URL = "";
    public static String HUAWEI_STORE_URL = "";
    public static String GOOGLE_AUTH_CLIENT_ID = "";

    public static String HUAWEI_APP_ID = "";

    // You can use this flag to disable Google Play Billing (If you're making fork and want it to be in Google Play)
    public static boolean IS_BILLING_UNAVAILABLE = true;

    static {
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
            LOGS_ENABLED = DEBUG_VERSION || sharedPreferences.getBoolean("logsEnabled", DEBUG_VERSION);
            if (LOGS_ENABLED) {
                Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
                    FileLog.fatal(exception, true);
                });
            }
        }
    }

    public static boolean useInvoiceBilling() {
        return BillingController.billingClientEmpty || DEBUG_VERSION || ApplicationLoader.isStandaloneBuild() || isBetaApp() || isHuaweiStoreApp() || hasDirectCurrency();
    }

    private static boolean hasDirectCurrency() {
        if (!BillingController.getInstance().isReady() || BillingController.PREMIUM_PRODUCT_DETAILS == null) {
            return false;
        }
        for (ProductDetails.SubscriptionOfferDetails offerDetails : BillingController.PREMIUM_PRODUCT_DETAILS.getSubscriptionOfferDetails()) {
            for (ProductDetails.PricingPhase phase : offerDetails.getPricingPhases().getPricingPhaseList()) {
                for (String cur : MessagesController.getInstance(UserConfig.selectedAccount).directPaymentsCurrency) {
                    if (Objects.equals(phase.getPriceCurrencyCode(), cur)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Boolean betaApp;
    public static boolean isBetaApp() {
        if (betaApp == null) {
            betaApp = ApplicationLoader.applicationContext != null && "ru.tusco.messenger.beta".equals(ApplicationLoader.applicationContext.getPackageName());
        }
        return betaApp;
    }


    public static boolean isHuaweiStoreApp() {
        return ApplicationLoader.isHuaweiStoreBuild();
    }

    public static String getSmsHash() {
        return Extra.SMS_HASH;
    }
}
