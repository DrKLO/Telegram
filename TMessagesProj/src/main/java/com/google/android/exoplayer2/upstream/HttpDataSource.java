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

import static java.lang.annotation.ElementType.TYPE_USE;

import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Ascii;
import com.google.common.base.Predicate;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An HTTP {@link DataSource}. */
public interface HttpDataSource extends DataSource {

  /** A factory for {@link HttpDataSource} instances. */
  interface Factory extends DataSource.Factory {

    @Override
    HttpDataSource createDataSource();

    /**
     * Sets the default request headers for {@link HttpDataSource} instances created by the factory.
     *
     * <p>The new request properties will be used for future requests made by {@link HttpDataSource
     * HttpDataSources} created by the factory, including instances that have already been created.
     * Modifying the {@code defaultRequestProperties} map after a call to this method will have no
     * effect, and so it's necessary to call this method again each time the request properties need
     * to be updated.
     *
     * @param defaultRequestProperties The default request properties.
     * @return This factory.
     */
    Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties);
  }

  /**
   * Stores HTTP request properties (aka HTTP headers) and provides methods to modify the headers in
   * a thread safe way to avoid the potential of creating snapshots of an inconsistent or unintended
   * state.
   */
  final class RequestProperties {

    private final Map<String, String> requestProperties;
    @Nullable private Map<String, String> requestPropertiesSnapshot;

    public RequestProperties() {
      requestProperties = new HashMap<>();
    }

    /**
     * Sets the specified property {@code value} for the specified {@code name}. If a property for
     * this name previously existed, the old value is replaced by the specified value.
     *
     * @param name The name of the request property.
     * @param value The value of the request property.
     */
    public synchronized void set(String name, String value) {
      requestPropertiesSnapshot = null;
      requestProperties.put(name, value);
    }

    /**
     * Sets the keys and values contained in the map. If a property previously existed, the old
     * value is replaced by the specified value. If a property previously existed and is not in the
     * map, the property is left unchanged.
     *
     * @param properties The request properties.
     */
    public synchronized void set(Map<String, String> properties) {
      requestPropertiesSnapshot = null;
      requestProperties.putAll(properties);
    }

    /**
     * Removes all properties previously existing and sets the keys and values of the map.
     *
     * @param properties The request properties.
     */
    public synchronized void clearAndSet(Map<String, String> properties) {
      requestPropertiesSnapshot = null;
      requestProperties.clear();
      requestProperties.putAll(properties);
    }

    /**
     * Removes a request property by name.
     *
     * @param name The name of the request property to remove.
     */
    public synchronized void remove(String name) {
      requestPropertiesSnapshot = null;
      requestProperties.remove(name);
    }

    /** Clears all request properties. */
    public synchronized void clear() {
      requestPropertiesSnapshot = null;
      requestProperties.clear();
    }

    /**
     * Gets a snapshot of the request properties.
     *
     * @return A snapshot of the request properties.
     */
    public synchronized Map<String, String> getSnapshot() {
      if (requestPropertiesSnapshot == null) {
        requestPropertiesSnapshot = Collections.unmodifiableMap(new HashMap<>(requestProperties));
      }
      return requestPropertiesSnapshot;
    }
  }

  /** Base implementation of {@link Factory} that sets default request properties. */
  abstract class BaseFactory implements Factory {

    private final RequestProperties defaultRequestProperties;

    public BaseFactory() {
      defaultRequestProperties = new RequestProperties();
    }

    @Override
    public final HttpDataSource createDataSource() {
      return createDataSourceInternal(defaultRequestProperties);
    }

    @CanIgnoreReturnValue
    @Override
    public final Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
      this.defaultRequestProperties.clearAndSet(defaultRequestProperties);
      return this;
    }

    /**
     * Called by {@link #createDataSource()} to create a {@link HttpDataSource} instance.
     *
     * @param defaultRequestProperties The default {@code RequestProperties} to be used by the
     *     {@link HttpDataSource} instance.
     * @return A {@link HttpDataSource} instance.
     */
    protected abstract HttpDataSource createDataSourceInternal(
        RequestProperties defaultRequestProperties);
  }

  /** A {@link Predicate} that rejects content types often used for pay-walls. */
  Predicate<String> REJECT_PAYWALL_TYPES =
      contentType -> {
        if (contentType == null) {
          return false;
        }
        contentType = Ascii.toLowerCase(contentType);
        return !TextUtils.isEmpty(contentType)
            && (!contentType.contains("text") || contentType.contains("text/vtt"))
            && !contentType.contains("html")
            && !contentType.contains("xml");
      };

  /** Thrown when an error is encountered when trying to read from a {@link HttpDataSource}. */
  class HttpDataSourceException extends DataSourceException {

    /**
     * The type of operation that produced the error. One of {@link #TYPE_READ}, {@link #TYPE_OPEN}
     * {@link #TYPE_CLOSE}.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef({TYPE_OPEN, TYPE_READ, TYPE_CLOSE})
    public @interface Type {}

    /** The error occurred reading data from a {@code HttpDataSource}. */
    public static final int TYPE_OPEN = 1;
    /** The error occurred in opening a {@code HttpDataSource}. */
    public static final int TYPE_READ = 2;
    /** The error occurred in closing a {@code HttpDataSource}. */
    public static final int TYPE_CLOSE = 3;

    /**
     * Returns a {@code HttpDataSourceException} whose error code is assigned according to the cause
     * and type.
     */
    public static HttpDataSourceException createForIOException(
        IOException cause, DataSpec dataSpec, @Type int type) {
      @PlaybackException.ErrorCode int errorCode;
      @Nullable String message = cause.getMessage();
      if (cause instanceof SocketTimeoutException) {
        errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT;
      } else if (cause instanceof InterruptedIOException) {
        // An interruption means the operation is being cancelled, in which case this exception
        // should not cause the player to fail. If it does, it likely means that the owner of the
        // operation is failing to swallow the interruption, which makes us enter an invalid state.
        errorCode = PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK;
      } else if (message != null
          && Ascii.toLowerCase(message).matches("cleartext.*not permitted.*")) {
        errorCode = PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED;
      } else {
        errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED;
      }
      return errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
          ? new CleartextNotPermittedException(cause, dataSpec)
          : new HttpDataSourceException(cause, dataSpec, errorCode, type);
    }

    /** The {@link DataSpec} associated with the current connection. */
    public final DataSpec dataSpec;

    public final @Type int type;

    /**
     * @deprecated Use {@link #HttpDataSourceException(DataSpec, int, int)
     *     HttpDataSourceException(DataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, int)}.
     */
    @Deprecated
    public HttpDataSourceException(DataSpec dataSpec, @Type int type) {
      this(dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, type);
    }

    /**
     * Constructs an HttpDataSourceException.
     *
     * @param dataSpec The {@link DataSpec}.
     * @param errorCode Reason of the error, should be one of the {@code ERROR_CODE_IO_*} in {@link
     *     PlaybackException.ErrorCode}.
     * @param type See {@link Type}.
     */
    public HttpDataSourceException(
        DataSpec dataSpec, @PlaybackException.ErrorCode int errorCode, @Type int type) {
      super(assignErrorCode(errorCode, type));
      this.dataSpec = dataSpec;
      this.type = type;
    }

    /**
     * @deprecated Use {@link #HttpDataSourceException(String, DataSpec, int, int)
     *     HttpDataSourceException(String, DataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
     *     int)}.
     */
    @Deprecated
    public HttpDataSourceException(String message, DataSpec dataSpec, @Type int type) {
      this(message, dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, type);
    }

    /**
     * Constructs an HttpDataSourceException.
     *
     * @param message The error message.
     * @param dataSpec The {@link DataSpec}.
     * @param errorCode Reason of the error, should be one of the {@code ERROR_CODE_IO_*} in {@link
     *     PlaybackException.ErrorCode}.
     * @param type See {@link Type}.
     */
    public HttpDataSourceException(
        String message,
        DataSpec dataSpec,
        @PlaybackException.ErrorCode int errorCode,
        @Type int type) {
      super(message, assignErrorCode(errorCode, type));
      this.dataSpec = dataSpec;
      this.type = type;
    }

    /**
     * @deprecated Use {@link #HttpDataSourceException(IOException, DataSpec, int, int)
     *     HttpDataSourceException(IOException, DataSpec,
     *     PlaybackException.ERROR_CODE_IO_UNSPECIFIED, int)}.
     */
    @Deprecated
    public HttpDataSourceException(IOException cause, DataSpec dataSpec, @Type int type) {
      this(cause, dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, type);
    }

    /**
     * Constructs an HttpDataSourceException.
     *
     * @param cause The error cause.
     * @param dataSpec The {@link DataSpec}.
     * @param errorCode Reason of the error, should be one of the {@code ERROR_CODE_IO_*} in {@link
     *     PlaybackException.ErrorCode}.
     * @param type See {@link Type}.
     */
    public HttpDataSourceException(
        IOException cause,
        DataSpec dataSpec,
        @PlaybackException.ErrorCode int errorCode,
        @Type int type) {
      super(cause, assignErrorCode(errorCode, type));
      this.dataSpec = dataSpec;
      this.type = type;
    }

    /**
     * @deprecated Use {@link #HttpDataSourceException(String, IOException, DataSpec, int, int)
     *     HttpDataSourceException(String, IOException, DataSpec,
     *     PlaybackException.ERROR_CODE_IO_UNSPECIFIED, int)}.
     */
    @Deprecated
    public HttpDataSourceException(
        String message, IOException cause, DataSpec dataSpec, @Type int type) {
      this(message, cause, dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, type);
    }

    /**
     * Constructs an HttpDataSourceException.
     *
     * @param message The error message.
     * @param cause The error cause.
     * @param dataSpec The {@link DataSpec}.
     * @param errorCode Reason of the error, should be one of the {@code ERROR_CODE_IO_*} in {@link
     *     PlaybackException.ErrorCode}.
     * @param type See {@link Type}.
     */
    public HttpDataSourceException(
        String message,
        @Nullable IOException cause,
        DataSpec dataSpec,
        @PlaybackException.ErrorCode int errorCode,
        @Type int type) {
      super(message, cause, assignErrorCode(errorCode, type));
      this.dataSpec = dataSpec;
      this.type = type;
    }

    private static @PlaybackException.ErrorCode int assignErrorCode(
        @PlaybackException.ErrorCode int errorCode, @Type int type) {
      return errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED && type == TYPE_OPEN
          ? PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
          : errorCode;
    }
  }

  /**
   * Thrown when cleartext HTTP traffic is not permitted. For more information including how to
   * enable cleartext traffic, see the <a
   * href="https://exoplayer.dev/issues/cleartext-not-permitted">corresponding troubleshooting
   * topic</a>.
   */
  final class CleartextNotPermittedException extends HttpDataSourceException {

    public CleartextNotPermittedException(IOException cause, DataSpec dataSpec) {
      super(
          "Cleartext HTTP traffic not permitted. See"
              + " https://exoplayer.dev/issues/cleartext-not-permitted",
          cause,
          dataSpec,
          PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
          TYPE_OPEN);
    }
  }

  /** Thrown when the content type is invalid. */
  final class InvalidContentTypeException extends HttpDataSourceException {

    public final String contentType;

    public InvalidContentTypeException(String contentType, DataSpec dataSpec) {
      super(
          "Invalid content type: " + contentType,
          dataSpec,
          PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
          TYPE_OPEN);
      this.contentType = contentType;
    }
  }

  /**
   * Thrown when an attempt to open a connection results in a response code not in the 2xx range.
   */
  final class InvalidResponseCodeException extends HttpDataSourceException {

    /** The response code that was outside of the 2xx range. */
    public final int responseCode;

    /** The http status message. */
    @Nullable public final String responseMessage;

    /** An unmodifiable map of the response header fields and values. */
    public final Map<String, List<String>> headerFields;

    /** The response body. */
    public final byte[] responseBody;

    /**
     * @deprecated Use {@link #InvalidResponseCodeException(int, String, IOException, Map, DataSpec,
     *     byte[])}.
     */
    @Deprecated
    public InvalidResponseCodeException(
        int responseCode, Map<String, List<String>> headerFields, DataSpec dataSpec) {
      this(
          responseCode,
          /* responseMessage= */ null,
          /* cause= */ null,
          headerFields,
          dataSpec,
          /* responseBody= */ Util.EMPTY_BYTE_ARRAY);
    }

    /**
     * @deprecated Use {@link #InvalidResponseCodeException(int, String, IOException, Map, DataSpec,
     *     byte[])}.
     */
    @Deprecated
    public InvalidResponseCodeException(
        int responseCode,
        @Nullable String responseMessage,
        Map<String, List<String>> headerFields,
        DataSpec dataSpec) {
      this(
          responseCode,
          responseMessage,
          /* cause= */ null,
          headerFields,
          dataSpec,
          /* responseBody= */ Util.EMPTY_BYTE_ARRAY);
    }

    public InvalidResponseCodeException(
        int responseCode,
        @Nullable String responseMessage,
        @Nullable IOException cause,
        Map<String, List<String>> headerFields,
        DataSpec dataSpec,
        byte[] responseBody) {
      super(
          "Response code: " + responseCode,
          cause,
          dataSpec,
          PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
          TYPE_OPEN);
      this.responseCode = responseCode;
      this.responseMessage = responseMessage;
      this.headerFields = headerFields;
      this.responseBody = responseBody;
    }
  }

  /**
   * Opens the source to read the specified data.
   *
   * <p>Note: {@link HttpDataSource} implementations are advised to set request headers passed via
   * (in order of decreasing priority) the {@code dataSpec}, {@link #setRequestProperty} and the
   * default parameters set in the {@link Factory}.
   */
  @Override
  long open(DataSpec dataSpec) throws HttpDataSourceException;

  @Override
  void close() throws HttpDataSourceException;

  @Override
  int read(byte[] buffer, int offset, int length) throws HttpDataSourceException;

  /**
   * Sets the value of a request header. The value will be used for subsequent connections
   * established by the source.
   *
   * <p>Note: If the same header is set as a default parameter in the {@link Factory}, then the
   * header value set with this method should be preferred when connecting with the data source. See
   * {@link #open}.
   *
   * @param name The name of the header field.
   * @param value The value of the field.
   */
  void setRequestProperty(String name, String value);

  /**
   * Clears the value of a request header. The change will apply to subsequent connections
   * established by the source.
   *
   * @param name The name of the header field.
   */
  void clearRequestProperty(String name);

  /** Clears all request headers that were set by {@link #setRequestProperty(String, String)}. */
  void clearAllRequestProperties();

  /**
   * When the source is open, returns the HTTP response status code associated with the last {@link
   * #open} call. Otherwise, returns a negative value.
   */
  int getResponseCode();

  @Override
  Map<String, List<String>> getResponseHeaders();
}
