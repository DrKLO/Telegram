/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.messenger;

public class MessageKeyData {

    public byte[] aesKey;
    public byte[] aesIv;

    public static MessageKeyData generateMessageKeyData(byte[] authKey, byte[] messageKey, boolean incoming) {
        MessageKeyData keyData = new MessageKeyData();
        if (authKey == null || authKey.length == 0) {
            keyData.aesIv = null;
            keyData.aesKey = null;
            return keyData;
        }

        int x = incoming ? 8 : 0;

        SerializedData data = new SerializedData();
        data.writeRaw(messageKey);
        data.writeRaw(authKey, x, 32);
        byte[] sha1_a = Utilities.computeSHA1(data.toByteArray());
        data.cleanup();

        data = new SerializedData();
        data.writeRaw(authKey, 32 + x, 16);
        data.writeRaw(messageKey);
        data.writeRaw(authKey, 48 + x, 16);
        byte[] sha1_b = Utilities.computeSHA1(data.toByteArray());
        data.cleanup();

        data = new SerializedData();
        data.writeRaw(authKey, 64 + x, 32);
        data.writeRaw(messageKey);
        byte[] sha1_c = Utilities.computeSHA1(data.toByteArray());
        data.cleanup();

        data = new SerializedData();
        data.writeRaw(messageKey);
        data.writeRaw(authKey, 96 + x, 32);
        byte[] sha1_d = Utilities.computeSHA1(data.toByteArray());
        data.cleanup();

        data = new SerializedData();
        data.writeRaw(sha1_a, 0, 8);
        data.writeRaw(sha1_b, 8, 12);
        data.writeRaw(sha1_c, 4, 12);
        keyData.aesKey = data.toByteArray();
        data.cleanup();

        data = new SerializedData();
        data.writeRaw(sha1_a, 8, 12);
        data.writeRaw(sha1_b, 0, 8);
        data.writeRaw(sha1_c, 16, 4);
        data.writeRaw(sha1_d, 0, 8);
        keyData.aesIv = data.toByteArray();
        data.cleanup();

        return keyData;
    }
}
