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
package org.telegram.messenger.exoplayer2.source.smoothstreaming.manifest;

import android.net.Uri;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.UriUtil;
import org.telegram.messenger.exoplayer2.util.Util;
import java.util.List;
import java.util.UUID;

/**
 * Represents a SmoothStreaming manifest.
 *
 * @see <a href="http://msdn.microsoft.com/en-us/library/ee673436(v=vs.90).aspx">
 * IIS Smooth Streaming Client Manifest Format</a>
 */
public class SsManifest {

  public static final int UNSET_LOOKAHEAD = -1;

  /**
   * The client manifest major version.
   */
  public final int majorVersion;

  /**
   * The client manifest minor version.
   */
  public final int minorVersion;

  /**
   * The number of fragments in a lookahead, or {@link #UNSET_LOOKAHEAD} if the lookahead is
   * unspecified.
   */
  public final int lookAheadCount;

  /**
   * Whether the manifest describes a live presentation still in progress.
   */
  public final boolean isLive;

  /**
   * Content protection information, or null if the content is not protected.
   */
  public final ProtectionElement protectionElement;

  /**
   * The contained stream elements.
   */
  public final StreamElement[] streamElements;

  /**
   * The overall presentation duration of the media in microseconds, or {@link C#TIME_UNSET}
   * if the duration is unknown.
   */
  public final long durationUs;

  /**
   * The length of the trailing window for a live broadcast in microseconds, or
   * {@link C#TIME_UNSET} if the stream is not live or if the window length is unspecified.
   */
  public final long dvrWindowLengthUs;

  /**
   * @param majorVersion The client manifest major version.
   * @param minorVersion The client manifest minor version.
   * @param timescale The timescale of the media as the number of units that pass in one second.
   * @param duration The overall presentation duration in units of the timescale attribute, or 0
   *     if the duration is unknown.
   * @param dvrWindowLength The length of the trailing window in units of the timescale attribute,
   *     or 0 if this attribute is unspecified or not applicable.
   * @param lookAheadCount The number of fragments in a lookahead, or {@link #UNSET_LOOKAHEAD} if
   *     this attribute is unspecified or not applicable.
   * @param isLive True if the manifest describes a live presentation still in progress. False
   *     otherwise.
   * @param protectionElement Content protection information, or null if the content is not
   *     protected.
   * @param streamElements The contained stream elements.
   */
  public SsManifest(int majorVersion, int minorVersion, long timescale, long duration,
      long dvrWindowLength, int lookAheadCount, boolean isLive, ProtectionElement protectionElement,
      StreamElement[] streamElements) {
    this.majorVersion = majorVersion;
    this.minorVersion = minorVersion;
    this.lookAheadCount = lookAheadCount;
    this.isLive = isLive;
    this.protectionElement = protectionElement;
    this.streamElements = streamElements;
    dvrWindowLengthUs = dvrWindowLength == 0 ? C.TIME_UNSET
        : Util.scaleLargeTimestamp(dvrWindowLength, C.MICROS_PER_SECOND, timescale);
    durationUs = duration == 0 ? C.TIME_UNSET
        : Util.scaleLargeTimestamp(duration, C.MICROS_PER_SECOND, timescale);
  }

  /**
   * Represents a protection element containing a single header.
   */
  public static class ProtectionElement {

    public final UUID uuid;
    public final byte[] data;

    public ProtectionElement(UUID uuid, byte[] data) {
      this.uuid = uuid;
      this.data = data;
    }

  }

  /**
   * Represents a StreamIndex element.
   */
  public static class StreamElement {

    private static final String URL_PLACEHOLDER_START_TIME_1 = "{start time}";
    private static final String URL_PLACEHOLDER_START_TIME_2 = "{start_time}";
    private static final String URL_PLACEHOLDER_BITRATE_1 = "{bitrate}";
    private static final String URL_PLACEHOLDER_BITRATE_2 = "{Bitrate}";

    public final int type;
    public final String subType;
    public final long timescale;
    public final String name;
    public final int maxWidth;
    public final int maxHeight;
    public final int displayWidth;
    public final int displayHeight;
    public final String language;
    public final Format[] formats;
    public final int chunkCount;

    private final String baseUri;
    private final String chunkTemplate;

    private final List<Long> chunkStartTimes;
    private final long[] chunkStartTimesUs;
    private final long lastChunkDurationUs;

    public StreamElement(String baseUri, String chunkTemplate, int type, String subType,
        long timescale, String name, int maxWidth, int maxHeight, int displayWidth,
        int displayHeight, String language, Format[] formats, List<Long> chunkStartTimes,
        long lastChunkDuration) {
      this.baseUri = baseUri;
      this.chunkTemplate = chunkTemplate;
      this.type = type;
      this.subType = subType;
      this.timescale = timescale;
      this.name = name;
      this.maxWidth = maxWidth;
      this.maxHeight = maxHeight;
      this.displayWidth = displayWidth;
      this.displayHeight = displayHeight;
      this.language = language;
      this.formats = formats;
      this.chunkCount = chunkStartTimes.size();
      this.chunkStartTimes = chunkStartTimes;
      lastChunkDurationUs =
          Util.scaleLargeTimestamp(lastChunkDuration, C.MICROS_PER_SECOND, timescale);
      chunkStartTimesUs =
          Util.scaleLargeTimestamps(chunkStartTimes, C.MICROS_PER_SECOND, timescale);
    }

    /**
     * Returns the index of the chunk that contains the specified time.
     *
     * @param timeUs The time in microseconds.
     * @return The index of the corresponding chunk.
     */
    public int getChunkIndex(long timeUs) {
      return Util.binarySearchFloor(chunkStartTimesUs, timeUs, true, true);
    }

    /**
     * Returns the start time of the specified chunk.
     *
     * @param chunkIndex The index of the chunk.
     * @return The start time of the chunk, in microseconds.
     */
    public long getStartTimeUs(int chunkIndex) {
      return chunkStartTimesUs[chunkIndex];
    }

    /**
     * Returns the duration of the specified chunk.
     *
     * @param chunkIndex The index of the chunk.
     * @return The duration of the chunk, in microseconds.
     */
    public long getChunkDurationUs(int chunkIndex) {
      return (chunkIndex == chunkCount - 1) ? lastChunkDurationUs
          : chunkStartTimesUs[chunkIndex + 1] - chunkStartTimesUs[chunkIndex];
    }

    /**
     * Builds a uri for requesting the specified chunk of the specified track.
     *
     * @param track The index of the track for which to build the URL.
     * @param chunkIndex The index of the chunk for which to build the URL.
     * @return The request uri.
     */
    public Uri buildRequestUri(int track, int chunkIndex) {
      Assertions.checkState(formats != null);
      Assertions.checkState(chunkStartTimes != null);
      Assertions.checkState(chunkIndex < chunkStartTimes.size());
      String bitrateString = Integer.toString(formats[track].bitrate);
      String startTimeString = chunkStartTimes.get(chunkIndex).toString();
      String chunkUrl = chunkTemplate
          .replace(URL_PLACEHOLDER_BITRATE_1, bitrateString)
          .replace(URL_PLACEHOLDER_BITRATE_2, bitrateString)
          .replace(URL_PLACEHOLDER_START_TIME_1, startTimeString)
          .replace(URL_PLACEHOLDER_START_TIME_2, startTimeString);
      return UriUtil.resolveToUri(baseUri, chunkUrl);
    }

  }

}
