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

import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.Timeline;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import java.io.IOException;

/**
 * A source of media consisting of one or more {@link MediaPeriod}s.
 */
public interface MediaSource {

  /**
   * Listener for source events.
   */
  interface Listener {

    /**
     * Called when manifest and/or timeline has been refreshed.
     *
     * @param timeline The source's timeline.
     * @param manifest The loaded manifest.
     */
    void onSourceInfoRefreshed(Timeline timeline, Object manifest);

  }

  /**
   * Starts preparation of the source.
   *
   * @param player The player for which this source is being prepared.
   * @param isTopLevelSource Whether this source has been passed directly to
   *     {@link ExoPlayer#prepare(MediaSource)} or
   *     {@link ExoPlayer#prepare(MediaSource, boolean, boolean)}. If {@code false}, this source is
   *     being prepared by another source (e.g. {@link ConcatenatingMediaSource}) for composition.
   * @param listener The listener for source events.
   */
  void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener);

  /**
   * Throws any pending error encountered while loading or refreshing source information.
   */
  void maybeThrowSourceInfoRefreshError() throws IOException;

  /**
   * Returns a new {@link MediaPeriod} corresponding to the period at the specified {@code index}.
   * This method may be called multiple times with the same index without an intervening call to
   * {@link #releasePeriod(MediaPeriod)}.
   *
   * @param index The index of the period.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param positionUs The player's current playback position.
   * @return A new {@link MediaPeriod}.
   */
  MediaPeriod createPeriod(int index, Allocator allocator, long positionUs);

  /**
   * Releases the period.
   *
   * @param mediaPeriod The period to release.
   */
  void releasePeriod(MediaPeriod mediaPeriod);

  /**
   * Releases the source.
   * <p>
   * This method should be called when the source is no longer required. It may be called in any
   * state.
   */
  void releaseSource();

}
