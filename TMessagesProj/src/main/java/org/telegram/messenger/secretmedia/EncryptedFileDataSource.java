/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger.secretmedia;

import android.net.Uri;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class EncryptedFileDataSource extends BaseDataSource {

    public static class EncryptedFileDataSourceException extends IOException {

        public EncryptedFileDataSourceException(Throwable cause) {
            super(cause);
        }

    }

    private Uri uri;
    private long bytesRemaining;
    private boolean opened;
    private int fileOffset;

    public EncryptedFileDataSource() {
        super(/* isNetwork= */ false);
    }

    @Deprecated
    public EncryptedFileDataSource(@Nullable TransferListener listener) {
        this();
        if (listener != null) {
            addTransferListener(listener);
        }
    }

    EncryptedFileInputStream fileInputStream;

    @Override
    public long open(DataSpec dataSpec) throws EncryptedFileDataSourceException {
        try {
            uri = dataSpec.uri;
            File path = new File(dataSpec.uri.getPath());
            String name = path.getName();
            File keyPath = new File(FileLoader.getInternalCacheDir(), name + ".key");

            FileLog.d("EncryptedFileDataSource " + path + " " + keyPath);
            fileInputStream = new EncryptedFileInputStream(path, keyPath);
            fileInputStream.skip(dataSpec.position);
            bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? fileInputStream.available() : dataSpec.length;
            FileLog.d("EncryptedFileDataSource bytesRemaining" + bytesRemaining);
            if (bytesRemaining < 0) {
                throw new EOFException();
            }
        } catch (Exception e) {
            FileLog.e(e);
            throw new EncryptedFileDataSourceException(e);
        }

        FileLog.d("EncryptedFileDataSource opened");
        opened = true;
        transferStarted(dataSpec);

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
                bytesRead = fileInputStream.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
                fileOffset += bytesRead;
            } catch (IOException e) {
                FileLog.e(e);
                throw new EncryptedFileDataSourceException(e);
            }

            if (bytesRead > 0) {
                bytesRemaining -= bytesRead;
                bytesTransferred(bytesRead);
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
            if (fileInputStream != null) {
                fileInputStream.close();
            }
        } catch (IOException e) {
            FileLog.e(e);
            throw new EncryptedFileDataSourceException(e);
        } finally {
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }
}
