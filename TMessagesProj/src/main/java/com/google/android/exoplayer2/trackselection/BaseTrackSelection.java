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

import static java.lang.Math.max;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;
import java.util.List;

/** An abstract base class suitable for most {@link ExoTrackSelection} implementations. */
public abstract class BaseTrackSelection implements ExoTrackSelection {

  /** The selected {@link TrackGroup}. */
  protected final TrackGroup group;
  /** The number of selected tracks within the {@link TrackGroup}. Always greater than zero. */
  protected final int length;
  /** The indices of the selected tracks in {@link #group}, in order of decreasing bandwidth. */
  protected final int[] tracks;

  /** The type of the selection. */
  private final @Type int type;
  /** The {@link Format}s of the selected tracks, in order of decreasing bandwidth. */
  private final Format[] formats;
  /** Selected track exclusion timestamps, in order of decreasing bandwidth. */
  private final long[] excludeUntilTimes;

  // Lazily initialized hashcode.
  private int hashCode;

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
   *     null or empty. May be in any order.
   */
  public BaseTrackSelection(TrackGroup group, int... tracks) {
    this(group, tracks, TrackSelection.TYPE_UNSET);
  }

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
   *     null or empty. May be in any order.
   * @param type The type that will be returned from {@link TrackSelection#getType()}.
   */
  public BaseTrackSelection(TrackGroup group, int[] tracks, @Type int type) {
    Assertions.checkState(tracks.length > 0);
    this.type = type;
    this.group = Assertions.checkNotNull(group);
    this.length = tracks.length;
    // Set the formats, sorted in order of decreasing bandwidth.
    formats = new Format[length];
    for (int i = 0; i < tracks.length; i++) {
      formats[i] = group.getFormat(tracks[i]);
    }
    // Sort in order of decreasing bandwidth.
    Arrays.sort(formats, (a, b) -> b.bitrate - a.bitrate);
    // Set the format indices in the same order.
    this.tracks = new int[length];
    for (int i = 0; i < length; i++) {
      this.tracks[i] = group.indexOf(formats[i]);
    }
    excludeUntilTimes = new long[length];
  }

  // TrackSelection implementation.

  @Override
  public final int getType() {
    return type;
  }

  @Override
  public final TrackGroup getTrackGroup() {
    return group;
  }

  @Override
  public final int length() {
    return tracks.length;
  }

  @Override
  public final Format getFormat(int index) {
    return formats[index];
  }

  @Override
  public final int getIndexInTrackGroup(int index) {
    return tracks[index];
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public final int indexOf(Format format) {
    for (int i = 0; i < length; i++) {
      if (formats[i] == format) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  @Override
  public final int indexOf(int indexInTrackGroup) {
    for (int i = 0; i < length; i++) {
      if (tracks[i] == indexInTrackGroup) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  // ExoTrackSelection specific methods.

  @Override
  public final Format getSelectedFormat() {
    return formats[getSelectedIndex()];
  }

  @Override
  public final int getSelectedIndexInTrackGroup() {
    return tracks[getSelectedIndex()];
  }

  @Override
  public void enable() {
    // Do nothing.
  }

  @Override
  public void disable() {
    // Do nothing.
  }

  @Override
  public void onPlaybackSpeed(float playbackSpeed) {
    // Do nothing.
  }

  @Override
  public int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
    return queue.size();
  }

  @Override
  public boolean blacklist(int index, long exclusionDurationMs) {
    long nowMs = SystemClock.elapsedRealtime();
    boolean canExclude = isBlacklisted(index, nowMs);
    for (int i = 0; i < length && !canExclude; i++) {
      canExclude = i != index && !isBlacklisted(i, nowMs);
    }
    if (!canExclude) {
      return false;
    }
    excludeUntilTimes[index] =
        max(
            excludeUntilTimes[index],
            Util.addWithOverflowDefault(nowMs, exclusionDurationMs, Long.MAX_VALUE));
    return true;
  }

  @Override
  public boolean isBlacklisted(int index, long nowMs) {
    return excludeUntilTimes[index] > nowMs;
  }

  // Object overrides.

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      hashCode = 31 * System.identityHashCode(group) + Arrays.hashCode(tracks);
    }
    return hashCode;
  }

  // Track groups are compared by identity not value, as distinct groups may have the same value.
  @Override
  @SuppressWarnings({"ReferenceEquality", "EqualsGetClass"})
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    BaseTrackSelection other = (BaseTrackSelection) obj;
    return group == other.group && Arrays.equals(tracks, other.tracks);
  }
}
