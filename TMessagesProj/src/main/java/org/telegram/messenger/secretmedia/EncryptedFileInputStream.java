/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger.secretmedia;

import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class EncryptedFileInputStream extends FileInputStream {

    private byte[] key = new byte[32];
    private byte[] iv = new byte[16];
    private int fileOffset;

    public EncryptedFileInputStream(File file, File keyFile) throws Exception {
        super(file);

        RandomAccessFile randomAccessFile = new RandomAccessFile(keyFile, "r");
        randomAccessFile.read(key, 0, 32);
        randomAccessFile.read(iv, 0, 16);
        randomAccessFile.close();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = super.read(b, off, len);
        Utilities.aesCtrDecryptionByteArray(b, key, iv, off, len, fileOffset);
        fileOffset += len;
        return result;
    }

    @Override
    public long skip(long n) throws IOException {
        fileOffset += n;
        return super.skip(n);
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
