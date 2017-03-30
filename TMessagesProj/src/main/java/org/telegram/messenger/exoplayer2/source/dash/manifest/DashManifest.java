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
package org.telegram.messenger.exoplayer2.source.dash.manifest;

import android.net.Uri;
import org.telegram.messenger.exoplayer2.C;
import java.util.Collections;
import java.util.List;

/**
 * Represents a DASH media presentation description (mpd).
 */
public class DashManifest {

  public final long availabilityStartTime;

  public final long duration;

  public final long minBufferTime;

  public final boolean dynamic;

  public final long minUpdatePeriod;

  public final long timeShiftBufferDepth;

  public final long suggestedPresentationDelay;

  public final UtcTimingElement utcTiming;

  public final Uri location;

  private final List<Period> periods;

  public DashManifest(long availabilityStartTime, long duration, long minBufferTime,
      boolean dynamic, long minUpdatePeriod, long timeShiftBufferDepth,
      long suggestedPresentationDelay, UtcTimingElement utcTiming, Uri location,
      List<Period> periods) {
    this.availabilityStartTime = availabilityStartTime;
    this.duration = duration;
    this.minBufferTime = minBufferTime;
    this.dynamic = dynamic;
    this.minUpdatePeriod = minUpdatePeriod;
    this.timeShiftBufferDepth = timeShiftBufferDepth;
    this.suggestedPresentationDelay = suggestedPresentationDelay;
    this.utcTiming = utcTiming;
    this.location = location;
    this.periods = periods == null ? Collections.<Period>emptyList() : periods;
  }

  public final int getPeriodCount() {
    return periods.size();
  }

  public final Period getPeriod(int index) {
    return periods.get(index);
  }

  public final long getPeriodDurationMs(int index) {
    return index == periods.size() - 1
        ? (duration == C.TIME_UNSET ? C.TIME_UNSET : (duration - periods.get(index).startMs))
        : (periods.get(index + 1).startMs - periods.get(index).startMs);
  }

  public final long getPeriodDurationUs(int index) {
    return C.msToUs(getPeriodDurationMs(index));
  }

}
