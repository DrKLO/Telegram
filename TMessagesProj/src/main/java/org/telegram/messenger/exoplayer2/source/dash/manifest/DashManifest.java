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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
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

  /**
   * Creates a copy of this manifest which includes only the representations identified by the given
   * keys.
   *
   * @param representationKeys List of keys for the representations to be included in the copy.
   * @return A copy of this manifest with the selected representations.
   * @throws IndexOutOfBoundsException If a key has an invalid index.
   */
  public final DashManifest copy(List<RepresentationKey> representationKeys) {
    LinkedList<RepresentationKey> keys = new LinkedList<>(representationKeys);
    Collections.sort(keys);
    keys.add(new RepresentationKey(-1, -1, -1)); // Add a stopper key to the end

    ArrayList<Period> copyPeriods = new ArrayList<>();
    long shiftMs = 0;
    for (int periodIndex = 0; periodIndex < getPeriodCount(); periodIndex++) {
      if (keys.peek().periodIndex != periodIndex) {
        // No representations selected in this period.
        long periodDurationMs = getPeriodDurationMs(periodIndex);
        if (periodDurationMs != C.TIME_UNSET) {
          shiftMs += periodDurationMs;
        }
      } else {
        Period period = getPeriod(periodIndex);
        ArrayList<AdaptationSet> copyAdaptationSets =
            copyAdaptationSets(period.adaptationSets, keys);
        copyPeriods.add(new Period(period.id, period.startMs - shiftMs, copyAdaptationSets));
      }
    }
    long newDuration = duration != C.TIME_UNSET ? duration - shiftMs : C.TIME_UNSET;
    return new DashManifest(availabilityStartTime, newDuration, minBufferTime, dynamic,
        minUpdatePeriod, timeShiftBufferDepth, suggestedPresentationDelay, utcTiming, location,
        copyPeriods);
  }

  private static ArrayList<AdaptationSet> copyAdaptationSets(
      List<AdaptationSet> adaptationSets, LinkedList<RepresentationKey> keys) {
    RepresentationKey key = keys.poll();
    int periodIndex = key.periodIndex;
    ArrayList<AdaptationSet> copyAdaptationSets = new ArrayList<>();
    do {
      int adaptationSetIndex = key.adaptationSetIndex;
      AdaptationSet adaptationSet = adaptationSets.get(adaptationSetIndex);

      List<Representation> representations = adaptationSet.representations;
      ArrayList<Representation> copyRepresentations = new ArrayList<>();
      do {
        Representation representation = representations.get(key.representationIndex);
        copyRepresentations.add(representation);
        key = keys.poll();
      } while(key.periodIndex == periodIndex && key.adaptationSetIndex == adaptationSetIndex);

      copyAdaptationSets.add(new AdaptationSet(adaptationSet.id, adaptationSet.type,
          copyRepresentations, adaptationSet.accessibilityDescriptors));
    } while(key.periodIndex == periodIndex);
    // Add back the last key which doesn't belong to the period being processed
    keys.addFirst(key);
    return copyAdaptationSets;
  }

}
