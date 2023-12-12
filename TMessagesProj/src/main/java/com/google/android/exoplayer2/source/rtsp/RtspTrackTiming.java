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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.UriUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;

/**
 * Represents an RTSP track's timing info, included as {@link RtspHeaders#RTP_INFO} in an RTSP PLAY
 * response (RFC2326 Section 12.33).
 */
/* package */ final class RtspTrackTiming {

  /**
   * Parses the RTP-Info header into a list of {@link RtspTrackTiming RtspTrackTimings}.
   *
   * <p>The syntax of the RTP-Info (RFC2326 Section 12.33):
   *
   * <pre>
   *   RTP-Info        = "RTP-Info" ":" 1#stream-url 1*parameter
   *   stream-url      = "url" "=" url
   *   parameter       = ";" "seq" "=" 1*DIGIT
   *                   | ";" "rtptime" "=" 1*DIGIT
   * </pre>
   *
   * <p>Examples from RFC2326:
   *
   * <pre>
   *   RTP-Info:url=rtsp://foo.com/bar.file; seq=232433;rtptime=972948234
   *   RTP-Info:url=rtsp://foo.com/bar.avi/streamid=0;seq=45102,
   *            url=rtsp://foo.com/bar.avi/streamid=1;seq=30211
   * </pre>
   *
   * @param rtpInfoString The value of the RTP-Info header, with header name (RTP-Info) removed.
   * @param sessionUri The session URI, must include an {@code rtsp} scheme.
   * @return A list of parsed {@link RtspTrackTiming}.
   * @throws ParserException If parsing failed.
   */
  public static ImmutableList<RtspTrackTiming> parseTrackTiming(
      String rtpInfoString, Uri sessionUri) throws ParserException {

    ImmutableList.Builder<RtspTrackTiming> listBuilder = new ImmutableList.Builder<>();
    for (String perTrackTimingString : Util.split(rtpInfoString, ",")) {
      long rtpTime = C.TIME_UNSET;
      int sequenceNumber = C.INDEX_UNSET;
      @Nullable Uri uri = null;

      for (String attributePair : Util.split(perTrackTimingString, ";")) {
        try {
          String[] attributes = Util.splitAtFirst(attributePair, "=");
          String attributeName = attributes[0];
          String attributeValue = attributes[1];

          switch (attributeName) {
            case "url":
              uri = resolveUri(/* urlString= */ attributeValue, sessionUri);
              break;
            case "seq":
              sequenceNumber = Integer.parseInt(attributeValue);
              break;
            case "rtptime":
              rtpTime = Long.parseLong(attributeValue);
              break;
            default:
              throw ParserException.createForMalformedManifest(attributeName, /* cause= */ null);
          }
        } catch (Exception e) {
          throw ParserException.createForMalformedManifest(attributePair, e);
        }
      }

      if (uri == null
          || uri.getScheme() == null // Checks if the URI is a URL.
          || (sequenceNumber == C.INDEX_UNSET && rtpTime == C.TIME_UNSET)) {
        throw ParserException.createForMalformedManifest(perTrackTimingString, /* cause= */ null);
      }

      listBuilder.add(new RtspTrackTiming(rtpTime, sequenceNumber, uri));
    }
    return listBuilder.build();
  }

  /**
   * Resolves the input string to always be an absolute URL with RTP-Info headers
   *
   * <p>Handles some servers do not send absolute URL in RTP-Info headers. This method takes in
   * RTP-Info header's url string, and returns the correctly formatted {@link Uri url} for this
   * track. The input url string could be
   *
   * <ul>
   *   <li>A correctly formatted URL, like "{@code rtsp://foo.bar/video}".
   *   <li>A correct URI that is missing the scheme, like "{@code foo.bar/video}".
   *   <li>A path to the resource, like "{@code video}" or "{@code /video}".
   * </ul>
   *
   * @param urlString The URL included in the RTP-Info header, without the {@code url=} identifier.
   * @param sessionUri The session URI, must include an {@code rtsp} scheme, or {@link
   *     IllegalArgumentException} is thrown.
   * @return The formatted URL.
   */
  @VisibleForTesting
  /* package */ static Uri resolveUri(String urlString, Uri sessionUri) {
    checkArgument(checkNotNull(sessionUri.getScheme()).equals("rtsp"));

    Uri uri = Uri.parse(urlString);
    if (uri.isAbsolute()) {
      return uri;
    }

    // The urlString is at least missing the scheme.
    uri = Uri.parse("rtsp://" + urlString);
    String sessionUriString = sessionUri.toString();

    String host = checkNotNull(uri.getHost());
    if (host.equals(sessionUri.getHost())) {
      // Handles the case that the urlString is only missing the scheme.
      return uri;
    }

    return sessionUriString.endsWith("/")
        ? UriUtil.resolveToUri(sessionUriString, urlString)
        : UriUtil.resolveToUri(sessionUriString + "/", urlString);
  }

  /**
   * The timestamp of the next RTP packet, {@link C#TIME_UNSET} if not present.
   *
   * <p>Cannot be {@link C#TIME_UNSET} if {@link #sequenceNumber} is {@link C#INDEX_UNSET}.
   */
  public final long rtpTimestamp;
  /**
   * The sequence number of the next RTP packet, {@link C#INDEX_UNSET} if not present.
   *
   * <p>Cannot be {@link C#INDEX_UNSET} if {@link #rtpTimestamp} is {@link C#TIME_UNSET}.
   */
  public final int sequenceNumber;
  /** The {@link Uri} that identifies a matching {@link RtspMediaTrack}. */
  public final Uri uri;

  private RtspTrackTiming(long rtpTimestamp, int sequenceNumber, Uri uri) {
    this.rtpTimestamp = rtpTimestamp;
    this.sequenceNumber = sequenceNumber;
    this.uri = uri;
  }
}
