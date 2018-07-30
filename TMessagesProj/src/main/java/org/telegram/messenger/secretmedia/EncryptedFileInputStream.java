/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger.secretmedia;

import org.telegram.messenger.SecureDocumentKey;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class EncryptedFileInputStream extends FileInputStream {

    private byte[] key = new byte[32];
    private byte[] iv = new byte[16];
    private int fileOffset;
    private int currentMode;

    private final static int MODE_CTR = 0;
    private final static int MODE_CBC = 1;

    public EncryptedFileInputStream(File file, File keyFile) throws Exception {
        super(file);

        currentMode = MODE_CTR;
        RandomAccessFile randomAccessFile = new RandomAccessFile(keyFile, "r");
        randomAccessFile.read(key, 0, 32);
        randomAccessFile.read(iv, 0, 16);
        randomAccessFile.close();
    }

    public EncryptedFileInputStream(File file, SecureDocumentKey secureDocumentKey) throws Exception {
        super(file);

        currentMode = MODE_CBC;
        System.arraycopy(secureDocumentKey.file_key, 0, key, 0, key.length);
        System.arraycopy(secureDocumentKey.file_iv, 0, iv, 0, iv.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (currentMode == MODE_CBC && fileOffset == 0) {
            byte[] temp = new byte[32];
            super.read(temp, 0, 32);
            Utilities.aesCbcEncryptionByteArraySafe(b, key, iv, off, len, fileOffset, 0);
            fileOffset += 32;
            skip((temp[0] & 0xff) - 32);
        }
        int result = super.read(b, off, len);
        if (currentMode == MODE_CBC) {
            Utilities.aesCbcEncryptionByteArraySafe(b, key, iv, off, len, fileOffset, 0);
        } else if (currentMode == MODE_CTR) {
            Utilities.aesCtrDecryptionByteArray(b, key, iv, off, len, fileOffset);
        }
        fileOffset += len;
        return result;
    }

    @Override
    public long skip(long n) throws IOException {
        fileOffset += n;
        return super.skip(n);
    }

    public static void decryptBytesWithKeyFile(byte[] bytes, int offset, int length, SecureDocumentKey secureDocumentKey) {
        Utilities.aesCbcEncryptionByteArraySafe(bytes, secureDocumentKey.file_key, secureDocumentKey.file_iv, offset, length, 0, 0);
    }

    public static void decryptBytesWithKeyFile(byte[] bytes, int offset, int length, File keyFile) throws Exception {
        byte[] key = new byte[32];
        byte[] iv = new byte[16];
        RandomAccessFile randomAccessFile = new RandomAccessFile(keyFile, "r");
        randomAccessFile.read(key, 0, 32);
        randomAccessFile.read(iv, 0, 16);
        randomAccessFile.close();
        Utilities.aesCtrDecryptionByteArray(bytes, key, iv, offset, length, 0);
    }
}
