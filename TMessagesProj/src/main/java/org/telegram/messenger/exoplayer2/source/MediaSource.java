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

import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.Timeline;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import java.io.IOException;

/**
 * Defines and provides media to be played by an {@link ExoPlayer}. A MediaSource has two main
 * responsibilities:
 * <ul>
 *   <li>To provide the player with a {@link Timeline} defining the structure of its media, and to
 *   provide a new timeline whenever the structure of the media changes. The MediaSource provides
 *   these timelines by calling {@link Listener#onSourceInfoRefreshed} on the {@link Listener}
 *   passed to {@link #prepareSource(ExoPlayer, boolean, Listener)}.</li>
 *   <li>To provide {@link MediaPeriod} instances for the periods in its timeline. MediaPeriods are
 *   obtained by calling {@link #createPeriod(MediaPeriodId, Allocator)}, and provide a way for the
 *   player to load and read the media.</li>
 * </ul>
 * All methods are called on the player's internal playback thread, as described in the
 * {@link ExoPlayer} Javadoc.
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
     * @param manifest The loaded manifest. May be null.
     */
    void onSourceInfoRefreshed(Timeline timeline, @Nullable Object manifest);

  }

  /**
   * Identifier for a {@link MediaPeriod}.
   */
  final class MediaPeriodId {

    /**
     * Value for unset media period identifiers.
     */
    public static final MediaPeriodId UNSET =
        new MediaPeriodId(C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET);

    /**
     * The timeline period index.
     */
    public final int periodIndex;

    /**
     * If the media period is in an ad group, the index of the ad group in the period.
     * {@link C#INDEX_UNSET} otherwise.
     */
    public final int adGroupIndex;

    /**
     * If the media period is in an ad group, the index of the ad in its ad group in the period.
     * {@link C#INDEX_UNSET} otherwise.
     */
    public final int adIndexInAdGroup;

    /**
     * Creates a media period identifier for the specified period in the timeline.
     *
     * @param periodIndex The timeline period index.
     */
    public MediaPeriodId(int periodIndex) {
      this(periodIndex, C.INDEX_UNSET, C.INDEX_UNSET);
    }

    /**
     * Creates a media period identifier that identifies an ad within an ad group at the specified
     * timeline period.
     *
     * @param periodIndex The index of the timeline period that contains the ad group.
     * @param adGroupIndex The index of the ad group.
     * @param adIndexInAdGroup The index of the ad in the ad group.
     */
    public MediaPeriodId(int periodIndex, int adGroupIndex, int adIndexInAdGroup) {
      this.periodIndex = periodIndex;
      this.adGroupIndex = adGroupIndex;
      this.adIndexInAdGroup = adIndexInAdGroup;
    }

    /**
     * Returns a copy of this period identifier but with {@code newPeriodIndex} as its period index.
     */
    public MediaPeriodId copyWithPeriodIndex(int newPeriodIndex) {
      return periodIndex == newPeriodIndex ? this
          : new MediaPeriodId(newPeriodIndex, adGroupIndex, adIndexInAdGroup);
    }

    /**
     * Returns whether this period identifier identifies an ad in an ad group in a period.
     */
    public boolean isAd() {
      return adGroupIndex != C.INDEX_UNSET;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }

      MediaPeriodId periodId = (MediaPeriodId) obj;
      return periodIndex == periodId.periodIndex && adGroupIndex == periodId.adGroupIndex
          && adIndexInAdGroup == periodId.adIndexInAdGroup;
    }

    @Override
    public int hashCode() {
      int result = 17;
      result = 31 * result + periodIndex;
      result = 31 * result + adGroupIndex;
      result = 31 * result + adIndexInAdGroup;
      return result;
    }

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
   * Returns a new {@link MediaPeriod} identified by {@code periodId}. This method may be called
   * multiple times with the same period identifier without an intervening call to
   * {@link #releasePeriod(MediaPeriod)}.
   *
   * @param id The identifier of the period.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @return A new {@link MediaPeriod}.
   */
  MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator);

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
