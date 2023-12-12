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
package com.google.android.exoplayer2.util;

import androidx.annotation.GuardedBy;
import com.google.android.exoplayer2.C;

/**
 * Adjusts and offsets sample timestamps. MPEG-2 TS timestamps scaling and adjustment is supported,
 * taking into account timestamp rollover.
 */
public final class TimestampAdjuster {

  /**
   * A special {@code firstSampleTimestampUs} value indicating that presentation timestamps should
   * not be offset. In this mode:
   *
   * <ul>
   *   <li>{@link #getFirstSampleTimestampUs()} will always return {@link C#TIME_UNSET}.
   *   <li>The only timestamp adjustment performed is to account for MPEG-2 TS timestamp rollover.
   * </ul>
   */
  public static final long MODE_NO_OFFSET = Long.MAX_VALUE;

  /**
   * A special {@code firstSampleTimestampUs} value indicating that the adjuster will be shared by
   * multiple threads. In this mode:
   *
   * <ul>
   *   <li>{@link #getFirstSampleTimestampUs()} will always return {@link C#TIME_UNSET}.
   *   <li>Calling threads must call {@link #sharedInitializeOrWait} prior to adjusting timestamps.
   * </ul>
   */
  public static final long MODE_SHARED = Long.MAX_VALUE - 1;

  /**
   * The value one greater than the largest representable (33 bit) MPEG-2 TS 90 kHz clock
   * presentation timestamp.
   */
  private static final long MAX_PTS_PLUS_ONE = 0x200000000L;

  @GuardedBy("this")
  private long firstSampleTimestampUs;

  @GuardedBy("this")
  private long timestampOffsetUs;

  @GuardedBy("this")
  private long lastUnadjustedTimestampUs;

  /**
   * Next sample timestamps for calling threads in shared mode when {@link #timestampOffsetUs} has
   * not yet been set.
   */
  // incompatible type argument for type parameter T of ThreadLocal.
  @SuppressWarnings("nullness:type.argument.type.incompatible")
  private final ThreadLocal<Long> nextSampleTimestampUs;

  /**
   * @param firstSampleTimestampUs The desired value of the first adjusted sample timestamp in
   *     microseconds, or {@link #MODE_NO_OFFSET} if timestamps should not be offset, or {@link
   *     #MODE_SHARED} if the adjuster will be used in shared mode.
   */
  // incompatible types in assignment.
  @SuppressWarnings("nullness:assignment.type.incompatible")
  public TimestampAdjuster(long firstSampleTimestampUs) {
    nextSampleTimestampUs = new ThreadLocal<>();
    reset(firstSampleTimestampUs);
  }

  /**
   * For shared timestamp adjusters, performs necessary initialization actions for a caller.
   *
   * <ul>
   *   <li>If the adjuster has already established a {@link #getTimestampOffsetUs timestamp offset}
   *       then this method is a no-op.
   *   <li>If {@code canInitialize} is {@code true} and the adjuster has not yet established a
   *       timestamp offset, then the adjuster records the desired first sample timestamp for the
   *       calling thread and returns to allow the caller to proceed. If the timestamp offset has
   *       still not been established when the caller attempts to adjust its first timestamp, then
   *       the recorded timestamp is used to set it.
   *   <li>If {@code canInitialize} is {@code false} and the adjuster has not yet established a
   *       timestamp offset, then the call blocks until the timestamp offset is set.
   * </ul>
   *
   * @param canInitialize Whether the caller is able to initialize the adjuster, if needed.
   * @param nextSampleTimestampUs The desired timestamp for the next sample loaded by the calling
   *     thread, in microseconds. Only used if {@code canInitialize} is {@code true}.
   * @throws InterruptedException If the thread is interrupted whilst blocked waiting for
   *     initialization to complete.
   */
  public synchronized void sharedInitializeOrWait(boolean canInitialize, long nextSampleTimestampUs)
      throws InterruptedException {
    Assertions.checkState(firstSampleTimestampUs == MODE_SHARED);
    if (timestampOffsetUs != C.TIME_UNSET) {
      // Already initialized.
      return;
    } else if (canInitialize) {
      this.nextSampleTimestampUs.set(nextSampleTimestampUs);
    } else {
      // Wait for another calling thread to complete initialization.
      while (timestampOffsetUs == C.TIME_UNSET) {
        wait();
      }
    }
  }

  /**
   * Returns the value of the first adjusted sample timestamp in microseconds, or {@link
   * C#TIME_UNSET} if timestamps will not be offset or if the adjuster is in shared mode.
   */
  public synchronized long getFirstSampleTimestampUs() {
    return firstSampleTimestampUs == MODE_NO_OFFSET || firstSampleTimestampUs == MODE_SHARED
        ? C.TIME_UNSET
        : firstSampleTimestampUs;
  }

  /**
   * Returns the last adjusted timestamp, in microseconds. If no timestamps have been adjusted yet
   * then the result of {@link #getFirstSampleTimestampUs()} is returned.
   */
  public synchronized long getLastAdjustedTimestampUs() {
    return lastUnadjustedTimestampUs != C.TIME_UNSET
        ? lastUnadjustedTimestampUs + timestampOffsetUs
        : getFirstSampleTimestampUs();
  }

  /**
   * Returns the offset between the input of {@link #adjustSampleTimestamp(long)} and its output, or
   * {@link C#TIME_UNSET} if the offset has not yet been determined.
   */
  public synchronized long getTimestampOffsetUs() {
    return timestampOffsetUs;
  }

  /**
   * Resets the instance.
   *
   * @param firstSampleTimestampUs The desired value of the first adjusted sample timestamp after
   *     this reset in microseconds, or {@link #MODE_NO_OFFSET} if timestamps should not be offset,
   *     or {@link #MODE_SHARED} if the adjuster will be used in shared mode.
   */
  public synchronized void reset(long firstSampleTimestampUs) {
    this.firstSampleTimestampUs = firstSampleTimestampUs;
    timestampOffsetUs = firstSampleTimestampUs == MODE_NO_OFFSET ? 0 : C.TIME_UNSET;
    lastUnadjustedTimestampUs = C.TIME_UNSET;
  }

  /**
   * Scales and offsets an MPEG-2 TS presentation timestamp considering wraparound.
   *
   * @param pts90Khz A 90 kHz clock MPEG-2 TS presentation timestamp.
   * @return The adjusted timestamp in microseconds.
   */
  public synchronized long adjustTsTimestamp(long pts90Khz) {
    if (pts90Khz == C.TIME_UNSET) {
      return C.TIME_UNSET;
    }
    if (lastUnadjustedTimestampUs != C.TIME_UNSET) {
      // The wrap count for the current PTS may be closestWrapCount or (closestWrapCount - 1),
      // and we need to snap to the one closest to lastSampleTimestampUs.
      long lastPts = usToNonWrappedPts(lastUnadjustedTimestampUs);
      long closestWrapCount = (lastPts + (MAX_PTS_PLUS_ONE / 2)) / MAX_PTS_PLUS_ONE;
      long ptsWrapBelow = pts90Khz + (MAX_PTS_PLUS_ONE * (closestWrapCount - 1));
      long ptsWrapAbove = pts90Khz + (MAX_PTS_PLUS_ONE * closestWrapCount);
      pts90Khz =
          Math.abs(ptsWrapBelow - lastPts) < Math.abs(ptsWrapAbove - lastPts)
              ? ptsWrapBelow
              : ptsWrapAbove;
    }
    return adjustSampleTimestamp(ptsToUs(pts90Khz));
  }

  /**
   * Offsets a timestamp in microseconds.
   *
   * @param timeUs The timestamp to adjust in microseconds.
   * @return The adjusted timestamp in microseconds.
   */
  public synchronized long adjustSampleTimestamp(long timeUs) {
    if (timeUs == C.TIME_UNSET) {
      return C.TIME_UNSET;
    }
    if (timestampOffsetUs == C.TIME_UNSET) {
      long desiredSampleTimestampUs =
          firstSampleTimestampUs == MODE_SHARED
              ? Assertions.checkNotNull(nextSampleTimestampUs.get())
              : firstSampleTimestampUs;
      timestampOffsetUs = desiredSampleTimestampUs - timeUs;
      // Notify threads waiting for the timestamp offset to be determined.
      notifyAll();
    }
    lastUnadjustedTimestampUs = timeUs;
    return timeUs + timestampOffsetUs;
  }

  /**
   * Converts a 90 kHz clock timestamp to a timestamp in microseconds.
   *
   * @param pts A 90 kHz clock timestamp.
   * @return The corresponding value in microseconds.
   */
  public static long ptsToUs(long pts) {
    return (pts * C.MICROS_PER_SECOND) / 90000;
  }

  /**
   * Converts a timestamp in microseconds to a 90 kHz clock timestamp, performing wraparound to keep
   * the result within 33-bits.
   *
   * @param us A value in microseconds.
   * @return The corresponding value as a 90 kHz clock timestamp, wrapped to 33 bits.
   */
  public static long usToWrappedPts(long us) {
    return usToNonWrappedPts(us) % MAX_PTS_PLUS_ONE;
  }

  /**
   * Converts a timestamp in microseconds to a 90 kHz clock timestamp.
   *
   * <p>Does not perform any wraparound. To get a 90 kHz timestamp suitable for use with MPEG-TS,
   * use {@link #usToWrappedPts(long)}.
   *
   * @param us A value in microseconds.
   * @return The corresponding value as a 90 kHz clock timestamp.
   */
  public static long usToNonWrappedPts(long us) {
    return (us * 90000) / C.MICROS_PER_SECOND;
  }
}
