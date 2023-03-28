/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger.secretmedia;

import static java.lang.Math.min;

import android.net.Uri;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class EncryptedFileDataSource extends BaseDataSource {

    public static class EncryptedFileDataSourceException extends IOException {

        public EncryptedFileDataSourceException(Throwable cause) {
            super(cause);
        }

    }

    private Uri uri;
    private boolean opened;
    private int bytesRemaining;
    EncryptedFileInputStream fileInputStream;

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


    @Override
    public long open(DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        File path = new File(dataSpec.uri.getPath());
        String name = path.getName();
        File keyPath = new File(FileLoader.getInternalCacheDir(), name + ".key");

        try {
            fileInputStream = new EncryptedFileInputStream(path, keyPath);
            fileInputStream.skip(dataSpec.position);
            int len = (int) path.length();

            transferInitializing(dataSpec);
            if (dataSpec.position > len) {
                throw new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
            }
            bytesRemaining = (int) (len - dataSpec.position);
            if (dataSpec.length != C.LENGTH_UNSET) {
                bytesRemaining = (int) min(bytesRemaining, dataSpec.length);
            }
            opened = true;
            transferStarted(dataSpec);
            return dataSpec.length != C.LENGTH_UNSET ? dataSpec.length : bytesRemaining;
        } catch (Throwable throwable) {
            throw new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
        if (length == 0) {
            return 0;
        } else if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        }
        length = min(length, bytesRemaining);
        try {
            fileInputStream.read(buffer, offset, length);
        } catch (IOException e) {
            e.printStackTrace();
        }
        bytesRemaining -= length;
        bytesTransferred(length);
        return length;
    }

    @Override
    @Nullable
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() {
        try {
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (opened) {
            opened = false;
            transferEnded();
        }
        fileInputStream = null;
        uri = null;
    }
}
