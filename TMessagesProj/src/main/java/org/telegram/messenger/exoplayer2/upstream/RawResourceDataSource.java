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

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.net.Uri;
import android.text.TextUtils;
import org.telegram.messenger.exoplayer2.C;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link DataSource} for reading a raw resource inside the APK.
 * <p>
 * URIs supported by this source are of the form {@code rawresource:///rawResourceId}, where
 * rawResourceId is the integer identifier of a raw resource. {@link #buildRawResourceUri(int)} can
 * be used to build {@link Uri}s in this format.
 */
public final class RawResourceDataSource implements DataSource {

  /**
   * Thrown when an {@link IOException} is encountered reading from a raw resource.
   */
  public static class RawResourceDataSourceException extends IOException {
    public RawResourceDataSourceException(String message) {
      super(message);
    }

    public RawResourceDataSourceException(IOException e) {
      super(e);
    }
  }

  /**
   * Builds a {@link Uri} for the specified raw resource identifier.
   *
   * @param rawResourceId A raw resource identifier (i.e. a constant defined in {@code R.raw}).
   * @return The corresponding {@link Uri}.
   */
  public static Uri buildRawResourceUri(int rawResourceId) {
    return Uri.parse(RAW_RESOURCE_SCHEME + ":///" + rawResourceId);
  }

  private static final String RAW_RESOURCE_SCHEME = "rawresource";

  private final Resources resources;
  private final TransferListener<? super RawResourceDataSource> listener;

  private Uri uri;
  private AssetFileDescriptor assetFileDescriptor;
  private InputStream inputStream;
  private long bytesRemaining;
  private boolean opened;

  /**
   * @param context A context.
   */
  public RawResourceDataSource(Context context) {
    this(context, null);
  }

  /**
   * @param context A context.
   * @param listener An optional listener.
   */
  public RawResourceDataSource(Context context,
      TransferListener<? super RawResourceDataSource> listener) {
    this.resources = context.getResources();
    this.listener = listener;
  }

  @Override
  public long open(DataSpec dataSpec) throws RawResourceDataSourceException {
    try {
      uri = dataSpec.uri;
      if (!TextUtils.equals(RAW_RESOURCE_SCHEME, uri.getScheme())) {
        throw new RawResourceDataSourceException("URI must use scheme " + RAW_RESOURCE_SCHEME);
      }

      int resourceId;
      try {
        resourceId = Integer.parseInt(uri.getLastPathSegment());
      } catch (NumberFormatException e) {
        throw new RawResourceDataSourceException("Resource identifier must be an integer.");
      }

      assetFileDescriptor = resources.openRawResourceFd(resourceId);
      inputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
      inputStream.skip(assetFileDescriptor.getStartOffset());
      long skipped = inputStream.skip(dataSpec.position);
      if (skipped < dataSpec.position) {
        // We expect the skip to be satisfied in full. If it isn't then we're probably trying to
        // skip beyond the end of the data.
        throw new EOFException();
      }
      if (dataSpec.length != C.LENGTH_UNSET) {
        bytesRemaining = dataSpec.length;
      } else {
        long assetFileDescriptorLength = assetFileDescriptor.getLength();
        // If the length is UNKNOWN_LENGTH then the asset extends to the end of the file.
        bytesRemaining = assetFileDescriptorLength == AssetFileDescriptor.UNKNOWN_LENGTH
            ? C.LENGTH_UNSET : (assetFileDescriptorLength - dataSpec.position);
      }
    } catch (IOException e) {
      throw new RawResourceDataSourceException(e);
    }

    opened = true;
    if (listener != null) {
      listener.onTransferStart(this, dataSpec);
    }

    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws RawResourceDataSourceException {
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
      throw new RawResourceDataSourceException(e);
    }

    if (bytesRead == -1) {
      if (bytesRemaining != C.LENGTH_UNSET) {
        // End of stream reached having not read sufficient data.
        throw new RawResourceDataSourceException(new EOFException());
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
  public void close() throws RawResourceDataSourceException {
    uri = null;
    try {
      if (inputStream != null) {
        inputStream.close();
      }
    } catch (IOException e) {
      throw new RawResourceDataSourceException(e);
    } finally {
      inputStream = null;
      try {
        if (assetFileDescriptor != null) {
          assetFileDescriptor.close();
        }
      } catch (IOException e) {
        throw new RawResourceDataSourceException(e);
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
