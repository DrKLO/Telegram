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
package org.telegram.messenger.exoplayer.util;

/**
 * Utility functions for FLAC
 */
public final class FlacUtil {

  private static final int FRAME_HEADER_SAMPLE_NUMBER_OFFSET = 4;

  /**
   * Prevents initialization.
   */
  private FlacUtil() {
  }

  /**
   * Extracts sample timestamp from the given binary FLAC frame header data structure.
   *
   * @param streamInfo A {@link FlacStreamInfo} instance
   * @param frameData A {@link ParsableByteArray} including binary FLAC frame header data structure.
   *     Its position should be set to the beginning of the structure.
   * @return Sample timestamp
   * @see <a href="https://xiph.org/flac/format.html#frame_header">FLAC format FRAME_HEADER</a>
   */
  public static long extractSampleTimestamp(FlacStreamInfo streamInfo,
      ParsableByteArray frameData) {
    frameData.skipBytes(FRAME_HEADER_SAMPLE_NUMBER_OFFSET);
    long sampleNumber = frameData.readUTF8EncodedLong();
    if (streamInfo.minBlockSize == streamInfo.maxBlockSize) {
      // if fixed block size then sampleNumber is frame number
      sampleNumber *= streamInfo.minBlockSize;
    }
    return (sampleNumber * 1000000L) / streamInfo.sampleRate;
  }

}
