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
package org.telegram.messenger.exoplayer2.upstream;

import android.net.Uri;
import org.telegram.messenger.exoplayer2.C;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * A {@link DataSource} for reading local files.
 */
public final class FileDataSource implements DataSource {

  /**
   * Thrown when IOException is encountered during local file read operation.
   */
  public static class FileDataSourceException extends IOException {

    public FileDataSourceException(IOException cause) {
      super(cause);
    }

  }

  private final TransferListener<? super FileDataSource> listener;

  private RandomAccessFile file;
  private Uri uri;
  private long bytesRemaining;
  private boolean opened;

  public FileDataSource() {
    this(null);
  }

  /**
   * @param listener An optional listener.
   */
  public FileDataSource(TransferListener<? super FileDataSource> listener) {
    this.listener = listener;
  }

  @Override
  public long open(DataSpec dataSpec) throws FileDataSourceException {
    try {
      uri = dataSpec.uri;
      file = new RandomAccessFile(dataSpec.uri.getPath(), "r");
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
    if (listener != null) {
      listener.onTransferStart(this, dataSpec);
    }

    return bytesRemaining;
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
        bytesRead = file.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
      } catch (IOException e) {
        throw new FileDataSourceException(e);
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
        if (listener != null) {
          listener.onTransferEnd(this);
        }
      }
    }
  }

}
