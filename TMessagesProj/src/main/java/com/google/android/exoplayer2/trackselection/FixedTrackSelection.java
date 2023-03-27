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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import java.util.List;

/** A {@link TrackSelection} consisting of a single track. */
public final class FixedTrackSelection extends BaseTrackSelection {

  private final @C.SelectionReason int reason;
  @Nullable private final Object data;

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param track The index of the selected track within the {@link TrackGroup}.
   */
  public FixedTrackSelection(TrackGroup group, int track) {
    this(group, /* track= */ track, /* type= */ TrackSelection.TYPE_UNSET);
  }

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param track The index of the selected track within the {@link TrackGroup}.
   * @param type The type that will be returned from {@link TrackSelection#getType()}.
   */
  public FixedTrackSelection(TrackGroup group, int track, @Type int type) {
    this(group, track, type, C.SELECTION_REASON_UNKNOWN, /* data= */ null);
  }

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param track The index of the selected track within the {@link TrackGroup}.
   * @param type The type that will be returned from {@link TrackSelection#getType()}.
   * @param reason A reason for the track selection.
   * @param data Optional data associated with the track selection.
   */
  public FixedTrackSelection(
      TrackGroup group,
      int track,
      @Type int type,
      @C.SelectionReason int reason,
      @Nullable Object data) {
    super(group, /* tracks= */ new int[] {track}, type);
    this.reason = reason;
    this.data = data;
  }

  @Override
  public void updateSelectedTrack(
      long playbackPositionUs,
      long bufferedDurationUs,
      long availableDurationUs,
      List<? extends MediaChunk> queue,
      MediaChunkIterator[] mediaChunkIterators) {
    // Do nothing.
  }

  @Override
  public int getSelectedIndex() {
    return 0;
  }

  @Override
  public @C.SelectionReason int getSelectionReason() {
    return reason;
  }

  @Override
  @Nullable
  public Object getSelectionData() {
    return data;
  }
}
