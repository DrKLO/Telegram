package org.telegram.ui.bots;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleRegistry;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.SRPHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.Text;
import org.telegram.ui.LaunchActivity;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class BotBiometry {

    public static final String PREF = "2botbiometry_";

    public final Context context;
    public final int currentAccount;
    public final long botId;

    public boolean disabled;
    public boolean access_granted;
    public boolean access_requested;

    private String encrypted_token;

    public BotBiometry(Context context, int currentAccount, long botId) {
        this.context = context;
        this.currentAccount = currentAccount;
        this.botId = botId;
        load();
    }

    public void load() {
        SharedPreferences prefs = context.getSharedPreferences(PREF + currentAccount, Activity.MODE_PRIVATE);
        this.encrypted_token = prefs.getString(String.valueOf(botId), null);
        this.access_granted = this.encrypted_token != null;
        this.access_requested = this.access_granted || prefs.getBoolean(botId + "_requested", false);
        this.disabled = prefs.getBoolean(botId + "_disabled", false);
    }

    @Nullable
    public static String getAvailableType(Context context) {
        try {
            BiometricManager manager = BiometricManager.from(context);
            if (manager == null) return null;
            if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
                return null;
            }
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
        return "unknown";
    }

    private BiometricPrompt prompt;

    public void requestToken(String reason, Utilities.Callback2<Boolean, String> whenDecrypted) {
        prompt(reason, true, null, result -> {
            String token = null;
            if (result != null) {
                try {
                    BiometricPrompt.CryptoObject cryptoObject = result.getCryptoObject();
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        if (!TextUtils.isEmpty(encrypted_token)) {
                            token = encrypted_token.split(";")[0];
                        } else {
                            token = encrypted_token;
                        }
                    } else if (cryptoObject != null) {
                        if (!TextUtils.isEmpty(encrypted_token)) {
                            token = new String(cryptoObject.getCipher().doFinal(Utilities.hexToBytes(encrypted_token.split(";")[0])), StandardCharsets.UTF_8);
                        } else {
                            token = encrypted_token;
                        }
                    } else {
                        if (!TextUtils.isEmpty(encrypted_token)) {
                            throw new RuntimeException("No cryptoObject found");
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    result = null;
                }
            }
            whenDecrypted.run(result != null, token);
        });
    }

    public void updateToken(String reason, String token, Utilities.Callback<Boolean> whenDone) {
        prompt(reason, false, token, result -> {
            boolean success = true;
            if (result != null) {
                try {
                    BiometricPrompt.CryptoObject cryptoObject = result.getCryptoObject();
                    if (TextUtils.isEmpty(token)) {
                        encrypted_token = null;
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        encrypted_token = token;
                    } else {
                        if (cryptoObject == null) {
                            cryptoObject = makeCryptoObject(false);
                        }
                        if (cryptoObject != null) {
                            encrypted_token = Utilities.bytesToHex(cryptoObject.getCipher().doFinal(token.getBytes(StandardCharsets.UTF_8))) + ";" + Utilities.bytesToHex(cryptoObject.getCipher().getIV());
                        } else {
                            throw new RuntimeException("No cryptoObject found");
                        }
                    }
                    save();
                } catch (Exception e) {
                    FileLog.e(e);
                    success = false;
                }
            }
            whenDone.run(success);
        });
    }

    private void initPrompt() {
        if (prompt != null) return;
        final Executor executor = ContextCompat.getMainExecutor(context);
        prompt = new BiometricPrompt(LaunchActivity.instance, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                FileLog.d("BotBiometry onAuthenticationError " + errorCode + " \"" + errString + "\"");
                if (callback != null) {
                    Utilities.Callback<BiometricPrompt.AuthenticationResult> thisCallback = callback;
                    callback = null;
                    thisCallback.run(null);
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                FileLog.d("BotBiometry onAuthenticationSucceeded");
                if (callback != null) {
                    Utilities.Callback<BiometricPrompt.AuthenticationResult> thisCallback = callback;
                    callback = null;
                    thisCallback.run(result);
                }
            }

            @Override
            public void onAuthenticationFailed() {
                FileLog.d("BotBiometry onAuthenticationFailed");
                if (callback != null) {
                    Utilities.Callback<BiometricPrompt.AuthenticationResult> thisCallback = callback;
                    callback = null;
                    thisCallback.run(null);
                }
            }
        });
    }

    private BiometricPrompt.CryptoObject makeCryptoObject(boolean decrypt) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Cipher cipher = getCipher();
                SecretKey secretKey = getSecretKey();
                if (decrypt) {
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(Utilities.hexToBytes(encrypted_token.split(";")[1])));
                } else {
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                }
                return new BiometricPrompt.CryptoObject(cipher);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private Utilities.Callback<BiometricPrompt.AuthenticationResult> callback;
    private void prompt(
        String text,
        boolean decrypt,
        String token,
        Utilities.Callback<BiometricPrompt.AuthenticationResult> whenDone
    ) {
        this.callback = whenDone;
        try {
            initPrompt();
        } catch (Exception e) {
            FileLog.e(e);
            whenDone.run(null);
            return;
        }
        BiometricPrompt.CryptoObject cryptoObject = makeCryptoObject(decrypt);
        final TLRPC.User bot = MessagesController.getInstance(currentAccount).getUser(botId);
        final BiometricPrompt.PromptInfo.Builder promptInfoBuilder = new BiometricPrompt.PromptInfo.Builder()
            .setTitle(UserObject.getUserName(bot))
            .setNegativeButtonText(LocaleController.getString(R.string.Back))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        if (!TextUtils.isEmpty(text)) {
            promptInfoBuilder.setDescription(text);
        }
        final BiometricPrompt.PromptInfo promptInfo = promptInfoBuilder.build();
        if (cryptoObject != null && !decrypt) {
            try {
                if (TextUtils.isEmpty(token)) {
                    encrypted_token = null;
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    encrypted_token = token;
                } else {
                    encrypted_token = Utilities.bytesToHex(cryptoObject.getCipher().doFinal(token.getBytes(StandardCharsets.UTF_8))) + ";" + Utilities.bytesToHex(cryptoObject.getCipher().getIV());
                }
                save();
                this.callback = null;
                whenDone.run(null);
                return;
            } catch (Exception e) {
                FileLog.e(e);
            }
            cryptoObject = makeCryptoObject(decrypt);
        }
        if (cryptoObject != null) {
            prompt.authenticate(promptInfo, cryptoObject);
        } else {
            prompt.authenticate(promptInfo);
        }
    }

    private static KeyStore keyStore;

    @RequiresApi(api = Build.VERSION_CODES.M)
    private SecretKey getSecretKey() throws Exception {
        if (keyStore == null) {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
        }
        if (keyStore.containsAlias("6bot_" + botId)) {
            return ((SecretKey) keyStore.getKey("6bot_" + botId, null));
        } else {
            KeyGenParameterSpec.Builder keygenBuilder = new KeyGenParameterSpec.Builder(
                    "6bot_" + botId,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
            );
            keygenBuilder.setBlockModes(KeyProperties.BLOCK_MODE_CBC);
            keygenBuilder.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7);
            keygenBuilder.setUserAuthenticationRequired(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                keygenBuilder.setUserAuthenticationParameters(60, KeyProperties.AUTH_BIOMETRIC_STRONG);
            } else {
                keygenBuilder.setUserAuthenticationValidityDurationSeconds(60);
            }
            if (Build.VERSION.SDK_INT >= 24) {
                keygenBuilder.setInvalidatedByBiometricEnrollment(true);
            }
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
            );
            keyGenerator.init(keygenBuilder.build());
            return keyGenerator.generateKey();
        }
    }

    private Cipher getCipher() throws Exception {
        return Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7
        );
    }

    public JSONObject getStatus() throws JSONException {
        JSONObject object = new JSONObject();
        final String availableType = getAvailableType(context);
        if (availableType != null) {
            object.put("available", true);
            object.put("type", availableType);
        } else {
            object.put("available", false);
        }
        object.put("access_requested", access_requested);
        object.put("access_granted", access_granted && !disabled);
        object.put("token_saved", !TextUtils.isEmpty(encrypted_token));
        object.put("device_id", getDeviceId(context, currentAccount, botId));
        return object;
    }

    public static String getDeviceId(Context context, int currentAccount, long botId) {
        final SharedPreferences prefs = context.getSharedPreferences(PREF + currentAccount, Activity.MODE_PRIVATE);
        String deviceId = prefs.getString("device_id" + botId, null);
        if (deviceId == null) {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            prefs.edit().putString("device_id" + botId, deviceId = Utilities.bytesToHex(bytes)).apply();
        }
        return deviceId;
    }

    public void save() {
        final SharedPreferences prefs = context.getSharedPreferences(PREF + currentAccount, Activity.MODE_PRIVATE);
        final SharedPreferences.Editor edit = prefs.edit();
        if (access_requested) {
            edit.putBoolean(botId + "_requested", true);
        } else {
            edit.remove(botId + "_requested");
        }
        if (access_granted) {
            edit.putString(String.valueOf(botId), encrypted_token == null ? "" : encrypted_token);
        } else {
            edit.remove(String.valueOf(botId));
        }
        if (disabled) {
            edit.putBoolean(botId + "_disabled", true);
        } else {
            edit.remove(botId + "_disabled");
        }
        edit.apply();
    }

    public static class Bot {
        private Bot(TLRPC.User user, boolean disabled) {
            this.user = user;
            this.disabled = disabled;
        }
        public TLRPC.User user;
        public boolean disabled;
    }

    public static void getBots(
        Context context,
        int currentAccount,
        Utilities.Callback<ArrayList<Bot>> whenDone
    ) {
        if (whenDone == null) return;

        final SharedPreferences prefs = context.getSharedPreferences(PREF + currentAccount, Activity.MODE_PRIVATE);

        final ArrayList<Long> botIds = new ArrayList<>();
        final ArrayList<Boolean> botDisabled = new ArrayList<>();
        final Map<String, ?> values = prefs.getAll();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!entry.getKey().startsWith("device_id") || !(entry.getValue() instanceof String)) continue;
            long botId;
            boolean disabled;
            try {
                botId = Long.parseLong(entry.getKey().substring("device_id".length()));
                Boolean disabledValue = (Boolean) values.get(botId + "_disabled");
                disabled = disabledValue != null && disabledValue;
            } catch (Exception e) {
                FileLog.e(e);
                continue;
            }
            botIds.add(botId);
            botDisabled.add(disabled);
        }

        if (botIds.isEmpty()) {
            whenDone.run(new ArrayList<>());
            return;
        }

        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            ArrayList<TLRPC.User> bots = MessagesStorage.getInstance(currentAccount).getUsers(botIds);
            AndroidUtilities.runOnUIThread(() -> {
                ArrayList<Bot> result = new ArrayList<>();
                for (int i = 0; i < bots.size(); ++i) {
                    result.add(new Bot(bots.get(i), i < botDisabled.size() && botDisabled.get(i)));
                }
                whenDone.run(result);
            });
        });
    }

    public static void toggleBotDisabled(
        Context context,
        int currentAccount,
        long botId,
        boolean disabled
    ) {
        final SharedPreferences prefs = context.getSharedPreferences(PREF + currentAccount, Activity.MODE_PRIVATE);
        final SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(botId + "_disabled", disabled);
        if (!disabled && prefs.getString(String.valueOf(botId), null) == null) {
            edit.putString(String.valueOf(botId), "");
        }
        edit.apply();
    }

    public static void removeBot(
        Context context,
        int currentAccount,
        long botId
    ) {
        final SharedPreferences prefs = context.getSharedPreferences(PREF + currentAccount, Activity.MODE_PRIVATE);
        final SharedPreferences.Editor edit = prefs.edit();
        edit.remove(String.valueOf(botId)).remove(botId + "_requested");
        edit.apply();

        try {
            if (keyStore == null) {
                keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
            }
            keyStore.deleteEntry("bot_" + botId);
            keyStore.deleteEntry("2bot_" + botId);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void clear() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) return;
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
            final SharedPreferences prefs = context.getSharedPreferences(PREF + i, Activity.MODE_PRIVATE);
            prefs.edit().clear().apply();
        }
    }

}
