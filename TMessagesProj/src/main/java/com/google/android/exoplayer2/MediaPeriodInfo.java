/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;

/** Stores the information required to load and play a {@link MediaPeriod}. */
/* package */ final class MediaPeriodInfo {

  /** The media period's identifier. */
  public final MediaPeriodId id;
  /** The start position of the media to play within the media period, in microseconds. */
  public final long startPositionUs;
  /**
   * If this is an ad, the position to play in the next content media period. {@link C#TIME_UNSET}
   * otherwise.
   */
  public final long contentPositionUs;
  /**
   * The duration of the media period, like {@link MediaPeriodId#endPositionUs} but with {@link
   * C#TIME_END_OF_SOURCE} resolved to the timeline period duration. May be {@link C#TIME_UNSET} if
   * the end position is not known.
   */
  public final long durationUs;
  /**
   * Whether this is the last media period in its timeline period (e.g., a postroll ad, or a media
   * period corresponding to a timeline period without ads).
   */
  public final boolean isLastInTimelinePeriod;
  /**
   * Whether this is the last media period in the entire timeline. If true, {@link
   * #isLastInTimelinePeriod} will also be true.
   */
  public final boolean isFinal;

  MediaPeriodInfo(
      MediaPeriodId id,
      long startPositionUs,
      long contentPositionUs,
      long durationUs,
      boolean isLastInTimelinePeriod,
      boolean isFinal) {
    this.id = id;
    this.startPositionUs = startPositionUs;
    this.contentPositionUs = contentPositionUs;
    this.durationUs = durationUs;
    this.isLastInTimelinePeriod = isLastInTimelinePeriod;
    this.isFinal = isFinal;
  }

  /**
   * Returns a copy of this instance with the period identifier's period index set to the specified
   * value.
   */
  public MediaPeriodInfo copyWithPeriodIndex(int periodIndex) {
    return new MediaPeriodInfo(
        id.copyWithPeriodIndex(periodIndex),
        startPositionUs,
        contentPositionUs,
        durationUs,
        isLastInTimelinePeriod,
        isFinal);
  }

  /** Returns a copy of this instance with the start position set to the specified value. */
  public MediaPeriodInfo copyWithStartPositionUs(long startPositionUs) {
    return new MediaPeriodInfo(
        id,
        startPositionUs,
        contentPositionUs,
        durationUs,
        isLastInTimelinePeriod,
        isFinal);
  }
}
