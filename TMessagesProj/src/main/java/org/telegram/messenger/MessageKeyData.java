/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.SerializedData;

public class MessageKeyData {

    public byte[] aesKey;
    public byte[] aesIv;

    public static MessageKeyData generateMessageKeyData(byte[] authKey, byte[] messageKey, boolean incoming, int version) {
        MessageKeyData keyData = new MessageKeyData();
        if (authKey == null || authKey.length == 0) {
            keyData.aesIv = null;
            keyData.aesKey = null;
            return keyData;
        }

        int x = incoming ? 8 : 0;

        switch (version) {
            case 2: {
                SerializedData data = new SerializedData();
                data.writeBytes(messageKey, 0, 16);
                data.writeBytes(authKey, x, 36);
                byte[] sha256_a = Utilities.computeSHA256(data.toByteArray());
                data.cleanup();

                data = new SerializedData();
                data.writeBytes(authKey, 40 + x, 36);
                data.writeBytes(messageKey, 0, 16);
                byte[] sha256_b = Utilities.computeSHA256(data.toByteArray());
                data.cleanup();

                data = new SerializedData();
                data.writeBytes(sha256_a, 0, 8);
                data.writeBytes(sha256_b, 8, 16);
                data.writeBytes(sha256_a, 24, 8);
                keyData.aesKey = data.toByteArray();
                data.cleanup();

                data = new SerializedData();
                data.writeBytes(sha256_b, 0, 8);
                data.writeBytes(sha256_a, 8, 16);
                data.writeBytes(sha256_b, 24, 8);
                keyData.aesIv = data.toByteArray();
                data.cleanup();
                break;
            }
            case 1: {
                SerializedData data = new SerializedData();
                data.writeBytes(messageKey);
                data.writeBytes(authKey, x, 32);
                byte[] sha1_a = Utilities.computeSHA1(data.toByteArray());
                data.cleanup();

                data = new SerializedData();
                data.writeBytes(authKey, 32 + x, 16);
                data.writeBytes(messageKey);
                data.writeBytes(authKey, 48 + x, 16);
                byte[] sha1_b = Utilities.computeSHA1(data.toByteArray());
                data.cleanup();

                data = new SerializedData();
                data.writeBytes(authKey, 64 + x, 32);
                data.writeBytes(messageKey);
                byte[] sha1_c = Utilities.computeSHA1(data.toByteArray());
                data.cleanup();

                data = new SerializedData();
                data.writeBytes(messageKey);
                data.writeBytes(authKey, 96 + x, 32);
                byte[] sha1_d = Utilities.computeSHA1(data.toByteArray());
                data.cleanup();

                data = new SerializedData();
                data.writeBytes(sha1_a, 0, 8);
                data.writeBytes(sha1_b, 8, 12);
                data.writeBytes(sha1_c, 4, 12);
                keyData.aesKey = data.toByteArray();
                data.cleanup();

                data = new SerializedData();
                data.writeBytes(sha1_a, 8, 12);
                data.writeBytes(sha1_b, 0, 8);
                data.writeBytes(sha1_c, 16, 4);
                data.writeBytes(sha1_d, 0, 8);
                keyData.aesIv = data.toByteArray();
                data.cleanup();
                break;
            }
        }
        return keyData;
    }
}
