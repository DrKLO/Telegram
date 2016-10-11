/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.upstream;

import org.telegram.messenger.exoplayer.C;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * A local file {@link UriDataSource}.
 */
public final class FileDataSource implements UriDataSource {

  /**
   * Thrown when IOException is encountered during local file read operation.
   */
  public static class FileDataSourceException extends IOException {

    public FileDataSourceException(IOException cause) {
      super(cause);
    }

  }

  private final TransferListener listener;

  private RandomAccessFile file;
  private String uriString;
  private long bytesRemaining;
  private boolean opened;

  /**
   * Constructs a new {@link DataSource} that retrieves data from a file.
   */
  public FileDataSource() {
    this(null);
  }

  /**
   * Constructs a new {@link DataSource} that retrieves data from a file.
   *
   * @param listener An optional listener. Specify {@code null} for no listener.
   */
  public FileDataSource(TransferListener listener) {
    this.listener = listener;
  }

  @Override
  public long open(DataSpec dataSpec) throws FileDataSourceException {
    try {
      uriString = dataSpec.uri.toString();
      file = new RandomAccessFile(dataSpec.uri.getPath(), "r");
      file.seek(dataSpec.position);
      bytesRemaining = dataSpec.length == C.LENGTH_UNBOUNDED ? file.length() - dataSpec.position
          : dataSpec.length;
      if (bytesRemaining < 0) {
        throw new EOFException();
      }
    } catch (IOException e) {
      throw new FileDataSourceException(e);
    }

    opened = true;
    if (listener != null) {
      listener.onTransferStart();
    }

    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws FileDataSourceException {
    if (bytesRemaining == 0) {
      return -1;
    } else {
      int bytesRead = 0;
      try {
        bytesRead = file.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
      } catch (IOException e) {
        throw new FileDataSourceException(e);
      }

      if (bytesRead > 0) {
        bytesRemaining -= bytesRead;
        if (listener != null) {
          listener.onBytesTransferred(bytesRead);
        }
      }

      return bytesRead;
    }
  }

  @Override
  public String getUri() {
    return uriString;
  }

  @Override
  public void close() throws FileDataSourceException {
    uriString = null;
    if (file != null) {
      try {
        file.close();
      } catch (IOException e) {
        throw new FileDataSourceException(e);
      } finally {
        file = null;
        if (opened) {
          opened = false;
          if (listener != null) {
            listener.onTransferEnd();
          }
        }
      }
    }
  }

}
