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
package org.telegram.messenger.exoplayer.dash.mpd;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates media content components over a contiguous period of time.
 */
public class Period {

  /**
   * The period identifier, if one exists.
   */
  public final String id;

  /**
   * The start time of the period in milliseconds.
   */
  public final long startMs;

  /**
   * The adaptation sets belonging to the period.
   */
  public final List<AdaptationSet> adaptationSets;

  /**
   * @param id The period identifier. May be null.
   * @param start The start time of the period in milliseconds.
   * @param adaptationSets The adaptation sets belonging to the period.
   */
  public Period(String id, long start, List<AdaptationSet> adaptationSets) {
    this.id = id;
    this.startMs = start;
    this.adaptationSets = Collections.unmodifiableList(adaptationSets);
  }

  /**
   * Returns the index of the first adaptation set of a given type, or -1 if no adaptation set of
   * the specified type exists.
   *
   * @param type An adaptation set type.
   * @return The index of the first adaptation set of the specified type, or -1.
   */
  public int getAdaptationSetIndex(int type) {
    int adaptationCount = adaptationSets.size();
    for (int i = 0; i < adaptationCount; i++) {
      if (adaptationSets.get(i).type == type) {
        return i;
      }
    }
    return -1;
  }

}
