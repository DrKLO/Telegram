package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionManager {
    public static final String ENCRYPTION_PREFIX = "[ENCRYPTED] ";

    private static final String PREF_CONFIG = "encryption_config";
    private static final String PREF_KEYS = "encryption_keys";
    private static final String PREF_CACHE = "encryption_cache";
    private static final int AES_KEY_SIZE_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int NETWORK_TIMEOUT_MS = 10000;

    private static final Map<String, String> publicKeyCache = new ConcurrentHashMap<>();

    public interface SimpleCallback<T> {
        void onResult(T result, String error);
    }

    public static class DisplayResult {
        public final String displayText;
        public final String statusLine;
        public final boolean encrypted;
        public final boolean error;

        public DisplayResult(String displayText, String statusLine, boolean encrypted, boolean error) {
            this.displayText = displayText;
            this.statusLine = statusLine;
            this.encrypted = encrypted;
            this.error = error;
        }
    }

    private static SharedPreferences getPrefs(String name) {
        return ApplicationLoader.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    private static String keyForAccount(String key, int account) {
        return key + "_" + account;
    }

    public static boolean isRegistered(int account) {
        return getPrefs(PREF_CONFIG).getBoolean(keyForAccount("registered", account), false);
    }

    public static boolean isVerified(int account) {
        return getPrefs(PREF_CONFIG).getBoolean(keyForAccount("verified", account), false);
    }

    public static String getServerAddress(int account) {
        return getPrefs(PREF_CONFIG).getString(keyForAccount("server", account), "");
    }

    public static void setServerAddress(int account, String address) {
        String normalized = normalizeServerAddress(address);
        String key = keyForAccount("server", account);
        String current = getPrefs(PREF_CONFIG).getString(key, "");
        if (!TextUtils.equals(current, normalized)) {
            getPrefs(PREF_CONFIG).edit().putString(key, normalized).apply();
            clearCache(account);
        }
    }

    public static void clearCache(int account) {
        String cacheKey = keyForAccount("public_keys", account);
        getPrefs(PREF_CACHE).edit().remove(cacheKey).apply();
        publicKeyCache.clear();
    }

    public static void registerUser(int account, SimpleCallback<String> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            String server = getServerAddress(account);
            if (TextUtils.isEmpty(server)) {
                respond(callback, null, "Server address is empty");
                return;
            }
            long userId = UserConfig.getInstance(account).getClientUserId();
            try {
                ensureKeys(account);
                JSONObject payload = new JSONObject();
                payload.put("userid", userId);
                JSONObject response = postJson(server + "/api/v1/register", payload);
                if (response == null) {
                    respond(callback, null, "Empty response");
                    return;
                }
                String status = response.optString("status", null);
                if (!TextUtils.isEmpty(status)) {
                    getPrefs(PREF_CONFIG).edit()
                        .putBoolean(keyForAccount("registered", account), true)
                        .apply();
                    respond(callback, status, null);
                } else {
                    respond(callback, null, response.optString("error", "Unknown error"));
                }
            } catch (Exception e) {
                respond(callback, null, e.getMessage());
            }
        });
    }

    public static void verifyUser(int account, String verifySecret, SimpleCallback<String> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            String server = getServerAddress(account);
            if (TextUtils.isEmpty(server)) {
                respond(callback, null, "Server address is empty");
                return;
            }
            if (TextUtils.isEmpty(verifySecret)) {
                respond(callback, null, "Verify secret is empty");
                return;
            }
            long userId = UserConfig.getInstance(account).getClientUserId();
            try {
                KeyBundle keys = ensureKeys(account);
                JSONObject publicKey = new JSONObject();
                publicKey.put("v", 1);
                publicKey.put("rsa", keys.rsaPublicB64);
                publicKey.put("ed25519", keys.edPublicB64);

                JSONObject payload = new JSONObject();
                payload.put("userid", userId);
                payload.put("public_key", publicKey.toString());
                payload.put("verify_secret", verifySecret);

                JSONObject response = postJson(server + "/api/v1/verify", payload);
                if (response == null) {
                    respond(callback, null, "Empty response");
                    return;
                }
                String status = response.optString("status", null);
                if (!TextUtils.isEmpty(status)) {
                    getPrefs(PREF_CONFIG).edit()
                        .putBoolean(keyForAccount("verified", account), true)
                        .apply();
                    respond(callback, status, null);
                } else {
                    respond(callback, null, response.optString("error", "Unknown error"));
                }
            } catch (Exception e) {
                respond(callback, null, e.getMessage());
            }
        });
    }

    public static void encryptOutgoingText(int account, long peerUserId, String message, SimpleCallback<String> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            if (TextUtils.isEmpty(message)) {
                respond(callback, null, "Message is empty");
                return;
            }
            if (message.startsWith(ENCRYPTION_PREFIX)) {
                respond(callback, message, null);
                return;
            }
            if (!DialogObject.isUserDialog(peerUserId)) {
                respond(callback, null, "Peer is not a user dialog");
                return;
            }
            String server = getServerAddress(account);
            if (TextUtils.isEmpty(server)) {
                respond(callback, null, "Server address is empty");
                return;
            }
            if (!isVerified(account)) {
                respond(callback, null, "User is not verified");
                return;
            }
            try {
                KeyBundle keys = ensureKeys(account);
                PublicKeyInfo peerKey = getPublicKey(account, peerUserId);
                if (peerKey == null) {
                    respond(callback, null, "User not registered or not verified");
                    return;
                }
                String encrypted = encryptPayload(keys, peerKey, message, UserConfig.getInstance(account).getClientUserId());
                respond(callback, encrypted, null);
            } catch (Exception e) {
                respond(callback, null, e.getMessage());
            }
        });
    }

    public static DisplayResult getDisplayText(int account, long senderUserId, String originalText) {
        if (TextUtils.isEmpty(originalText)) {
            return new DisplayResult("", LocaleController.getString(R.string.EncryptionMessageNotEncrypted), false, false);
        }
        if (!originalText.startsWith(ENCRYPTION_PREFIX)) {
            return new DisplayResult(originalText, LocaleController.getString(R.string.EncryptionMessageNotEncrypted), false, false);
        }
        try {
            KeyBundle keys = ensureKeys(account);
            String payloadB64 = originalText.substring(ENCRYPTION_PREFIX.length());
            JSONObject payload = new JSONObject(new String(Base64.decode(payloadB64, Base64.NO_WRAP), StandardCharsets.UTF_8));
            String ivB64 = payload.getString("iv");
            String cipherB64 = payload.getString("ciphertext");
            String keyToB64 = payload.getString("key_to");
            String keyFromB64 = payload.getString("key_from");
            String signatureB64 = payload.optString("sig", null);
            long sender = payload.optLong("sender", senderUserId);

            byte[] aesKey = tryDecryptAesKey(keys, keyToB64, keyFromB64);
            if (aesKey == null) {
                return new DisplayResult(originalText, LocaleController.getString(R.string.EncryptionMessageDecryptError), true, true);
            }
            String decrypted = decryptAes(aesKey, ivB64, cipherB64);
            boolean signatureOk = true;
            if (!TextUtils.isEmpty(signatureB64)) {
                PublicKeyInfo senderInfo = sender == UserConfig.getInstance(account).getClientUserId()
                    ? new PublicKeyInfo(keys.rsaPublic, keys.edPublic)
                    : getCachedPublicKey(account, sender);
                if (senderInfo != null && senderInfo.edPublic != null) {
                    signatureOk = verifySignature(senderInfo.edPublic, payload, signatureB64);
                }
            }
            String status = signatureOk
                ? LocaleController.getString(R.string.EncryptionMessageDecrypted)
                : LocaleController.getString(R.string.EncryptionMessageSignatureError);
            return new DisplayResult(decrypted, status, true, !signatureOk);
        } catch (Exception e) {
            return new DisplayResult(originalText, LocaleController.getString(R.string.EncryptionMessageDecryptError), true, true);
        }
    }

    private static String normalizeServerAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        String normalized = address.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static void respond(SimpleCallback<String> callback, String result, String error) {
        AndroidUtilities.runOnUIThread(() -> callback.onResult(result, error));
    }

    private static KeyBundle ensureKeys(int account) throws GeneralSecurityException {
        SharedPreferences prefs = getPrefs(PREF_KEYS);
        String rsaPublicKey = prefs.getString(keyForAccount("rsa_public", account), null);
        String rsaPrivateKey = prefs.getString(keyForAccount("rsa_private", account), null);
        String edPublicKey = prefs.getString(keyForAccount("ed_public", account), null);
        String edPrivateKey = prefs.getString(keyForAccount("ed_private", account), null);

        if (rsaPublicKey == null || rsaPrivateKey == null || edPublicKey == null || edPrivateKey == null) {
            KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
            rsaGen.initialize(2048);
            KeyPair rsaPair = rsaGen.generateKeyPair();

            KeyPairGenerator edGen = KeyPairGenerator.getInstance("Ed25519");
            KeyPair edPair = edGen.generateKeyPair();

            rsaPublicKey = Base64.encodeToString(rsaPair.getPublic().getEncoded(), Base64.NO_WRAP);
            rsaPrivateKey = Base64.encodeToString(rsaPair.getPrivate().getEncoded(), Base64.NO_WRAP);
            edPublicKey = Base64.encodeToString(edPair.getPublic().getEncoded(), Base64.NO_WRAP);
            edPrivateKey = Base64.encodeToString(edPair.getPrivate().getEncoded(), Base64.NO_WRAP);

            prefs.edit()
                .putString(keyForAccount("rsa_public", account), rsaPublicKey)
                .putString(keyForAccount("rsa_private", account), rsaPrivateKey)
                .putString(keyForAccount("ed_public", account), edPublicKey)
                .putString(keyForAccount("ed_private", account), edPrivateKey)
                .apply();
        }

        PublicKey rsaPublic = decodePublicKey("RSA", rsaPublicKey);
        PrivateKey rsaPrivate = decodePrivateKey("RSA", rsaPrivateKey);
        PublicKey edPublic = decodePublicKey("Ed25519", edPublicKey);
        PrivateKey edPrivate = decodePrivateKey("Ed25519", edPrivateKey);

        return new KeyBundle(rsaPublic, rsaPrivate, edPublic, edPrivate, rsaPublicKey, edPublicKey);
    }

    private static PublicKey decodePublicKey(String algorithm, String b64) throws GeneralSecurityException {
        byte[] data = Base64.decode(b64, Base64.NO_WRAP);
        return KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(data));
    }

    private static PrivateKey decodePrivateKey(String algorithm, String b64) throws GeneralSecurityException {
        byte[] data = Base64.decode(b64, Base64.NO_WRAP);
        return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(data));
    }

    private static String encryptPayload(KeyBundle keys, PublicKeyInfo peerKey, String message, long senderUserId) throws GeneralSecurityException, JSONException {
        byte[] aesKey = new byte[AES_KEY_SIZE_BYTES];
        new SecureRandom().nextBytes(aesKey);

        byte[] iv = new byte[GCM_IV_BYTES];
        new SecureRandom().nextBytes(iv);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] cipherText = aes.doFinal(message.getBytes(StandardCharsets.UTF_8));

        String keyToB64 = Base64.encodeToString(rsaEncrypt(aesKey, peerKey.rsaPublic), Base64.NO_WRAP);
        String keyFromB64 = Base64.encodeToString(rsaEncrypt(aesKey, keys.rsaPublic), Base64.NO_WRAP);

        JSONObject payload = new JSONObject();
        payload.put("v", 1);
        payload.put("sender", senderUserId);
        payload.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        payload.put("ciphertext", Base64.encodeToString(cipherText, Base64.NO_WRAP));
        payload.put("key_to", keyToB64);
        payload.put("key_from", keyFromB64);

        String signData = buildSignatureData(payload);
        payload.put("sig", Base64.encodeToString(sign(keys.edPrivate, signData), Base64.NO_WRAP));

        String encoded = Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return ENCRYPTION_PREFIX + encoded;
    }

    private static byte[] rsaEncrypt(byte[] data, PublicKey key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    private static byte[] tryDecryptAesKey(KeyBundle keys, String keyToB64, String keyFromB64) throws GeneralSecurityException {
        byte[] keyTo = Base64.decode(keyToB64, Base64.NO_WRAP);
        byte[] keyFrom = Base64.decode(keyFromB64, Base64.NO_WRAP);
        byte[] result = rsaDecryptOrNull(keyTo, keys.rsaPrivate);
        if (result != null) {
            return result;
        }
        return rsaDecryptOrNull(keyFrom, keys.rsaPrivate);
    }

    private static byte[] rsaDecryptOrNull(byte[] data, PrivateKey key) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    private static String decryptAes(byte[] aesKey, String ivB64, String cipherB64) throws GeneralSecurityException {
        byte[] iv = Base64.decode(ivB64, Base64.NO_WRAP);
        byte[] cipherText = Base64.decode(cipherB64, Base64.NO_WRAP);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plain = aes.doFinal(cipherText);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static byte[] sign(PrivateKey key, String data) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    private static boolean verifySignature(PublicKey key, JSONObject payload, String signatureB64) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(key);
            signature.update(buildSignatureData(payload).getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.decode(signatureB64, Base64.NO_WRAP));
        } catch (GeneralSecurityException | JSONException e) {
            return false;
        }
    }

    private static String buildSignatureData(JSONObject payload) throws JSONException {
        StringBuilder builder = new StringBuilder();
        builder.append("v=").append(payload.optInt("v", 1)).append('\n');
        builder.append("sender=").append(payload.optLong("sender", 0)).append('\n');
        builder.append("iv=").append(payload.getString("iv")).append('\n');
        builder.append("ciphertext=").append(payload.getString("ciphertext")).append('\n');
        builder.append("key_to=").append(payload.getString("key_to")).append('\n');
        builder.append("key_from=").append(payload.getString("key_from"));
        return builder.toString();
    }

    private static PublicKeyInfo getPublicKey(int account, long userId) throws IOException, JSONException, GeneralSecurityException {
        PublicKeyInfo cached = getCachedPublicKey(account, userId);
        if (cached != null) {
            return cached;
        }
        String server = getServerAddress(account);
        if (TextUtils.isEmpty(server)) {
            return null;
        }
        JSONObject response = getJson(server + "/api/v1/get_public_key/" + userId);
        if (response == null) {
            return null;
        }
        if (response.has("error")) {
            return null;
        }
        String publicKeyValue = response.optString("public_key", null);
        if (TextUtils.isEmpty(publicKeyValue)) {
            return null;
        }
        PublicKeyInfo info = parsePublicKey(publicKeyValue);
        if (info != null) {
            saveCachedPublicKey(account, userId, publicKeyValue, info);
        }
        return info;
    }

    private static PublicKeyInfo parsePublicKey(String publicKeyValue) throws JSONException, GeneralSecurityException {
        if (publicKeyValue.startsWith("{")) {
            JSONObject obj = new JSONObject(publicKeyValue);
            String rsaB64 = obj.optString("rsa", null);
            String edB64 = obj.optString("ed25519", null);
            if (TextUtils.isEmpty(rsaB64)) {
                return null;
            }
            PublicKey rsa = decodePublicKey("RSA", rsaB64);
            PublicKey ed = TextUtils.isEmpty(edB64) ? null : decodePublicKey("Ed25519", edB64);
            return new PublicKeyInfo(rsa, ed);
        } else {
            PublicKey rsa = decodePublicKey("RSA", publicKeyValue);
            return new PublicKeyInfo(rsa, null);
        }
    }

    private static PublicKeyInfo getCachedPublicKey(int account, long userId) {
        String mapKey = cacheKey(account, userId);
        String cached = publicKeyCache.get(mapKey);
        if (cached == null) {
            String cacheKey = keyForAccount("public_keys", account);
            String json = getPrefs(PREF_CACHE).getString(cacheKey, null);
            if (!TextUtils.isEmpty(json)) {
                try {
                    JSONObject obj = new JSONObject(json);
                    cached = obj.optString(String.valueOf(userId), null);
                    if (!TextUtils.isEmpty(cached)) {
                        publicKeyCache.put(mapKey, cached);
                    }
                } catch (JSONException ignored) {
                }
            }
        }
        if (TextUtils.isEmpty(cached)) {
            return null;
        }
        try {
            return parsePublicKey(cached);
        } catch (Exception e) {
            return null;
        }
    }

    private static void saveCachedPublicKey(int account, long userId, String publicKeyValue, PublicKeyInfo info) {
        String mapKey = cacheKey(account, userId);
        publicKeyCache.put(mapKey, publicKeyValue);
        String cacheKey = keyForAccount("public_keys", account);
        try {
            JSONObject obj = new JSONObject(getPrefs(PREF_CACHE).getString(cacheKey, "{}"));
            obj.put(String.valueOf(userId), publicKeyValue);
            getPrefs(PREF_CACHE).edit().putString(cacheKey, obj.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private static String cacheKey(int account, long userId) {
        return account + ":" + userId;
    }

    private static JSONObject postJson(String url, JSONObject payload) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
        connection.setReadTimeout(NETWORK_TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        byte[] out = payload.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(out.length);
        connection.connect();
        try (OutputStream os = connection.getOutputStream()) {
            os.write(out);
        }
        return readResponse(connection);
    }

    private static JSONObject getJson(String url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
        connection.setReadTimeout(NETWORK_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.connect();
        return readResponse(connection);
    }

    private static JSONObject readResponse(HttpURLConnection connection) throws IOException, JSONException {
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            if (builder.length() == 0) {
                return null;
            }
            return new JSONObject(builder.toString());
        } finally {
            connection.disconnect();
        }
    }

    public static void showEncryptionWarning(String text) {
        AndroidUtilities.runOnUIThread(() ->
            Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_LONG).show()
        );
    }

    private static class KeyBundle {
        final PublicKey rsaPublic;
        final PrivateKey rsaPrivate;
        final PublicKey edPublic;
        final PrivateKey edPrivate;
        final String rsaPublicB64;
        final String edPublicB64;

        KeyBundle(PublicKey rsaPublic, PrivateKey rsaPrivate, PublicKey edPublic, PrivateKey edPrivate, String rsaPublicB64, String edPublicB64) {
            this.rsaPublic = rsaPublic;
            this.rsaPrivate = rsaPrivate;
            this.edPublic = edPublic;
            this.edPrivate = edPrivate;
            this.rsaPublicB64 = rsaPublicB64;
            this.edPublicB64 = edPublicB64;
        }
    }

    private static class PublicKeyInfo {
        final PublicKey rsaPublic;
        final PublicKey edPublic;

        PublicKeyInfo(PublicKey rsaPublic, PublicKey edPublic) {
            this.rsaPublic = rsaPublic;
            this.edPublic = edPublic;
        }
    }
}
