/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.exoplayer2.extractor;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;

/**
 * A {@link SeekMap} implementation based on a mapping between times and positions in the input
 * stream.
 */
public final class IndexSeekMap implements SeekMap {

  private final long[] positions;
  private final long[] timesUs;
  private final long durationUs;
  private final boolean isSeekable;

  /**
   * Creates an instance.
   *
   * @param positions The positions in the stream corresponding to {@code timesUs}, in bytes.
   * @param timesUs The times corresponding to {@code positions}, in microseconds.
   * @param durationUs The duration of the input stream, or {@link C#TIME_UNSET} if it is unknown.
   */
  public IndexSeekMap(long[] positions, long[] timesUs, long durationUs) {
    checkArgument(positions.length == timesUs.length);
    int length = timesUs.length;
    isSeekable = length > 0;
    if (isSeekable && timesUs[0] > 0) {
      // Add (position = 0, timeUs = 0) as first entry.
      this.positions = new long[length + 1];
      this.timesUs = new long[length + 1];
      System.arraycopy(positions, 0, this.positions, 1, length);
      System.arraycopy(timesUs, 0, this.timesUs, 1, length);
    } else {
      this.positions = positions;
      this.timesUs = timesUs;
    }
    this.durationUs = durationUs;
  }

  @Override
  public boolean isSeekable() {
    return isSeekable;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public SeekMap.SeekPoints getSeekPoints(long timeUs) {
    if (!isSeekable) {
      return new SeekMap.SeekPoints(SeekPoint.START);
    }
    int targetIndex =
        Util.binarySearchFloor(timesUs, timeUs, /* inclusive= */ true, /* stayInBounds= */ true);
    SeekPoint leftSeekPoint = new SeekPoint(timesUs[targetIndex], positions[targetIndex]);
    if (leftSeekPoint.timeUs == timeUs || targetIndex == timesUs.length - 1) {
      return new SeekMap.SeekPoints(leftSeekPoint);
    } else {
      SeekPoint rightSeekPoint =
          new SeekPoint(timesUs[targetIndex + 1], positions[targetIndex + 1]);
      return new SeekMap.SeekPoints(leftSeekPoint, rightSeekPoint);
    }
  }
}
