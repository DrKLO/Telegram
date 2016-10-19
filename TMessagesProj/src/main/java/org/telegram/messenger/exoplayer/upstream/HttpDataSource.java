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

import android.text.TextUtils;
import org.telegram.messenger.exoplayer.util.Predicate;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * An HTTP specific extension to {@link UriDataSource}.
 */
public interface HttpDataSource extends UriDataSource {

  /**
   * A {@link Predicate} that rejects content types often used for pay-walls.
   */
  Predicate<String> REJECT_PAYWALL_TYPES = new Predicate<String>() {

    @Override
    public boolean evaluate(String contentType) {
      contentType = Util.toLowerInvariant(contentType);
      return !TextUtils.isEmpty(contentType)
          && (!contentType.contains("text") || contentType.contains("text/vtt"))
          && !contentType.contains("html") && !contentType.contains("xml");
    }

  };

  /**
   * Thrown when an error is encountered when trying to read from a {@link HttpDataSource}.
   */
  class HttpDataSourceException extends IOException {

    public static final int TYPE_OPEN = 1;
    public static final int TYPE_READ = 2;
    public static final int TYPE_CLOSE = 3;

    public final int type;

    /**
     * The {@link DataSpec} associated with the current connection.
     */
    public final DataSpec dataSpec;

    public HttpDataSourceException(DataSpec dataSpec, int type) {
      super();
      this.dataSpec = dataSpec;
      this.type = type;
    }

    public HttpDataSourceException(String message, DataSpec dataSpec, int type) {
      super(message);
      this.dataSpec = dataSpec;
      this.type = type;
    }

    public HttpDataSourceException(IOException cause, DataSpec dataSpec, int type) {
      super(cause);
      this.dataSpec = dataSpec;
      this.type = type;
    }

    public HttpDataSourceException(String message, IOException cause, DataSpec dataSpec, int type) {
      super(message, cause);
      this.dataSpec = dataSpec;
      this.type = type;
    }

  }

  /**
   * Thrown when the content type is invalid.
   */
  final class InvalidContentTypeException extends HttpDataSourceException {

    public final String contentType;

    public InvalidContentTypeException(String contentType, DataSpec dataSpec) {
      super("Invalid content type: " + contentType, dataSpec, TYPE_OPEN);
      this.contentType = contentType;
    }

  }

  /**
   * Thrown when an attempt to open a connection results in a response code not in the 2xx range.
   */
  final class InvalidResponseCodeException extends HttpDataSourceException {

    /**
     * The response code that was outside of the 2xx range.
     */
    public final int responseCode;

    /**
     * An unmodifiable map of the response header fields and values.
     */
    public final Map<String, List<String>> headerFields;

    public InvalidResponseCodeException(int responseCode, Map<String, List<String>> headerFields,
        DataSpec dataSpec) {
      super("Response code: " + responseCode, dataSpec, TYPE_OPEN);
      this.responseCode = responseCode;
      this.headerFields = headerFields;
    }

  }

  @Override
  long open(DataSpec dataSpec) throws HttpDataSourceException;

  @Override
  void close() throws HttpDataSourceException;

  @Override
  int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException;

  /**
   * Sets the value of a request header field. The value will be used for subsequent connections
   * established by the source.
   *
   * @param name The name of the header field.
   * @param value The value of the field.
   */
  void setRequestProperty(String name, String value);

  /**
   * Clears the value of a request header field. The change will apply to subsequent connections
   * established by the source.
   *
   * @param name The name of the header field.
   */
  void clearRequestProperty(String name);

  /**
   * Clears all request header fields that were set by {@link #setRequestProperty(String, String)}.
   */
  void clearAllRequestProperties();

  /**
   * Gets the headers provided in the response.
   *
   * @return The response headers, or {@code null} if response headers are unavailable.
   */
  Map<String, List<String>> getResponseHeaders();

}
