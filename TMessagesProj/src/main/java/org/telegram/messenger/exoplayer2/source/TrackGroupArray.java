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
package org.telegram.messenger.exoplayer2.source;

import org.telegram.messenger.exoplayer2.C;
import java.util.Arrays;

/**
 * An array of {@link TrackGroup}s exposed by a {@link MediaPeriod}.
 */
public final class TrackGroupArray {

  /**
   * The empty array.
   */
  public static final TrackGroupArray EMPTY = new TrackGroupArray();

  /**
   * The number of groups in the array. Greater than or equal to zero.
   */
  public final int length;

  private final TrackGroup[] trackGroups;

  // Lazily initialized hashcode.
  private int hashCode;

  /**
   * @param trackGroups The groups. Must not be null or contain null elements, but may be empty.
   */
  public TrackGroupArray(TrackGroup... trackGroups) {
    this.trackGroups = trackGroups;
    this.length = trackGroups.length;
  }

  /**
   * Returns the group at a given index.
   *
   * @param index The index of the group.
   * @return The group.
   */
  public TrackGroup get(int index) {
    return trackGroups[index];
  }

  /**
   * Returns the index of a group within the array.
   *
   * @param group The group.
   * @return The index of the group, or {@link C#INDEX_UNSET} if no such group exists.
   */
  public int indexOf(TrackGroup group) {
    for (int i = 0; i < length; i++) {
      if (trackGroups[i] == group) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      hashCode = Arrays.hashCode(trackGroups);
    }
    return hashCode;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TrackGroupArray other = (TrackGroupArray) obj;
    return length == other.length && Arrays.equals(trackGroups, other.trackGroups);
  }

}
