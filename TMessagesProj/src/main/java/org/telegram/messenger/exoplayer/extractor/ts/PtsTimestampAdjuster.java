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
package org.telegram.messenger.exoplayer.extractor.ts;

import org.telegram.messenger.exoplayer.C;

/**
 * Scales and adjusts MPEG-2 TS presentation timestamps, taking into account an initial offset and
 * timestamp rollover.
 */
public final class PtsTimestampAdjuster {

  /**
   * A special {@code firstSampleTimestampUs} value indicating that presentation timestamps should
   * not be offset.
   */
  public static final long DO_NOT_OFFSET = Long.MAX_VALUE;

  /**
   * The value one greater than the largest representable (33 bit) presentation timestamp.
   */
  private static final long MAX_PTS_PLUS_ONE = 0x200000000L;

  private final long firstSampleTimestampUs;

  private long timestampOffsetUs;

  // Volatile to allow isInitialized to be called on a different thread to adjustTimestamp.
  private volatile long lastPts;

  /**
   * @param firstSampleTimestampUs The desired result of the first call to
   *     {@link #adjustTimestamp(long)}, or {@link #DO_NOT_OFFSET} if presentation timestamps
   *     should not be offset.
   */
  public PtsTimestampAdjuster(long firstSampleTimestampUs) {
    this.firstSampleTimestampUs = firstSampleTimestampUs;
    lastPts = Long.MIN_VALUE;
  }

  /**
   * Resets the instance to its initial state.
   */
  public void reset() {
    lastPts = Long.MIN_VALUE;
  }

  /**
   * Whether this adjuster has been initialized with a first MPEG-2 TS presentation timestamp.
   */
  public boolean isInitialized() {
    return lastPts != Long.MIN_VALUE;
  }

  /**
   * Scales and offsets an MPEG-2 TS presentation timestamp.
   *
   * @param pts The MPEG-2 TS presentation timestamp.
   * @return The adjusted timestamp in microseconds.
   */
  public long adjustTimestamp(long pts) {
    if (lastPts != Long.MIN_VALUE) {
      // The wrap count for the current PTS may be closestWrapCount or (closestWrapCount - 1),
      // and we need to snap to the one closest to lastPts.
      long closestWrapCount = (lastPts + (MAX_PTS_PLUS_ONE / 2)) / MAX_PTS_PLUS_ONE;
      long ptsWrapBelow = pts + (MAX_PTS_PLUS_ONE * (closestWrapCount - 1));
      long ptsWrapAbove = pts + (MAX_PTS_PLUS_ONE * closestWrapCount);
      pts = Math.abs(ptsWrapBelow - lastPts) < Math.abs(ptsWrapAbove - lastPts)
          ? ptsWrapBelow : ptsWrapAbove;
    }
    // Calculate the corresponding timestamp.
    long timeUs = ptsToUs(pts);
    if (firstSampleTimestampUs != DO_NOT_OFFSET && lastPts == Long.MIN_VALUE) {
      // Calculate the timestamp offset.
      timestampOffsetUs = firstSampleTimestampUs - timeUs;
    }
    // Record the adjusted PTS to adjust for wraparound next time.
    lastPts = pts;
    return timeUs + timestampOffsetUs;
  }

  /**
   * Converts a value in MPEG-2 timestamp units to the corresponding value in microseconds.
   *
   * @param pts A value in MPEG-2 timestamp units.
   * @return The corresponding value in microseconds.
   */
  public static long ptsToUs(long pts) {
    return (pts * C.MICROS_PER_SECOND) / 90000;
  }

  /**
   * Converts a value in microseconds to the corresponding values in MPEG-2 timestamp units.
   *
   * @param us A value in microseconds.
   * @return The corresponding value in MPEG-2 timestamp units.
   */
  public static long usToPts(long us) {
    return (us * 90000) / C.MICROS_PER_SECOND;
  }

}
