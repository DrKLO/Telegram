package org.telegram.messenger.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.core.util.Pair;

import com.android.billingclient.api.AccountIdentifiers;
import com.android.billingclient.api.Purchase;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Charsets;

import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BillingUtilities {
    private static final String CURRENCY_FILE = "currencies.json";
    private static final String CURRENCY_EXP = "exp";

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
        return Pair.create(obfuscatedAccountId, savePurpose(paymentPurpose));
    }

    public static String savePurpose(TLRPC.InputStorePaymentPurpose paymentPurpose) {
        final long id = Utilities.random.nextLong();
        FileLog.d("BillingUtilities.savePurpose id=" + id + " paymentPurpose=" + paymentPurpose);

        SerializedData id_data = new SerializedData(8);
        id_data.writeInt64(id);
        String id_hex = Utilities.bytesToHex(id_data.toByteArray());
        id_data.cleanup();

        FileLog.d("BillingUtilities.savePurpose id_hex=" + id_hex + " paymentPurpose=" + paymentPurpose);

        TL_savedPurpose savedPurpose = new TL_savedPurpose();
        savedPurpose.id = id;
        savedPurpose.flags = 1;
        savedPurpose.purpose = paymentPurpose;

        SerializedData data = new SerializedData(savedPurpose.getObjectSize());
        savedPurpose.serializeToStream(data);
        String full_data_hex = Utilities.bytesToHex(data.toByteArray());
        data.cleanup();

        if (savedPurpose.getObjectSize() > 28) {
            FileLog.d("BillingUtilities.savePurpose: sending short version, original size is " + savedPurpose.getObjectSize() + " bytes");

            savedPurpose.flags = 0;
            savedPurpose.purpose = null;
        }

        data = new SerializedData(savedPurpose.getObjectSize());
        savedPurpose.serializeToStream(data);
        String data_hex = Utilities.bytesToHex(data.toByteArray());
        data.cleanup();

        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("purchases", Activity.MODE_PRIVATE);
        prefs.edit().putString(id_hex, full_data_hex).apply();
        FileLog.d("BillingUtilities.savePurpose: saved {" + full_data_hex + "} under " + id_hex);
        FileLog.d("BillingUtilities.savePurpose: but sending {" + data_hex + "}");

        return data_hex;
    }

    public static TLRPC.InputStorePaymentPurpose getPurpose(String data_hex) throws RuntimeException {
        FileLog.d("BillingUtilities.getPurpose " + data_hex);

        SerializedData data = new SerializedData(Utilities.hexToBytes(data_hex));
        TL_savedPurpose savedPurpose = TL_savedPurpose.TLdeserialize(data, data.readInt32(true), true);
        data.cleanup();

        if (savedPurpose.purpose != null) {
            FileLog.d("BillingUtilities.getPurpose: got purpose from received obfuscated profile id");
            return savedPurpose.purpose;
        }

        SerializedData id_data = new SerializedData(8);
        id_data.writeInt64(savedPurpose.id);
        String id_hex = Utilities.bytesToHex(id_data.toByteArray());
        id_data.cleanup();

        FileLog.d("BillingUtilities.getPurpose: searching purpose under " + id_hex);

        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("purchases", Activity.MODE_PRIVATE);
        String full_data_hex = prefs.getString(id_hex, null);
        if (full_data_hex == null) {
            FileLog.d("BillingUtilities.getPurpose: purpose under " + id_hex + " not found");
            throw new RuntimeException("no purpose under " + id_hex + " found :(");
        }

        FileLog.d("BillingUtilities.getPurpose: got {" + full_data_hex + "} under " + id_hex);

        SerializedData full_data = new SerializedData(Utilities.hexToBytes(full_data_hex));
        savedPurpose = TL_savedPurpose.TLdeserialize(full_data, full_data.readInt32(true), true);
        full_data.cleanup();

        return savedPurpose.purpose;
    }

    public static void clearPurpose(String data_hex) {
        try {
            FileLog.d("BillingUtilities.clearPurpose: got {" + data_hex + "}");

            SerializedData data = new SerializedData(Utilities.hexToBytes(data_hex));
            TL_savedPurpose savedPurpose = TL_savedPurpose.TLdeserialize(data, data.readInt32(true), true);

            SerializedData id_data = new SerializedData(8);
            id_data.writeInt64(savedPurpose.id);
            String id_hex = Utilities.bytesToHex(id_data.toByteArray());
            id_data.cleanup();
            FileLog.d("BillingUtilities.clearPurpose: id_hex = " + id_hex);

            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("purchases", Activity.MODE_PRIVATE);
            prefs.edit().remove(id_hex).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static class TL_savedPurpose extends TLObject {
        public static final int constructor = 0x1d8ad892;

        public int flags;
        public long id;
        public TLRPC.InputStorePaymentPurpose purpose;

        public static TL_savedPurpose TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            TL_savedPurpose result = null;
            switch (constructor) {
                case TL_savedPurpose.constructor:
                    result = new TL_savedPurpose();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in TL_savedPurpose", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt64(exception);
            if ((flags & 1) != 0) {
                purpose = TLRPC.InputStorePaymentPurpose.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            if ((flags & 1) != 0) {
                purpose.serializeToStream(stream);
            }
        }
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

    public static Pair<AccountInstance, TLRPC.InputStorePaymentPurpose> extractDeveloperPayload(Purchase purchase) {
        AccountIdentifiers identifiers = purchase.getAccountIdentifiers();
        if (identifiers == null) {
            FileLog.d("Billing: Extract payload. No AccountIdentifiers");
            return null;
        }
        String obfuscatedAccountId = identifiers.getObfuscatedAccountId();
        String obfuscatedData = identifiers.getObfuscatedProfileId();
        if (obfuscatedAccountId == null || obfuscatedAccountId.isEmpty() || obfuscatedData == null || obfuscatedData.isEmpty()) {
            FileLog.d("Billing: Extract payload. Empty AccountIdentifiers");
            return null;
        }

        try {
            TLRPC.InputStorePaymentPurpose purpose;
            try {
                purpose = getPurpose(obfuscatedData);
            } catch (Exception e) {
                FileLog.e("Billing: Extract payload, failed to get purpose", e);
                purpose = null;
            }

            byte[] obfuscatedAccountIdBytes = Base64.decode(obfuscatedAccountId, Base64.DEFAULT);
            long accountId = Long.parseLong(new String(obfuscatedAccountIdBytes, Charsets.UTF_8));

            AccountInstance acc = findAccountById(accountId);
            if (acc == null) {
                FileLog.d("Billing: Extract payload. AccountInstance not found, accountId=" + accountId);
                return null;
            }
            return Pair.create(acc, purpose);
        } catch (Exception e) {
            FileLog.e("Billing: Extract Payload", e);
            return null;
        }
    }

    public static void cleanupPurchase(Purchase purchase) {
        AccountIdentifiers identifiers = purchase.getAccountIdentifiers();
        clearPurpose(identifiers.getObfuscatedProfileId());
    }
}
