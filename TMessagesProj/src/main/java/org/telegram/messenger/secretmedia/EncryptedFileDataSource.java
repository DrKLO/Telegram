/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger.secretmedia;

import android.net.Uri;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;
import org.telegram.messenger.exoplayer2.upstream.TransferListener;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class EncryptedFileDataSource implements DataSource {

    /**
     * Thrown when IOException is encountered during local file read operation.
     */
    public static class EncryptedFileDataSourceException extends IOException {

        public EncryptedFileDataSourceException(IOException cause) {
            super(cause);
        }

    }

    private final TransferListener<? super EncryptedFileDataSource> listener;

    private RandomAccessFile file;
    private Uri uri;
    private long bytesRemaining;
    private boolean opened;
    private byte[] key = new byte[32];
    private byte[] iv = new byte[16];
    private int fileOffset;

    public EncryptedFileDataSource() {
        this(null);
    }

    public EncryptedFileDataSource(TransferListener<? super EncryptedFileDataSource> listener) {
        this.listener = listener;
    }

    @Override
    public long open(DataSpec dataSpec) throws EncryptedFileDataSourceException {
        try {
            uri = dataSpec.uri;
            File path = new File(dataSpec.uri.getPath());
            String name = path.getName();
            File keyPath = new File(FileLoader.getInternalCacheDir(), name + ".key");
            RandomAccessFile keyFile = new RandomAccessFile(keyPath, "r");
            keyFile.read(key);
            keyFile.read(iv);
            keyFile.close();

            file = new RandomAccessFile(path, "r");
            file.seek(dataSpec.position);
            fileOffset = (int) dataSpec.position;
            bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? file.length() - dataSpec.position : dataSpec.length;
            if (bytesRemaining < 0) {
                throw new EOFException();
            }
        } catch (IOException e) {
            throw new EncryptedFileDataSourceException(e);
        }

        opened = true;
        if (listener != null) {
            listener.onTransferStart(this, dataSpec);
        }

        return bytesRemaining;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws EncryptedFileDataSourceException {
        if (readLength == 0) {
            return 0;
        } else if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        } else {
            int bytesRead;
            try {
                bytesRead = file.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
                Utilities.aesCtrDecryptionByteArray(buffer, key, iv, offset, bytesRead, fileOffset);
                fileOffset += bytesRead;
            } catch (IOException e) {
                throw new EncryptedFileDataSourceException(e);
            }

            if (bytesRead > 0) {
                bytesRemaining -= bytesRead;
                if (listener != null) {
                    listener.onBytesTransferred(this, bytesRead);
                }
            }

            return bytesRead;
        }
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() throws EncryptedFileDataSourceException {
        uri = null;
        fileOffset = 0;
        try {
            if (file != null) {
                file.close();
            }
        } catch (IOException e) {
            throw new EncryptedFileDataSourceException(e);
        } finally {
            file = null;
            if (opened) {
                opened = false;
                if (listener != null) {
                    listener.onTransferEnd(this);
                }
            }
        }
    }
}
