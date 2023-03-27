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
import android.content.res.Resources;
import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.util.Assertions;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

/**
 * A {@link DataSource} for reading a raw resource inside the APK.
 *
 * <p>URIs supported by this source are of one of the forms:
 *
 * <ul>
 *   <li>{@code rawresource:///id}, where {@code id} is the integer identifier of a raw resource.
 *   <li>{@code android.resource:///id}, where {@code id} is the integer identifier of a raw
 *       resource.
 *   <li>{@code android.resource://[package]/[type/]name}, where {@code package} is the name of the
 *       package in which the resource is located, {@code type} is the resource type and {@code
 *       name} is the resource name. The package and the type are optional. Their default value is
 *       the package of this application and "raw", respectively. Using the two other forms is more
 *       efficient.
 * </ul>
 *
 * <p>{@link #buildRawResourceUri(int)} can be used to build supported {@link Uri}s.
 */
public final class RawResourceDataSource extends BaseDataSource {

  /** Thrown when an {@link IOException} is encountered reading from a raw resource. */
  public static class RawResourceDataSourceException extends DataSourceException {
    /**
     * @deprecated Use {@link #RawResourceDataSourceException(String, Throwable, int)}.
     */
    @Deprecated
    public RawResourceDataSourceException(String message) {
      super(message, /* cause= */ null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    /**
     * @deprecated Use {@link #RawResourceDataSourceException(String, Throwable, int)}.
     */
    @Deprecated
    public RawResourceDataSourceException(Throwable cause) {
      super(cause, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    /** Creates a new instance. */
    public RawResourceDataSourceException(
        @Nullable String message,
        @Nullable Throwable cause,
        @PlaybackException.ErrorCode int errorCode) {
      super(message, cause, errorCode);
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

  /** The scheme part of a raw resource URI. */
  public static final String RAW_RESOURCE_SCHEME = "rawresource";

  private final Resources resources;
  private final String packageName;

  @Nullable private Uri uri;
  @Nullable private AssetFileDescriptor assetFileDescriptor;
  @Nullable private InputStream inputStream;
  private long bytesRemaining;
  private boolean opened;

  /**
   * @param context A context.
   */
  public RawResourceDataSource(Context context) {
    super(/* isNetwork= */ false);
    this.resources = context.getResources();
    this.packageName = context.getPackageName();
  }

  @Override
  public long open(DataSpec dataSpec) throws RawResourceDataSourceException {
    Uri uri = dataSpec.uri;
    this.uri = uri;

    int resourceId;
    if (TextUtils.equals(RAW_RESOURCE_SCHEME, uri.getScheme())
        || (TextUtils.equals(ContentResolver.SCHEME_ANDROID_RESOURCE, uri.getScheme())
            && uri.getPathSegments().size() == 1
            && Assertions.checkNotNull(uri.getLastPathSegment()).matches("\\d+"))) {
      try {
        resourceId = Integer.parseInt(Assertions.checkNotNull(uri.getLastPathSegment()));
      } catch (NumberFormatException e) {
        throw new RawResourceDataSourceException(
            "Resource identifier must be an integer.",
            /* cause= */ null,
            PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK);
      }
    } else if (TextUtils.equals(ContentResolver.SCHEME_ANDROID_RESOURCE, uri.getScheme())) {
      String path = Assertions.checkNotNull(uri.getPath());
      if (path.startsWith("/")) {
        path = path.substring(1);
      }
      @Nullable String host = uri.getHost();
      String resourceName = (TextUtils.isEmpty(host) ? "" : (host + ":")) + path;
      resourceId =
          resources.getIdentifier(
              resourceName, /* defType= */ "raw", /* defPackage= */ packageName);
      if (resourceId == 0) {
        throw new RawResourceDataSourceException(
            "Resource not found.",
            /* cause= */ null,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND);
      }
    } else {
      throw new RawResourceDataSourceException(
          "URI must either use scheme "
              + RAW_RESOURCE_SCHEME
              + " or "
              + ContentResolver.SCHEME_ANDROID_RESOURCE,
          /* cause= */ null,
          PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK);
    }

    transferInitializing(dataSpec);

    AssetFileDescriptor assetFileDescriptor;
    try {
      assetFileDescriptor = resources.openRawResourceFd(resourceId);
    } catch (Resources.NotFoundException e) {
      throw new RawResourceDataSourceException(
          /* message= */ null, e, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND);
    }

    this.assetFileDescriptor = assetFileDescriptor;
    if (assetFileDescriptor == null) {
      throw new RawResourceDataSourceException(
          "Resource is compressed: " + uri,
          /* cause= */ null,
          PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    long assetFileDescriptorLength = assetFileDescriptor.getLength();
    FileInputStream inputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
    this.inputStream = inputStream;

    try {
      // We can't rely only on the "skipped < dataSpec.position" check below to detect whether the
      // position is beyond the end of the resource being read. This is because the file will
      // typically contain multiple resources, and there's nothing to prevent InputStream.skip()
      // from succeeding by skipping into the data of the next resource. Hence we also need to check
      // against the resource length explicitly, which is guaranteed to be set unless the resource
      // extends to the end of the file.
      if (assetFileDescriptorLength != AssetFileDescriptor.UNKNOWN_LENGTH
          && dataSpec.position > assetFileDescriptorLength) {
        throw new RawResourceDataSourceException(
            /* message= */ null,
            /* cause= */ null,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
      }
      long assetFileDescriptorOffset = assetFileDescriptor.getStartOffset();
      long skipped =
          inputStream.skip(assetFileDescriptorOffset + dataSpec.position)
              - assetFileDescriptorOffset;
      if (skipped != dataSpec.position) {
        // We expect the skip to be satisfied in full. If it isn't then we're probably trying to
        // read beyond the end of the last resource in the file.
        throw new RawResourceDataSourceException(
            /* message= */ null,
            /* cause= */ null,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
      }
      if (assetFileDescriptorLength == AssetFileDescriptor.UNKNOWN_LENGTH) {
        // The asset must extend to the end of the file. We can try and resolve the length with
        // FileInputStream.getChannel().size().
        FileChannel channel = inputStream.getChannel();
        if (channel.size() == 0) {
          bytesRemaining = C.LENGTH_UNSET;
        } else {
          bytesRemaining = channel.size() - channel.position();
          if (bytesRemaining < 0) {
            // The skip above was satisfied in full, but skipped beyond the end of the file.
            throw new RawResourceDataSourceException(
                /* message= */ null,
                /* cause= */ null,
                PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
          }
        }
      } else {
        bytesRemaining = assetFileDescriptorLength - skipped;
        if (bytesRemaining < 0) {
          throw new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
        }
      }
    } catch (RawResourceDataSourceException e) {
      throw e;
    } catch (IOException e) {
      throw new RawResourceDataSourceException(
          /* message= */ null, e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
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
  public int read(byte[] buffer, int offset, int length) throws RawResourceDataSourceException {
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
      throw new RawResourceDataSourceException(
          /* message= */ null, e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    if (bytesRead == -1) {
      if (bytesRemaining != C.LENGTH_UNSET) {
        // End of stream reached having not read sufficient data.
        throw new RawResourceDataSourceException(
            "End of stream reached having not read sufficient data.",
            new EOFException(),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
      }
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
  public void close() throws RawResourceDataSourceException {
    uri = null;
    try {
      if (inputStream != null) {
        inputStream.close();
      }
    } catch (IOException e) {
      throw new RawResourceDataSourceException(
          /* message= */ null, e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    } finally {
      inputStream = null;
      try {
        if (assetFileDescriptor != null) {
          assetFileDescriptor.close();
        }
      } catch (IOException e) {
        throw new RawResourceDataSourceException(
            /* message= */ null, e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
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
