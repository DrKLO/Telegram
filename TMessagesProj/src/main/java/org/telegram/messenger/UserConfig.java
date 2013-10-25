/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import org.telegram.TL.TLClassStore;
import org.telegram.TL.TLRPC;

import java.io.File;
import java.io.FileOutputStream;

public class UserConfig {
    public static TLRPC.User currentUser;
    public static int clientUserId = 0;
    public static boolean clientActivated = false;
    public static int lastDateValue = 0;
    public static int lastPtsValue = 0;
    public static int lastQtsValue = 0;
    public static int lastSeqValue = 0;
    public static boolean registeredForPush = false;
    public static String pushString = "";
    public static int lastSendMessageId = -1;
    public static int lastLocalId = -1;
    public static int lastSecretVersion = 0;
    public static byte[] secretPBytes = null;
    public static int secretG = 0;
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

    public static void saveConfig() {
        synchronized (sync) {
            SerializedData data = new SerializedData();
            if (currentUser != null) {
                data.writeInt32(1);
                currentUser.serializeToStream(data);
                clientUserId = currentUser.id;
                clientActivated = true;
                data.writeInt32(lastDateValue);
                data.writeInt32(lastPtsValue);
                data.writeInt32(lastSeqValue);
                data.writeBool(registeredForPush);
                data.writeString(pushString);
                data.writeInt32(lastSendMessageId);
                data.writeInt32(lastLocalId);
                data.writeString(contactsHash);
                data.writeString(importHash);
                data.writeBool(saveIncomingPhotos);
                data.writeInt32(lastQtsValue);
                data.writeInt32(lastSecretVersion);
                if (secretPBytes != null) {
                    data.writeInt32(1);
                    data.writeByteArray(secretPBytes);
                } else {
                    data.writeInt32(0);
                }
                data.writeInt32(secretG);
            } else {
                data.writeInt32(0);
            }
            try {
                File configFile = new File(Utilities.applicationContext.getFilesDir(), "user.dat");
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

    public static void loadConfig() {
        synchronized (sync) {
            File configFile = new File(Utilities.applicationContext.getFilesDir(), "user.dat");
            if (configFile.exists()) {
                try {
                    SerializedData data = new SerializedData(configFile);
                    if (data.readInt32() != 0) {
                        int constructor = data.readInt32();
                        currentUser = (TLRPC.TL_userSelf)TLClassStore.Instance().TLdeserialize(data, constructor);
                        clientUserId = currentUser.id;
                        clientActivated = true;
                        lastDateValue = data.readInt32();
                        lastPtsValue = data.readInt32();
                        lastSeqValue = data.readInt32();
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
                        lastQtsValue = data.readInt32();
                        lastSecretVersion = data.readInt32();
                        int val = data.readInt32();
                        if (val == 1) {
                            secretPBytes = data.readByteArray();
                        }
                        secretG = data.readInt32();
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
        lastDateValue = 0;
        lastSeqValue = 0;
        lastPtsValue = 0;
        registeredForPush = false;
        contactsHash = "";
        lastLocalId = -1;
        importHash = "";
        lastSendMessageId = -1;
        saveIncomingPhotos = false;
        saveConfig();
    }
}
