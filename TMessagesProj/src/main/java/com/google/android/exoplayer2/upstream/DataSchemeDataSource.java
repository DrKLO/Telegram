/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.util.Base64;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.net.URLDecoder;

/** A {@link DataSource} for reading data URLs, as defined by RFC 2397. */
public final class DataSchemeDataSource extends BaseDataSource {

  public static final String SCHEME_DATA = "data";

  @Nullable private DataSpec dataSpec;
  @Nullable private byte[] data;
  private int readPosition;
  private int bytesRemaining;

  public DataSchemeDataSource() {
    super(/* isNetwork= */ false);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    transferInitializing(dataSpec);
    this.dataSpec = dataSpec;
    Uri uri = dataSpec.uri;
    String scheme = uri.getScheme();
    Assertions.checkArgument(SCHEME_DATA.equals(scheme), "Unsupported scheme: " + scheme);
    String[] uriParts = Util.split(uri.getSchemeSpecificPart(), ",");
    if (uriParts.length != 2) {
      throw ParserException.createForMalformedDataOfUnknownType(
          "Unexpected URI format: " + uri, /* cause= */ null);
    }
    String dataString = uriParts[1];
    if (uriParts[0].contains(";base64")) {
      try {
        data = Base64.decode(dataString, /* flags= */ Base64.DEFAULT);
      } catch (IllegalArgumentException e) {
        throw ParserException.createForMalformedDataOfUnknownType(
            "Error while parsing Base64 encoded string: " + dataString, e);
      }
    } else {
      // TODO: Add support for other charsets.
      data = Util.getUtf8Bytes(URLDecoder.decode(dataString, Charsets.US_ASCII.name()));
    }
    if (dataSpec.position > data.length) {
      data = null;
      throw new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
    }
    readPosition = (int) dataSpec.position;
    bytesRemaining = data.length - readPosition;
    if (dataSpec.length != C.LENGTH_UNSET) {
      bytesRemaining = (int) min(bytesRemaining, dataSpec.length);
    }
    transferStarted(dataSpec);
    return dataSpec.length != C.LENGTH_UNSET ? dataSpec.length : bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) {
    if (length == 0) {
      return 0;
    }
    if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }
    length = min(length, bytesRemaining);
    System.arraycopy(castNonNull(data), readPosition, buffer, offset, length);
    readPosition += length;
    bytesRemaining -= length;
    bytesTransferred(length);
    return length;
  }

  @Override
  @Nullable
  public Uri getUri() {
    return dataSpec != null ? dataSpec.uri : null;
  }

  @Override
  public void close() {
    if (data != null) {
      data = null;
      transferEnded();
    }
    dataSpec = null;
  }
}
