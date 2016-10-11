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
package org.telegram.messenger.exoplayer.chunk;

import org.telegram.messenger.exoplayer.util.Assertions;
import java.util.Comparator;

/**
 * Defines the high level format of a media stream.
 */
public class Format {

  /**
   * Sorts {@link Format} objects in order of decreasing bandwidth.
   */
  public static final class DecreasingBandwidthComparator implements Comparator<Format> {

    @Override
    public int compare(Format a, Format b) {
      return b.bitrate - a.bitrate;
    }

  }

  /**
   * An identifier for the format.
   */
  public final String id;

  /**
   * The mime type of the format.
   */
  public final String mimeType;

  /**
   * The average bandwidth in bits per second.
   */
  public final int bitrate;

  /**
   * The width of the video in pixels, or -1 if unknown or not applicable.
   */
  public final int width;

  /**
   * The height of the video in pixels, or -1 if unknown or not applicable.
   */
  public final int height;

  /**
   * The video frame rate in frames per second, or -1 if unknown or not applicable.
   */
  public final float frameRate;

  /**
   * The number of audio channels, or -1 if unknown or not applicable.
   */
  public final int audioChannels;

  /**
   * The audio sampling rate in Hz, or -1 if unknown or not applicable.
   */
  public final int audioSamplingRate;

  /**
   * The codecs used to decode the format. Can be {@code null} if unknown.
   */
  public final String codecs;

  /**
   * The language of the format. Can be null if unknown.
   * <p>
   * The language codes are two-letter lowercase ISO language codes (such as "en") as defined by
   * ISO 639-1.
   */
  public final String language;

  /**
   * @param id The format identifier.
   * @param mimeType The format mime type.
   * @param width The width of the video in pixels, or -1 if unknown or not applicable.
   * @param height The height of the video in pixels, or -1 if unknown or not applicable.
   * @param frameRate The frame rate of the video in frames per second, or -1 if unknown or not
   *     applicable.
   * @param numChannels The number of audio channels, or -1 if unknown or not applicable.
   * @param audioSamplingRate The audio sampling rate in Hz, or -1 if unknown or not applicable.
   * @param bitrate The average bandwidth of the format in bits per second.
   */
  public Format(String id, String mimeType, int width, int height, float frameRate, int numChannels,
      int audioSamplingRate, int bitrate) {
    this(id, mimeType, width, height, frameRate, numChannels, audioSamplingRate, bitrate, null);
  }

  /**
   * @param id The format identifier.
   * @param mimeType The format mime type.
   * @param width The width of the video in pixels, or -1 if unknown or not applicable.
   * @param height The height of the video in pixels, or -1 if unknown or not applicable.
   * @param frameRate The frame rate of the video in frames per second, or -1 if unknown or not
   *     applicable.
   * @param numChannels The number of audio channels, or -1 if unknown or not applicable.
   * @param audioSamplingRate The audio sampling rate in Hz, or -1 if unknown or not applicable.
   * @param bitrate The average bandwidth of the format in bits per second.
   * @param language The language of the format.
   */
  public Format(String id, String mimeType, int width, int height, float frameRate, int numChannels,
      int audioSamplingRate, int bitrate, String language) {
    this(id, mimeType, width, height, frameRate, numChannels, audioSamplingRate, bitrate, language,
        null);
  }

  /**
   * @param id The format identifier.
   * @param mimeType The format mime type.
   * @param width The width of the video in pixels, or -1 if unknown or not applicable.
   * @param height The height of the video in pixels, or -1 if unknown or not applicable.
   * @param frameRate The frame rate of the video in frames per second, or -1 if unknown or not
   *     applicable.
   * @param audioChannels The number of audio channels, or -1 if unknown or not applicable.
   * @param audioSamplingRate The audio sampling rate in Hz, or -1 if unknown or not applicable.
   * @param bitrate The average bandwidth of the format in bits per second.
   * @param language The language of the format.
   * @param codecs The codecs used to decode the format.
   */
  public Format(String id, String mimeType, int width, int height, float frameRate,
      int audioChannels, int audioSamplingRate, int bitrate, String language, String codecs) {
    this.id = Assertions.checkNotNull(id);
    this.mimeType = mimeType;
    this.width = width;
    this.height = height;
    this.frameRate = frameRate;
    this.audioChannels = audioChannels;
    this.audioSamplingRate = audioSamplingRate;
    this.bitrate = bitrate;
    this.language = language;
    this.codecs = codecs;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  /**
   * Implements equality based on {@link #id} only.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Format other = (Format) obj;
    return other.id.equals(id);
  }

}
