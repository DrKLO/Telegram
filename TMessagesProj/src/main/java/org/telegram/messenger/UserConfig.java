/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;

public class UserConfig {

    private static TLRPC.User currentUser;
    public static boolean registeredForPush = false;
    public static String pushString = "";
    public static int lastSendMessageId = -210000;
    public static int lastLocalId = -210000;
    public static int lastBroadcastId = -1;
    public static String contactsHash = "";
    public static String importHash = "";
    public static boolean blockedUsersLoaded = false;
    private final static Object sync = new Object();
    public static boolean saveIncomingPhotos = false;
    public static int contactsVersion = 1;
    public static String passcodeHash = "";
    public static byte[] passcodeSalt = new byte[0];
    public static boolean appLocked = false;
    public static int passcodeType = 0;
    public static int autoLockIn = 60 * 60;
    public static int lastPauseTime = 0;
    public static boolean isWaitingForPasscodeEnter = false;
    public static boolean useFingerprint = true;
    public static String lastUpdateVersion;
    public static int lastContactsSyncTime;

    public static int migrateOffsetId = -1;
    public static int migrateOffsetDate = -1;
    public static int migrateOffsetUserId = -1;
    public static int migrateOffsetChatId = -1;
    public static int migrateOffsetChannelId = -1;
    public static long migrateOffsetAccess = -1;

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
                editor.putString("contactsHash", contactsHash);
                editor.putString("importHash", importHash);
                editor.putBoolean("saveIncomingPhotos", saveIncomingPhotos);
                editor.putInt("contactsVersion", contactsVersion);
                editor.putInt("lastBroadcastId", lastBroadcastId);
                editor.putBoolean("blockedUsersLoaded", blockedUsersLoaded);
                editor.putString("passcodeHash1", passcodeHash);
                editor.putString("passcodeSalt", passcodeSalt.length > 0 ? Base64.encodeToString(passcodeSalt, Base64.DEFAULT) : "");
                editor.putBoolean("appLocked", appLocked);
                editor.putInt("passcodeType", passcodeType);
                editor.putInt("autoLockIn", autoLockIn);
                editor.putInt("lastPauseTime", lastPauseTime);
                editor.putString("lastUpdateVersion2", lastUpdateVersion);
                editor.putInt("lastContactsSyncTime", lastContactsSyncTime);
                editor.putBoolean("useFingerprint", useFingerprint);

                editor.putInt("migrateOffsetId", migrateOffsetId);
                if (migrateOffsetId != -1) {
                    editor.putInt("migrateOffsetDate", migrateOffsetDate);
                    editor.putInt("migrateOffsetUserId", migrateOffsetUserId);
                    editor.putInt("migrateOffsetChatId", migrateOffsetChatId);
                    editor.putInt("migrateOffsetChannelId", migrateOffsetChannelId);
                    editor.putLong("migrateOffsetAccess", migrateOffsetAccess);
                }

                if (currentUser != null) {
                    if (withFile) {
                        SerializedData data = new SerializedData();
                        currentUser.serializeToStream(data);
                        String userString = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                        editor.putString("user", userString);
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
                FileLog.e("tmessages", e);
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
                        contactsHash = data.readString(false);
                        importHash = data.readString(false);
                        saveIncomingPhotos = data.readBool(false);
                        contactsVersion = 0;
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
                        contactsHash = preferences.getString("contactsHash", "");
                        importHash = preferences.getString("importHash", "");
                        saveIncomingPhotos = preferences.getBoolean("saveIncomingPhotos", false);
                        contactsVersion = preferences.getInt("contactsVersion", 0);
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
                    FileLog.e("tmessages", e);
                }
            } else {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
                registeredForPush = preferences.getBoolean("registeredForPush", false);
                pushString = preferences.getString("pushString2", "");
                lastSendMessageId = preferences.getInt("lastSendMessageId", -210000);
                lastLocalId = preferences.getInt("lastLocalId", -210000);
                contactsHash = preferences.getString("contactsHash", "");
                importHash = preferences.getString("importHash", "");
                saveIncomingPhotos = preferences.getBoolean("saveIncomingPhotos", false);
                contactsVersion = preferences.getInt("contactsVersion", 0);
                lastBroadcastId = preferences.getInt("lastBroadcastId", -1);
                blockedUsersLoaded = preferences.getBoolean("blockedUsersLoaded", false);
                passcodeHash = preferences.getString("passcodeHash1", "");
                appLocked = preferences.getBoolean("appLocked", false);
                passcodeType = preferences.getInt("passcodeType", 0);
                autoLockIn = preferences.getInt("autoLockIn", 60 * 60);
                lastPauseTime = preferences.getInt("lastPauseTime", 0);
                useFingerprint = preferences.getBoolean("useFingerprint", true);
                lastUpdateVersion = preferences.getString("lastUpdateVersion2", "3.5");
                lastContactsSyncTime = preferences.getInt("lastContactsSyncTime", (int) (System.currentTimeMillis() / 1000) - 23 * 60 * 60);

                migrateOffsetId = preferences.getInt("migrateOffsetId", 0);
                if (migrateOffsetId != -1) {
                    migrateOffsetDate = preferences.getInt("migrateOffsetDate", 0);
                    migrateOffsetUserId = preferences.getInt("migrateOffsetUserId", 0);
                    migrateOffsetChatId = preferences.getInt("migrateOffsetChatId", 0);
                    migrateOffsetChannelId = preferences.getInt("migrateOffsetChannelId", 0);
                    migrateOffsetAccess = preferences.getLong("migrateOffsetAccess", 0);
                }

                String user = preferences.getString("user", null);
                if (user != null) {
                    byte[] userBytes = Base64.decode(user, Base64.DEFAULT);
                    if (userBytes != null) {
                        SerializedData data = new SerializedData(userBytes);
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
            }
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
                    FileLog.e("tmessages", e);
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
                FileLog.e("tmessages", e);
            }
        }
        return false;
    }

    public static void clearConfig() {
        currentUser = null;
        registeredForPush = false;
        contactsHash = "";
        importHash = "";
        lastSendMessageId = -210000;
        contactsVersion = 1;
        lastBroadcastId = -1;
        saveIncomingPhotos = false;
        blockedUsersLoaded = false;
        migrateOffsetId = -1;
        migrateOffsetDate = -1;
        migrateOffsetUserId = -1;
        migrateOffsetChatId = -1;
        migrateOffsetChannelId = -1;
        migrateOffsetAccess = -1;
        appLocked = false;
        passcodeType = 0;
        passcodeHash = "";
        passcodeSalt = new byte[0];
        autoLockIn = 60 * 60;
        lastPauseTime = 0;
        useFingerprint = true;
        isWaitingForPasscodeEnter = false;
        lastUpdateVersion = BuildVars.BUILD_VERSION_STRING;
        lastContactsSyncTime = (int) (System.currentTimeMillis() / 1000) - 23 * 60 * 60;
        saveConfig(true);
    }
}
