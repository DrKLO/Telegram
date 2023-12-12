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
package com.google.android.exoplayer2.upstream;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.max;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Log;
import com.google.common.net.HttpHeaders;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility methods for HTTP. */
public final class HttpUtil {

  private static final String TAG = "HttpUtil";
  private static final Pattern CONTENT_RANGE_WITH_START_AND_END =
      Pattern.compile("bytes (\\d+)-(\\d+)/(?:\\d+|\\*)");
  private static final Pattern CONTENT_RANGE_WITH_SIZE =
      Pattern.compile("bytes (?:(?:\\d+-\\d+)|\\*)/(\\d+)");

  /** Class only contains static methods. */
  private HttpUtil() {}

  /**
   * Builds a {@link HttpHeaders#RANGE Range header} for the given position and length.
   *
   * @param position The request position.
   * @param length The request length, or {@link C#LENGTH_UNSET} if the request is unbounded.
   * @return The corresponding range header, or {@code null} if a header is unnecessary because the
   *     whole resource is being requested.
   */
  @Nullable
  public static String buildRangeRequestHeader(long position, long length) {
    if (position == 0 && length == C.LENGTH_UNSET) {
      return null;
    }
    StringBuilder rangeValue = new StringBuilder();
    rangeValue.append("bytes=");
    rangeValue.append(position);
    rangeValue.append("-");
    if (length != C.LENGTH_UNSET) {
      rangeValue.append(position + length - 1);
    }
    return rangeValue.toString();
  }

  /**
   * Attempts to parse the document size from a {@link HttpHeaders#CONTENT_RANGE Content-Range
   * header}.
   *
   * @param contentRangeHeader The {@link HttpHeaders#CONTENT_RANGE Content-Range header}, or {@code
   *     null} if not set.
   * @return The document size, or {@link C#LENGTH_UNSET} if it could not be determined.
   */
  public static long getDocumentSize(@Nullable String contentRangeHeader) {
    if (TextUtils.isEmpty(contentRangeHeader)) {
      return C.LENGTH_UNSET;
    }
    Matcher matcher = CONTENT_RANGE_WITH_SIZE.matcher(contentRangeHeader);
    return matcher.matches() ? Long.parseLong(checkNotNull(matcher.group(1))) : C.LENGTH_UNSET;
  }

  /**
   * Attempts to parse the length of a response body from the corresponding response headers.
   *
   * @param contentLengthHeader The {@link HttpHeaders#CONTENT_LENGTH Content-Length header}, or
   *     {@code null} if not set.
   * @param contentRangeHeader The {@link HttpHeaders#CONTENT_RANGE Content-Range header}, or {@code
   *     null} if not set.
   * @return The length of the response body, or {@link C#LENGTH_UNSET} if it could not be
   *     determined.
   */
  public static long getContentLength(
      @Nullable String contentLengthHeader, @Nullable String contentRangeHeader) {
    long contentLength = C.LENGTH_UNSET;
    if (!TextUtils.isEmpty(contentLengthHeader)) {
      try {
        contentLength = Long.parseLong(contentLengthHeader);
      } catch (NumberFormatException e) {
        Log.e(TAG, "Unexpected Content-Length [" + contentLengthHeader + "]");
      }
    }
    if (!TextUtils.isEmpty(contentRangeHeader)) {
      Matcher matcher = CONTENT_RANGE_WITH_START_AND_END.matcher(contentRangeHeader);
      if (matcher.matches()) {
        try {
          long contentLengthFromRange =
              Long.parseLong(checkNotNull(matcher.group(2)))
                  - Long.parseLong(checkNotNull(matcher.group(1)))
                  + 1;
          if (contentLength < 0) {
            // Some proxy servers strip the Content-Length header. Fall back to the length
            // calculated here in this case.
            contentLength = contentLengthFromRange;
          } else if (contentLength != contentLengthFromRange) {
            // If there is a discrepancy between the Content-Length and Content-Range headers,
            // assume the one with the larger value is correct. We have seen cases where carrier
            // change one of them to reduce the size of a request, but it is unlikely anybody would
            // increase it.
            Log.w(
                TAG,
                "Inconsistent headers [" + contentLengthHeader + "] [" + contentRangeHeader + "]");
            contentLength = max(contentLength, contentLengthFromRange);
          }
        } catch (NumberFormatException e) {
          Log.e(TAG, "Unexpected Content-Range [" + contentRangeHeader + "]");
        }
      }
    }
    return contentLength;
  }
}
