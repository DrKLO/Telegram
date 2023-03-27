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

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.util.Assertions;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/** A {@link DataSource} for reading from a local asset. */
public final class AssetDataSource extends BaseDataSource {

  /** Thrown when an {@link IOException} is encountered reading a local asset. */
  public static final class AssetDataSourceException extends DataSourceException {

    /**
     * @deprecated Use {@link #AssetDataSourceException(Throwable, int)}.
     */
    @Deprecated
    public AssetDataSourceException(IOException cause) {
      super(cause, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    /**
     * Creates a new instance.
     *
     * @param cause The error cause.
     * @param errorCode See {@link PlaybackException.ErrorCode}.
     */
    public AssetDataSourceException(
        @Nullable Throwable cause, @PlaybackException.ErrorCode int errorCode) {
      super(cause, errorCode);
    }
  }

  private final AssetManager assetManager;

  @Nullable private Uri uri;
  @Nullable private InputStream inputStream;
  private long bytesRemaining;
  private boolean opened;

  /**
   * @param context A context.
   */
  public AssetDataSource(Context context) {
    super(/* isNetwork= */ false);
    this.assetManager = context.getAssets();
  }

  @Override
  public long open(DataSpec dataSpec) throws AssetDataSourceException {
    try {
      uri = dataSpec.uri;
      String path = Assertions.checkNotNull(uri.getPath());
      if (path.startsWith("/android_asset/")) {
        path = path.substring(15);
      } else if (path.startsWith("/")) {
        path = path.substring(1);
      }
      transferInitializing(dataSpec);
      inputStream = assetManager.open(path, AssetManager.ACCESS_RANDOM);
      long skipped = inputStream.skip(dataSpec.position);
      if (skipped < dataSpec.position) {
        // assetManager.open() returns an AssetInputStream, whose skip() implementation only skips
        // fewer bytes than requested if the skip is beyond the end of the asset's data.
        throw new AssetDataSourceException(
            /* cause= */ null, PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
      }
      if (dataSpec.length != C.LENGTH_UNSET) {
        bytesRemaining = dataSpec.length;
      } else {
        bytesRemaining = inputStream.available();
        if (bytesRemaining == Integer.MAX_VALUE) {
          // assetManager.open() returns an AssetInputStream, whose available() implementation
          // returns Integer.MAX_VALUE if the remaining length is greater than (or equal to)
          // Integer.MAX_VALUE. We don't know the true length in this case, so treat as unbounded.
          bytesRemaining = C.LENGTH_UNSET;
        }
      }
    } catch (AssetDataSourceException e) {
      throw e;
    } catch (IOException e) {
      throw new AssetDataSourceException(
          e,
          e instanceof FileNotFoundException
              ? PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
              : PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    opened = true;
    transferStarted(dataSpec);
    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws AssetDataSourceException {
    if (length == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }

    int bytesRead;
    try {
      int bytesToRead =
          bytesRemaining == C.LENGTH_UNSET ? length : (int) min(bytesRemaining, length);
      bytesRead = castNonNull(inputStream).read(buffer, offset, bytesToRead);
    } catch (IOException e) {
      throw new AssetDataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    if (bytesRead == -1) {
      return C.RESULT_END_OF_INPUT;
    }
    if (bytesRemaining != C.LENGTH_UNSET) {
      bytesRemaining -= bytesRead;
    }
    bytesTransferred(bytesRead);
    return bytesRead;
  }

  @Override
  @Nullable
  public Uri getUri() {
    return uri;
  }

  @Override
  public void close() throws AssetDataSourceException {
    uri = null;
    try {
      if (inputStream != null) {
        inputStream.close();
      }
    } catch (IOException e) {
      throw new AssetDataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    } finally {
      inputStream = null;
      if (opened) {
        opened = false;
        transferEnded();
      }
    }
  }
}
