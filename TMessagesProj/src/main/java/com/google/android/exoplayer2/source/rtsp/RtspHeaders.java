/*
 * Copyright 2021 The Android Open Source Project
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

package com.google.android.exoplayer2.source.rtsp;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;
import java.util.Map;

/**
 * RTSP message headers.
 *
 * <p>{@link Builder} must be used to construct an instance. Use {@link #get} to query header values
 * with case-insensitive header names. The extra spaces around header names and values are trimmed.
 * Contrary to HTTP, RTSP does not allow ambiguous/arbitrary header names (RFC 2326 Section 12).
 */
/* package */ final class RtspHeaders {

  public static final String ACCEPT = "Accept";
  public static final String ALLOW = "Allow";
  public static final String AUTHORIZATION = "Authorization";
  public static final String BANDWIDTH = "Bandwidth";
  public static final String BLOCKSIZE = "Blocksize";
  public static final String CACHE_CONTROL = "Cache-Control";
  public static final String CONNECTION = "Connection";
  public static final String CONTENT_BASE = "Content-Base";
  public static final String CONTENT_ENCODING = "Content-Encoding";
  public static final String CONTENT_LANGUAGE = "Content-Language";
  public static final String CONTENT_LENGTH = "Content-Length";
  public static final String CONTENT_LOCATION = "Content-Location";
  public static final String CONTENT_TYPE = "Content-Type";
  public static final String CSEQ = "CSeq";
  public static final String DATE = "Date";
  public static final String EXPIRES = "Expires";
  public static final String LOCATION = "Location";
  public static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";
  public static final String PROXY_REQUIRE = "Proxy-Require";
  public static final String PUBLIC = "Public";
  public static final String RANGE = "Range";
  public static final String RTP_INFO = "RTP-Info";
  public static final String RTCP_INTERVAL = "RTCP-Interval";
  public static final String SCALE = "Scale";
  public static final String SESSION = "Session";
  public static final String SPEED = "Speed";
  public static final String SUPPORTED = "Supported";
  public static final String TIMESTAMP = "Timestamp";
  public static final String TRANSPORT = "Transport";
  public static final String USER_AGENT = "User-Agent";
  public static final String VIA = "Via";
  public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

  /** An empty header object. */
  public static final RtspHeaders EMPTY = new RtspHeaders.Builder().build();

  /** Builds {@link RtspHeaders} instances. */
  public static final class Builder {
    private final ImmutableListMultimap.Builder<String, String> namesAndValuesBuilder;

    /** Creates a new instance. */
    public Builder() {
      namesAndValuesBuilder = new ImmutableListMultimap.Builder<>();
    }

    /**
     * Creates a new instance with common header values.
     *
     * @param userAgent The user agent string.
     * @param sessionId The RTSP session ID; use {@code null} when the session is not yet set up.
     * @param cSeq The RTSP cSeq sequence number.
     */
    public Builder(String userAgent, @Nullable String sessionId, int cSeq) {
      this();

      add(USER_AGENT, userAgent);
      add(CSEQ, String.valueOf(cSeq));
      if (sessionId != null) {
        add(SESSION, sessionId);
      }
    }

    /**
     * Creates a new instance to build upon the provided {@link RtspHeaders}.
     *
     * @param namesAndValuesBuilder A {@link ImmutableListMultimap.Builder} that this builder builds
     *     upon.
     */
    private Builder(ImmutableListMultimap.Builder<String, String> namesAndValuesBuilder) {
      this.namesAndValuesBuilder = namesAndValuesBuilder;
    }

    /**
     * Adds a header name and header value pair.
     *
     * @param headerName The name of the header.
     * @param headerValue The value of the header.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder add(String headerName, String headerValue) {
      namesAndValuesBuilder.put(convertToStandardHeaderName(headerName.trim()), headerValue.trim());
      return this;
    }

    /**
     * Adds a list of headers.
     *
     * @param headers The list of headers, each item must following the format &lt;headerName&gt;:
     *     &lt;headerValue&gt;
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder addAll(List<String> headers) {
      for (int i = 0; i < headers.size(); i++) {
        String[] header = Util.splitAtFirst(headers.get(i), ":\\s?");
        if (header.length == 2) {
          add(header[0], header[1]);
        }
      }
      return this;
    }

    /**
     * Adds multiple headers in a map.
     *
     * @param headers The map of headers, where the keys are the header names and the values are the
     *     header values.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder addAll(Map<String, String> headers) {
      for (Map.Entry<String, String> header : headers.entrySet()) {
        add(header.getKey(), header.getValue());
      }
      return this;
    }

    /**
     * Builds a new {@link RtspHeaders} instance.
     *
     * @return The newly built {@link RtspHeaders} instance.
     */
    public RtspHeaders build() {
      return new RtspHeaders(this);
    }
  }

  private final ImmutableListMultimap<String, String> namesAndValues;

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof RtspHeaders)) {
      return false;
    }
    RtspHeaders headers = (RtspHeaders) obj;
    return namesAndValues.equals(headers.namesAndValues);
  }

  @Override
  public int hashCode() {
    return namesAndValues.hashCode();
  }

  /** Returns a {@link Builder} initialized with the values of this instance. */
  public Builder buildUpon() {
    ImmutableListMultimap.Builder<String, String> namesAndValuesBuilder =
        new ImmutableListMultimap.Builder<>();
    namesAndValuesBuilder.putAll(namesAndValues);
    return new Builder(namesAndValuesBuilder);
  }

  /**
   * Returns a map that associates header names to the list of values associated with the
   * corresponding header name.
   */
  public ImmutableListMultimap<String, String> asMultiMap() {
    return namesAndValues;
  }

  /**
   * Returns the most recent header value mapped to the argument, {@code null} if the header name is
   * not recorded.
   */
  @Nullable
  public String get(String headerName) {
    ImmutableList<String> headerValues = values(headerName);
    if (headerValues.isEmpty()) {
      return null;
    }
    return Iterables.getLast(headerValues);
  }

  /**
   * Returns a list of header values mapped to the argument, in the addition order. The returned
   * list is empty if the header name is not recorded.
   */
  public ImmutableList<String> values(String headerName) {
    return namesAndValues.get(convertToStandardHeaderName(headerName));
  }

  private RtspHeaders(Builder builder) {
    this.namesAndValues = builder.namesAndValuesBuilder.build();
  }

  private static String convertToStandardHeaderName(String messageHeaderName) {
    if (Ascii.equalsIgnoreCase(messageHeaderName, ACCEPT)) {
      return ACCEPT;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, ALLOW)) {
      return ALLOW;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, AUTHORIZATION)) {
      return AUTHORIZATION;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, BANDWIDTH)) {
      return BANDWIDTH;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, BLOCKSIZE)) {
      return BLOCKSIZE;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, CACHE_CONTROL)) {
      return CACHE_CONTROL;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, CONNECTION)) {
      return CONNECTION;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, CONTENT_BASE)) {
      return CONTENT_BASE;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, CONTENT_ENCODING)) {
      return CONTENT_ENCODING;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, CONTENT_LANGUAGE)) {
      return CONTENT_LANGUAGE;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, CONTENT_LENGTH)) {
      return CONTENT_LENGTH;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, CONTENT_LOCATION)) {
      return CONTENT_LOCATION;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, CONTENT_TYPE)) {
      return CONTENT_TYPE;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, CSEQ)) {
      return CSEQ;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, DATE)) {
      return DATE;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, EXPIRES)) {
      return EXPIRES;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, LOCATION)) {
      return LOCATION;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, PROXY_AUTHENTICATE)) {
      return PROXY_AUTHENTICATE;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, PROXY_REQUIRE)) {
      return PROXY_REQUIRE;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, PUBLIC)) {
      return PUBLIC;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, RANGE)) {
      return RANGE;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, RTP_INFO)) {
      return RTP_INFO;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, RTCP_INTERVAL)) {
      return RTCP_INTERVAL;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, SCALE)) {
      return SCALE;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, SESSION)) {
      return SESSION;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, SPEED)) {
      return SPEED;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, SUPPORTED)) {
      return SUPPORTED;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, TIMESTAMP)) {
      return TIMESTAMP;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, TRANSPORT)) {
      return TRANSPORT;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, USER_AGENT)) {
      return USER_AGENT;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, VIA)) {
      return VIA;
    } else if (Ascii.equalsIgnoreCase(messageHeaderName, WWW_AUTHENTICATE)) {
      return WWW_AUTHENTICATE;
    }
    return messageHeaderName;
  }
}
