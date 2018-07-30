/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.telegram.messenger.exoplayer2.source.dash.manifest;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/** Uniquely identifies a {@link Representation} in a {@link DashManifest}. */
public final class RepresentationKey implements Comparable<RepresentationKey> {

  public final int periodIndex;
  public final int adaptationSetIndex;
  public final int representationIndex;

  public RepresentationKey(int periodIndex, int adaptationSetIndex, int representationIndex) {
    this.periodIndex = periodIndex;
    this.adaptationSetIndex = adaptationSetIndex;
    this.representationIndex = representationIndex;
  }

  @Override
  public String toString() {
    return periodIndex + "." + adaptationSetIndex + "." + representationIndex;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    RepresentationKey that = (RepresentationKey) o;
    return periodIndex == that.periodIndex
        && adaptationSetIndex == that.adaptationSetIndex
        && representationIndex == that.representationIndex;
  }

  @Override
  public int hashCode() {
    int result = periodIndex;
    result = 31 * result + adaptationSetIndex;
    result = 31 * result + representationIndex;
    return result;
  }

  // Comparable implementation.

  @Override
  public int compareTo(@NonNull RepresentationKey o) {
    int result = periodIndex - o.periodIndex;
    if (result == 0) {
      result = adaptationSetIndex - o.adaptationSetIndex;
      if (result == 0) {
        result = representationIndex - o.representationIndex;
      }
    }
    return result;
  }

}
