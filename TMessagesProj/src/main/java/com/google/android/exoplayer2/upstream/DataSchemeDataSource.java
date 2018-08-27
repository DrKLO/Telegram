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

import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Base64;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.net.URLDecoder;

/** A {@link DataSource} for reading data URLs, as defined by RFC 2397. */
public final class DataSchemeDataSource extends BaseDataSource {

  public static final String SCHEME_DATA = "data";

  private @Nullable DataSpec dataSpec;
  private int bytesRead;
  private @Nullable byte[] data;

  public DataSchemeDataSource() {
    super(/* isNetwork= */ false);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    transferInitializing(dataSpec);
    this.dataSpec = dataSpec;
    Uri uri = dataSpec.uri;
    String scheme = uri.getScheme();
    if (!SCHEME_DATA.equals(scheme)) {
      throw new ParserException("Unsupported scheme: " + scheme);
    }
    String[] uriParts = Util.split(uri.getSchemeSpecificPart(), ",");
    if (uriParts.length != 2) {
      throw new ParserException("Unexpected URI format: " + uri);
    }
    String dataString = uriParts[1];
    if (uriParts[0].contains(";base64")) {
      try {
        data = Base64.decode(dataString, 0);
      } catch (IllegalArgumentException e) {
        throw new ParserException("Error while parsing Base64 encoded string: " + dataString, e);
      }
    } else {
      // TODO: Add support for other charsets.
      data = URLDecoder.decode(dataString, C.ASCII_NAME).getBytes();
    }
    transferStarted(dataSpec);
    return data.length;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) {
    if (readLength == 0) {
      return 0;
    }
    int remainingBytes = data.length - bytesRead;
    if (remainingBytes == 0) {
      return C.RESULT_END_OF_INPUT;
    }
    readLength = Math.min(readLength, remainingBytes);
    System.arraycopy(data, bytesRead, buffer, offset, readLength);
    bytesRead += readLength;
    bytesTransferred(readLength);
    return readLength;
  }

  @Override
  public @Nullable Uri getUri() {
    return dataSpec != null ? dataSpec.uri : null;
  }

  @Override
  public void close() throws IOException {
    if (data != null) {
      data = null;
      transferEnded();
    }
    dataSpec = null;
  }

}
