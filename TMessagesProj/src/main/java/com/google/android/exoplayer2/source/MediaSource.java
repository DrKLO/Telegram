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
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import java.io.IOException;

/**
 * Defines and provides media to be played by an {@link ExoPlayer}. A MediaSource has two main
 * responsibilities:
 *
 * <ul>
 *   <li>To provide the player with a {@link Timeline} defining the structure of its media, and to
 *       provide a new timeline whenever the structure of the media changes. The MediaSource
 *       provides these timelines by calling {@link MediaSourceCaller#onSourceInfoRefreshed} on the
 *       {@link MediaSourceCaller}s passed to {@link #prepareSource(MediaSourceCaller,
 *       TransferListener, PlayerId)}.
 *   <li>To provide {@link MediaPeriod} instances for the periods in its timeline. MediaPeriods are
 *       obtained by calling {@link #createPeriod(MediaPeriodId, Allocator, long)}, and provide a
 *       way for the player to load and read the media.
 * </ul>
 *
 * All methods are called on the player's internal playback thread, as described in the {@link
 * ExoPlayer} Javadoc. They should not be called directly from application code. Instances can be
 * re-used, but only for one {@link ExoPlayer} instance simultaneously.
 */
public interface MediaSource {

  /** Factory for creating {@link MediaSource MediaSources} from {@link MediaItem MediaItems}. */
  interface Factory {

    /**
     * An instance that throws {@link UnsupportedOperationException} from {@link #createMediaSource}
     * and {@link #getSupportedTypes()}.
     */
    @SuppressWarnings("deprecation")
    Factory UNSUPPORTED = MediaSourceFactory.UNSUPPORTED;

    /**
     * Sets the {@link DrmSessionManagerProvider} used to obtain a {@link DrmSessionManager} for a
     * {@link MediaItem}.
     *
     * @return This factory, for convenience.
     */
    Factory setDrmSessionManagerProvider(DrmSessionManagerProvider drmSessionManagerProvider);

    /**
     * Sets an optional {@link LoadErrorHandlingPolicy}.
     *
     * @return This factory, for convenience.
     */
    Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy loadErrorHandlingPolicy);

    /**
     * Returns the {@link C.ContentType content types} supported by media sources created by this
     * factory.
     */
    @C.ContentType
    int[] getSupportedTypes();

    /**
     * Creates a new {@link MediaSource} with the specified {@link MediaItem}.
     *
     * @param mediaItem The media item to play.
     * @return The new {@link MediaSource media source}.
     */
    MediaSource createMediaSource(MediaItem mediaItem);
  }

  /** A caller of media sources, which will be notified of source events. */
  interface MediaSourceCaller {

    /**
     * Called when the {@link Timeline} has been refreshed.
     *
     * <p>Called on the playback thread.
     *
     * @param source The {@link MediaSource} whose info has been refreshed.
     * @param timeline The source's timeline.
     */
    void onSourceInfoRefreshed(MediaSource source, Timeline timeline);
  }

  // TODO(b/172315872) Delete when all clients have been migrated to base class.
  /**
   * Identifier for a {@link MediaPeriod}.
   *
   * <p>Extends for backward-compatibility {@link
   * com.google.android.exoplayer2.source.MediaPeriodId}.
   */
  final class MediaPeriodId extends com.google.android.exoplayer2.source.MediaPeriodId {

    /** See {@link com.google.android.exoplayer2.source.MediaPeriodId#MediaPeriodId(Object)}. */
    public MediaPeriodId(Object periodUid) {
      super(periodUid);
    }

    /**
     * See {@link com.google.android.exoplayer2.source.MediaPeriodId#MediaPeriodId(Object, long)}.
     */
    public MediaPeriodId(Object periodUid, long windowSequenceNumber) {
      super(periodUid, windowSequenceNumber);
    }

    /**
     * See {@link com.google.android.exoplayer2.source.MediaPeriodId#MediaPeriodId(Object, long,
     * int)}.
     */
    public MediaPeriodId(Object periodUid, long windowSequenceNumber, int nextAdGroupIndex) {
      super(periodUid, windowSequenceNumber, nextAdGroupIndex);
    }

    /**
     * See {@link com.google.android.exoplayer2.source.MediaPeriodId#MediaPeriodId(Object, int, int,
     * long)}.
     */
    public MediaPeriodId(
        Object periodUid, int adGroupIndex, int adIndexInAdGroup, long windowSequenceNumber) {
      super(periodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber);
    }

    /** Wraps an {@link com.google.android.exoplayer2.source.MediaPeriodId} into a MediaPeriodId. */
    public MediaPeriodId(com.google.android.exoplayer2.source.MediaPeriodId mediaPeriodId) {
      super(mediaPeriodId);
    }

    /** See {@link com.google.android.exoplayer2.source.MediaPeriodId#copyWithPeriodUid(Object)}. */
    @Override
    public MediaPeriodId copyWithPeriodUid(Object newPeriodUid) {
      return new MediaPeriodId(super.copyWithPeriodUid(newPeriodUid));
    }

    /**
     * See {@link
     * com.google.android.exoplayer2.source.MediaPeriodId#copyWithWindowSequenceNumber(long)}.
     */
    @Override
    public MediaPeriodId copyWithWindowSequenceNumber(long windowSequenceNumber) {
      return new MediaPeriodId(super.copyWithWindowSequenceNumber(windowSequenceNumber));
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

  /**
   * Adds a {@link DrmSessionEventListener} to the list of listeners which are notified of DRM
   * events for this media source.
   *
   * @param handler A handler on the which listener events will be posted.
   * @param eventListener The listener to be added.
   */
  void addDrmEventListener(Handler handler, DrmSessionEventListener eventListener);

  /**
   * Removes a {@link DrmSessionEventListener} from the list of listeners which are notified of DRM
   * events for this media source.
   *
   * @param eventListener The listener to be removed.
   */
  void removeDrmEventListener(DrmSessionEventListener eventListener);

  /**
   * Returns the initial placeholder timeline that is returned immediately when the real timeline is
   * not yet known, or null to let the player create an initial timeline.
   *
   * <p>The initial timeline must use the same uids for windows and periods that the real timeline
   * will use. It also must provide windows which are marked as dynamic to indicate that the window
   * is expected to change when the real timeline arrives.
   *
   * <p>Any media source which has multiple windows should typically provide such an initial
   * timeline to make sure the player reports the correct number of windows immediately.
   */
  @Nullable
  default Timeline getInitialTimeline() {
    return null;
  }

  /**
   * Returns true if the media source is guaranteed to never have zero or more than one window.
   *
   * <p>The default implementation returns {@code true}.
   *
   * @return true if the source has exactly one window.
   */
  default boolean isSingleWindow() {
    return true;
  }

  /** Returns the {@link MediaItem} whose media is provided by the source. */
  MediaItem getMediaItem();

  /**
   * @deprecated Implement {@link #prepareSource(MediaSourceCaller, TransferListener, PlayerId)}
   *     instead.
   */
  @Deprecated
  default void prepareSource(
      MediaSourceCaller caller, @Nullable TransferListener mediaTransferListener) {
    prepareSource(caller, mediaTransferListener, PlayerId.UNSET);
  }

  /**
   * Registers a {@link MediaSourceCaller}. Starts source preparation if needed and enables the
   * source for the creation of {@link MediaPeriod MediaPerods}.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>{@link MediaSourceCaller#onSourceInfoRefreshed(MediaSource, Timeline)} will be called once
   * the source has a {@link Timeline}.
   *
   * <p>For each call to this method, a call to {@link #releaseSource(MediaSourceCaller)} is needed
   * to remove the caller and to release the source if no longer required.
   *
   * @param caller The {@link MediaSourceCaller} to be registered.
   * @param mediaTransferListener The transfer listener which should be informed of any media data
   *     transfers. May be null if no listener is available. Note that this listener should be only
   *     informed of transfers related to the media loads and not of auxiliary loads for manifests
   *     and other data.
   * @param playerId The {@link PlayerId} of the player using this media source.
   */
  void prepareSource(
      MediaSourceCaller caller,
      @Nullable TransferListener mediaTransferListener,
      PlayerId playerId);

  /**
   * Throws any pending error encountered while loading or refreshing source information.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>Must only be called after {@link #prepareSource(MediaSourceCaller, TransferListener,
   * PlayerId)}.
   */
  void maybeThrowSourceInfoRefreshError() throws IOException;

  /**
   * Enables the source for the creation of {@link MediaPeriod MediaPeriods}.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>Must only be called after {@link #prepareSource(MediaSourceCaller, TransferListener,
   * PlayerId)}.
   *
   * @param caller The {@link MediaSourceCaller} enabling the source.
   */
  void enable(MediaSourceCaller caller);

  /**
   * Returns a new {@link MediaPeriod} identified by {@code periodId}.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>Must only be called if the source is enabled.
   *
   * @param id The identifier of the period.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param startPositionUs The expected start position, in microseconds.
   * @return A new {@link MediaPeriod}.
   */
  MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs);

  /**
   * Releases the period.
   *
   * <p>Should not be called directly from application code.
   *
   * @param mediaPeriod The period to release.
   */
  void releasePeriod(MediaPeriod mediaPeriod);

  /**
   * Disables the source for the creation of {@link MediaPeriod MediaPeriods}. The implementation
   * should not hold onto limited resources used for the creation of media periods.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>Must only be called after all {@link MediaPeriod MediaPeriods} previously created by {@link
   * #createPeriod(MediaPeriodId, Allocator, long)} have been released by {@link
   * #releasePeriod(MediaPeriod)}.
   *
   * @param caller The {@link MediaSourceCaller} disabling the source.
   */
  void disable(MediaSourceCaller caller);

  /**
   * Unregisters a caller, and disables and releases the source if no longer required.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>Must only be called if all created {@link MediaPeriod MediaPeriods} have been released by
   * {@link #releasePeriod(MediaPeriod)}.
   *
   * @param caller The {@link MediaSourceCaller} to be unregistered.
   */
  void releaseSource(MediaSourceCaller caller);
}
