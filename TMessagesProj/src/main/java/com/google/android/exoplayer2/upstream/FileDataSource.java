/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.upstream;

import static com.google.android.exoplayer2.util.Util.castNonNull;
import static java.lang.Math.min;

import android.net.Uri;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.text.TextUtils;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/** A {@link DataSource} for reading local files. */
public final class FileDataSource extends BaseDataSource {

  /** Thrown when a {@link FileDataSource} encounters an error reading a file. */
  public static class FileDataSourceException extends DataSourceException {

    /**
     * @deprecated Use {@link #FileDataSourceException(Throwable, int)}
     */
    @Deprecated
    public FileDataSourceException(Exception cause) {
      super(cause, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    /**
     * @deprecated Use {@link #FileDataSourceException(String, Throwable, int)}
     */
    @Deprecated
    public FileDataSourceException(String message, IOException cause) {
      super(message, cause, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    /** Creates a {@code FileDataSourceException}. */
    public FileDataSourceException(Throwable cause, @PlaybackException.ErrorCode int errorCode) {
      super(cause, errorCode);
    }

    /** Creates a {@code FileDataSourceException}. */
    public FileDataSourceException(
        @Nullable String message,
        @Nullable Throwable cause,
        @PlaybackException.ErrorCode int errorCode) {
      super(message, cause, errorCode);
    }
  }

  /** {@link DataSource.Factory} for {@link FileDataSource} instances. */
  public static final class Factory implements DataSource.Factory {

    @Nullable private TransferListener listener;

    /**
     * Sets a {@link TransferListener} for {@link FileDataSource} instances created by this factory.
     *
     * @param listener The {@link TransferListener}.
     * @return This factory.
     */
    @CanIgnoreReturnValue
    public Factory setListener(@Nullable TransferListener listener) {
      this.listener = listener;
      return this;
    }

    @Override
    public FileDataSource createDataSource() {
      FileDataSource dataSource = new FileDataSource();
      if (listener != null) {
        dataSource.addTransferListener(listener);
      }
      return dataSource;
    }
  }

  @Nullable private RandomAccessFile file;
  @Nullable private Uri uri;
  private long bytesRemaining;
  private boolean opened;

  public FileDataSource() {
    super(/* isNetwork= */ false);
  }

  @Override
  public long open(DataSpec dataSpec) throws FileDataSourceException {
    Uri uri = dataSpec.uri;
    this.uri = uri;
    transferInitializing(dataSpec);
    this.file = openLocalFile(uri);
    try {
      file.seek(dataSpec.position);
      bytesRemaining =
          dataSpec.length == C.LENGTH_UNSET ? file.length() - dataSpec.position : dataSpec.length;
    } catch (IOException e) {
      throw new FileDataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }
    if (bytesRemaining < 0) {
      throw new FileDataSourceException(
          /* message= */ null,
          /* cause= */ null,
          PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
    }

    opened = true;
    transferStarted(dataSpec);

    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws FileDataSourceException {
    if (length == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    } else {
      int bytesRead;
      try {
        bytesRead = castNonNull(file).read(buffer, offset, (int) min(bytesRemaining, length));
      } catch (IOException e) {
        throw new FileDataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
      }

      if (bytesRead > 0) {
        bytesRemaining -= bytesRead;
        bytesTransferred(bytesRead);
      }

      return bytesRead;
    }
  }

  @Override
  @Nullable
  public Uri getUri() {
    return uri;
  }

  @Override
  public void close() throws FileDataSourceException {
    uri = null;
    try {
      if (file != null) {
        file.close();
      }
    } catch (IOException e) {
      throw new FileDataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    } finally {
      file = null;
      if (opened) {
        opened = false;
        transferEnded();
      }
    }
  }

  private static RandomAccessFile openLocalFile(Uri uri) throws FileDataSourceException {
    try {
      return new RandomAccessFile(Assertions.checkNotNull(uri.getPath()), "r");
    } catch (FileNotFoundException e) {
      if (!TextUtils.isEmpty(uri.getQuery()) || !TextUtils.isEmpty(uri.getFragment())) {
        throw new FileDataSourceException(
            String.format(
                "uri has query and/or fragment, which are not supported. Did you call Uri.parse()"
                    + " on a string containing '?' or '#'? Use Uri.fromFile(new File(path)) to"
                    + " avoid this. path=%s,query=%s,fragment=%s",
                uri.getPath(), uri.getQuery(), uri.getFragment()),
            e,
            PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK);
      }

      // TODO(internal b/193503588): Add tests to ensure the correct error codes are assigned under
      // different SDK versions.
      throw new FileDataSourceException(
          e,
          Util.SDK_INT >= 21 && Api21.isPermissionError(e.getCause())
              ? PlaybackException.ERROR_CODE_IO_NO_PERMISSION
              : PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND);
    } catch (SecurityException e) {
      throw new FileDataSourceException(e, PlaybackException.ERROR_CODE_IO_NO_PERMISSION);
    } catch (RuntimeException e) {
      throw new FileDataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }
  }

  @RequiresApi(21)
  private static final class Api21 {
    @DoNotInline
    private static boolean isPermissionError(@Nullable Throwable e) {
      return e instanceof ErrnoException && ((ErrnoException) e).errno == OsConstants.EACCES;
    }
  }
}
