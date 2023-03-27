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

import com.google.android.exoplayer2.C;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Utility methods for handling Opus audio streams. */
public class OpusUtil {

  /** Opus streams are always 48000 Hz. */
  public static final int SAMPLE_RATE = 48_000;

  /** Maximum achievable Opus bitrate. */
  public static final int MAX_BYTES_PER_SECOND = 510 * 1000 / 8; // See RFC 6716. Section 2.1.1

  private static final int DEFAULT_SEEK_PRE_ROLL_SAMPLES = 3840;
  private static final int FULL_CODEC_INITIALIZATION_DATA_BUFFER_COUNT = 3;

  private OpusUtil() {} // Prevents instantiation.

  /**
   * Parses the channel count from an Opus Identification Header.
   *
   * @param header An Opus Identification Header, as defined by RFC 7845.
   * @return The parsed channel count.
   */
  public static int getChannelCount(byte[] header) {
    return header[9] & 0xFF;
  }

  /**
   * Builds codec initialization data from an Opus Identification Header.
   *
   * @param header An Opus Identification Header, as defined by RFC 7845.
   * @return Codec initialization data suitable for an Opus <a
   *     href="https://developer.android.com/reference/android/media/MediaCodec#initialization">MediaCodec</a>.
   */
  public static List<byte[]> buildInitializationData(byte[] header) {
    int preSkipSamples = getPreSkipSamples(header);
    long preSkipNanos = sampleCountToNanoseconds(preSkipSamples);
    long seekPreRollNanos = sampleCountToNanoseconds(DEFAULT_SEEK_PRE_ROLL_SAMPLES);

    List<byte[]> initializationData = new ArrayList<>(FULL_CODEC_INITIALIZATION_DATA_BUFFER_COUNT);
    initializationData.add(header);
    initializationData.add(buildNativeOrderByteArray(preSkipNanos));
    initializationData.add(buildNativeOrderByteArray(seekPreRollNanos));
    return initializationData;
  }

  /**
   * Returns the number of audio samples in the given audio packet.
   *
   * <p>The buffer's position is not modified.
   *
   * @param buffer The audio packet.
   * @return Returns the number of audio samples in the packet.
   */
  public static int parsePacketAudioSampleCount(ByteBuffer buffer) {
    long packetDurationUs =
        getPacketDurationUs(buffer.get(0), buffer.limit() > 1 ? buffer.get(1) : 0);
    return (int) (packetDurationUs * SAMPLE_RATE / C.MICROS_PER_SECOND);
  }

  /**
   * Returns the duration of the given audio packet.
   *
   * @param buffer The audio packet.
   * @return Returns the duration of the given audio packet, in microseconds.
   */
  public static long getPacketDurationUs(byte[] buffer) {
    return getPacketDurationUs(buffer[0], buffer.length > 1 ? buffer[1] : 0);
  }

  private static long getPacketDurationUs(byte packetByte0, byte packetByte1) {
    // See RFC6716, Sections 3.1 and 3.2.
    int toc = packetByte0 & 0xFF;
    int frames;
    switch (toc & 0x3) {
      case 0:
        frames = 1;
        break;
      case 1:
      case 2:
        frames = 2;
        break;
      default:
        frames = packetByte1 & 0x3F;
        break;
    }

    int config = toc >> 3;
    int length = config & 0x3;
    int frameDurationUs;
    if (config >= 16) {
      frameDurationUs = 2500 << length;
    } else if (config >= 12) {
      frameDurationUs = 10000 << (length & 0x1);
    } else if (length == 3) {
      frameDurationUs = 60000;
    } else {
      frameDurationUs = 10000 << length;
    }
    return (long) frames * frameDurationUs;
  }

  private static int getPreSkipSamples(byte[] header) {
    return ((header[11] & 0xFF) << 8) | (header[10] & 0xFF);
  }

  private static byte[] buildNativeOrderByteArray(long value) {
    return ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(value).array();
  }

  private static long sampleCountToNanoseconds(long sampleCount) {
    return (sampleCount * C.NANOS_PER_SECOND) / SAMPLE_RATE;
  }
}
