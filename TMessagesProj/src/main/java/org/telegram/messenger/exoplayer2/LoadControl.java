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
package org.telegram.messenger.exoplayer2;

import org.telegram.messenger.exoplayer2.source.TrackGroup;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelectionArray;
import org.telegram.messenger.exoplayer2.upstream.Allocator;

/**
 * Controls buffering of media.
 */
public interface LoadControl {

  /**
   * Called by the player when prepared with a new source.
   */
  void onPrepared();

  /**
   * Called by the player when a track selection occurs.
   *
   * @param renderers The renderers.
   * @param trackGroups The {@link TrackGroup}s from which the selection was made.
   * @param trackSelections The track selections that were made.
   */
  void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups,
      TrackSelectionArray trackSelections);

  /**
   * Called by the player when stopped.
   */
  void onStopped();

  /**
   * Called by the player when released.
   */
  void onReleased();

  /**
   * Returns the {@link Allocator} that should be used to obtain media buffer allocations.
   */
  Allocator getAllocator();

  /**
   * Called by the player to determine whether sufficient media is buffered for playback to be
   * started or resumed.
   *
   * @param bufferedDurationUs The duration of media that's currently buffered.
   * @param rebuffering Whether the player is rebuffering. A rebuffer is defined to be caused by
   *     buffer depletion rather than a user action. Hence this parameter is false during initial
   *     buffering and when buffering as a result of a seek operation.
   * @return Whether playback should be allowed to start or resume.
   */
  boolean shouldStartPlayback(long bufferedDurationUs, boolean rebuffering);

  /**
   * Called by the player to determine whether it should continue to load the source.
   *
   * @param bufferedDurationUs The duration of media that's currently buffered.
   * @return Whether the loading should continue.
   */
  boolean shouldContinueLoading(long bufferedDurationUs);

}
