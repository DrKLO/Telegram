/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/**
 * Parameters that apply to seeking.
 *
 * <p>The predefined {@link #EXACT}, {@link #CLOSEST_SYNC}, {@link #PREVIOUS_SYNC} and {@link
 * #NEXT_SYNC} parameters are suitable for most use cases. Seeking to sync points is typically
 * faster but less accurate than exact seeking.
 *
 * <p>In the general case, an instance specifies a maximum tolerance before ({@link
 * #toleranceBeforeUs}) and after ({@link #toleranceAfterUs}) a requested seek position ({@code x}).
 * If one or more sync points falls within the window {@code [x - toleranceBeforeUs, x +
 * toleranceAfterUs]} then the seek will be performed to the sync point within the window that's
 * closest to {@code x}. If no sync point falls within the window then the seek will be performed to
 * {@code x - toleranceBeforeUs}. Internally the player may need to seek to an earlier sync point
 * and discard media until this position is reached.
 */
public final class SeekParameters {

  /** Parameters for exact seeking. */
  public static final SeekParameters EXACT = new SeekParameters(0, 0);
  /** Parameters for seeking to the closest sync point. */
  public static final SeekParameters CLOSEST_SYNC =
      new SeekParameters(Long.MAX_VALUE, Long.MAX_VALUE);
  /** Parameters for seeking to the sync point immediately before a requested seek position. */
  public static final SeekParameters PREVIOUS_SYNC = new SeekParameters(Long.MAX_VALUE, 0);
  /** Parameters for seeking to the sync point immediately after a requested seek position. */
  public static final SeekParameters NEXT_SYNC = new SeekParameters(0, Long.MAX_VALUE);
  /** Default parameters. */
  public static final SeekParameters DEFAULT = EXACT;

  /**
   * The maximum time that the actual position seeked to may precede the requested seek position, in
   * microseconds.
   */
  public final long toleranceBeforeUs;
  /**
   * The maximum time that the actual position seeked to may exceed the requested seek position, in
   * microseconds.
   */
  public final long toleranceAfterUs;

  /**
   * @param toleranceBeforeUs The maximum time that the actual position seeked to may precede the
   *     requested seek position, in microseconds. Must be non-negative.
   * @param toleranceAfterUs The maximum time that the actual position seeked to may exceed the
   *     requested seek position, in microseconds. Must be non-negative.
   */
  public SeekParameters(long toleranceBeforeUs, long toleranceAfterUs) {
    Assertions.checkArgument(toleranceBeforeUs >= 0);
    Assertions.checkArgument(toleranceAfterUs >= 0);
    this.toleranceBeforeUs = toleranceBeforeUs;
    this.toleranceAfterUs = toleranceAfterUs;
  }

  /**
   * Resolves a seek based on the parameters, given the requested seek position and two candidate
   * sync points.
   *
   * @param positionUs The requested seek position, in microseocnds.
   * @param firstSyncUs The first candidate seek point, in micrseconds.
   * @param secondSyncUs The second candidate seek point, in microseconds. May equal {@code
   *     firstSyncUs} if there's only one candidate.
   * @return The resolved seek position, in microseconds.
   */
  public long resolveSeekPositionUs(long positionUs, long firstSyncUs, long secondSyncUs) {
    if (toleranceBeforeUs == 0 && toleranceAfterUs == 0) {
      return positionUs;
    }
    long minPositionUs =
        Util.subtractWithOverflowDefault(positionUs, toleranceBeforeUs, Long.MIN_VALUE);
    long maxPositionUs = Util.addWithOverflowDefault(positionUs, toleranceAfterUs, Long.MAX_VALUE);
    boolean firstSyncPositionValid = minPositionUs <= firstSyncUs && firstSyncUs <= maxPositionUs;
    boolean secondSyncPositionValid =
        minPositionUs <= secondSyncUs && secondSyncUs <= maxPositionUs;
    if (firstSyncPositionValid && secondSyncPositionValid) {
      if (Math.abs(firstSyncUs - positionUs) <= Math.abs(secondSyncUs - positionUs)) {
        return firstSyncUs;
      } else {
        return secondSyncUs;
      }
    } else if (firstSyncPositionValid) {
      return firstSyncUs;
    } else if (secondSyncPositionValid) {
      return secondSyncUs;
    } else {
      return minPositionUs;
    }
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    SeekParameters other = (SeekParameters) obj;
    return toleranceBeforeUs == other.toleranceBeforeUs
        && toleranceAfterUs == other.toleranceAfterUs;
  }

  @Override
  public int hashCode() {
    return (31 * (int) toleranceBeforeUs) + (int) toleranceAfterUs;
  }
}
