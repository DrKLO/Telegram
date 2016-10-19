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

import android.content.Context;
import android.content.res.AssetManager;
import org.telegram.messenger.exoplayer.C;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * A local asset {@link UriDataSource}.
 */
public final class AssetDataSource implements UriDataSource {

  /**
   * Thrown when an {@link IOException} is encountered reading a local asset.
   */
  public static final class AssetDataSourceException extends IOException {

    public AssetDataSourceException(IOException cause) {
      super(cause);
    }

  }

  private final AssetManager assetManager;
  private final TransferListener listener;

  private String uriString;
  private InputStream inputStream;
  private long bytesRemaining;
  private boolean opened;

  /**
   * Constructs a new {@link DataSource} that retrieves data from a local asset.
   */
  public AssetDataSource(Context context) {
    this(context, null);
  }

  /**
   * Constructs a new {@link DataSource} that retrieves data from a local asset.
   *
   * @param listener An optional listener. Specify {@code null} for no listener.
   */
  public AssetDataSource(Context context, TransferListener listener) {
    this.assetManager = context.getAssets();
    this.listener = listener;
  }

  @Override
  public long open(DataSpec dataSpec) throws AssetDataSourceException {
    try {
      uriString = dataSpec.uri.toString();
      String path = dataSpec.uri.getPath();
      if (path.startsWith("/android_asset/")) {
        path = path.substring(15);
      } else if (path.startsWith("/")) {
        path = path.substring(1);
      }
      uriString = dataSpec.uri.toString();
      inputStream = assetManager.open(path, AssetManager.ACCESS_RANDOM);
      long skipped = inputStream.skip(dataSpec.position);
      if (skipped < dataSpec.position) {
        // assetManager.open() returns an AssetInputStream, whose skip() implementation only skips
        // fewer bytes than requested if the skip is beyond the end of the asset's data.
        throw new EOFException();
      }
      if (dataSpec.length != C.LENGTH_UNBOUNDED) {
        bytesRemaining = dataSpec.length;
      } else {
        bytesRemaining = inputStream.available();
        if (bytesRemaining == Integer.MAX_VALUE) {
          // assetManager.open() returns an AssetInputStream, whose available() implementation
          // returns Integer.MAX_VALUE if the remaining length is greater than (or equal to)
          // Integer.MAX_VALUE. We don't know the true length in this case, so treat as unbounded.
          bytesRemaining = C.LENGTH_UNBOUNDED;
        }
      }
    } catch (IOException e) {
      throw new AssetDataSourceException(e);
    }

    opened = true;
    if (listener != null) {
      listener.onTransferStart();
    }
    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws AssetDataSourceException {
    if (bytesRemaining == 0) {
      return -1;
    } else {
      int bytesRead = 0;
      try {
        int bytesToRead = bytesRemaining == C.LENGTH_UNBOUNDED ? readLength
            : (int) Math.min(bytesRemaining, readLength);
        bytesRead = inputStream.read(buffer, offset, bytesToRead);
      } catch (IOException e) {
        throw new AssetDataSourceException(e);
      }

      if (bytesRead > 0) {
        if (bytesRemaining != C.LENGTH_UNBOUNDED) {
          bytesRemaining -= bytesRead;
        }
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
  public void close() throws AssetDataSourceException {
    uriString = null;
    if (inputStream != null) {
      try {
        inputStream.close();
      } catch (IOException e) {
        throw new AssetDataSourceException(e);
      } finally {
        inputStream = null;
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
