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
package com.google.android.exoplayer2.source;

import android.os.Handler;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import java.io.IOException;

/**
 * Defines and provides media to be played by an {@link ExoPlayer}. A MediaSource has two main
 * responsibilities:
 *
 * <ul>
 *   <li>To provide the player with a {@link Timeline} defining the structure of its media, and to
 *       provide a new timeline whenever the structure of the media changes. The MediaSource
 *       provides these timelines by calling {@link SourceInfoRefreshListener#onSourceInfoRefreshed}
 *       on the {@link SourceInfoRefreshListener}s passed to {@link #prepareSource(ExoPlayer,
 *       boolean, SourceInfoRefreshListener, TransferListener)}.
 *   <li>To provide {@link MediaPeriod} instances for the periods in its timeline. MediaPeriods are
 *       obtained by calling {@link #createPeriod(MediaPeriodId, Allocator)}, and provide a way for
 *       the player to load and read the media.
 * </ul>
 *
 * All methods are called on the player's internal playback thread, as described in the {@link
 * ExoPlayer} Javadoc. They should not be called directly from application code. Instances can be
 * re-used, but only for one {@link ExoPlayer} instance simultaneously.
 */
public interface MediaSource {

  /** Listener for source events. */
  interface SourceInfoRefreshListener {

    /**
     * Called when manifest and/or timeline has been refreshed.
     * <p>
     * Called on the playback thread.
     *
     * @param source The {@link MediaSource} whose info has been refreshed.
     * @param timeline The source's timeline.
     * @param manifest The loaded manifest. May be null.
     */
    void onSourceInfoRefreshed(MediaSource source, Timeline timeline, @Nullable Object manifest);

  }

  /**
   * Identifier for a {@link MediaPeriod}.
   */
  final class MediaPeriodId {

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
     * The sequence number of the window in the buffered sequence of windows this media period is
     * part of. {@link C#INDEX_UNSET} if the media period id is not part of a buffered sequence of
     * windows.
     */
    public final long windowSequenceNumber;

    /**
     * The end position of the media to play within the media period, in microseconds, or {@link
     * C#TIME_END_OF_SOURCE} if the end position is the end of the media period.
     *
     * <p>Note that this only applies if the media period is for content (i.e., not for an ad) and
     * is clipped to the position of the next ad group.
     */
    public final long endPositionUs;

    /**
     * Creates a media period identifier for a dummy period which is not part of a buffered sequence
     * of windows.
     *
     * @param periodIndex The period index.
     */
    public MediaPeriodId(int periodIndex) {
      this(periodIndex, C.INDEX_UNSET);
    }

    /**
     * Creates a media period identifier for the specified period in the timeline.
     *
     * @param periodIndex The timeline period index.
     * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
     *     windows this media period is part of.
     */
    public MediaPeriodId(int periodIndex, long windowSequenceNumber) {
      this(periodIndex, C.INDEX_UNSET, C.INDEX_UNSET, windowSequenceNumber, C.TIME_END_OF_SOURCE);
    }

    /**
     * Creates a media period identifier for the specified clipped period in the timeline.
     *
     * @param periodIndex The timeline period index.
     * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
     *     windows this media period is part of.
     * @param endPositionUs The end position of the media period within the timeline period, in
     *     microseconds.
     */
    public MediaPeriodId(int periodIndex, long windowSequenceNumber, long endPositionUs) {
      this(periodIndex, C.INDEX_UNSET, C.INDEX_UNSET, windowSequenceNumber, endPositionUs);
    }

    /**
     * Creates a media period identifier that identifies an ad within an ad group at the specified
     * timeline period.
     *
     * @param periodIndex The index of the timeline period that contains the ad group.
     * @param adGroupIndex The index of the ad group.
     * @param adIndexInAdGroup The index of the ad in the ad group.
     * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
     *     windows this media period is part of.
     */
    public MediaPeriodId(
        int periodIndex, int adGroupIndex, int adIndexInAdGroup, long windowSequenceNumber) {
      this(periodIndex, adGroupIndex, adIndexInAdGroup, windowSequenceNumber, C.TIME_END_OF_SOURCE);
    }

    private MediaPeriodId(
        int periodIndex,
        int adGroupIndex,
        int adIndexInAdGroup,
        long windowSequenceNumber,
        long endPositionUs) {
      this.periodIndex = periodIndex;
      this.adGroupIndex = adGroupIndex;
      this.adIndexInAdGroup = adIndexInAdGroup;
      this.windowSequenceNumber = windowSequenceNumber;
      this.endPositionUs = endPositionUs;
    }

    /**
     * Returns a copy of this period identifier but with {@code newPeriodIndex} as its period index.
     */
    public MediaPeriodId copyWithPeriodIndex(int newPeriodIndex) {
      return periodIndex == newPeriodIndex
          ? this
          : new MediaPeriodId(
              newPeriodIndex, adGroupIndex, adIndexInAdGroup, windowSequenceNumber, endPositionUs);
    }

    /**
     * Returns whether this period identifier identifies an ad in an ad group in a period.
     */
    public boolean isAd() {
      return adGroupIndex != C.INDEX_UNSET;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }

      MediaPeriodId periodId = (MediaPeriodId) obj;
      return periodIndex == periodId.periodIndex
          && adGroupIndex == periodId.adGroupIndex
          && adIndexInAdGroup == periodId.adIndexInAdGroup
          && windowSequenceNumber == periodId.windowSequenceNumber
          && endPositionUs == periodId.endPositionUs;
    }

    @Override
    public int hashCode() {
      int result = 17;
      result = 31 * result + periodIndex;
      result = 31 * result + adGroupIndex;
      result = 31 * result + adIndexInAdGroup;
      result = 31 * result + (int) windowSequenceNumber;
      result = 31 * result + (int) endPositionUs;
      return result;
    }

  }

  /**
   * Adds a {@link MediaSourceEventListener} to the list of listeners which are notified of media
   * source events.
   *
   * @param handler A handler on the which listener events will be posted.
   * @param eventListener The listener to be added.
   */
  void addEventListener(Handler handler, MediaSourceEventListener eventListener);

  /**
   * Removes a {@link MediaSourceEventListener} from the list of listeners which are notified of
   * media source events.
   *
   * @param eventListener The listener to be removed.
   */
  void removeEventListener(MediaSourceEventListener eventListener);

  /** @deprecated Will be removed in the next release. */
  @Deprecated
  void prepareSource(
      ExoPlayer player, boolean isTopLevelSource, SourceInfoRefreshListener listener);

  /**
   * Starts source preparation if not yet started, and adds a listener for timeline and/or manifest
   * updates.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>The listener will be also be notified if the source already has a timeline and/or manifest.
   *
   * <p>For each call to this method, a call to {@link #releaseSource(SourceInfoRefreshListener)} is
   * needed to remove the listener and to release the source if no longer required.
   *
   * @param player The player for which this source is being prepared.
   * @param isTopLevelSource Whether this source has been passed directly to {@link
   *     ExoPlayer#prepare(MediaSource)} or {@link ExoPlayer#prepare(MediaSource, boolean,
   *     boolean)}. If {@code false}, this source is being prepared by another source (e.g. {@link
   *     ConcatenatingMediaSource}) for composition.
   * @param listener The listener to be added.
   * @param mediaTransferListener The transfer listener which should be informed of any media data
   *     transfers. May be null if no listener is available. Note that this listener should be only
   *     informed of transfers related to the media loads and not of auxiliary loads for manifests
   *     and other data.
   */
  void prepareSource(
      ExoPlayer player,
      boolean isTopLevelSource,
      SourceInfoRefreshListener listener,
      @Nullable TransferListener mediaTransferListener);

  /**
   * Throws any pending error encountered while loading or refreshing source information.
   * <p>
   * Should not be called directly from application code.
   */
  void maybeThrowSourceInfoRefreshError() throws IOException;

  /**
   * Returns a new {@link MediaPeriod} identified by {@code periodId}. This method may be called
   * multiple times with the same period identifier without an intervening call to
   * {@link #releasePeriod(MediaPeriod)}.
   * <p>
   * Should not be called directly from application code.
   *
   * @param id The identifier of the period.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @return A new {@link MediaPeriod}.
   */
  MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator);

  /**
   * Releases the period.
   * <p>
   * Should not be called directly from application code.
   *
   * @param mediaPeriod The period to release.
   */
  void releasePeriod(MediaPeriod mediaPeriod);

  /**
   * Removes a listener for timeline and/or manifest updates and releases the source if no longer
   * required.
   *
   * <p>Should not be called directly from application code.
   *
   * @param listener The listener to be removed.
   */
  void releaseSource(SourceInfoRefreshListener listener);
}
