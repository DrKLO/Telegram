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
package com.google.android.exoplayer2.trackselection;

import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A track selection consisting of a static subset of selected tracks belonging to a {@link
 * TrackGroup}.
 *
 * <p>Tracks belonging to the subset are exposed in decreasing bandwidth order.
 */
public interface TrackSelection {

  /**
   * Represents a type track selection. Either {@link #TYPE_UNSET} or an app-defined value (see
   * {@link #TYPE_CUSTOM_BASE}).
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      open = true,
      value = {TYPE_UNSET})
  @interface Type {}
  /** An unspecified track selection type. */
  int TYPE_UNSET = 0;
  /** The first value that can be used for application specific track selection types. */
  int TYPE_CUSTOM_BASE = 10000;

  /**
   * Returns an integer specifying the type of the selection, or {@link #TYPE_UNSET} if not
   * specified.
   *
   * <p>Track selection types are specific to individual applications, but should be defined
   * starting from {@link #TYPE_CUSTOM_BASE} to ensure they don't conflict with any types that may
   * be added to the library in the future.
   */
  @Type
  int getType();

  /** Returns the {@link TrackGroup} to which the selected tracks belong. */
  TrackGroup getTrackGroup();

  // Static subset of selected tracks.

  /** Returns the number of tracks in the selection. */
  int length();

  /**
   * Returns the format of the track at a given index in the selection.
   *
   * @param index The index in the selection.
   * @return The format of the selected track.
   */
  Format getFormat(int index);

  /**
   * Returns the index in the track group of the track at a given index in the selection.
   *
   * @param index The index in the selection.
   * @return The index of the selected track.
   */
  int getIndexInTrackGroup(int index);

  /**
   * Returns the index in the selection of the track with the specified format. The format is
   * located by identity so, for example, {@code selection.indexOf(selection.getFormat(index)) ==
   * index} even if multiple selected tracks have formats that contain the same values.
   *
   * @param format The format.
   * @return The index in the selection, or {@link C#INDEX_UNSET} if the track with the specified
   *     format is not part of the selection.
   */
  int indexOf(Format format);

  /**
   * Returns the index in the selection of the track with the specified index in the track group.
   *
   * @param indexInTrackGroup The index in the track group.
   * @return The index in the selection, or {@link C#INDEX_UNSET} if the track with the specified
   *     index is not part of the selection.
   */
  int indexOf(int indexInTrackGroup);
}
