/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

public class UserConfig {

    private static TLRPC.User currentUser;
    public static boolean registeredForPush;
    public static String pushString = "";
    public static int lastSendMessageId = -210000;
    public static int lastLocalId = -210000;
    public static int lastBroadcastId = -1;
    public static int contactsSavedCount;
    public static boolean blockedUsersLoaded;
    private final static Object sync = new Object();
    public static boolean saveIncomingPhotos;
    public static String passcodeHash = "";
    public static byte[] passcodeSalt = new byte[0];
    public static boolean appLocked;
    public static int passcodeType;
    public static int autoLockIn = 60 * 60;
    public static boolean allowScreenCapture;
    public static int lastPauseTime;
    public static long lastAppPauseTime;
    public static boolean isWaitingForPasscodeEnter;
    public static boolean useFingerprint = true;
    public static String lastUpdateVersion;
    public static int lastContactsSyncTime;
    public static int lastHintsSyncTime;
    public static boolean draftsLoaded;
    public static boolean notificationsConverted = true;
    public static boolean pinnedDialogsLoaded = true;
    public static TLRPC.TL_account_tmpPassword tmpPassword;
    public static int ratingLoadTime;
    public static int botRatingLoadTime;
    public static boolean contactsReimported;

    private static boolean configLoaded;

    public static int migrateOffsetId = -1;
    public static int migrateOffsetDate = -1;
    public static int migrateOffsetUserId = -1;
    public static int migrateOffsetChatId = -1;
    public static int migrateOffsetChannelId = -1;
    public static long migrateOffsetAccess = -1;

    public static int totalDialogsLoadCount = 0;
    public static int dialogsLoadOffsetId = 0;
    public static int dialogsLoadOffsetDate = 0;
    public static int dialogsLoadOffsetUserId = 0;
    public static int dialogsLoadOffsetChatId = 0;
    public static int dialogsLoadOffsetChannelId = 0;
    public static long dialogsLoadOffsetAccess = 0;

    public static int getNewMessageId() {
        int id;
        synchronized (sync) {
            id = lastSendMessageId;
            lastSendMessageId--;
        }
        return id;
    }

    public static void saveConfig(boolean withFile) {
        saveConfig(withFile, null);
    }

    public static void saveConfig(boolean withFile, File oldFile) {
        synchronized (sync) {
            try {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("registeredForPush", registeredForPush);
                editor.putString("pushString2", pushString);
                editor.putInt("lastSendMessageId", lastSendMessageId);
                editor.putInt("lastLocalId", lastLocalId);
                editor.putInt("contactsSavedCount", contactsSavedCount);
                editor.putBoolean("saveIncomingPhotos", saveIncomingPhotos);
                editor.putInt("lastBroadcastId", lastBroadcastId);
                editor.putBoolean("blockedUsersLoaded", blockedUsersLoaded);
                editor.putString("passcodeHash1", passcodeHash);
                editor.putString("passcodeSalt", passcodeSalt.length > 0 ? Base64.encodeToString(passcodeSalt, Base64.DEFAULT) : "");
                editor.putBoolean("appLocked", appLocked);
                editor.putInt("passcodeType", passcodeType);
                editor.putInt("autoLockIn", autoLockIn);
                editor.putInt("lastPauseTime", lastPauseTime);
                editor.putLong("lastAppPauseTime", lastAppPauseTime);
                editor.putString("lastUpdateVersion2", lastUpdateVersion);
                editor.putInt("lastContactsSyncTime", lastContactsSyncTime);
                editor.putBoolean("useFingerprint", useFingerprint);
                editor.putInt("lastHintsSyncTime", lastHintsSyncTime);
                editor.putBoolean("draftsLoaded", draftsLoaded);
                editor.putBoolean("notificationsConverted", notificationsConverted);
                editor.putBoolean("allowScreenCapture", allowScreenCapture);
                editor.putBoolean("pinnedDialogsLoaded", pinnedDialogsLoaded);
                editor.putInt("ratingLoadTime", ratingLoadTime);
                editor.putInt("botRatingLoadTime", botRatingLoadTime);
                editor.putBoolean("contactsReimported", contactsReimported);

                editor.putInt("3migrateOffsetId", migrateOffsetId);
                if (migrateOffsetId != -1) {
                    editor.putInt("3migrateOffsetDate", migrateOffsetDate);
                    editor.putInt("3migrateOffsetUserId", migrateOffsetUserId);
                    editor.putInt("3migrateOffsetChatId", migrateOffsetChatId);
                    editor.putInt("3migrateOffsetChannelId", migrateOffsetChannelId);
                    editor.putLong("3migrateOffsetAccess", migrateOffsetAccess);
                }


                editor.putInt("2totalDialogsLoadCount", totalDialogsLoadCount);
                editor.putInt("2dialogsLoadOffsetId", dialogsLoadOffsetId);
                editor.putInt("2dialogsLoadOffsetDate", dialogsLoadOffsetDate);
                editor.putInt("2dialogsLoadOffsetUserId", dialogsLoadOffsetUserId);
                editor.putInt("2dialogsLoadOffsetChatId", dialogsLoadOffsetChatId);
                editor.putInt("2dialogsLoadOffsetChannelId", dialogsLoadOffsetChannelId);
                editor.putLong("2dialogsLoadOffsetAccess", dialogsLoadOffsetAccess);

                if (tmpPassword != null) {
                    SerializedData data = new SerializedData();
                    tmpPassword.serializeToStream(data);
                    String string = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                    editor.putString("tmpPassword", string);
                    data.cleanup();
                } else {
                    editor.remove("tmpPassword");
                }

                if (currentUser != null) {
                    if (withFile) {
                        SerializedData data = new SerializedData();
                        currentUser.serializeToStream(data);
                        String string = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                        editor.putString("user", string);
                        data.cleanup();
                    }
                } else {
                    editor.remove("user");
                }

                editor.commit();
                if (oldFile != null) {
                    oldFile.delete();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static boolean isClientActivated() {
        synchronized (sync) {
            return currentUser != null;
        }
    }

    public static int getClientUserId() {
        synchronized (sync) {
            return currentUser != null ? currentUser.id : 0;
        }
    }

    public static TLRPC.User getCurrentUser() {
        synchronized (sync) {
            return currentUser;
        }
    }

    public static void setCurrentUser(TLRPC.User user) {
        synchronized (sync) {
            currentUser = user;
        }
    }

    public static void loadConfig() {
        synchronized (sync) {
            if (configLoaded) {
                return;
            }
            final File configFile = new File(ApplicationLoader.getFilesDirFixed(), "user.dat");
            if (configFile.exists()) {
                try {
                    SerializedData data = new SerializedData(configFile);
                    int ver = data.readInt32(false);
                    if (ver == 1) {
                        int constructor = data.readInt32(false);
                        currentUser = TLRPC.User.TLdeserialize(data, constructor, false);
                        MessagesStorage.lastDateValue = data.readInt32(false);
                        MessagesStorage.lastPtsValue = data.readInt32(false);
                        MessagesStorage.lastSeqValue = data.readInt32(false);
                        registeredForPush = data.readBool(false);
                        pushString = data.readString(false);
                        lastSendMessageId = data.readInt32(false);
                        lastLocalId = data.readInt32(false);
                        data.readString(false);
                        data.readString(false);
                        saveIncomingPhotos = data.readBool(false);
                        MessagesStorage.lastQtsValue = data.readInt32(false);
                        MessagesStorage.lastSecretVersion = data.readInt32(false);
                        int val = data.readInt32(false);
                        if (val == 1) {
                            MessagesStorage.secretPBytes = data.readByteArray(false);
                        }
                        MessagesStorage.secretG = data.readInt32(false);
                        Utilities.stageQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                saveConfig(true, configFile);
                            }
                        });
                    } else if (ver == 2) {
                        int constructor = data.readInt32(false);
                        currentUser = TLRPC.User.TLdeserialize(data, constructor, false);

                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
                        registeredForPush = preferences.getBoolean("registeredForPush", false);
                        pushString = preferences.getString("pushString2", "");
                        lastSendMessageId = preferences.getInt("lastSendMessageId", -210000);
                        lastLocalId = preferences.getInt("lastLocalId", -210000);
                        contactsSavedCount = preferences.getInt("contactsHash", 0);
                        saveIncomingPhotos = preferences.getBoolean("saveIncomingPhotos", false);
                    }
                    if (lastLocalId > -210000) {
                        lastLocalId = -210000;
                    }
                    if (lastSendMessageId > -210000) {
                        lastSendMessageId = -210000;
                    }
                    data.cleanup();
                    Utilities.stageQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            saveConfig(true, configFile);
                        }
                    });
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
                registeredForPush = preferences.getBoolean("registeredForPush", false);
                pushString = preferences.getString("pushString2", "");
                lastSendMessageId = preferences.getInt("lastSendMessageId", -210000);
                lastLocalId = preferences.getInt("lastLocalId", -210000);
                contactsSavedCount = preferences.getInt("contactsSavedCount", 0);
                saveIncomingPhotos = preferences.getBoolean("saveIncomingPhotos", false);
                lastBroadcastId = preferences.getInt("lastBroadcastId", -1);
                blockedUsersLoaded = preferences.getBoolean("blockedUsersLoaded", false);
                passcodeHash = preferences.getString("passcodeHash1", "");
                appLocked = preferences.getBoolean("appLocked", false);
                passcodeType = preferences.getInt("passcodeType", 0);
                autoLockIn = preferences.getInt("autoLockIn", 60 * 60);
                lastPauseTime = preferences.getInt("lastPauseTime", 0);
                lastAppPauseTime = preferences.getLong("lastAppPauseTime", 0);
                useFingerprint = preferences.getBoolean("useFingerprint", true);
                lastUpdateVersion = preferences.getString("lastUpdateVersion2", "3.5");
                lastContactsSyncTime = preferences.getInt("lastContactsSyncTime", (int) (System.currentTimeMillis() / 1000) - 23 * 60 * 60);
                lastHintsSyncTime = preferences.getInt("lastHintsSyncTime", (int) (System.currentTimeMillis() / 1000) - 25 * 60 * 60);
                draftsLoaded = preferences.getBoolean("draftsLoaded", false);
                notificationsConverted = preferences.getBoolean("notificationsConverted", false);
                allowScreenCapture = preferences.getBoolean("allowScreenCapture", false);
                pinnedDialogsLoaded = preferences.getBoolean("pinnedDialogsLoaded", false);
                contactsReimported = preferences.getBoolean("contactsReimported", false);
                ratingLoadTime = preferences.getInt("ratingLoadTime", 0);
                botRatingLoadTime = preferences.getInt("botRatingLoadTime", 0);

                if (UserConfig.passcodeHash.length() > 0 && lastPauseTime == 0) {
                    lastPauseTime = (int) (System.currentTimeMillis() / 1000 - 60 * 10);
                }

                migrateOffsetId = preferences.getInt("3migrateOffsetId", 0);
                if (migrateOffsetId != -1) {
                    migrateOffsetDate = preferences.getInt("3migrateOffsetDate", 0);
                    migrateOffsetUserId = preferences.getInt("3migrateOffsetUserId", 0);
                    migrateOffsetChatId = preferences.getInt("3migrateOffsetChatId", 0);
                    migrateOffsetChannelId = preferences.getInt("3migrateOffsetChannelId", 0);
                    migrateOffsetAccess = preferences.getLong("3migrateOffsetAccess", 0);
                }

                dialogsLoadOffsetId = preferences.getInt("2dialogsLoadOffsetId", -1);
                totalDialogsLoadCount = preferences.getInt("2totalDialogsLoadCount", 0);
                dialogsLoadOffsetDate = preferences.getInt("2dialogsLoadOffsetDate", -1);
                dialogsLoadOffsetUserId = preferences.getInt("2dialogsLoadOffsetUserId", -1);
                dialogsLoadOffsetChatId = preferences.getInt("2dialogsLoadOffsetChatId", -1);
                dialogsLoadOffsetChannelId = preferences.getInt("2dialogsLoadOffsetChannelId", -1);
                dialogsLoadOffsetAccess = preferences.getLong("2dialogsLoadOffsetAccess", -1);

                String string = preferences.getString("tmpPassword", null);
                if (string != null) {
                    byte[] bytes = Base64.decode(string, Base64.DEFAULT);
                    if (bytes != null) {
                        SerializedData data = new SerializedData(bytes);
                        tmpPassword = TLRPC.TL_account_tmpPassword.TLdeserialize(data, data.readInt32(false), false);
                        data.cleanup();
                    }
                }

                string = preferences.getString("user", null);
                if (string != null) {
                    byte[] bytes = Base64.decode(string, Base64.DEFAULT);
                    if (bytes != null) {
                        SerializedData data = new SerializedData(bytes);
                        currentUser = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                        data.cleanup();
                    }
                }
                String passcodeSaltString = preferences.getString("passcodeSalt", "");
                if (passcodeSaltString.length() > 0) {
                    passcodeSalt = Base64.decode(passcodeSaltString, Base64.DEFAULT);
                } else {
                    passcodeSalt = new byte[0];
                }

                if (!notificationsConverted) {
                    try {
                        ArrayList<Long> customDialogs = new ArrayList<>();
                        preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
                        Map<String, ?> all = preferences.getAll();
                        String defaultSound = LocaleController.getString("SoundDefault", R.string.SoundDefault);
                        int defaultVibrate = 0;
                        int defaultPriority = 0;
                        int defaultColor = 0;
                        int defaultMaxCount = 2;
                        int defaultMaxDelay = 3 * 60;
                        for (Map.Entry<String, ?> entry : all.entrySet()) {
                            String key = entry.getKey();
                            if (key.startsWith("sound_")) {
                                String value = (String) entry.getValue();
                                if (!value.equals(defaultSound)) {
                                    long dialogId = Utilities.parseLong(key);
                                    if (!customDialogs.contains(dialogId)) {
                                        customDialogs.add(dialogId);
                                    }
                                }
                            } else if (key.startsWith("vibrate_")) {
                                Integer value = (Integer) entry.getValue();
                                if (value != defaultVibrate) {
                                    long dialogId = Utilities.parseLong(key);
                                    if (!customDialogs.contains(dialogId)) {
                                        customDialogs.add(dialogId);
                                    }
                                }
                            } else if (key.startsWith("priority_")) {
                                Integer value = (Integer) entry.getValue();
                                if (value != defaultPriority) {
                                    long dialogId = Utilities.parseLong(key);
                                    if (!customDialogs.contains(dialogId)) {
                                        customDialogs.add(dialogId);
                                    }
                                }
                            } else if (key.startsWith("color_")) {
                                Integer value = (Integer) entry.getValue();
                                if (value != defaultColor) {
                                    long dialogId = Utilities.parseLong(key);
                                    if (!customDialogs.contains(dialogId)) {
                                        customDialogs.add(dialogId);
                                    }
                                }
                            } else if (key.startsWith("smart_max_count_")) {
                                Integer value = (Integer) entry.getValue();
                                if (value != defaultMaxCount) {
                                    long dialogId = Utilities.parseLong(key);
                                    if (!customDialogs.contains(dialogId)) {
                                        customDialogs.add(dialogId);
                                    }
                                }
                            } else if (key.startsWith("smart_delay_")) {
                                Integer value = (Integer) entry.getValue();
                                if (value != defaultMaxDelay) {
                                    long dialogId = Utilities.parseLong(key);
                                    if (!customDialogs.contains(dialogId)) {
                                        customDialogs.add(dialogId);
                                    }
                                }
                            }
                        }
                        if (!customDialogs.isEmpty()) {
                            SharedPreferences.Editor editor = preferences.edit();
                            for (int a = 0; a < customDialogs.size(); a++) {
                                editor.putBoolean("custom_" + customDialogs.get(a), true);
                            }
                            editor.commit();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    notificationsConverted = true;
                    saveConfig(false);
                }
            }
            configLoaded = true;
        }
    }

    public static boolean checkPasscode(String passcode) {
        if (passcodeSalt.length == 0) {
            boolean result = Utilities.MD5(passcode).equals(passcodeHash);
            if (result) {
                try {
                    passcodeSalt = new byte[16];
                    Utilities.random.nextBytes(passcodeSalt);
                    byte[] passcodeBytes = passcode.getBytes("UTF-8");
                    byte[] bytes = new byte[32 + passcodeBytes.length];
                    System.arraycopy(passcodeSalt, 0, bytes, 0, 16);
                    System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
                    System.arraycopy(passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
                    passcodeHash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
                    saveConfig(false);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            return result;
        } else {
            try {
                byte[] passcodeBytes = passcode.getBytes("UTF-8");
                byte[] bytes = new byte[32 + passcodeBytes.length];
                System.arraycopy(passcodeSalt, 0, bytes, 0, 16);
                System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
                System.arraycopy(passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
                String hash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
                return passcodeHash.equals(hash);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return false;
    }

    public static void clearConfig() {
        currentUser = null;
        registeredForPush = false;
        contactsSavedCount = 0;
        lastSendMessageId = -210000;
        lastBroadcastId = -1;
        saveIncomingPhotos = false;
        blockedUsersLoaded = false;
        migrateOffsetId = -1;
        migrateOffsetDate = -1;
        migrateOffsetUserId = -1;
        migrateOffsetChatId = -1;
        migrateOffsetChannelId = -1;
        migrateOffsetAccess = -1;
        dialogsLoadOffsetId = 0;
        totalDialogsLoadCount = 0;
        dialogsLoadOffsetDate = 0;
        dialogsLoadOffsetUserId = 0;
        dialogsLoadOffsetChatId = 0;
        dialogsLoadOffsetChannelId = 0;
        dialogsLoadOffsetAccess = 0;
        ratingLoadTime = 0;
        botRatingLoadTime = 0;
        appLocked = false;
        passcodeType = 0;
        passcodeHash = "";
        passcodeSalt = new byte[0];
        autoLockIn = 60 * 60;
        lastPauseTime = 0;
        useFingerprint = true;
        draftsLoaded = true;
        notificationsConverted = true;
        contactsReimported = true;
        isWaitingForPasscodeEnter = false;
        allowScreenCapture = false;
        pinnedDialogsLoaded = false;
        lastUpdateVersion = BuildVars.BUILD_VERSION_STRING;
        lastContactsSyncTime = (int) (System.currentTimeMillis() / 1000) - 23 * 60 * 60;
        lastHintsSyncTime = (int) (System.currentTimeMillis() / 1000) - 25 * 60 * 60;
        saveConfig(true);
    }
}
