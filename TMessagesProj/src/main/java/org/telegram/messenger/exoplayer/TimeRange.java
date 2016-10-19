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
package org.telegram.messenger.exoplayer;

import android.os.SystemClock;
import org.telegram.messenger.exoplayer.util.Clock;

/**
 * A container to store a start and end time in microseconds.
 */
public interface TimeRange {

  /**
   * Whether the range is static, meaning repeated calls to {@link #getCurrentBoundsMs(long[])}
   * or {@link #getCurrentBoundsUs(long[])} will return identical results.
   *
   * @return Whether the range is static.
   */
  public boolean isStatic();

  /**
   * Returns the start and end times (in milliseconds) of the TimeRange in the provided array,
   * or creates a new one.
   *
   * @param out An array to store the start and end times; can be null.
   * @return An array containing the start time (index 0) and end time (index 1) in milliseconds.
   */
  public long[] getCurrentBoundsMs(long[] out);

  /**
   * Returns the start and end times (in microseconds) of the TimeRange in the provided array,
   * or creates a new one.
   *
   * @param out An array to store the start and end times; can be null.
   * @return An array containing the start time (index 0) and end time (index 1) in microseconds.
   */
  public long[] getCurrentBoundsUs(long[] out);

  /**
   * A static {@link TimeRange}.
   */
  public static final class StaticTimeRange implements TimeRange {

    private final long startTimeUs;
    private final long endTimeUs;

    /**
     * @param startTimeUs The beginning of the range.
     * @param endTimeUs The end of the range.
     */
    public StaticTimeRange(long startTimeUs, long endTimeUs) {
      this.startTimeUs = startTimeUs;
      this.endTimeUs = endTimeUs;
    }

    @Override
    public boolean isStatic() {
      return true;
    }

    @Override
    public long[] getCurrentBoundsMs(long[] out) {
      out = getCurrentBoundsUs(out);
      out[0] /= 1000;
      out[1] /= 1000;
      return out;
    }

    @Override
    public long[] getCurrentBoundsUs(long[] out) {
      if (out == null || out.length < 2) {
        out = new long[2];
      }
      out[0] = startTimeUs;
      out[1] = endTimeUs;
      return out;
    }

    @Override
    public int hashCode() {
      int result = 17;
      result = 31 * result + (int) startTimeUs;
      result = 31 * result + (int) endTimeUs;
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      StaticTimeRange other = (StaticTimeRange) obj;
      return other.startTimeUs == startTimeUs
          && other.endTimeUs == endTimeUs;
    }

  }

  /**
   * A dynamic {@link TimeRange}.
   */
  public static final class DynamicTimeRange implements TimeRange {

    private final long minStartTimeUs;
    private final long maxEndTimeUs;
    private final long elapsedRealtimeAtStartUs;
    private final long bufferDepthUs;
    private final Clock systemClock;

    /**
     * @param minStartTimeUs A lower bound on the beginning of the range.
     * @param maxEndTimeUs An upper bound on the end of the range.
     * @param elapsedRealtimeAtStartUs The value of {@link SystemClock#elapsedRealtime()},
     *     multiplied by 1000, corresponding to a media time of zero.
     * @param bufferDepthUs The buffer depth of the media, or -1.
     * @param systemClock A system clock.
     */
    public DynamicTimeRange(long minStartTimeUs, long maxEndTimeUs, long elapsedRealtimeAtStartUs,
        long bufferDepthUs, Clock systemClock) {
      this.minStartTimeUs = minStartTimeUs;
      this.maxEndTimeUs = maxEndTimeUs;
      this.elapsedRealtimeAtStartUs = elapsedRealtimeAtStartUs;
      this.bufferDepthUs = bufferDepthUs;
      this.systemClock = systemClock;
    }

    @Override
    public boolean isStatic() {
      return false;
    }

    @Override
    public long[] getCurrentBoundsMs(long[] out) {
      out = getCurrentBoundsUs(out);
      out[0] /= 1000;
      out[1] /= 1000;
      return out;
    }

    @Override
    public long[] getCurrentBoundsUs(long[] out) {
      if (out == null || out.length < 2) {
        out = new long[2];
      }
      // Don't allow the end time to be greater than the total elapsed time.
      long currentEndTimeUs = Math.min(maxEndTimeUs,
          (systemClock.elapsedRealtime() * 1000) - elapsedRealtimeAtStartUs);
      long currentStartTimeUs = minStartTimeUs;
      if (bufferDepthUs != -1) {
        // Don't allow the start time to be less than the current end time minus the buffer depth.
        currentStartTimeUs = Math.max(currentStartTimeUs,
            currentEndTimeUs - bufferDepthUs);
      }
      out[0] = currentStartTimeUs;
      out[1] = currentEndTimeUs;
      return out;
    }

    @Override
    public int hashCode() {
      int result = 17;
      result = 31 * result + (int) minStartTimeUs;
      result = 31 * result + (int) maxEndTimeUs;
      result = 31 * result + (int) elapsedRealtimeAtStartUs;
      result = 31 * result + (int) bufferDepthUs;
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      DynamicTimeRange other = (DynamicTimeRange) obj;
      return other.minStartTimeUs == minStartTimeUs
          && other.maxEndTimeUs == maxEndTimeUs
          && other.elapsedRealtimeAtStartUs == elapsedRealtimeAtStartUs
          && other.bufferDepthUs == bufferDepthUs;
    }

  }

}
