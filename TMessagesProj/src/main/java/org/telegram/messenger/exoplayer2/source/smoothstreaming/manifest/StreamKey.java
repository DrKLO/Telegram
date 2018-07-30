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
package org.telegram.messenger.exoplayer2.source.smoothstreaming.manifest;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/** Uniquely identifies a track in a {@link SsManifest}. */
public final class StreamKey implements Comparable<StreamKey> {

  public final int streamElementIndex;
  public final int trackIndex;

  public StreamKey(int streamElementIndex, int trackIndex) {
    this.streamElementIndex = streamElementIndex;
    this.trackIndex = trackIndex;
  }

  @Override
  public String toString() {
    return streamElementIndex + "." + trackIndex;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    StreamKey that = (StreamKey) o;
    return streamElementIndex == that.streamElementIndex && trackIndex == that.trackIndex;
  }

  @Override
  public int hashCode() {
    int result = streamElementIndex;
    result = 31 * result + trackIndex;
    return result;
  }

  // Comparable implementation.

  @Override
  public int compareTo(@NonNull StreamKey o) {
    int result = streamElementIndex - o.streamElementIndex;
    if (result == 0) {
      result = trackIndex - o.trackIndex;
    }
    return result;
  }
}
