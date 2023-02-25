/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.util.Collections.max;
import static java.util.Collections.min;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Bundleable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.util.List;

/**
 * A track selection override, consisting of a {@link TrackGroup} and the indices of the tracks
 * within the group that should be selected.
 *
 * <p>A track selection override is applied during playback if the media being played contains a
 * {@link TrackGroup} equal to the one in the override. If a {@link TrackSelectionParameters}
 * contains only one override of a given track type that applies to the media, this override will be
 * used to control the track selection for that type. If multiple overrides of a given track type
 * apply then the player will apply only one of them.
 *
 * <p>If {@link #trackIndices} is empty then the override specifies that no tracks should be
 * selected. Adding an empty override to a {@link TrackSelectionParameters} is similar to {@link
 * TrackSelectionParameters.Builder#setTrackTypeDisabled disabling a track type}, except that an
 * empty override will only be applied if the media being played contains a {@link TrackGroup} equal
 * to the one in the override. Conversely, disabling a track type will prevent selection of tracks
 * of that type for all media.
 */
public final class TrackSelectionOverride implements Bundleable {

  /** The media {@link TrackGroup} whose {@link #trackIndices} are forced to be selected. */
  public final TrackGroup mediaTrackGroup;
  /** The indices of tracks in a {@link TrackGroup} to be selected. */
  public final ImmutableList<Integer> trackIndices;

  private static final String FIELD_TRACK_GROUP = Util.intToStringMaxRadix(0);
  private static final String FIELD_TRACKS = Util.intToStringMaxRadix(1);

  /**
   * Constructs an instance to force {@code trackIndex} in {@code trackGroup} to be selected.
   *
   * @param mediaTrackGroup The media {@link TrackGroup} for which to override the track selection.
   * @param trackIndex The index of the track in the {@link TrackGroup} to select.
   */
  public TrackSelectionOverride(TrackGroup mediaTrackGroup, int trackIndex) {
    this(mediaTrackGroup, ImmutableList.of(trackIndex));
  }

  /**
   * Constructs an instance to force {@code trackIndices} in {@code trackGroup} to be selected.
   *
   * @param mediaTrackGroup The media {@link TrackGroup} for which to override the track selection.
   * @param trackIndices The indices of the tracks in the {@link TrackGroup} to select.
   */
  public TrackSelectionOverride(TrackGroup mediaTrackGroup, List<Integer> trackIndices) {
    if (!trackIndices.isEmpty()) {
      if (min(trackIndices) < 0 || max(trackIndices) >= mediaTrackGroup.length) {
        throw new IndexOutOfBoundsException();
      }
    }
    this.mediaTrackGroup = mediaTrackGroup;
    this.trackIndices = ImmutableList.copyOf(trackIndices);
  }

  /** Returns the {@link C.TrackType} of the overridden track group. */
  public @C.TrackType int getType() {
    return mediaTrackGroup.type;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TrackSelectionOverride that = (TrackSelectionOverride) obj;
    return mediaTrackGroup.equals(that.mediaTrackGroup) && trackIndices.equals(that.trackIndices);
  }

  @Override
  public int hashCode() {
    return mediaTrackGroup.hashCode() + 31 * trackIndices.hashCode();
  }

  // Bundleable implementation

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putBundle(FIELD_TRACK_GROUP, mediaTrackGroup.toBundle());
    bundle.putIntArray(FIELD_TRACKS, Ints.toArray(trackIndices));
    return bundle;
  }

  /** Object that can restore {@code TrackSelectionOverride} from a {@link Bundle}. */
  public static final Creator<TrackSelectionOverride> CREATOR =
      bundle -> {
        Bundle trackGroupBundle = checkNotNull(bundle.getBundle(FIELD_TRACK_GROUP));
        TrackGroup mediaTrackGroup = TrackGroup.CREATOR.fromBundle(trackGroupBundle);
        int[] tracks = checkNotNull(bundle.getIntArray(FIELD_TRACKS));
        return new TrackSelectionOverride(mediaTrackGroup, Ints.asList(tracks));
      };
}
