/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtsp.reader;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;

/** Utility methods for {@link RtpPayloadReader}s. */
/* package */ class RtpReaderUtils {

  /**
   * Converts RTP timestamp and media frequency to sample presentation time, in microseconds
   *
   * @param startTimeOffsetUs The offset of the RTP timebase, in microseconds.
   * @param rtpTimestamp The RTP timestamp to convert.
   * @param firstReceivedRtpTimestamp The first received RTP timestamp.
   * @param mediaFrequency The media frequency.
   * @return The calculated sample presentation time, in microseconds.
   */
  public static long toSampleTimeUs(
      long startTimeOffsetUs,
      long rtpTimestamp,
      long firstReceivedRtpTimestamp,
      int mediaFrequency) {
    return startTimeOffsetUs
        + Util.scaleLargeTimestamp(
            rtpTimestamp - firstReceivedRtpTimestamp,
            /* multiplier= */ C.MICROS_PER_SECOND,
            /* divisor= */ mediaFrequency);
  }

  private RtpReaderUtils() {}
}
