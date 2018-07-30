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
package org.telegram.messenger.exoplayer2.source.hls.playlist;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Uniquely identifies a rendition in an {@link HlsMasterPlaylist}. */
public final class RenditionKey implements Comparable<RenditionKey> {

  /** Types of rendition. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TYPE_VARIANT, TYPE_AUDIO, TYPE_SUBTITLE})
  public @interface Type {}

  public static final int TYPE_VARIANT = 0;
  public static final int TYPE_AUDIO = 1;
  public static final int TYPE_SUBTITLE = 2;

  public final @Type int type;
  public final int trackIndex;

  public RenditionKey(@Type int type, int trackIndex) {
    this.type = type;
    this.trackIndex = trackIndex;
  }

  @Override
  public String toString() {
    return type + "." + trackIndex;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    RenditionKey that = (RenditionKey) o;
    return type == that.type && trackIndex == that.trackIndex;
  }

  @Override
  public int hashCode() {
    int result = type;
    result = 31 * result + trackIndex;
    return result;
  }

  // Comparable implementation.

  @Override
  public int compareTo(@NonNull RenditionKey other) {
    int result = type - other.type;
    if (result == 0) {
      result = trackIndex - other.trackIndex;
    }
    return result;
  }
}
