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
package org.telegram.messenger.exoplayer2.trackselection;

import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.source.TrackGroup;
import org.telegram.messenger.exoplayer2.util.Assertions;

/**
 * A {@link TrackSelection} consisting of a single track.
 */
public final class FixedTrackSelection extends BaseTrackSelection {

  /**
   * Factory for {@link FixedTrackSelection} instances.
   */
  public static final class Factory implements TrackSelection.Factory {

    private final int reason;
    private final Object data;

    public Factory() {
      this.reason = C.SELECTION_REASON_UNKNOWN;
      this.data = null;
    }

    /**
     * @param reason A reason for the track selection.
     * @param data Optional data associated with the track selection.
     */
    public Factory(int reason, Object data) {
      this.reason = reason;
      this.data = data;
    }

    @Override
    public FixedTrackSelection createTrackSelection(TrackGroup group, int... tracks) {
      Assertions.checkArgument(tracks.length == 1);
      return new FixedTrackSelection(group, tracks[0], reason, data);
    }

  }

  private final int reason;
  private final Object data;

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param track The index of the selected track within the {@link TrackGroup}.
   */
  public FixedTrackSelection(TrackGroup group, int track) {
    this(group, track, C.SELECTION_REASON_UNKNOWN, null);
  }

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param track The index of the selected track within the {@link TrackGroup}.
   * @param reason A reason for the track selection.
   * @param data Optional data associated with the track selection.
   */
  public FixedTrackSelection(TrackGroup group, int track, int reason, Object data) {
    super(group, track);
    this.reason = reason;
    this.data = data;
  }

  @Override
  public void updateSelectedTrack(long bufferedDurationUs) {
    // Do nothing.
  }

  @Override
  public int getSelectedIndex() {
    return 0;
  }

  @Override
  public int getSelectionReason() {
    return reason;
  }

  @Override
  public Object getSelectionData() {
    return data;
  }

}
