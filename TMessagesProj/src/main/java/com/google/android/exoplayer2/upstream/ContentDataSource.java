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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;

/** A {@link DataSource} for reading from a content URI. */
public final class ContentDataSource extends BaseDataSource {

  /** Thrown when an {@link IOException} is encountered reading from a content URI. */
  public static class ContentDataSourceException extends DataSourceException {

    /**
     * @deprecated Use {@link #ContentDataSourceException(IOException, int)}.
     */
    @Deprecated
    public ContentDataSourceException(IOException cause) {
      this(cause, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    /** Creates a new instance. */
    public ContentDataSourceException(
        @Nullable IOException cause, @PlaybackException.ErrorCode int errorCode) {
      super(cause, errorCode);
    }
  }

  private final ContentResolver resolver;

  @Nullable private Uri uri;
  @Nullable private AssetFileDescriptor assetFileDescriptor;
  @Nullable private FileInputStream inputStream;
  private long bytesRemaining;
  private boolean opened;

  /**
   * @param context A context.
   */
  public ContentDataSource(Context context) {
    super(/* isNetwork= */ false);
    this.resolver = context.getContentResolver();
  }

  @Override
  @SuppressWarnings("InlinedApi") // We are inlining EXTRA_ACCEPT_ORIGINAL_MEDIA_FORMAT.
  public long open(DataSpec dataSpec) throws ContentDataSourceException {
    try {
      Uri uri = dataSpec.uri;
      this.uri = uri;

      transferInitializing(dataSpec);

      AssetFileDescriptor assetFileDescriptor;
      if ("content".equals(dataSpec.uri.getScheme())) {
        Bundle providerOptions = new Bundle();
        // We don't want compatible media transcoding.
        providerOptions.putBoolean(MediaStore.EXTRA_ACCEPT_ORIGINAL_MEDIA_FORMAT, true);
        assetFileDescriptor =
            resolver.openTypedAssetFileDescriptor(uri, /* mimeType= */ "*/*", providerOptions);
      } else {
        // This path supports file URIs, although support may be removed in the future. See
        // [Internal ref: b/195384732].
        assetFileDescriptor = resolver.openAssetFileDescriptor(uri, "r");
      }
      this.assetFileDescriptor = assetFileDescriptor;
      if (assetFileDescriptor == null) {
        // assetFileDescriptor may be null if the provider recently crashed.
        throw new ContentDataSourceException(
            new IOException("Could not open file descriptor for: " + uri),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
      }

      long assetFileDescriptorLength = assetFileDescriptor.getLength();
      FileInputStream inputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
      this.inputStream = inputStream;

      // We can't rely only on the "skipped < dataSpec.position" check below to detect whether the
      // position is beyond the end of the asset being read. This is because the file may contain
      // multiple assets, and there's nothing to prevent InputStream.skip() from succeeding by
      // skipping into the data of the next asset. Hence we also need to check against the asset
      // length explicitly, which is guaranteed to be set unless the asset extends to the end of the
      // file.
      if (assetFileDescriptorLength != AssetFileDescriptor.UNKNOWN_LENGTH
          && dataSpec.position > assetFileDescriptorLength) {
        throw new ContentDataSourceException(
            /* cause= */ null, PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
      }
      long assetFileDescriptorOffset = assetFileDescriptor.getStartOffset();
      long skipped =
          inputStream.skip(assetFileDescriptorOffset + dataSpec.position)
              - assetFileDescriptorOffset;
      if (skipped != dataSpec.position) {
        // We expect the skip to be satisfied in full. If it isn't then we're probably trying to
        // read beyond the end of the last resource in the file.
        throw new ContentDataSourceException(
            /* cause= */ null, PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
      }
      if (assetFileDescriptorLength == AssetFileDescriptor.UNKNOWN_LENGTH) {
        // The asset must extend to the end of the file. We can try and resolve the length with
        // FileInputStream.getChannel().size().
        FileChannel channel = inputStream.getChannel();
        long channelSize = channel.size();
        if (channelSize == 0) {
          bytesRemaining = C.LENGTH_UNSET;
        } else {
          bytesRemaining = channelSize - channel.position();
          if (bytesRemaining < 0) {
            // The skip above was satisfied in full, but skipped beyond the end of the file.
            throw new ContentDataSourceException(
                /* cause= */ null, PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
          }
        }
      } else {
        bytesRemaining = assetFileDescriptorLength - skipped;
        if (bytesRemaining < 0) {
          throw new ContentDataSourceException(
              /* cause= */ null, PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
        }
      }
    } catch (ContentDataSourceException e) {
      throw e;
    } catch (IOException e) {
      throw new ContentDataSourceException(
          e,
          e instanceof FileNotFoundException
              ? PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
              : PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    if (dataSpec.length != C.LENGTH_UNSET) {
      bytesRemaining =
          bytesRemaining == C.LENGTH_UNSET ? dataSpec.length : min(bytesRemaining, dataSpec.length);
    }
    opened = true;
    transferStarted(dataSpec);
    return dataSpec.length != C.LENGTH_UNSET ? dataSpec.length : bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws ContentDataSourceException {
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
      throw new ContentDataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
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

  @SuppressWarnings("Finally")
  @Override
  public void close() throws ContentDataSourceException {
    uri = null;
    try {
      if (inputStream != null) {
        inputStream.close();
      }
    } catch (IOException e) {
      throw new ContentDataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    } finally {
      inputStream = null;
      try {
        if (assetFileDescriptor != null) {
          assetFileDescriptor.close();
        }
      } catch (IOException e) {
        throw new ContentDataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
      } finally {
        assetFileDescriptor = null;
        if (opened) {
          opened = false;
          transferEnded();
        }
      }
    }
  }
}
