package org.telegram.ui.bots;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class BotStorage {

    public static boolean isSecuredSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static File getDir() {
        try {
            File file = ApplicationLoader.applicationContext.getFilesDir();
            if (file != null) {
                File storageFile = new File(file, "apps_storage/");
                storageFile.mkdirs();
                if ((file.exists() || file.mkdirs()) && file.canWrite()) {
                    return storageFile;
                }
            }
        } catch (Exception e) {}
        return new File("");
    }

    public final Context context;
    public final long bot_id;
    public final boolean secured;

    public BotStorage(Context context, long bot_id, boolean secured) {
        this.context = context;
        this.bot_id = bot_id;
        this.secured = secured;
    }

    public File getFile() {
        return new File(getDir(), bot_id + (secured ? "_s" : ""));
    }

    private SecretKey getSecretKey() throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException, UnrecoverableKeyException, NoSuchProviderException, InvalidAlgorithmParameterException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            throw new RuntimeException("UNSUPPORTED");
        }

        final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias("MiniAppsKey")) {
            final KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            final KeyGenParameterSpec keyGenParameterSpec =
                new KeyGenParameterSpec.Builder("MiniAppsKey", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build();
            keyGenerator.init(keyGenParameterSpec);
            keyGenerator.generateKey();
        }

        return (SecretKey) keyStore.getKey("MiniAppsKey", null);
    }

    private byte[] getBytes(File file) throws IOException {
        final FileInputStream fis = new FileInputStream(file);
        int length = (int) file.length();
        byte[] iv = null;
        if (secured) {
            int iv_size = fis.read(); length--;
            iv = new byte[iv_size]; length -= iv_size;
            fis.read(iv);
        }
        final byte[] buffer;
        try {
            buffer = new byte[length];
        } catch (OutOfMemoryError e) {
            FileLog.e(e);
            throw new RuntimeException("QUOTA_EXCEEDED");
        }
        fis.read(buffer);
        fis.close();
        if (secured) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec spec = new GCMParameterSpec(128, iv);
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);
                return cipher.doFinal(buffer);
            } catch (Exception e) {
                FileLog.e(e);
                setBytes(file, "{}".getBytes());
                throw new RuntimeException("UNKNOWN_ERROR");
            }
        }
        return buffer;
    }

    private void setBytes(File file, byte[] bytes) throws IOException {
        final FileOutputStream fos = new FileOutputStream(file);
        if (secured) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
                byte[] iv = cipher.getIV();
                fos.write(iv.length);
                fos.write(iv);
                bytes = cipher.doFinal(bytes);
            } catch (Exception e) {
                FileLog.e(e);
                throw new RuntimeException("UNKNOWN_ERROR");
            }
        }
        fos.write(bytes);
        fos.close();
    }

    private JSONObject getJSON() {
        final File file = getFile();
        if (!file.exists() || file.length() > 5 * 1024 * 1024)
            return new JSONObject();
        try {
            return new JSONObject(new String(getBytes(file)));
        } catch (Exception e) {
            FileLog.e(e);
            return new JSONObject();
        }
    }

    private void setJSON(JSONObject obj) {
        byte[] bytes;
        try {
            bytes = obj.toString().getBytes();
        } catch (OutOfMemoryError e) {
            FileLog.e(e);
            throw new RuntimeException("QUOTA_EXCEEDED");
        } catch (Exception e) {
            FileLog.e(e);
            throw new RuntimeException("UNKNOWN_ERROR");
        }
        if (bytes.length > 5 * 1024 * 1024) {
            throw new RuntimeException("QUOTA_EXCEEDED");
        }
        try {
            setBytes(getFile(), bytes);
        } catch (Exception e) {
            FileLog.e(e);
            throw new RuntimeException("UNKNOWN_ERROR");
        }
    }

    public void setKey(String key, String value) {
        if (secured && !isSecuredSupported())
            throw new RuntimeException("UNSUPPORTED");
        if (key.length() + value.length() > 5 * 1024 * 1024)
            throw new RuntimeException("QUOTA_EXCEEDED");
        final JSONObject object = getJSON();
        try {
            if (value == null) {
                object.remove(key);
            } else {
                object.put(key, value);
            }
        } catch (Exception e) {
            FileLog.e(e);
            throw new RuntimeException("UNKNOWN_ERROR");
        }
        if (object.length() > 10 && secured)
            throw new RuntimeException("QUOTA_EXCEEDED");
        setJSON(object);
    }

    public String getKey(String key) {
        if (secured && !isSecuredSupported())
            throw new RuntimeException("UNSUPPORTED");
        return getJSON().optString(key);
    }

    public void clear() {
        setJSON(new JSONObject());
    }

}
