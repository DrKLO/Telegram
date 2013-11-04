/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.TL.TLClassStore;
import org.telegram.TL.TLRPC;
import org.telegram.ui.ApplicationLoader;

import java.io.File;
import java.io.FileOutputStream;

public class UserConfig {
    public static TLRPC.User currentUser;
    public static int clientUserId = 0;
    public static boolean clientActivated = false;
    public static boolean registeredForPush = false;
    public static String pushString = "";
    public static int lastSendMessageId = -1;
    public static int lastLocalId = -1;
    public static String contactsHash = "";
    public static String importHash = "";
    private final static Integer sync = 1;
    public static boolean saveIncomingPhotos = false;

    public static int getNewMessageId() {
        int id;
        synchronized (sync) {
            id = lastSendMessageId;
            lastSendMessageId--;
        }
        return id;
    }

    public static void saveConfig(boolean withFile) {
        synchronized (sync) {
            SerializedData data = new SerializedData();
            if (currentUser != null) {
                data.writeInt32(2);
                currentUser.serializeToStream(data);
                clientUserId = currentUser.id;
                clientActivated = true;
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("registeredForPush", registeredForPush);
                editor.putString("pushString", pushString);
                editor.putInt("lastSendMessageId", lastSendMessageId);
                editor.putInt("lastLocalId", lastLocalId);
                editor.putString("contactsHash", contactsHash);
                editor.putString("importHash", importHash);
                editor.putBoolean("saveIncomingPhotos", saveIncomingPhotos);
                editor.commit();
            } else {
                data.writeInt32(0);
            }
            if (withFile) {
                try {
                    File configFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "user.dat");
                    if (!configFile.exists()) {
                        configFile.createNewFile();
                    }
                    FileOutputStream stream = new FileOutputStream(configFile);
                    stream.write(data.toByteArray());
                    stream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void loadConfig() {
        synchronized (sync) {
            File configFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "user.dat");
            if (configFile.exists()) {
                try {
                    SerializedData data = new SerializedData(configFile);
                    int ver = data.readInt32();
                    if (ver == 1) {
                        int constructor = data.readInt32();
                        currentUser = (TLRPC.TL_userSelf)TLClassStore.Instance().TLdeserialize(data, constructor);
                        clientUserId = currentUser.id;
                        clientActivated = true;
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
                        if (currentUser.status != null) {
                            if (currentUser.status.expires != 0) {
                                currentUser.status.was_online = currentUser.status.expires;
                            } else {
                                currentUser.status.expires = currentUser.status.was_online;
                            }
                        }
                        MessagesStorage.lastQtsValue = data.readInt32();
                        MessagesStorage.lastSecretVersion = data.readInt32();
                        int val = data.readInt32();
                        if (val == 1) {
                            MessagesStorage.secretPBytes = data.readByteArray();
                        }
                        MessagesStorage.secretG = data.readInt32();
                    } else if (ver == 2) {
                        int constructor = data.readInt32();
                        currentUser = (TLRPC.TL_userSelf)TLClassStore.Instance().TLdeserialize(data, constructor);
                        clientUserId = currentUser.id;
                        clientActivated = true;

                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
                        registeredForPush = preferences.getBoolean("registeredForPush", false);
                        pushString = preferences.getString("pushString", "");
                        lastSendMessageId = preferences.getInt("lastSendMessageId", -1);
                        lastLocalId = preferences.getInt("lastLocalId", -1);
                        contactsHash = preferences.getString("contactsHash", "");
                        importHash = preferences.getString("importHash", "");
                        saveIncomingPhotos = preferences.getBoolean("saveIncomingPhotos", false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void clearConfig() {
        clientUserId = 0;
        clientActivated = false;
        currentUser = null;
        registeredForPush = false;
        contactsHash = "";
        lastLocalId = -1;
        importHash = "";
        lastSendMessageId = -1;
        saveIncomingPhotos = false;
        saveConfig(true);
    }
}
