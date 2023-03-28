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
package com.google.android.exoplayer2.source.ads;

import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ForwardingTimeline;
import com.google.android.exoplayer2.util.Assertions;

/** A {@link Timeline} for sources that have ads. */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public final class SinglePeriodAdTimeline extends ForwardingTimeline {

  private final AdPlaybackState adPlaybackState;

  /**
   * Creates a new timeline with a single period containing ads.
   *
   * @param contentTimeline The timeline of the content alongside which ads will be played. It must
   *     have one window and one period.
   * @param adPlaybackState The state of the period's ads.
   */
  public SinglePeriodAdTimeline(Timeline contentTimeline, AdPlaybackState adPlaybackState) {
    super(contentTimeline);
    Assertions.checkState(contentTimeline.getPeriodCount() == 1);
    Assertions.checkState(contentTimeline.getWindowCount() == 1);
    this.adPlaybackState = adPlaybackState;
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    timeline.getPeriod(periodIndex, period, setIds);
    long durationUs =
        period.durationUs == C.TIME_UNSET ? adPlaybackState.contentDurationUs : period.durationUs;
    period.set(
        period.id,
        period.uid,
        period.windowIndex,
        durationUs,
        period.getPositionInWindowUs(),
        adPlaybackState,
        period.isPlaceholder);
    return period;
  }
}
