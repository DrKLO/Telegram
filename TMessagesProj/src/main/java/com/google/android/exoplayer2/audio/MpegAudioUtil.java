/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.MimeTypes;

/** Utility methods for handling MPEG audio streams. */
public final class MpegAudioUtil {

  /** Stores the metadata for an MPEG audio frame. */
  public static final class Header {

    /** MPEG audio header version. */
    public int version;
    /** The mime type. */
    @Nullable public String mimeType;
    /** Size of the frame associated with this header, in bytes. */
    public int frameSize;
    /** Sample rate in samples per second. */
    public int sampleRate;
    /** Number of audio channels in the frame. */
    public int channels;
    /** Bitrate of the frame in bit/s. */
    public int bitrate;
    /** Number of samples stored in the frame. */
    public int samplesPerFrame;

    /**
     * Populates the fields in this instance to reflect the MPEG audio header in {@code headerData},
     * returning whether the header was valid. If false, the values of the fields in this instance
     * will not be updated.
     *
     * @param headerData Header data to parse.
     * @return True if the fields were populated. False otherwise, indicating that {@code
     *     headerData} is not a valid MPEG audio header.
     */
    public boolean setForHeaderData(int headerData) {
      if (!isMagicPresent(headerData)) {
        return false;
      }

      int version = (headerData >>> 19) & 3;
      if (version == 1) {
        return false;
      }

      int layer = (headerData >>> 17) & 3;
      if (layer == 0) {
        return false;
      }

      int bitrateIndex = (headerData >>> 12) & 15;
      if (bitrateIndex == 0 || bitrateIndex == 0xF) {
        // Disallow "free" bitrate.
        return false;
      }

      int samplingRateIndex = (headerData >>> 10) & 3;
      if (samplingRateIndex == 3) {
        return false;
      }

      this.version = version;
      mimeType = MIME_TYPE_BY_LAYER[3 - layer];
      sampleRate = SAMPLING_RATE_V1[samplingRateIndex];
      if (version == 2) {
        // Version 2
        sampleRate /= 2;
      } else if (version == 0) {
        // Version 2.5
        sampleRate /= 4;
      }
      int padding = (headerData >>> 9) & 1;
      samplesPerFrame = getFrameSizeInSamples(version, layer);
      if (layer == 3) {
        // Layer I (layer == 3)
        bitrate = version == 3 ? BITRATE_V1_L1[bitrateIndex - 1] : BITRATE_V2_L1[bitrateIndex - 1];
        frameSize = (12 * bitrate / sampleRate + padding) * 4;
      } else {
        // Layer II (layer == 2) or III (layer == 1)
        if (version == 3) {
          // Version 1
          bitrate = layer == 2 ? BITRATE_V1_L2[bitrateIndex - 1] : BITRATE_V1_L3[bitrateIndex - 1];
          frameSize = 144 * bitrate / sampleRate + padding;
        } else {
          // Version 2 or 2.5.
          bitrate = BITRATE_V2[bitrateIndex - 1];
          frameSize = (layer == 1 ? 72 : 144) * bitrate / sampleRate + padding;
        }
      }
      channels = ((headerData >> 6) & 3) == 3 ? 1 : 2;
      return true;
    }
  }

  /**
   * Returns the size of the frame associated with {@code header}, or {@link C#LENGTH_UNSET} if it
   * is invalid.
   */
  public static int getFrameSize(int headerData) {
    if (!isMagicPresent(headerData)) {
      return C.LENGTH_UNSET;
    }

    int version = (headerData >>> 19) & 3;
    if (version == 1) {
      return C.LENGTH_UNSET;
    }

    int layer = (headerData >>> 17) & 3;
    if (layer == 0) {
      return C.LENGTH_UNSET;
    }

    int bitrateIndex = (headerData >>> 12) & 15;
    if (bitrateIndex == 0 || bitrateIndex == 0xF) {
      // Disallow "free" bitrate.
      return C.LENGTH_UNSET;
    }

    int samplingRateIndex = (headerData >>> 10) & 3;
    if (samplingRateIndex == 3) {
      return C.LENGTH_UNSET;
    }

    int samplingRate = SAMPLING_RATE_V1[samplingRateIndex];
    if (version == 2) {
      // Version 2
      samplingRate /= 2;
    } else if (version == 0) {
      // Version 2.5
      samplingRate /= 4;
    }

    int bitrate;
    int padding = (headerData >>> 9) & 1;
    if (layer == 3) {
      // Layer I (layer == 3)
      bitrate = version == 3 ? BITRATE_V1_L1[bitrateIndex - 1] : BITRATE_V2_L1[bitrateIndex - 1];
      return (12 * bitrate / samplingRate + padding) * 4;
    } else {
      // Layer II (layer == 2) or III (layer == 1)
      if (version == 3) {
        bitrate = layer == 2 ? BITRATE_V1_L2[bitrateIndex - 1] : BITRATE_V1_L3[bitrateIndex - 1];
      } else {
        // Version 2 or 2.5.
        bitrate = BITRATE_V2[bitrateIndex - 1];
      }
    }

    if (version == 3) {
      // Version 1
      return 144 * bitrate / samplingRate + padding;
    } else {
      // Version 2 or 2.5
      return (layer == 1 ? 72 : 144) * bitrate / samplingRate + padding;
    }
  }

  /**
   * Returns the number of samples per frame associated with {@code headerData}, or {@link
   * C#LENGTH_UNSET} if it is invalid.
   */
  public static int parseMpegAudioFrameSampleCount(int headerData) {
    if (!isMagicPresent(headerData)) {
      return C.LENGTH_UNSET;
    }

    int version = (headerData >>> 19) & 3;
    if (version == 1) {
      return C.LENGTH_UNSET;
    }

    int layer = (headerData >>> 17) & 3;
    if (layer == 0) {
      return C.LENGTH_UNSET;
    }

    // Those header values are not used but are checked for consistency with the other methods
    int bitrateIndex = (headerData >>> 12) & 15;
    int samplingRateIndex = (headerData >>> 10) & 3;
    if (bitrateIndex == 0 || bitrateIndex == 0xF || samplingRateIndex == 3) {
      return C.LENGTH_UNSET;
    }

    return getFrameSizeInSamples(version, layer);
  }

  /**
   * Theoretical maximum frame size for an MPEG audio stream, which occurs when playing a Layer 2
   * MPEG 2.5 audio stream at 16 kb/s (with padding). The size is 1152 sample/frame * 160000 bit/s /
   * (8000 sample/s * 8 bit/byte) + 1 padding byte/frame = 2881 byte/frame. The next power of two
   * size is 4 KiB.
   */
  public static final int MAX_FRAME_SIZE_BYTES = 4096;

  /**
   * Maximum rate for an MPEG audio stream corresponding to MPEG-1 layer III (320 kbit/s), in bytes
   * per second.
   */
  public static final int MAX_RATE_BYTES_PER_SECOND = 320 * 1000 / 8;

  private static final String[] MIME_TYPE_BY_LAYER =
      new String[] {MimeTypes.AUDIO_MPEG_L1, MimeTypes.AUDIO_MPEG_L2, MimeTypes.AUDIO_MPEG};
  private static final int[] SAMPLING_RATE_V1 = {44100, 48000, 32000};
  private static final int[] BITRATE_V1_L1 = {
    32000, 64000, 96000, 128000, 160000, 192000, 224000, 256000, 288000, 320000, 352000, 384000,
    416000, 448000
  };
  private static final int[] BITRATE_V2_L1 = {
    32000, 48000, 56000, 64000, 80000, 96000, 112000, 128000, 144000, 160000, 176000, 192000,
    224000, 256000
  };
  private static final int[] BITRATE_V1_L2 = {
    32000, 48000, 56000, 64000, 80000, 96000, 112000, 128000, 160000, 192000, 224000, 256000,
    320000, 384000
  };
  private static final int[] BITRATE_V1_L3 = {
    32000, 40000, 48000, 56000, 64000, 80000, 96000, 112000, 128000, 160000, 192000, 224000, 256000,
    320000
  };
  private static final int[] BITRATE_V2 = {
    8000, 16000, 24000, 32000, 40000, 48000, 56000, 64000, 80000, 96000, 112000, 128000, 144000,
    160000
  };

  private static final int SAMPLES_PER_FRAME_L1 = 384;
  private static final int SAMPLES_PER_FRAME_L2 = 1152;
  private static final int SAMPLES_PER_FRAME_L3_V1 = 1152;
  private static final int SAMPLES_PER_FRAME_L3_V2 = 576;

  private MpegAudioUtil() {}

  private static boolean isMagicPresent(int headerData) {
    return (headerData & 0xFFE00000) == 0xFFE00000;
  }

  private static int getFrameSizeInSamples(int version, int layer) {
    switch (layer) {
      case 1:
        return version == 3 ? SAMPLES_PER_FRAME_L3_V1 : SAMPLES_PER_FRAME_L3_V2; // Layer III
      case 2:
        return SAMPLES_PER_FRAME_L2; // Layer II
      case 3:
        return SAMPLES_PER_FRAME_L1; // Layer I
      default:
        throw new IllegalArgumentException();
    }
  }
}
