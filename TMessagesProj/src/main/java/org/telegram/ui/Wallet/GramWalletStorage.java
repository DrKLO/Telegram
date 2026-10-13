package org.telegram.ui.Wallet;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Base64;

import org.json.JSONObject;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Wallet.GramWallet.SecretPhrase;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

/**
 * Address-scoped, versioned collection of public-key/recovery-phrase pairs (min SDK 21).
 *
 * Each record has a separate keystore alias. AES-GCM authenticates the address, owner, public
 * key and protection mode. API 21/22 use an RSA keystore key to wrap a random AES key; they do
 * not support fingerprint protection. No new software key is stored in plaintext.
 *
 * Protected AES keys use a 30-second OS authentication window. API 30+ accepts strong
 * biometrics or device credentials; API 23-29 may require device credentials after the
 * fingerprint prompt. Authentication success alone is never treated as proof a key works.
 * Removing/resetting the screen lock can still permanently invalidate protected keys.
 *
 * Writes are serialized across instances. A candidate is synced to a private staging file,
 * reopened, parsed and decrypted using fresh key lookups/ciphers, then atomically renamed
 * over the committed file. Failed verification leaves the old collection untouched. Staging
 * files are never listed as wallets. Data lives in noBackupFilesDir because keystore keys
 * cannot be restored with an Android backup. Legacy single-phrase prefs are read and migrated
 * on the next successful init/protection change; a committed file takes precedence over them.
 *
 * Async callbacks run once on the main thread, outside storage exception handlers. Wipes are
 * synchronous and invalidate outstanding operations for the address. This class assumes the
 * app's main process is the only writer. See docs/gram-wallet-storage.md for limitations/tests.
 */
public class GramWalletStorage {
    public static final String STORAGE_BROKEN = "STORAGE_BROKEN";
    private static final String PREF = "gramwallet_";
    private static final int AUTH_SECONDS = 30;
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    private static final Object LOCK = new Object();
    private static final Map<String, Long> EPOCHS = new HashMap<>();
    // Also prevents competing wallet biometric prompts from replacing each other's callbacks.
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> new Thread(r, "GramWalletStorage"));

    private static final CopyOnWriteArrayList<WeakReference<Runnable>> changeListeners = new CopyOnWriteArrayList<>();

    /** The caller must retain the listener strongly for as long as it needs updates. */
    public static synchronized void addChangeListener(Runnable listener) {
        boolean found = false;
        for (WeakReference<Runnable> reference : changeListeners) {
            Runnable existing = reference.get();
            if (existing == null) changeListeners.remove(reference);
            else if (existing == listener) found = true;
        }
        if (listener != null && !found) changeListeners.add(new WeakReference<>(listener));
    }

    public static synchronized void removeChangeListener(Runnable listener) {
        for (WeakReference<Runnable> reference : changeListeners) {
            Runnable existing = reference.get();
            if (existing == null || existing == listener) changeListeners.remove(reference);
        }
    }

    private static void notifyChanged() {
        // Always enqueue: listeners must run outside the storage transaction/LOCK.
        AndroidUtilities.runOnUIThread(() -> {
            for (WeakReference<Runnable> reference : changeListeners) {
                Runnable listener = reference.get();
                if (listener == null) changeListeners.remove(reference);
                else listener.run();
            }
        });
    }

    private final Context context;
    private final int currentAccount;
    private final String address;
    private final File file;
    private final String aliasPrefix;
    private volatile Exception lastError;

    public GramWalletStorage(Context context, int currentAccount, String address) {
        if (context == null || address == null || address.isEmpty()) throw new IllegalArgumentException("context/address");
        Context app = context.getApplicationContext();
        this.context = app == null ? context : app;
        this.currentAccount = currentAccount;
        this.address = address;
        String id = digest(address);
        file = new File(directory(this.context), id + ".json");
        aliasPrefix = "gramwallet_v2_" + id + "_";
    }

    public String getAddress() { return address; }

    public int getLastUsage() {
        synchronized (LOCK) {
            try { return load().lastUsage; } catch (Exception e) { failed(e); return 0; }
        }
    }

    public byte[][] getPublicKeys() {
        synchronized (LOCK) {
            try {
                ArrayList<byte[]> keys = new ArrayList<>();
                for (String key : load().records.keySet()) keys.add(unhex(key));
                return keys.toArray(new byte[0][]);
            } catch (Exception e) { failed(e); return new byte[0][]; }
        }
    }

    public boolean containsSecretPhrase(byte[] publicKey) {
        if (publicKey == null || publicKey.length == 0) return false;
        synchronized (LOCK) {
            try { return load().records.containsKey(hex(publicKey)); } catch (Exception e) { failed(e); return false; }
        }
    }

    public boolean isProtectedByFingerprint() {
        synchronized (LOCK) {
            try { return load().locked; } catch (Exception e) { failed(e); return true; }
        }
    }

    /** The void setter/wipe APIs report failures here; their previous committed state survives. */
    public Exception getLastError() { return lastError; }

    /**
     * Adds/replaces only this public key. Null error means the persisted candidate decrypted
     * byte-for-byte correctly. Crypto, serialization and IO failures return STORAGE_BROKEN.
     * Invalid input, a different owner, cancellation and unavailable auth have distinct errors.
     */
    public void init(long userId, byte[] publicKey, SecretPhrase input, Utilities.Callback<String> whenDone) {
        final SecretPhrase secretPhrase = input == null ? null : input.copy();
        final String key = publicKey == null ? "" : hex(publicKey);
        final long epoch = epoch();
        WORKER.execute(() -> {
            String error = null;
            ArrayList<String> created = new ArrayList<>();
            Map<String, SecretPhrase> verify = new LinkedHashMap<>();
            try {
                lastError = null;
                if (key.isEmpty() || secretPhrase == null || secretPhrase.isEmpty()) throw new StorageException("INVALID_ARGUMENT");
                Wallet next;
                synchronized (LOCK) { checkEpoch(epoch); next = load(); }
                if (!next.records.isEmpty() && next.userId != userId) throw new StorageException("USER_MISMATCH");
                next.userId = userId;
                // Migrating the legacy record is part of the same transaction as the new pair.
                migrate(next, key, verify, created, epoch);
                next.records.put(key, encrypt(next, key, secretPhrase, created, epoch));
                verify.put(key, secretPhrase);
                next.lastUsage = now();
                synchronized (LOCK) {
                    checkEpoch(epoch);
                    persist(next, verify);
                    cleanup(next);
                }
            } catch (Exception e) {
                failed(e);
                error = e instanceof StorageException ? e.getMessage() : STORAGE_BROKEN;
            } finally {
                if (secretPhrase != null) secretPhrase.close();
                for (SecretPhrase value : verify.values()) value.close();
                cleanupUncommitted(created);
            }
            final String result = error;
            deliver(epoch, whenDone, result);
        });
    }

    /** The callback borrows the phrase; it is wiped as soon as the callback returns. */
    public void getSecretPhrase(byte[] publicKey, Utilities.Callback<SecretPhrase> whenDone) {
        getSecretPhrase(publicKey, whenDone, true);
    }

    public void getSecretPhrase(byte[] publicKey, Utilities.Callback<SecretPhrase> whenDone, boolean markUsage) {
        final String key = publicKey == null ? "" : hex(publicKey);
        final long epoch = epoch();
        WORKER.execute(() -> {
            SecretPhrase phrase = null;
            try {
                lastError = null;
                Wallet wallet;
                synchronized (LOCK) { checkEpoch(epoch); wallet = load(); }
                if (wallet.records.containsKey(key)) {
                    phrase = decryptAuthenticated(wallet, key, epoch);
                    synchronized (LOCK) {
                        checkEpoch(epoch);
                        if (markUsage) {
                            // A usage-write failure must not discard an otherwise readable phrase.
                            try {
                                wallet.lastUsage = now();
                                if (!file.exists()) {
                                    if (!legacyPrefs().edit().putInt("lastUsageDate", wallet.lastUsage).commit()) throw new IOException("usage write");
                                    notifyChanged();
                                } else persist(wallet, new LinkedHashMap<>());
                            } catch (Exception e) { failed(e); }
                        }
                    }
                }
            } catch (Exception e) {
                failed(e);
                if (phrase != null) phrase.close();
                phrase = null;
            }
            final SecretPhrase result = phrase;
            AndroidUtilities.runOnUIThread(() -> {
                SecretPhrase.deliver(result, value -> {
                    if (whenDone != null) whenDone.run(epoch == epoch() ? value : null);
                });
            });
        });
    }

    public void setProtectedByFingerprint(boolean protect) {
        setProtectedByFingerprint(protect, null);
    }

    /** Optional completion overload for settings UIs; same error contract as init. */
    public void setProtectedByFingerprint(boolean protect, Utilities.Callback<String> whenDone) {
        final long epoch = epoch();
        WORKER.execute(() -> {
            String error = null;
            ArrayList<String> created = new ArrayList<>();
            Map<String, SecretPhrase> plain = new LinkedHashMap<>();
            try {
                lastError = null;
                Wallet next;
                synchronized (LOCK) { checkEpoch(epoch); next = load(); }
                if (next.locked != protect || next.records.isEmpty()) {
                    if (protect && !deviceHasFingerprint(context)) throw new StorageException("AUTH_UNAVAILABLE");
                    if (!next.records.isEmpty()) {
                        // Even disabling an unexpired key requires explicit confirmation.
                        authenticate(false, epoch);
                        for (String key : next.records.keySet()) plain.put(key, decryptAuthenticated(next, key, epoch));
                    }
                    next.locked = protect;
                    next.records.clear();
                    for (Map.Entry<String, SecretPhrase> entry : plain.entrySet()) {
                        next.records.put(entry.getKey(), encrypt(next, entry.getKey(), entry.getValue(), created, epoch));
                    }
                    next.legacy = false;
                    synchronized (LOCK) {
                        checkEpoch(epoch);
                        persist(next, plain);
                        cleanup(next);
                    }
                }
            } catch (Exception e) {
                failed(e);
                error = e instanceof StorageException ? e.getMessage() : STORAGE_BROKEN;
            } finally {
                for (SecretPhrase value : plain.values()) value.close();
                cleanupUncommitted(created);
            }
            deliver(epoch, whenDone, error);
        });
    }

    public void wipeExcept(byte[] publicKey) {
        final String keep = publicKey == null ? "" : hex(publicKey);
        synchronized (LOCK) {
            invalidate();
            try {
                lastError = null;
                Wallet next = load();
                Iterator<String> keys = next.records.keySet().iterator();
                while (keys.hasNext()) if (!keys.next().equals(keep)) keys.remove();
                next.legacy = false;
                for (JSONObject record : next.records.values()) next.legacy |= record.optBoolean("legacy", false);
                // Retain legacy prefs/alias only if the surviving record still needs them.
                persist(next, new LinkedHashMap<>());
                cleanup(next);
            } catch (Exception e) { failed(e); }
        }
    }

    public void wipe() {
        synchronized (LOCK) {
            invalidate();
            try {
                lastError = null;
                Wallet empty = new Wallet();
                empty.locked = deviceHasFingerprint(context);
                persist(empty, new LinkedHashMap<>());
                cleanup(empty);
            } catch (Exception e) { failed(e); }
        }
    }

    public static void wipeForUser(Context context, int currentAccount, long userId) {
        if (userId == 0) return;
        synchronized (LOCK) {
            for (String address : getWallets(context, userId)) {
                new GramWalletStorage(context, currentAccount, address).wipe();
            }
        }
    }

    public static boolean deviceHasFingerprint(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 23) return false;
        if (SharedConfig.passcodeHash == null || SharedConfig.passcodeHash.length() == 0) return false;
        try {
            KeyguardManager manager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            return manager != null && manager.isDeviceSecure();
        } catch (Exception e) { FileLog.e(e); return false; }
    }

    public static ArrayList<String> getWallets(Context context, long userId) {
        synchronized (LOCK) {
            ArrayList<String> result = new ArrayList<>();
            File[] files = directory(context).listFiles();
            if (files != null) for (File file : files) {
                if (!file.getName().endsWith(".json")) continue;
                try {
                    JSONObject data = readJson(file);
                    String address = data.getString("address");
                    GramWalletStorage storage = new GramWalletStorage(context, 0, address);
                    if (!storage.file.equals(file)) continue;
                    Wallet wallet = storage.parse(data);
                    if (wallet.userId == userId && !wallet.records.isEmpty()) result.add(address);
                } catch (Exception e) { FileLog.e(e); }
            }
            File prefsDirectory = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            File[] legacy = prefsDirectory.listFiles();
            if (legacy != null) for (File old : legacy) {
                String name = old.getName();
                int suffix = name.endsWith(".xml.bak") ? 8 : name.endsWith(".xml") ? 4 : 0;
                if (!name.startsWith(PREF) || suffix == 0) continue;
                String address = name.substring(PREF.length(), name.length() - suffix);
                try {
                    GramWalletStorage storage = new GramWalletStorage(context, 0, address);
                    if (storage.file.exists()) continue;
                    Wallet wallet = storage.load();
                    if (wallet.userId == userId && !wallet.records.isEmpty() && !result.contains(address)) result.add(address);
                } catch (Exception e) { FileLog.e(e); }
            }
            return result;
        }
    }

    static class Wallet {
        long userId;
        int lastUsage;
        boolean locked;
        boolean legacy;
        final LinkedHashMap<String, JSONObject> records = new LinkedHashMap<>();
    }

    private Wallet load() throws Exception {
        if (file.exists()) return parse(readJson(file));
        Wallet wallet = new Wallet();
        SharedPreferences prefs = legacyPrefs();
        wallet.locked = prefs.getBoolean("locked", deviceHasFingerprint(context));
        wallet.userId = prefs.getLong("userId", 0);
        wallet.lastUsage = prefs.getInt("lastUsageDate", 0);
        if (prefs.contains("phrase")) {
            String key = hex(unhex(prefs.getString("pubkey", "")));
            if (key.isEmpty()) throw new IOException("legacy public key missing");
            JSONObject record = new JSONObject();
            record.put("legacy", true);
            record.put("ciphertext", prefs.getString("phrase", null));
            record.put("iv", prefs.getString("iv", null));
            wallet.records.put(key, record);
            wallet.legacy = true;
        }
        return wallet;
    }

    private Wallet parse(JSONObject data) throws Exception {
        if (data.getInt("version") != 2 || !address.equals(data.getString("address"))) throw new IOException("wallet format/address");
        Wallet wallet = new Wallet();
        wallet.userId = data.getLong("userId");
        wallet.lastUsage = data.getInt("lastUsage");
        wallet.locked = data.getBoolean("locked");
        JSONObject records = data.getJSONObject("records");
        Iterator<String> keys = records.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.isEmpty() || !key.equals(hex(unhex(key)))) throw new IOException("public key encoding");
            JSONObject record = records.getJSONObject(key);
            wallet.records.put(key, record);
            wallet.legacy |= record.optBoolean("legacy", false);
        }
        return wallet;
    }

    private byte[] serialize(Wallet wallet) throws Exception {
        JSONObject records = new JSONObject();
        for (Map.Entry<String, JSONObject> entry : wallet.records.entrySet()) records.put(entry.getKey(), entry.getValue());
        return new JSONObject().put("version", 2).put("address", address).put("userId", wallet.userId)
                .put("lastUsage", wallet.lastUsage).put("locked", wallet.locked).put("records", records)
                .toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Called under LOCK. No authentication UI/retry is allowed during read-back verification. */
    private void persist(Wallet next, Map<String, SecretPhrase> verify) throws Exception {
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("wallet directory");
        byte[] bytes = serialize(next);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("wallet too large");
        File staging = new File(directory, file.getName() + ".pending");
        try {
            try (FileOutputStream out = new FileOutputStream(staging)) {
                out.write(bytes);
                out.flush();
                out.getFD().sync();
            }
            byte[] stored = readBytes(staging);
            if (!Arrays.equals(bytes, stored)) throw new IOException("wallet read-back mismatch");
            Wallet reread = parse(new JSONObject(new String(stored, StandardCharsets.UTF_8)));
            for (Map.Entry<String, SecretPhrase> entry : verify.entrySet()) {
                byte[] expected = entry.getValue().copyBytes();
                byte[] actual = null;
                try {
                    actual = decrypt(reread, entry.getKey());
                    if (!MessageDigest.isEqual(expected, actual)) throw new IOException("wallet decryption mismatch");
                } finally { Arrays.fill(expected, (byte) 0); if (actual != null) Arrays.fill(actual, (byte) 0); }
            }
            // Same directory/filesystem; rename publishes the verified bytes atomically.
            Os.rename(staging.getAbsolutePath(), file.getAbsolutePath());
            notifyChanged();
        } finally {
            if (staging.exists() && !staging.delete()) FileLog.e(new IOException("wallet staging cleanup"));
        }
    }

    private void migrate(Wallet wallet, String replacingKey, Map<String, SecretPhrase> verify, ArrayList<String> created, long epoch) throws Exception {
        for (String key : new ArrayList<>(wallet.records.keySet())) {
            // A caller supplying this phrase can repair a lost legacy key without decrypting
            // the obsolete ciphertext first. Other public keys must remain recoverable.
            if (key.equals(replacingKey)) continue;
            if (!wallet.records.get(key).optBoolean("legacy", false)) continue;
            SecretPhrase phrase = decryptAuthenticated(wallet, key, epoch);
            verify.put(key, phrase);
            wallet.records.put(key, encrypt(wallet, key, phrase, created, epoch));
        }
        wallet.legacy = false;
    }

    private JSONObject encrypt(Wallet wallet, String publicKey, SecretPhrase phrase, ArrayList<String> created, long epoch) throws Exception {
        String alias = aliasPrefix + UUID.randomUUID();
        created.add(alias); // also clean up a partially generated key
        JSONObject record = new JSONObject().put("alias", alias);
        if (Build.VERSION.SDK_INT >= 23) {
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(wallet.locked);
            if (wallet.locked) {
                if (Build.VERSION.SDK_INT >= 30) builder.setUserAuthenticationParameters(AUTH_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL);
                else builder.setUserAuthenticationValidityDurationSeconds(AUTH_SECONDS);
                if (Build.VERSION.SDK_INT >= 24) builder.setInvalidatedByBiometricEnrollment(false);
            }
            KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            generator.init(builder.build());
            generator.generateKey();
        } else {
            if (wallet.locked) throw new StorageException("AUTH_UNAVAILABLE");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            generator.initialize(new KeyPairGeneratorSpec.Builder(context).setAlias(alias).setKeySize(2048)
                    .setSubject(new X500Principal("CN=GramWallet")).setSerialNumber(BigInteger.ONE)
                    .setStartDate(new Date(0)).setEndDate(new Date(4102444800000L)).build());
            generator.generateKeyPair();
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            try {
                Cipher wrapper = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                wrapper.init(Cipher.ENCRYPT_MODE, keyStore().getCertificate(alias).getPublicKey());
                record.put("wrappedKey", encode(wrapper.doFinal(raw)));
            } finally { Arrays.fill(raw, (byte) 0); }
        }
        Cipher cipher;
        try { cipher = encryptionCipher(wallet, publicKey, record); }
        catch (Exception e) {
            if (!wallet.locked || !needsAuthentication(e)) throw e;
            authenticate(false, epoch);
            try { cipher = encryptionCipher(wallet, publicKey, record); }
            catch (Exception retry) {
                if (!needsAuthentication(retry)) throw retry;
                authenticate(true, epoch);
                cipher = encryptionCipher(wallet, publicKey, record);
            }
        }
        byte[] plain = phrase.copyBytes();
        try {
            record.put("ciphertext", encode(cipher.doFinal(plain)));
            record.put("iv", encode(cipher.getIV()));
        } finally { Arrays.fill(plain, (byte) 0); }
        return record;
    }

    private Cipher encryptionCipher(Wallet wallet, String publicKey, JSONObject record) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, recordKey(record));
        cipher.updateAAD(aad(wallet, publicKey));
        return cipher;
    }

    private SecretPhrase decryptAuthenticated(Wallet wallet, String key, long epoch) throws Exception {
        byte[] plain;
        try { plain = decrypt(wallet, key); }
        catch (Exception e) {
            if (!wallet.locked || !needsAuthentication(e)) throw e;
            authenticate(false, epoch);
            try { plain = decrypt(wallet, key); }
            catch (Exception retry) {
                if (!needsAuthentication(retry)) throw retry;
                authenticate(true, epoch);
                plain = decrypt(wallet, key);
            }
        }
        try { return new SecretPhrase(plain); }
        finally { Arrays.fill(plain, (byte) 0); }
    }

    byte[] decrypt(Wallet wallet, String publicKey) throws Exception {
        JSONObject record = wallet.records.get(publicKey);
        if (record == null) throw new IOException("record missing");
        if (record.optBoolean("legacy", false)) {
            SecretKey key;
            String software = legacyPrefs().getString("swkey", null);
            if (software != null) {
                key = new SecretKeySpec(Base64.decode(software, Base64.NO_WRAP), "AES");
            } else {
                Key old = keyStore().getKey((wallet.locked ? "gramwallet_lock_" : "gramwallet_free_") + address, null);
                if (!(old instanceof SecretKey)) throw new IOException("legacy key missing");
                key = (SecretKey) old;
            }
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(unhex(record.getString("iv"))));
            return cipher.doFinal(unhex(record.getString("ciphertext")));
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, recordKey(record), new GCMParameterSpec(128, decode(record.getString("iv"))));
        cipher.updateAAD(aad(wallet, publicKey));
        return cipher.doFinal(decode(record.getString("ciphertext")));
    }

    /** Read never generates/replaces an absent or invalidated key. Reopen the keystore each time. */
    private SecretKey recordKey(JSONObject record) throws Exception {
        String alias = record.getString("alias");
        if (!alias.startsWith(aliasPrefix)) throw new IOException("key alias outside wallet");
        Key key = keyStore().getKey(alias, null);
        if (key == null) throw new IOException("keystore key missing");
        if (record.has("wrappedKey")) {
            Cipher wrapper = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            wrapper.init(Cipher.DECRYPT_MODE, key);
            byte[] raw = wrapper.doFinal(decode(record.getString("wrappedKey")));
            try {
                if (raw.length != 32) throw new IOException("wrapped key length");
                return new SecretKeySpec(raw, "AES");
            } finally { Arrays.fill(raw, (byte) 0); }
        }
        if (!(key instanceof SecretKey)) throw new IOException("keystore key type");
        return (SecretKey) key;
    }

    private byte[] aad(Wallet wallet, String publicKey) {
        return ("GramWallet:2\n" + address + "\n" + wallet.userId + "\n" + publicKey + "\n" + wallet.locked)
                .getBytes(StandardCharsets.UTF_8);
    }

    private void authenticate(boolean credentialOnly, long epoch) throws Exception {
        synchronized (LOCK) { checkEpoch(epoch); }
        String error = GramWalletStorageAuth.authenticate(file.getAbsolutePath(), credentialOnly, () -> epoch == epoch());
        synchronized (LOCK) { checkEpoch(epoch); }
        if (error != null) throw new StorageException(error);
    }

    private static boolean needsAuthentication(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause()) {
            if (e instanceof UserNotAuthenticatedException) return true;
        }
        return false;
    }

    /** Delete only unreferenced keys AFTER publishing, so failed writes cannot destroy old data. */
    private void cleanup(Wallet committed) {
        try {
            // Never retire the previous keys until the directory entry for the replacement
            // is durable. If a filesystem refuses fsync, retain the old keys for recovery.
            FileDescriptor directory = Os.open(file.getParent(), OsConstants.O_RDONLY, 0);
            try { Os.fsync(directory); } finally { Os.close(directory); }
            KeyStore store = keyStore();
            Enumeration<String> aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (alias.startsWith(aliasPrefix) && !references(committed, alias)) store.deleteEntry(alias);
            }
            if (!committed.legacy) {
                // Tombstone/new data already wins even if removing old prefs fails.
                if (!legacyPrefs().edit().clear().commit()) throw new IOException("legacy cleanup");
                store.deleteEntry("gramwallet_free_" + address);
                store.deleteEntry("gramwallet_lock_" + address);
            }
        } catch (Exception e) { failed(e); }
    }

    private void cleanupUncommitted(ArrayList<String> created) {
        if (created.isEmpty()) return;
        synchronized (LOCK) {
            try {
                Wallet committed = load();
                KeyStore store = keyStore();
                for (String alias : created) if (!references(committed, alias)) store.deleteEntry(alias);
            } catch (Exception e) { failed(e); }
        }
    }

    private static boolean references(Wallet wallet, String alias) {
        for (JSONObject record : wallet.records.values()) if (alias.equals(record.optString("alias"))) return true;
        return false;
    }

    private static KeyStore keyStore() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }

    private SharedPreferences legacyPrefs() { return context.getSharedPreferences(PREF + address, Context.MODE_PRIVATE); }
    private static File directory(Context context) { return new File(context.getNoBackupFilesDir(), "gramwallets"); }
    private int now() { return ConnectionsManager.getInstance(currentAccount).getCurrentTime(); }
    private void failed(Exception e) { lastError = e; FileLog.e(e); }
    private long epoch() { synchronized (LOCK) { Long value = EPOCHS.get(file.getAbsolutePath()); return value == null ? 0 : value; } }
    private void invalidate() {
        EPOCHS.put(file.getAbsolutePath(), epoch() + 1);
        GramWalletStorageAuth.cancel(file.getAbsolutePath());
    }
    private void checkEpoch(long value) throws StorageException { if (value != epoch()) throw new StorageException("STORAGE_CANCELED"); }
    private void deliver(long epoch, Utilities.Callback<String> callback, String error) {
        AndroidUtilities.runOnUIThread(() -> { if (callback != null) callback.run(epoch == epoch() ? error : "STORAGE_CANCELED"); });
    }
    private static class StorageException extends Exception { StorageException(String error) { super(error); } }
    private static String encode(byte[] bytes) { return Base64.encodeToString(bytes, Base64.NO_WRAP); }
    private static byte[] decode(String value) { return Base64.decode(value, Base64.NO_WRAP); }
    private static String hex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) { result[i * 2] = digits[(bytes[i] & 255) >>> 4]; result[i * 2 + 1] = digits[bytes[i] & 15]; }
        return new String(result);
    }
    private static byte[] unhex(String value) throws IOException {
        if (value == null || value.length() % 2 != 0) throw new IOException("hex length");
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16), low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IOException("hex digit");
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }
    private static String digest(String address) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static JSONObject readJson(File file) throws Exception { return new JSONObject(new String(readBytes(file), StandardCharsets.UTF_8)); }
    private static byte[] readBytes(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > MAX_FILE_BYTES) throw new IOException("wallet too large");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}
