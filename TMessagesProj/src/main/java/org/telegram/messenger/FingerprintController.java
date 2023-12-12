package org.telegram.messenger;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.support.fingerprint.FingerprintManagerCompat;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Locale;

import javax.crypto.Cipher;

@RequiresApi(api = Build.VERSION_CODES.M)
public class FingerprintController {
    private final static String KEY_ALIAS = "tmessages_passcode";

    private static KeyStore keyStore;
    private static KeyPairGenerator keyPairGenerator;
    private static Boolean hasChangedFingerprints;

    private static KeyStore getKeyStore() {
        if (keyStore != null) {
            return keyStore;
        }
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            return keyStore;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private static KeyPairGenerator getKeyPairGenerator() {
        if (keyPairGenerator != null) {
            return keyPairGenerator;
        }

        try {
            keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            return keyPairGenerator;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private static void generateNewKey(boolean notifyCheckFingerprint) {
        KeyPairGenerator generator = getKeyPairGenerator();
        if (generator != null) {
            try {
                Locale realLocale = Locale.getDefault();
                // A workaround for AndroidKeyStore bug in RTL languages
                setLocale(Locale.ENGLISH);

                generator.initialize(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                        .setUserAuthenticationRequired(true)
                        .build());
                generator.generateKeyPair();
                setLocale(realLocale);

                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didGenerateFingerprintKeyPair, notifyCheckFingerprint));
            } catch (InvalidAlgorithmParameterException e) {
                FileLog.e(e);
            } catch (Exception e) {
                if (e.getClass().getName().equals("android.security.KeyStoreException")) {
                    // Keystore is somehow broken?
                } else {
                    FileLog.e(e);
                }
            }
        }
    }

    public static void deleteInvalidKey() {
        KeyStore keyStore = getKeyStore();
        try {
            keyStore.deleteEntry(KEY_ALIAS);
        } catch (KeyStoreException e) {
            FileLog.e(e);
        }
        hasChangedFingerprints = null;

        checkKeyReady(false);
    }

    public static void checkKeyReady() {
        checkKeyReady(true);
    }

    public static void checkKeyReady(boolean notifyCheckFingerprint) {
        if (!isKeyReady() && AndroidUtilities.isKeyguardSecure() && FingerprintManagerCompat.from(ApplicationLoader.applicationContext).isHardwareDetected()
                && FingerprintManagerCompat.from(ApplicationLoader.applicationContext).hasEnrolledFingerprints()) {
            Utilities.globalQueue.postRunnable(() -> generateNewKey(notifyCheckFingerprint));
        }
    }

    public static boolean isKeyReady() {
        try {
            return getKeyStore().containsAlias(KEY_ALIAS);
        } catch (KeyStoreException e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean checkDeviceFingerprintsChanged() {
        if (hasChangedFingerprints != null) {
            return hasChangedFingerprints;
        }
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, keyStore.getKey(KEY_ALIAS, null));
            return hasChangedFingerprints = false;
        } catch (KeyPermanentlyInvalidatedException ignored) {
            // Device fingerprints changed, then we should delete old key
            return hasChangedFingerprints = true;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return hasChangedFingerprints = false;
    }

    private static void setLocale(Locale locale) {
        Locale.setDefault(locale);
        Resources resources = ApplicationLoader.applicationContext.getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
}
