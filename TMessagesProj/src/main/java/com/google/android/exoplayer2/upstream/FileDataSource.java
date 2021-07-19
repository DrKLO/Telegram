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

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/** A {@link DataSource} for reading local files. */
public final class FileDataSource extends BaseDataSource {

  /** Thrown when a {@link FileDataSource} encounters an error reading a file. */
  public static class FileDataSourceException extends IOException {

    public FileDataSourceException(IOException cause) {
      super(cause);
    }

    public FileDataSourceException(String message, IOException cause) {
      super(message, cause);
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
    try {
      Uri uri = dataSpec.uri;
      this.uri = uri;

      transferInitializing(dataSpec);

      this.file = openLocalFile(uri);

      file.seek(dataSpec.position);
      bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? file.length() - dataSpec.position
          : dataSpec.length;
      if (bytesRemaining < 0) {
        throw new EOFException();
      }
    } catch (IOException e) {
      throw new FileDataSourceException(e);
    }

    opened = true;
    transferStarted(dataSpec);

    return bytesRemaining;
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
            e);
      }
      throw new FileDataSourceException(e);
    }
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws FileDataSourceException {
    if (readLength == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    } else {
      int bytesRead;
      try {
        bytesRead =
            castNonNull(file).read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
      } catch (IOException e) {
        throw new FileDataSourceException(e);
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
      throw new FileDataSourceException(e);
    } finally {
      file = null;
      if (opened) {
        opened = false;
        transferEnded();
      }
    }
  }

}
