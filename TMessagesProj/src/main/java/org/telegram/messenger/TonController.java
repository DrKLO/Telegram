/*
 * This is the source code of Wallet for Android v. 1.0.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2019.
 */

package org.telegram.messenger;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.ui.Wallet.WalletConfigLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.security.auth.x500.X500Principal;

import androidx.core.content.FileProvider;
import drinkless.org.ton.Client;
import drinkless.org.ton.TonApi;

@TargetApi(18)
public class TonController extends BaseController {

    public interface StringCallback {
        void run(String result);
    }

    public interface TonLibCallback {
        void run(Object result);
    }

    public interface FeeCallback {
        void run(long result);
    }

    public interface ErrorCallback {
        void run(String text, TonApi.Error error);
    }

    public interface DangerousCallback {
        void run(TonApi.InputKey inputKey);
    }

    public interface WordsCallback {
        void run(String[] words);
    }

    public interface GetTransactionsCallback {
        void run(boolean reload, ArrayList<TonApi.RawTransaction> transactions);
    }

    public interface AccountStateCallback {
        void run(TonApi.GenericAccountState state);
    }

    public interface BooleanCallback {
        void run(boolean param);
    }

    public interface BytesCallback {
        void run(byte[] param);
    }

    private static volatile TonController[] Instance = new TonController[UserConfig.MAX_ACCOUNT_COUNT];
    public static TonController getInstance(int num) {
        TonController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (TonController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new TonController(num);
                }
            }
        }
        return localInstance;
    }

    public final static int CIPHER_INIT_FAILED = 0;
    public final static int CIPHER_INIT_OK = 1;
    public final static int CIPHER_INIT_KEY_INVALIDATED = 2;

    public final static int KEY_PROTECTION_TYPE_NONE = 0;
    public final static int KEY_PROTECTION_TYPE_LOCKSCREEN = 1;
    public final static int KEY_PROTECTION_TYPE_BIOMETRIC = 2;

    public final static int CONFIG_TYPE_URL = 0;
    public final static int CONFIG_TYPE_JSON = 1;

    private static KeyStore keyStore;
    private static KeyPairGenerator keyGenerator;
    private static Cipher cipher;

    private Client client;
    private boolean initied;
    private File keyDirectoty;
    private ArrayList<TonApi.RawTransaction> cachedTransactions = new ArrayList<>();
    private ArrayList<TonApi.RawTransaction> pendingTransactions = new ArrayList<>();
    private TonApi.GenericAccountState cachedAccountState;
    private TonApi.AccountAddress accountAddress;
    private long walletId;

    private byte[] creatingDataForLaterEncrypt;
    private String creatingEncryptedData;
    private String creatingPublicKey;
    private int creatingPasscodeType;
    private byte[] creatingPasscodeSalt;
    private SharedPreferences tonCache;
    private boolean isPrealodingWallet;
    private boolean walletLoaded;
    private int syncProgress;

    private String currentSetConfig;
    private String currentSetConfigName;

    private boolean shortPollingInProgress;
    private Runnable shortPollRunnable;
    private Runnable onPendingTransactionsEmpty;

    private GetTransactionsCallback uiTransactionCallback;

    static {
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (Build.VERSION.SDK_INT >= 23) {
                keyGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
                cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            } else {
                keyGenerator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
                cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public TonController(int num) {
        super(num);
        tonCache = ApplicationLoader.applicationContext.getSharedPreferences("tonCache" + num, Activity.MODE_PRIVATE);
        loadCache();
        /*Client.ResultHandler resultHandler = object -> {
            if (object instanceof TonApi.UpdateSyncState) {
                AndroidUtilities.runOnUIThread(() -> {
                    TonApi.UpdateSyncState updateSyncState = (TonApi.UpdateSyncState) object;
                    if (updateSyncState.syncState instanceof TonApi.SyncStateDone) {
                        syncProgress = 100;
                    } else if (updateSyncState.syncState instanceof TonApi.SyncStateInProgress) {
                        TonApi.SyncStateInProgress progress = (TonApi.SyncStateInProgress) updateSyncState.syncState;
                        syncProgress = (int) ((progress.currentSeqno - progress.fromSeqno) / (double) (progress.toSeqno - progress.fromSeqno) * 100);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.walletSyncProgressChanged, syncProgress);
                });
            } else {
                AndroidUtilities.processTonUpdate(currentAccount, client, object);
            }
        };
        client = Client.create(resultHandler, null, null);
        if (BuildVars.LOGS_ENABLED) {
            client.send(new TonApi.SetLogStream(new TonApi.LogStreamFile(FileLog.getTonlibLogPath(), 1024 * 1024 * 5)), null);
        }*/
        loadTonConfigFromUrl();
    }

    private void loadCache() {
        try {
            if (tonCache.getBoolean("hasCache", false)) {
                walletLoaded = true;
                int stateType = tonCache.getInt("state.type", 0);
                long balance = tonCache.getLong("state.balance", 0);
                int seqno = tonCache.getInt("state.seqno", 0);
                long walletId = tonCache.getLong("state.walletId", 0);
                TonApi.InternalTransactionId transactionId = new TonApi.InternalTransactionId(tonCache.getLong("transaction.lt", 0), Base64.decode(tonCache.getString("transaction.hash", null), Base64.DEFAULT));
                long syncUtime = tonCache.getLong("syncUtime", 0);

                if (stateType == 0) {
                    cachedAccountState = new TonApi.GenericAccountStateRaw(new TonApi.RawAccountState(balance, null, null, transactionId, null, syncUtime));
                } else if (stateType == 1) {
                    cachedAccountState = new TonApi.GenericAccountStateTestWallet(new TonApi.TestWalletAccountState(balance, seqno, transactionId, syncUtime));
                } else if (stateType == 2) {
                    cachedAccountState = new TonApi.GenericAccountStateTestGiver(new TonApi.TestGiverAccountState(balance, seqno, transactionId, syncUtime));
                } else if (stateType == 3) {
                    cachedAccountState = new TonApi.GenericAccountStateUninited(new TonApi.UninitedAccountState(balance, transactionId, null, syncUtime));
                } else if (stateType == 4) {
                    cachedAccountState = new TonApi.GenericAccountStateWallet(new TonApi.WalletAccountState(balance, seqno, transactionId, syncUtime));
                } else if (stateType == 5) {
                    cachedAccountState = new TonApi.GenericAccountStateWalletV3(new TonApi.WalletV3AccountState(balance, walletId, seqno, transactionId, syncUtime));
                }

                int transactionsCount = tonCache.getInt("transactionsCount", 0);
                for (int a = 0; a < transactionsCount; a++) {
                    String key = "transaction" + a + ".";

                    TonApi.RawMessage inMsg;
                    if (tonCache.contains(key + "inMsg.source")) {
                        inMsg = new TonApi.RawMessage(
                                tonCache.getString(key + "inMsg.source", null),
                                tonCache.getString(key + "inMsg.destination", null),
                                tonCache.getLong(key + "inMsg.value", 0),
                                tonCache.getLong(key + "inMsg.fwdFee", 0),
                                tonCache.getLong(key + "inMsg.ihrFee", 0),
                                tonCache.getLong(key + "inMsg.createdLt", 0),
                                Base64.decode(tonCache.getString(key + "inMsg.bodyHash", null), Base64.DEFAULT),
                                Base64.decode(tonCache.getString(key + "inMsg.message", null), Base64.DEFAULT)
                        );
                    } else {
                        inMsg = null;
                    }

                    TonApi.RawMessage[] outMsg;
                    if (tonCache.contains(key + "outMsgCount")) {
                        outMsg = new TonApi.RawMessage[tonCache.getInt(key + "outMsgCount", 0)];
                        for (int b = 0; b < outMsg.length; b++) {
                            String key2 = key + "outMsg" + b + ".";
                            outMsg[b] = new TonApi.RawMessage(
                                    tonCache.getString(key2 + "source", null),
                                    tonCache.getString(key2 + "destination", null),
                                    tonCache.getLong(key2 + "value", 0),
                                    tonCache.getLong(key2 + "fwdFee", 0),
                                    tonCache.getLong(key2 + "ihrFee", 0),
                                    tonCache.getLong(key2 + "createdLt", 0),
                                    Base64.decode(tonCache.getString(key2 + "bodyHash", null), Base64.DEFAULT),
                                    Base64.decode(tonCache.getString(key2 + "message", null), Base64.DEFAULT)
                            );
                        }
                    } else {
                        outMsg = null;
                    }
                    TonApi.RawTransaction transaction = new TonApi.RawTransaction(
                            tonCache.getLong(key + "utime", 0),
                            Base64.decode(tonCache.getString(key + "data", null), Base64.DEFAULT),
                            new TonApi.InternalTransactionId(tonCache.getLong(key + "lt", 0), Base64.decode(tonCache.getString(key + "hash", null), Base64.DEFAULT)),
                            tonCache.getLong(key + "fee", 0),
                            tonCache.getLong(key + "storageFee", 0),
                            tonCache.getLong(key + "otherFee", 0),
                            inMsg,
                            outMsg
                    );
                    cachedTransactions.add(transaction);
                }

                transactionsCount = tonCache.getInt("pendingCount", 0);
                for (int a = 0; a < transactionsCount; a++) {
                    String key = "pending" + a + ".";
                    TonApi.RawMessage inMsg = new TonApi.RawMessage(
                            tonCache.getString(key + "inMsg.source", null),
                            tonCache.getString(key + "inMsg.destination", null),
                            tonCache.getLong(key + "inMsg.value", 0),
                            0,
                            0,
                            0,
                            Base64.decode(tonCache.getString(key + "inMsg.bodyHash", null), Base64.DEFAULT),
                            Base64.decode(tonCache.getString(key + "inMsg.message", null), Base64.DEFAULT)
                    );
                    TonApi.RawTransaction transaction = new TonApi.RawTransaction(
                            tonCache.getLong(key + "utime", 0),
                            new byte[0],
                            new TonApi.InternalTransactionId(),
                            0,
                            0,
                            0,
                            inMsg,
                            new TonApi.RawMessage[0]
                    );
                    pendingTransactions.add(transaction);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            cachedAccountState = null;
            cachedTransactions.clear();
        }
    }

    private void saveCache() {
        if (cachedAccountState == null) {
            return;
        }
        try {
            SharedPreferences.Editor editor = tonCache.edit();
            editor.clear();

            int stateType;
            long balance;
            int seqno;
            TonApi.InternalTransactionId transactionId;
            long syncUtime;
            long walletId;

            if (cachedAccountState instanceof TonApi.GenericAccountStateRaw) {
                TonApi.GenericAccountStateRaw state = (TonApi.GenericAccountStateRaw) cachedAccountState;
                stateType = 0;
                balance = state.accountState.balance;
                seqno = 0;
                transactionId = state.accountState.lastTransactionId;
                syncUtime = state.accountState.syncUtime;
                walletId = 0;
            } else if (cachedAccountState instanceof TonApi.GenericAccountStateTestWallet) {
                TonApi.GenericAccountStateTestWallet state = (TonApi.GenericAccountStateTestWallet) cachedAccountState;
                stateType = 1;
                balance = state.accountState.balance;
                seqno = state.accountState.seqno;
                transactionId = state.accountState.lastTransactionId;
                syncUtime = state.accountState.syncUtime;
                walletId = 0;
            } else if (cachedAccountState instanceof TonApi.GenericAccountStateTestGiver) {
                TonApi.GenericAccountStateTestGiver state = (TonApi.GenericAccountStateTestGiver) cachedAccountState;
                stateType = 2;
                balance = state.accountState.balance;
                seqno = state.accountState.seqno;
                transactionId = state.accountState.lastTransactionId;
                syncUtime = state.accountState.syncUtime;
                walletId = 0;
            } else if (cachedAccountState instanceof TonApi.GenericAccountStateUninited) {
                TonApi.GenericAccountStateUninited state = (TonApi.GenericAccountStateUninited) cachedAccountState;
                stateType = 3;
                balance = state.accountState.balance;
                seqno = 0;
                transactionId = state.accountState.lastTransactionId;
                syncUtime = state.accountState.syncUtime;
                walletId = 0;
            } else if (cachedAccountState instanceof TonApi.GenericAccountStateWallet) {
                TonApi.GenericAccountStateWallet state = (TonApi.GenericAccountStateWallet) cachedAccountState;
                stateType = 4;
                balance = state.accountState.balance;
                seqno = state.accountState.seqno;
                transactionId = state.accountState.lastTransactionId;
                syncUtime = state.accountState.syncUtime;
                walletId = 0;
            } else if (cachedAccountState instanceof TonApi.GenericAccountStateWalletV3) {
                TonApi.GenericAccountStateWalletV3 state = (TonApi.GenericAccountStateWalletV3) cachedAccountState;
                stateType = 5;
                balance = state.accountState.balance;
                seqno = state.accountState.seqno;
                transactionId = state.accountState.lastTransactionId;
                syncUtime = state.accountState.syncUtime;
                walletId = state.accountState.walletId;
            } else {
                return;
            }

            editor.putInt("state.type", stateType);
            editor.putLong("state.balance", balance);
            editor.putInt("state.seqno", seqno);
            editor.putLong("state.walletId", walletId);
            editor.putLong("transaction.lt", transactionId.lt);
            editor.putString("transaction.hash", Base64.encodeToString(transactionId.hash, Base64.DEFAULT));
            editor.putLong("syncUtime", syncUtime);

            int transactionsCount = Math.min(10, cachedTransactions.size());
            editor.putInt("transactionsCount", transactionsCount);
            for (int a = 0; a < transactionsCount; a++) {
                String key = "transaction" + a + ".";
                TonApi.RawTransaction transaction = cachedTransactions.get(a);

                if (transaction.inMsg != null) {
                    editor.putString(key + "inMsg.source", transaction.inMsg.source);
                    editor.putString(key + "inMsg.destination", transaction.inMsg.destination);
                    editor.putLong(key + "inMsg.value", transaction.inMsg.value);
                    editor.putLong(key + "inMsg.fwdFee", transaction.inMsg.fwdFee);
                    editor.putLong(key + "inMsg.ihrFee", transaction.inMsg.ihrFee);
                    editor.putLong(key + "inMsg.createdLt", transaction.inMsg.createdLt);
                    editor.putString(key + "inMsg.bodyHash", Base64.encodeToString(transaction.inMsg.bodyHash, Base64.DEFAULT));
                    editor.putString(key + "inMsg.message", Base64.encodeToString(transaction.inMsg.message, Base64.DEFAULT));
                }

                if (transaction.outMsgs != null) {
                    int outMsgCount = transaction.outMsgs.length;
                    editor.putInt(key + "outMsgCount", outMsgCount);
                    for (int b = 0; b < outMsgCount; b++) {
                        String key2 = key + "outMsg" + b + ".";
                        editor.putString(key2 + "source", transaction.outMsgs[b].source);
                        editor.putString(key2 + "destination", transaction.outMsgs[b].destination);
                        editor.putLong(key2 + "value", transaction.outMsgs[b].value);
                        editor.putLong(key2 + "fwdFee", transaction.outMsgs[b].fwdFee);
                        editor.putLong(key2 + "ihrFee", transaction.outMsgs[b].ihrFee);
                        editor.putLong(key2 + "createdLt", transaction.outMsgs[b].createdLt);
                        editor.putString(key2 + "bodyHash", Base64.encodeToString(transaction.outMsgs[b].bodyHash, Base64.DEFAULT));
                        editor.putString(key2 + "message", Base64.encodeToString(transaction.outMsgs[b].message, Base64.DEFAULT));
                    }
                }
                editor.putLong(key + "utime", transaction.utime);
                editor.putString(key + "data", Base64.encodeToString(transaction.data, Base64.DEFAULT));
                editor.putLong(key + "lt", transaction.transactionId.lt);
                editor.putString(key + "hash", Base64.encodeToString(transaction.transactionId.hash, Base64.DEFAULT));
                editor.putLong(key + "fee", transaction.fee);
                editor.putLong(key + "storageFee", transaction.storageFee);
                editor.putLong(key + "otherFee", transaction.otherFee);
            }

            transactionsCount = pendingTransactions.size();
            editor.putInt("pendingCount", transactionsCount);
            for (int a = 0; a < transactionsCount; a++) {
                String key = "pending" + a + ".";
                TonApi.RawTransaction transaction = pendingTransactions.get(a);

                editor.putString(key + "inMsg.source", transaction.inMsg.source);
                editor.putString(key + "inMsg.destination", transaction.inMsg.destination);
                editor.putLong(key + "inMsg.value", transaction.inMsg.value);
                editor.putString(key + "inMsg.bodyHash", Base64.encodeToString(transaction.inMsg.bodyHash, Base64.DEFAULT));
                editor.putString(key + "inMsg.message", Base64.encodeToString(transaction.inMsg.message, Base64.DEFAULT));
                editor.putLong(key + "utime", transaction.utime);
                editor.putLong(key + "validUntil", transaction.otherFee);
            }

            editor.putBoolean("hasCache", true);
            editor.commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void cleanup() {
        try {
            keyStore.deleteEntry(getUserConfig().tonKeyName);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (keyDirectoty != null) {
            initTonLib();
            sendRequest(new TonApi.DeleteAllKeys(), true);
        }
        cancelShortPoll();
        tonCache.edit().clear().commit();
        isPrealodingWallet = false;
        accountAddress = null;
        cachedTransactions.clear();
        pendingTransactions.clear();
        cachedAccountState = null;
        creatingDataForLaterEncrypt = null;
        creatingEncryptedData = null;
        creatingPublicKey = null;
        creatingPasscodeType = -1;
        creatingPasscodeSalt = null;
        walletLoaded = false;
    }

    public ArrayList<TonApi.RawTransaction> getCachedTransactions() {
        return cachedTransactions;
    }

    public ArrayList<TonApi.RawTransaction> getPendingTransactions() {
        return pendingTransactions;
    }

    public TonApi.GenericAccountState getCachedAccountState() {
        return cachedAccountState;
    }

    private boolean createKeyPair(boolean useBiometric) {
        if (Build.VERSION.SDK_INT >= 23) {
            for (int a = 0; a < 2; a++) {
                try {
                    KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(getUserConfig().tonKeyName, KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT)
                            .setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                            .setKeySize(2048);
                    if (a == 0 && Build.VERSION.SDK_INT >= 28) {
                        builder.setIsStrongBoxBacked(true);
                    }
                    KeyguardManager keyguardManager = (KeyguardManager) ApplicationLoader.applicationContext.getSystemService(Context.KEYGUARD_SERVICE);
                    if (keyguardManager.isDeviceSecure()) {
                        builder.setUserAuthenticationRequired(true);
                        if (!useBiometric) {
                            builder.setUserAuthenticationValidityDurationSeconds(15);
                        }
                        if (Build.VERSION.SDK_INT >= 24) {
                            builder.setInvalidatedByBiometricEnrollment(true);
                        }
                    }
                    keyGenerator.initialize(builder.build());
                    keyGenerator.generateKeyPair();
                    return true;
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        } else {
            try {
                Calendar start = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                end.add(Calendar.YEAR, 30);
                KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(ApplicationLoader.applicationContext)
                        .setAlias(getUserConfig().tonKeyName)
                        .setSubject(new X500Principal("CN=Telegram, O=Telegram C=UAE"))
                        .setSerialNumber(BigInteger.ONE)
                        .setStartDate(start.getTime())
                        .setEndDate(end.getTime())
                        .build();
                keyGenerator.initialize(spec);
                keyGenerator.generateKeyPair();
                return true;
            } catch (Throwable ignore) {

            }
        }
        return false;
    }

    public int getKeyProtectionType() {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Key key = keyStore.getKey(getUserConfig().tonKeyName, null);
                KeyFactory factory = KeyFactory.getInstance(key.getAlgorithm(), "AndroidKeyStore");
                KeyInfo keyInfo = factory.getKeySpec(key, KeyInfo.class);
                if (keyInfo.isUserAuthenticationRequired()) {
                    if (keyInfo.getUserAuthenticationValidityDurationSeconds() > 0) {
                        return KEY_PROTECTION_TYPE_LOCKSCREEN;
                    } else {
                        return KEY_PROTECTION_TYPE_BIOMETRIC;
                    }
                }
            } catch (Exception ignore) {

            }
        }
        return KEY_PROTECTION_TYPE_NONE;
    }

    private int initCipher(int mode) {
        try {
            switch (mode) {
                case Cipher.ENCRYPT_MODE: {
                    PublicKey key = keyStore.getCertificate(getUserConfig().tonKeyName).getPublicKey();
                    PublicKey unrestricted = KeyFactory.getInstance(key.getAlgorithm()).generatePublic(new X509EncodedKeySpec(key.getEncoded()));
                    if (Build.VERSION.SDK_INT >= 23) {
                        OAEPParameterSpec spec = new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
                        cipher.init(mode, unrestricted, spec);
                    } else {
                        cipher.init(mode, unrestricted);
                    }
                    break;
                }
                case Cipher.DECRYPT_MODE: {
                    PrivateKey key = (PrivateKey) keyStore.getKey(getUserConfig().tonKeyName, null);
                    if (Build.VERSION.SDK_INT >= 23) {
                        OAEPParameterSpec spec = new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
                        cipher.init(mode, key, spec);
                    } else {
                        cipher.init(mode, key);
                    }
                    break;
                }
                default:
                    return CIPHER_INIT_FAILED;
            }
            return CIPHER_INIT_OK;
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= 23) {
                if (e instanceof KeyPermanentlyInvalidatedException) {
                    return CIPHER_INIT_KEY_INVALIDATED;
                }
            } else if (e instanceof InvalidKeyException) {
                try {
                    if (!keyStore.containsAlias(getUserConfig().tonKeyName)) {
                        return CIPHER_INIT_KEY_INVALIDATED;
                    }
                } catch (Exception ignore) {

                }
            }
            if (e instanceof UnrecoverableKeyException) {
                return CIPHER_INIT_KEY_INVALIDATED;
            }
            FileLog.e(e);
        }
        return CIPHER_INIT_FAILED;
    }

    private boolean isKeyCreated(boolean useBiometric) {
        try {
            return keyStore.containsAlias(getUserConfig().tonKeyName) || createKeyPair(useBiometric);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private String encrypt(byte[] input) {
        try {
            if (initCipher(Cipher.ENCRYPT_MODE) == CIPHER_INIT_OK) {
                byte[] bytes = cipher.doFinal(input);
                return Base64.encodeToString(bytes, Base64.NO_WRAP);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public byte[] decrypt(String encodedString, Cipher decryptCipher) {
        try {
            if (decryptCipher == null) {
                initCipher(Cipher.DECRYPT_MODE);
                decryptCipher = cipher;
            }
            byte[] bytes = Base64.decode(encodedString, Base64.NO_WRAP);
            return decryptCipher.doFinal(bytes);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public Cipher getCipherForDecrypt() {
        try {
            PrivateKey key = (PrivateKey) keyStore.getKey(getUserConfig().tonKeyName, null);
            OAEPParameterSpec spec = new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            return cipher;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private Object sendRequest(TonApi.Function query, boolean sync) {
        return sendRequest(query, null, sync);
    }

    private void sendRequest(TonApi.Function query, TonLibCallback callback) {
        sendRequest(query, callback, false);
    }

    private Object sendRequest(TonApi.Function query, TonLibCallback callback, boolean sync) {
        if (true) {
            return new Object();
        }
        Object[] result = new Object[1];
        CountDownLatch countDownLatch = sync ? new CountDownLatch(1) : null;
        client.send(query, object -> {
            if (callback != null) {
                callback.run(object);
            } else {
                if (object instanceof TonApi.Error) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("TonApi query " + query + " error " + ((TonApi.Error) object).message);
                    }
                    result[0] = object;
                } else {
                    result[0] = object;
                }
                if (countDownLatch != null) {
                    countDownLatch.countDown();
                }
            }
        }, null);
        if (countDownLatch != null) {
            try {
                countDownLatch.await();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return result[0];
    }

    private boolean initTonLib() {
        if (initied) {
            return true;
        }
        TonApi.Config config = getConfig();
        Object info = sendRequest(new TonApi.OptionsValidateConfig(config), true);
        if (info instanceof TonApi.OptionsConfigInfo) {
            TonApi.OptionsConfigInfo configInfo = (TonApi.OptionsConfigInfo) info;
            walletId = configInfo.defaultWalletId;
        } else {
            return false;
        }

        keyDirectoty = new File(ApplicationLoader.getFilesDirFixed(), "ton" + currentAccount);
        keyDirectoty.mkdirs();
        currentSetConfig = config.config;
        currentSetConfigName = config.blockchainName;
        TonApi.Options options = new TonApi.Options(config, new TonApi.KeyStoreTypeDirectory(keyDirectoty.getAbsolutePath()));
        Object result = sendRequest(new TonApi.Init(options), true);
        return initied = result instanceof TonApi.Ok;
    }

    private TonApi.Config getConfig() {
        UserConfig userConfig = getUserConfig();
        if (userConfig.walletConfigType == CONFIG_TYPE_URL) {
            return new TonApi.Config(userConfig.walletConfigFromUrl, userConfig.walletBlockchainName, !BuildVars.TON_WALLET_STANDALONE, false);
        } else {
            return new TonApi.Config(userConfig.walletConfig, userConfig.walletBlockchainName, !BuildVars.TON_WALLET_STANDALONE, false);
        }
    }

    public boolean onTonConfigUpdated() {
        if (!initied) {
            return true;
        }
        TonApi.Config config = getConfig();
        if (TextUtils.equals(currentSetConfig, config.config) && TextUtils.equals(currentSetConfigName, config.blockchainName)) {
            return true;
        }
        Object info = sendRequest(new TonApi.OptionsValidateConfig(config), true);
        if (info instanceof TonApi.OptionsConfigInfo) {
            TonApi.OptionsConfigInfo configInfo = (TonApi.OptionsConfigInfo) info;
            walletId = configInfo.defaultWalletId;
        } else {
            return false;
        }
        currentSetConfig = config.config;
        currentSetConfigName = config.blockchainName;
        sendRequest(new TonApi.OptionsSetConfig(config), false);
        return true;
    }

    private void onFinishWalletCreate(String[] words, WordsCallback onFinishRunnable, byte[] password, TonApi.Key key) {
        AndroidUtilities.runOnUIThread(() -> {
            preloadWallet(key.publicKey);
            int len = 1 + 2 + password.length + key.secret.length;
            int padding = len % 16;
            if (padding != 0) {
                padding = 16 - padding;
                len += padding;
            }
            byte[] dataToEncrypt = new byte[len];
            dataToEncrypt[0] = (byte) padding;
            dataToEncrypt[1] = 'o';
            dataToEncrypt[2] = 'k';
            System.arraycopy(password, 0, dataToEncrypt, 3, password.length);
            System.arraycopy(key.secret, 0, dataToEncrypt, 3 + password.length, key.secret.length);
            if (padding != 0) {
                byte[] pad = new byte[padding];
                Utilities.random.nextBytes(pad);
                System.arraycopy(pad, 0, dataToEncrypt, 3 + password.length + key.secret.length, pad.length);
            }
            creatingPublicKey = key.publicKey;
            if (getKeyProtectionType() != KEY_PROTECTION_TYPE_NONE) {
                creatingEncryptedData = encrypt(dataToEncrypt);
                Arrays.fill(dataToEncrypt, (byte) 0);
                Arrays.fill(key.secret, (byte) 0);
                Arrays.fill(password, (byte) 0);
                saveWalletKeys(words == null);
            } else {
                creatingDataForLaterEncrypt = dataToEncrypt;
            }
            onFinishRunnable.run(words);
        });
    }

    private TonApi.Error getTonApiErrorSafe(Object error) {
        if (error instanceof TonApi.Error) {
            return (TonApi.Error) error;
        }
        return null;
    }

    public static TonApi.InternalTransactionId getLastTransactionId(TonApi.GenericAccountState accountState) {
        if (accountState instanceof TonApi.GenericAccountStateRaw) {
            return ((TonApi.GenericAccountStateRaw) accountState).accountState.lastTransactionId;
        } else if (accountState instanceof TonApi.GenericAccountStateTestWallet) {
            return ((TonApi.GenericAccountStateTestWallet) accountState).accountState.lastTransactionId;
        } else if (accountState instanceof TonApi.GenericAccountStateTestGiver) {
            return ((TonApi.GenericAccountStateTestGiver) accountState).accountState.lastTransactionId;
        } else if (accountState instanceof TonApi.GenericAccountStateUninited) {
            return ((TonApi.GenericAccountStateUninited) accountState).accountState.lastTransactionId;
        } else if (accountState instanceof TonApi.GenericAccountStateWallet) {
            return ((TonApi.GenericAccountStateWallet) accountState).accountState.lastTransactionId;
        } else if (accountState instanceof TonApi.GenericAccountStateWalletV3) {
            return ((TonApi.GenericAccountStateWalletV3) accountState).accountState.lastTransactionId;
        }
        return null;
    }

    public static long getLastSyncTime(TonApi.GenericAccountState accountState) {
        if (accountState instanceof TonApi.GenericAccountStateRaw) {
            return ((TonApi.GenericAccountStateRaw) accountState).accountState.syncUtime;
        } else if (accountState instanceof TonApi.GenericAccountStateTestWallet) {
            return ((TonApi.GenericAccountStateTestWallet) accountState).accountState.syncUtime;
        } else if (accountState instanceof TonApi.GenericAccountStateTestGiver) {
            return ((TonApi.GenericAccountStateTestGiver) accountState).accountState.syncUtime;
        } else if (accountState instanceof TonApi.GenericAccountStateUninited) {
            return ((TonApi.GenericAccountStateUninited) accountState).accountState.syncUtime;
        } else if (accountState instanceof TonApi.GenericAccountStateWallet) {
            return ((TonApi.GenericAccountStateWallet) accountState).accountState.syncUtime;
        } else if (accountState instanceof TonApi.GenericAccountStateWalletV3) {
            return ((TonApi.GenericAccountStateWalletV3) accountState).accountState.syncUtime;
        }
        return 0;
    }

    public static long getBalance(TonApi.GenericAccountState accountState) {
        long balance;
        if (accountState instanceof TonApi.GenericAccountStateRaw) {
            balance = ((TonApi.GenericAccountStateRaw) accountState).accountState.balance;
        } else if (accountState instanceof TonApi.GenericAccountStateTestWallet) {
            balance = ((TonApi.GenericAccountStateTestWallet) accountState).accountState.balance;
        } else if (accountState instanceof TonApi.GenericAccountStateTestGiver) {
            balance = ((TonApi.GenericAccountStateTestGiver) accountState).accountState.balance;
        } else if (accountState instanceof TonApi.GenericAccountStateUninited) {
            balance = ((TonApi.GenericAccountStateUninited) accountState).accountState.balance;
        } else if (accountState instanceof TonApi.GenericAccountStateWallet) {
            balance = ((TonApi.GenericAccountStateWallet) accountState).accountState.balance;
        } else if (accountState instanceof TonApi.GenericAccountStateWalletV3) {
            balance = ((TonApi.GenericAccountStateWalletV3) accountState).accountState.balance;
        } else {
            balance = 0;
        }
        return balance >= 0 ? balance : 0;
    }

    public boolean isValidWalletAddress(String address) {
        TonApi.UnpackAccountAddress req = new TonApi.UnpackAccountAddress(address);
        Object res = sendRequest(req, true);
        return res instanceof TonApi.UnpackedAccountAddress;
    }

    public void isKeyStoreInvalidated(BooleanCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            boolean invalidated = initCipher(Cipher.DECRYPT_MODE) == CIPHER_INIT_KEY_INVALIDATED;
            AndroidUtilities.runOnUIThread(() -> callback.run(invalidated));
        });
    }

    public void createWallet(String[] words, boolean useBiometric, WordsCallback onFinishRunnable, ErrorCallback onErrorRunnable) {
        AndroidUtilities.getTonWalletSalt(currentAccount, param -> Utilities.globalQueue.postRunnable(() -> {
            if (param != null) {
                if (initTonLib()) {
                    sendRequest(new TonApi.DeleteAllKeys(), true);
                    if (keyStore == null) {
                        AndroidUtilities.runOnUIThread(() -> onErrorRunnable.run("KEYSTORE_FAIL", null));
                        return;
                    }
                    cleanup();
                    getUserConfig().tonKeyName = "walletKey" + Utilities.random.nextLong();
                    if (!isKeyCreated(useBiometric)) {
                        AndroidUtilities.runOnUIThread(() -> onErrorRunnable.run("KEYSTORE_FAIL", null));
                        return;
                    }

                    byte[] password = new byte[64];
                    byte[] seed = new byte[32];
                    Utilities.random.nextBytes(password);
                    Utilities.random.nextBytes(seed);
                    if (param.length == 32) {
                        System.arraycopy(param, 0, password, 32, 32);
                        Arrays.fill(param, (byte) 0);
                    }
                    Object result;
                    if (words == null) {
                        result = sendRequest(new TonApi.CreateNewKey(password, new byte[0], seed), true);
                    } else {
                        result = sendRequest(new TonApi.ImportKey(password, new byte[0], new TonApi.ExportedKey(words)), true);
                    }
                    if (result instanceof TonApi.Key) {
                        TonApi.Key key = (TonApi.Key) result;
                        if (words == null) {
                            Object exportedKey = sendRequest(new TonApi.ExportKey(new TonApi.InputKeyRegular(key, password)), true);
                            if (exportedKey instanceof TonApi.ExportedKey) {
                                TonApi.ExportedKey exportKey = (TonApi.ExportedKey) exportedKey;
                                onFinishWalletCreate(exportKey.wordList, onFinishRunnable, password, key);
                            } else {
                                AndroidUtilities.runOnUIThread(() -> onErrorRunnable.run("TONLIB_FAIL", getTonApiErrorSafe(exportedKey)));
                            }
                        } else {
                            onFinishWalletCreate(null, onFinishRunnable, password, key);
                        }
                    } else {
                        AndroidUtilities.runOnUIThread(() -> onErrorRunnable.run("TONLIB_FAIL", getTonApiErrorSafe(result)));
                    }
                } else {
                    AndroidUtilities.runOnUIThread(() -> onErrorRunnable.run("TONLIB_INIT_FAIL", null));
                }
            } else {
                AndroidUtilities.runOnUIThread(() -> onErrorRunnable.run("SALT_GET_FAIL", null));
            }
        }));
    }

    public void setUserPasscode(String passcode, int type, Runnable onFinishRunnable) {
        Utilities.globalQueue.postRunnable(() -> {
            creatingPasscodeType = type;
            creatingPasscodeSalt = new byte[32];
            Utilities.random.nextBytes(creatingPasscodeSalt);
            byte[] hash = Utilities.computePBKDF2(passcode.getBytes(), creatingPasscodeSalt);
            byte[] key = new byte[32];
            byte[] iv = new byte[32];
            System.arraycopy(hash, 0, key, 0, key.length);
            System.arraycopy(hash, key.length, iv, 0, iv.length);
            Utilities.aesIgeEncryptionByteArray(creatingDataForLaterEncrypt, key, iv, true, false, 0, creatingDataForLaterEncrypt.length);
            creatingEncryptedData = encrypt(creatingDataForLaterEncrypt);
            AndroidUtilities.runOnUIThread(onFinishRunnable);
        });
    }

    public void finishSettingUserPasscode() {
        if (creatingDataForLaterEncrypt != null) {
            Arrays.fill(creatingDataForLaterEncrypt, (byte) 0);
            creatingDataForLaterEncrypt = null;
        }
    }

    public boolean isWaitingForUserPasscode() {
        return creatingDataForLaterEncrypt != null;
    }

    public void clearPendingCache() {
        pendingTransactions.clear();
        saveCache();
    }

    public void saveWalletKeys(boolean finish) {
        UserConfig userConfig = getUserConfig();
        if (creatingEncryptedData != null) {
            userConfig.tonEncryptedData = creatingEncryptedData;
            userConfig.tonPublicKey = creatingPublicKey;
            userConfig.tonPasscodeType = creatingPasscodeType;
            userConfig.tonPasscodeSalt = creatingPasscodeSalt;
            creatingEncryptedData = null;
            creatingPublicKey = null;
            creatingPasscodeType = -1;
            creatingPasscodeSalt = null;
        }
        userConfig.tonCreationFinished = finish;
        userConfig.saveConfig(false);
    }

    public Bitmap createTonQR(Context context, String key, Bitmap oldBitmap) {
        try {
            HashMap<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            return new QRCodeWriter().encode(key, BarcodeFormat.QR_CODE, 768, 768, hints, oldBitmap, context);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static void shareBitmap(Activity activity, View view, String text) {
        try {
            ImageView imageView = (ImageView) view;
            BitmapDrawable bitmapDrawable = (BitmapDrawable) imageView.getDrawable();
            File f = AndroidUtilities.getSharingDirectory();
            f.mkdirs();
            f = new File(f, "qr.jpg");
            FileOutputStream outputStream = new FileOutputStream(f.getAbsolutePath());
            bitmapDrawable.getBitmap().compress(Bitmap.CompressFormat.JPEG, 87, outputStream);
            outputStream.close();

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/jpeg");
            if (!TextUtils.isEmpty(text)) {
                intent.putExtra(Intent.EXTRA_TEXT, text);
            }
            if (Build.VERSION.SDK_INT >= 24) {
                try {
                    intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", f));
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignore) {
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                }
            } else {
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
            }
            activity.startActivityForResult(Intent.createChooser(intent, LocaleController.getString("WalletShareQr", R.string.WalletShareQr)), 500);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public String[] getHintWords() {
        initTonLib();
        Object response = sendRequest(new TonApi.GetBip39Hints(), true);
        if (response instanceof TonApi.Bip39Hints) {
            return ((TonApi.Bip39Hints) response).words;
        }
        return null;
    }

    public String getWalletAddress(String publicKey) {
        if (accountAddress != null) {
            return accountAddress.accountAddress;
        }
        initTonLib();
        Object response = sendRequest(new TonApi.WalletV3GetAccountAddress(new TonApi.WalletV3InitialAccountState(publicKey, walletId)), true);
        if (response instanceof TonApi.AccountAddress) {
            return (accountAddress = ((TonApi.AccountAddress) response)).accountAddress;
        }
        return null;
    }

    public void checkPendingTransactionsForFailure(TonApi.GenericAccountState state) {
        if (state == null || pendingTransactions.isEmpty()) {
            return;
        }
        long time = getLastSyncTime(state);
        boolean pendingChanged = false;
        for (int a = 0, N = pendingTransactions.size(); a < N; a++) {
            TonApi.RawTransaction pending = pendingTransactions.get(a);
            if (pending.otherFee <= time) {
                pendingTransactions.remove(a);
                N--;
                a--;
                pendingChanged = true;
            }
        }
        if (onPendingTransactionsEmpty != null && pendingTransactions.isEmpty()) {
            onPendingTransactionsEmpty.run();
            onPendingTransactionsEmpty = null;
        }
        if (pendingChanged) {
            saveCache();
            getNotificationCenter().postNotificationName(NotificationCenter.walletPendingTransactionsChanged);
        }
    }

    public void setTransactionCallback(GetTransactionsCallback callback) {
        uiTransactionCallback = callback;
    }

    public void getTransactions(boolean reload, TonApi.InternalTransactionId fromTransaction) {
        getTransactions(reload, fromTransaction, null);
    }

    private void getTransactions(boolean reload, TonApi.InternalTransactionId fromTransaction, Runnable callback) {
        sendRequest(new TonApi.RawGetTransactions(accountAddress, fromTransaction), response -> {
            if (response instanceof TonApi.RawTransactions) {
                TonApi.RawTransactions rawTransactions = (TonApi.RawTransactions) response;
                ArrayList<TonApi.RawTransaction> transactions = new ArrayList<>(Arrays.asList(rawTransactions.transactions));
                AndroidUtilities.runOnUIThread(() -> {
                    walletLoaded = true;
                    boolean pendingChanged = false;
                    if (!pendingTransactions.isEmpty()) {
                        for (int a = 0, N = pendingTransactions.size(); a < N; a++) {
                            TonApi.RawTransaction pending = pendingTransactions.get(a);
                            for (int b = 0, N2 = transactions.size(); b < N2; b++) {
                                TonApi.RawTransaction transaction = transactions.get(b);
                                if (transaction.inMsg != null) {
                                    if (Arrays.equals(pending.inMsg.bodyHash, transaction.inMsg.bodyHash)) {
                                        pendingTransactions.remove(a);
                                        N--;
                                        a--;
                                        pendingChanged = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (onPendingTransactionsEmpty != null && pendingTransactions.isEmpty()) {
                        onPendingTransactionsEmpty.run();
                        onPendingTransactionsEmpty = null;
                    }
                    if (reload) {
                        cachedTransactions.clear();
                        cachedTransactions.addAll(transactions);
                        saveCache();
                    } else if (pendingChanged) {
                        saveCache();
                    }
                    if (callback != null) {
                        callback.run();
                    }
                    if (uiTransactionCallback != null) {
                        uiTransactionCallback.run(reload, transactions);
                    }
                });
            } else {
                if (callback != null || uiTransactionCallback != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (callback != null) {
                            callback.run();
                        }
                        if (uiTransactionCallback != null) {
                            uiTransactionCallback.run(reload, null);
                        }
                    });
                }

            }
        });
    }

    private void preloadWallet(String publicKey) {
        if (isPrealodingWallet) {
            return;
        }
        isPrealodingWallet = true;
        getWalletAddress(publicKey);
        getAccountState(state -> {
            if (state == null) {
                isPrealodingWallet = false;
                return;
            }
            getTransactions(true, getLastTransactionId(state), () -> isPrealodingWallet = false);
        });
    }

    public boolean isWalletLoaded() {
        return walletLoaded;
    }

    public int getSyncProgress() {
        return syncProgress;
    }

    public void getAccountState(AccountStateCallback callback) {
        sendRequest(new TonApi.GenericGetAccountState(accountAddress), response -> {
            if (response instanceof TonApi.GenericAccountState) {
                AndroidUtilities.runOnUIThread(() -> callback.run(cachedAccountState = (TonApi.GenericAccountState) response));
            } else {
                AndroidUtilities.runOnUIThread(() -> callback.run(null));
            }
        });
    }

    private TonApi.InputKey decryptTonData(String passcode, Cipher cipherForDecrypt, Runnable onPasscodeOk, ErrorCallback onErrorRunnable, boolean forPasscodeChange) {
        UserConfig userConfig = getUserConfig();
        byte[] decrypted = decrypt(userConfig.tonEncryptedData, cipherForDecrypt);
        if (decrypted == null || decrypted.length <= 3) {
            AndroidUtilities.runOnUIThread(() -> onErrorRunnable.run("KEYSTORE_FAIL", null));
            return null;
        }
        if (userConfig.tonPasscodeType != -1) {
            byte[] hash = Utilities.computePBKDF2(passcode.getBytes(), userConfig.tonPasscodeSalt);
            byte[] key = new byte[32];
            byte[] iv = new byte[32];
            System.arraycopy(hash, 0, key, 0, key.length);
            System.arraycopy(hash, key.length, iv, 0, iv.length);
            Utilities.aesIgeEncryptionByteArray(decrypted, key, iv, false, false, 0, decrypted.length);
        }
        if (decrypted[1] == 'o' && decrypted[2] == 'k') {
            if (!TextUtils.isEmpty(passcode) && onPasscodeOk != null) {
                AndroidUtilities.runOnUIThread(onPasscodeOk);
            }
            int padding = decrypted[0];
            byte[] password = new byte[64];
            byte[] secret = new byte[decrypted.length - 64 - padding - 3];
            System.arraycopy(decrypted, 3, password, 0, password.length);
            System.arraycopy(decrypted, 3 + password.length, secret, 0, secret.length);
            if (forPasscodeChange) {
                creatingDataForLaterEncrypt = decrypted;
                creatingPublicKey = userConfig.tonPublicKey;
            }
            return new TonApi.InputKeyRegular(new TonApi.Key(userConfig.tonPublicKey, secret), password);
        } else {
            if (!TextUtils.isEmpty(passcode)) {
                AndroidUtilities.runOnUIThread(() -> onErrorRunnable.run("PASSCODE_INVALID", null));
            } else {
                AndroidUtilities.runOnUIThread(() -> onErrorRunnable.run("KEYSTORE_FAIL_DECRYPT", null));
            }
            return null;
        }
    }

    public void prepareForPasscodeChange(String passcode, Runnable onFinishRunnable, ErrorCallback onErrorRunnable) {
        Utilities.globalQueue.postRunnable(() -> {
            TonApi.InputKey inputKey = decryptTonData(passcode, null, null, onErrorRunnable, true);
            if (inputKey == null) {
                return;
            }
            AndroidUtilities.runOnUIThread(onFinishRunnable);
        });
    }

    public void getSecretWords(String passcode, Cipher cipherForDecrypt, WordsCallback onFinishRunnable, ErrorCallback onErrorRunnable) {
        Utilities.globalQueue.postRunnable(() -> {
            initTonLib();
            TonApi.InputKey inputKey = decryptTonData(passcode, cipherForDecrypt, null, onErrorRunnable, false);
            if (inputKey == null) {
                return;
            }
            sendRequest(new TonApi.ExportKey(inputKey), exportedKey -> {
                if (exportedKey instanceof TonApi.ExportedKey) {
                    TonApi.ExportedKey exportKey = (TonApi.ExportedKey) exportedKey;
                    AndroidUtilities.runOnUIThread(() -> onFinishRunnable.run(exportKey.wordList));
                } else {
                    AndroidUtilities.runOnUIThread(() -> onErrorRunnable.run("TONLIB_FAIL", getTonApiErrorSafe(exportedKey)));
                }
            });
        });
    }

    public void getSendFee(String fromWallet, String toWallet, long amount, String message, FeeCallback feeCallback) {
        Utilities.globalQueue.postRunnable(() -> {
            TonApi.GenericCreateSendGramsQuery req = new TonApi.GenericCreateSendGramsQuery(new TonApi.InputKeyFake(), new TonApi.AccountAddress(fromWallet), new TonApi.AccountAddress(toWallet), amount, 0, true, message != null ? message.getBytes() : new byte[0]);
            sendRequest(req, result -> {
                if (result instanceof TonApi.QueryInfo) {
                    TonApi.QueryInfo queryInfo = (TonApi.QueryInfo) result;
                    TonApi.QueryEstimateFees req2 = new TonApi.QueryEstimateFees(queryInfo.id, true);
                    sendRequest(req2, result1 -> {
                        if (result1 instanceof TonApi.QueryFees) {
                            TonApi.QueryFees queryFees = (TonApi.QueryFees) result1;
                            long fee = queryFees.sourceFees.fwdFee + queryFees.sourceFees.gasFee + queryFees.sourceFees.inFwdFee + queryFees.sourceFees.storageFee;
                            AndroidUtilities.runOnUIThread(() -> feeCallback.run(fee));
                        } else {
                            AndroidUtilities.runOnUIThread(() -> feeCallback.run(0));
                        }
                    });
                } else {
                    AndroidUtilities.runOnUIThread(() -> feeCallback.run(0));
                }
            });
        });
    }

    public void sendGrams(String passcode, Cipher cipherForDecrypt, TonApi.InputKey key, String fromWallet, String toWallet, long amount, String message, Runnable onPasscodeOk, Runnable onRequestDone, Runnable onFinishRunnable, DangerousCallback onDangerousSend, ErrorCallback onErrorRunnable) {
        Utilities.globalQueue.postRunnable(() -> {
            TonApi.InputKey inputKey = key;
            if (inputKey == null) {
                inputKey = decryptTonData(passcode, cipherForDecrypt, onPasscodeOk, onErrorRunnable, false);
            }
            if (inputKey == null) {
                return;
            }
            final TonApi.InputKey inputKeyFinal = inputKey;
            TonApi.GenericCreateSendGramsQuery req = new TonApi.GenericCreateSendGramsQuery(inputKey, new TonApi.AccountAddress(fromWallet), new TonApi.AccountAddress(toWallet), amount, 0, true, message != null ? message.getBytes() : new byte[0]);
            sendRequest(req, result -> {
                if (result instanceof TonApi.QueryInfo) {
                    TonApi.QueryInfo queryInfo = (TonApi.QueryInfo) result;
                    TonApi.QuerySend req2 = new TonApi.QuerySend(queryInfo.id);
                    sendRequest(req2, result1 -> {
                        if (result1 instanceof TonApi.Ok) {
                            AndroidUtilities.runOnUIThread(() -> {
                                TonApi.RawMessage rawMessage = new TonApi.RawMessage();
                                rawMessage.source = fromWallet;
                                rawMessage.destination = toWallet;
                                rawMessage.value = -amount;
                                rawMessage.message = message != null ? message.getBytes() : new byte[0];
                                rawMessage.bodyHash = queryInfo.bodyHash;
                                TonApi.RawTransaction sendingTransaction = new TonApi.RawTransaction(System.currentTimeMillis() / 1000, new byte[0], new TonApi.InternalTransactionId(), 0, 0, queryInfo.validUntil, rawMessage, new TonApi.RawMessage[0]);
                                pendingTransactions.add(0, sendingTransaction);
                                saveCache();
                                getNotificationCenter().postNotificationName(NotificationCenter.walletPendingTransactionsChanged);
                                onPendingTransactionsEmpty = onFinishRunnable;
                                onRequestDone.run();
                            });
                        } else {
                            AndroidUtilities.runOnUIThread(() -> onErrorRunnable.run("TONLIB_FAIL", getTonApiErrorSafe(result1)));
                        }
                    });
                } else {
                    AndroidUtilities.runOnUIThread(() -> {
                        TonApi.Error error = getTonApiErrorSafe(result);
                        if (error != null && error.message.startsWith("DANGEROUS_TRANSACTION")) {
                            onDangerousSend.run(inputKeyFinal);
                        } else {
                            onErrorRunnable.run("TONLIB_FAIL", error);
                        }
                    });
                }
            });
        });
    }

    private void runShortPolling() {
        if (shortPollingInProgress || pendingTransactions.isEmpty()) {
            return;
        }
        shortPollingInProgress = true;
        TonApi.GenericAccountState oldState = cachedAccountState;
        getAccountState(state -> {
            boolean needUpdateTransactions = false;
            if (state != null) {
                if (oldState != null) {
                    TonApi.InternalTransactionId oldTransaction = getLastTransactionId(oldState);
                    TonApi.InternalTransactionId newTransaction = getLastTransactionId(state);
                    needUpdateTransactions = oldTransaction == null || newTransaction == null || oldTransaction.lt != newTransaction.lt;
                } else {
                    needUpdateTransactions = true;
                }
            }
            if (needUpdateTransactions) {
                getTransactions(true, getLastTransactionId(state), () -> {
                    shortPollRunnable = null;
                    shortPollingInProgress = false;
                    checkPendingTransactionsForFailure(state);
                    if (!pendingTransactions.isEmpty()) {
                        scheduleShortPoll();
                    }
                });
            } else {
                shortPollRunnable = null;
                shortPollingInProgress = false;
                checkPendingTransactionsForFailure(state);
                if (!pendingTransactions.isEmpty()) {
                    scheduleShortPoll();
                }
            }
        });
    }

    public void scheduleShortPoll() {
        if (shortPollRunnable != null) {
            return;
        }
        AndroidUtilities.runOnUIThread(shortPollRunnable = this::runShortPolling, 3000);
    }

    public void cancelShortPoll() {
        if (shortPollRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
            shortPollRunnable = null;
        }
        onPendingTransactionsEmpty = null;
        shortPollingInProgress = false;
    }

    public boolean hasPendingTransactions() {
        return !pendingTransactions.isEmpty();
    }

    public static CharSequence formatCurrency(long value) {
        if (value == 0) {
            return "0";
        }
        String sign = value < 0 ? "-" : "";
        StringBuilder builder = new StringBuilder(String.format(Locale.US, "%s%d.%09d", sign, Math.abs(value / 1000000000L), Math.abs(value % 1000000000)));
        while (builder.length() > 1 && builder.charAt(builder.length() - 1) == '0' && builder.charAt(builder.length() - 2) != '.') {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder;
    }

    private void loadTonConfigFromUrl() {
        UserConfig userConfig = getUserConfig();
        if (userConfig.walletConfigType != CONFIG_TYPE_URL) {
            return;
        }
        WalletConfigLoader.loadConfig(userConfig.walletConfigUrl, result -> {
            if (TextUtils.isEmpty(result)) {
                return;
            }
            if (!TextUtils.equals(userConfig.walletConfigFromUrl, result)) {
                userConfig.walletConfigFromUrl = result;
                userConfig.saveConfig(false);
                onTonConfigUpdated();
            }
        });
    }
}
