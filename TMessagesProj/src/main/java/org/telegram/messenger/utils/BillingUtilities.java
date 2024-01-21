package org.telegram.messenger.utils;

import android.content.Context;
import android.util.Base64;

import androidx.core.util.Pair;

import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Charsets;

import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BillingUtilities {
    private static final String CURRENCY_FILE = "currencies.json";
    private static final String CURRENCY_EXP = "exp";

    private static TLRPC.InputStorePaymentPurpose remPaymentPurpose;

    @SuppressWarnings("ConstantConditions")
    public static void extractCurrencyExp(Map<String, Integer> currencyExpMap) {
        if (!currencyExpMap.isEmpty()) {
            return;
        }
        try {
            Context ctx = ApplicationLoader.applicationContext;
            InputStream in = ctx.getAssets().open(CURRENCY_FILE);
            JSONObject obj = new JSONObject(new String(Util.toByteArray(in), Charsets.UTF_8));
            Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String key = it.next();
                JSONObject currency = obj.optJSONObject(key);
                currencyExpMap.put(key, currency.optInt(CURRENCY_EXP));
            }
            in.close();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static Pair<String, String> createDeveloperPayload(TLRPC.InputStorePaymentPurpose paymentPurpose, AccountInstance accountInstance) {
        long currentAccountId = accountInstance.getUserConfig().getClientUserId();
        byte[] currentAccountIdBytes = String.valueOf(currentAccountId).getBytes(Charsets.UTF_8);
        String obfuscatedAccountId = Base64.encodeToString(currentAccountIdBytes, Base64.DEFAULT);

        SerializedData serializedData = new SerializedData(paymentPurpose.getObjectSize());
        paymentPurpose.serializeToStream(serializedData);
        String obfuscatedData = Base64.encodeToString(serializedData.toByteArray(), Base64.DEFAULT);
        serializedData.cleanup();
        if (paymentPurpose instanceof TLRPC.TL_inputStorePaymentPremiumGiftCode || paymentPurpose instanceof TLRPC.TL_inputStorePaymentPremiumGiveaway) {
            remPaymentPurpose = paymentPurpose;
            return Pair.create(obfuscatedAccountId, obfuscatedAccountId);
        } else {
            remPaymentPurpose = null;
        }
        return Pair.create(obfuscatedAccountId, obfuscatedData);
    }

    private static AccountInstance findAccountById(long accountId) {
        AccountInstance result = null;
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            AccountInstance acc = AccountInstance.getInstance(i);
            if (acc.getUserConfig().getClientUserId() == accountId) {
                result = acc;
                break;
            }
        }
        return result;
    }
}
