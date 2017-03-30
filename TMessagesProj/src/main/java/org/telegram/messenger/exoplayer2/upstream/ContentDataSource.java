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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import org.telegram.messenger.exoplayer2.C;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link DataSource} for reading from a content URI.
 */
public final class ContentDataSource implements DataSource {

  /**
   * Thrown when an {@link IOException} is encountered reading from a content URI.
   */
  public static class ContentDataSourceException extends IOException {

    public ContentDataSourceException(IOException cause) {
      super(cause);
    }

  }

  private final ContentResolver resolver;
  private final TransferListener<? super ContentDataSource> listener;

  private Uri uri;
  private AssetFileDescriptor assetFileDescriptor;
  private InputStream inputStream;
  private long bytesRemaining;
  private boolean opened;

  /**
   * @param context A context.
   */
  public ContentDataSource(Context context) {
    this(context, null);
  }

  /**
   * @param context A context.
   * @param listener An optional listener.
   */
  public ContentDataSource(Context context, TransferListener<? super ContentDataSource> listener) {
    this.resolver = context.getContentResolver();
    this.listener = listener;
  }

  @Override
  public long open(DataSpec dataSpec) throws ContentDataSourceException {
    try {
      uri = dataSpec.uri;
      assetFileDescriptor = resolver.openAssetFileDescriptor(uri, "r");
      inputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
      long skipped = inputStream.skip(dataSpec.position);
      if (skipped < dataSpec.position) {
        // We expect the skip to be satisfied in full. If it isn't then we're probably trying to
        // skip beyond the end of the data.
        throw new EOFException();
      }
      if (dataSpec.length != C.LENGTH_UNSET) {
        bytesRemaining = dataSpec.length;
      } else {
        bytesRemaining = inputStream.available();
        if (bytesRemaining == 0) {
          // FileInputStream.available() returns 0 if the remaining length cannot be determined, or
          // if it's greater than Integer.MAX_VALUE. We don't know the true length in either case,
          // so treat as unbounded.
          bytesRemaining = C.LENGTH_UNSET;
        }
      }
    } catch (IOException e) {
      throw new ContentDataSourceException(e);
    }

    opened = true;
    if (listener != null) {
      listener.onTransferStart(this, dataSpec);
    }

    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws ContentDataSourceException {
    if (readLength == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }

    int bytesRead;
    try {
      int bytesToRead = bytesRemaining == C.LENGTH_UNSET ? readLength
          : (int) Math.min(bytesRemaining, readLength);
      bytesRead = inputStream.read(buffer, offset, bytesToRead);
    } catch (IOException e) {
      throw new ContentDataSourceException(e);
    }

    if (bytesRead == -1) {
      if (bytesRemaining != C.LENGTH_UNSET) {
        // End of stream reached having not read sufficient data.
        throw new ContentDataSourceException(new EOFException());
      }
      return C.RESULT_END_OF_INPUT;
    }
    if (bytesRemaining != C.LENGTH_UNSET) {
      bytesRemaining -= bytesRead;
    }
    if (listener != null) {
      listener.onBytesTransferred(this, bytesRead);
    }
    return bytesRead;
  }

  @Override
  public Uri getUri() {
    return uri;
  }

  @Override
  public void close() throws ContentDataSourceException {
    uri = null;
    try {
      if (inputStream != null) {
        inputStream.close();
      }
    } catch (IOException e) {
      throw new ContentDataSourceException(e);
    } finally {
      inputStream = null;
      try {
        if (assetFileDescriptor != null) {
          assetFileDescriptor.close();
        }
      } catch (IOException e) {
        throw new ContentDataSourceException(e);
      } finally {
        assetFileDescriptor = null;
        if (opened) {
          opened = false;
          if (listener != null) {
            listener.onTransferEnd(this);
          }
        }
      }
    }
  }

}
