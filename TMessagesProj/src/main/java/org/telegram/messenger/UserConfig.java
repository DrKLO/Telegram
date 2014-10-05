/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.telegram.android.MessagesStorage;
import org.telegram.ui.ApplicationLoader;

import java.io.File;

public class UserConfig {
    private static TLRPC.User currentUser;
    public static boolean registeredForPush = false;
    public static boolean registeredForInternalPush = false;
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
                editor.putString("pushString", pushString);
                editor.putInt("lastSendMessageId", lastSendMessageId);
                editor.putInt("lastLocalId", lastLocalId);
                editor.putString("contactsHash", contactsHash);
                editor.putString("importHash", importHash);
                editor.putBoolean("saveIncomingPhotos", saveIncomingPhotos);
                editor.putInt("contactsVersion", contactsVersion);
                editor.putInt("lastBroadcastId", lastBroadcastId);
                editor.putBoolean("registeredForInternalPush", registeredForInternalPush);
                editor.putBoolean("blockedUsersLoaded", blockedUsersLoaded);
                if (currentUser != null) {
                    if (withFile) {
                        SerializedData data = new SerializedData();
                        currentUser.serializeToStream(data);
                        String userString = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                        editor.putString("user", userString);
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
            final File configFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "user.dat");
            if (configFile.exists()) {
                try {
                    SerializedData data = new SerializedData(configFile);
                    int ver = data.readInt32();
                    if (ver == 1) {
                        int constructor = data.readInt32();
                        currentUser = (TLRPC.TL_userSelf)TLClassStore.Instance().TLdeserialize(data, constructor);
                        MessagesStorage.lastDateValue = data.readInt32();
                        MessagesStorage.lastPtsValue = data.readInt32();
                        MessagesStorage.lastSeqValue = data.readInt32();
                        registeredForPush = data.readBool();
                        pushString = data.readString();
                        lastSendMessageId = data.readInt32();
                        lastLocalId = data.readInt32();
                        contactsHash = data.readString();
                        importHash = data.readString();
                        saveIncomingPhotos = data.readBool();
                        contactsVersion = 0;
                        MessagesStorage.lastQtsValue = data.readInt32();
                        MessagesStorage.lastSecretVersion = data.readInt32();
                        int val = data.readInt32();
                        if (val == 1) {
                            MessagesStorage.secretPBytes = data.readByteArray();
                        }
                        MessagesStorage.secretG = data.readInt32();
                        Utilities.stageQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                saveConfig(true, configFile);
                            }
                        });
                    } else if (ver == 2) {
                        int constructor = data.readInt32();
                        currentUser = (TLRPC.TL_userSelf)TLClassStore.Instance().TLdeserialize(data, constructor);

                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
                        registeredForPush = preferences.getBoolean("registeredForPush", false);
                        pushString = preferences.getString("pushString", "");
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
                pushString = preferences.getString("pushString", "");
                lastSendMessageId = preferences.getInt("lastSendMessageId", -210000);
                lastLocalId = preferences.getInt("lastLocalId", -210000);
                contactsHash = preferences.getString("contactsHash", "");
                importHash = preferences.getString("importHash", "");
                saveIncomingPhotos = preferences.getBoolean("saveIncomingPhotos", false);
                contactsVersion = preferences.getInt("contactsVersion", 0);
                lastBroadcastId = preferences.getInt("lastBroadcastId", -1);
                registeredForInternalPush = preferences.getBoolean("registeredForInternalPush", false);
                blockedUsersLoaded = preferences.getBoolean("blockedUsersLoaded", false);
                String user = preferences.getString("user", null);
                if (user != null) {
                    byte[] userBytes = Base64.decode(user, Base64.DEFAULT);
                    if (userBytes != null) {
                        SerializedData data = new SerializedData(userBytes);
                        currentUser = (TLRPC.TL_userSelf)TLClassStore.Instance().TLdeserialize(data, data.readInt32());
                    }
                }
            }
        }
    }

    public static void clearConfig() {
        currentUser = null;
        registeredForInternalPush = false;
        registeredForPush = false;
        contactsHash = "";
        importHash = "";
        lastLocalId = -210000;
        lastSendMessageId = -210000;
        contactsVersion = 1;
        lastBroadcastId = -1;
        saveIncomingPhotos = false;
        blockedUsersLoaded = false;
        saveConfig(true);
    }
}
