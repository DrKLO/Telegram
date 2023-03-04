/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.max;

import android.os.Handler;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Player.RepeatMode;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.common.collect.ImmutableList;

/**
 * Holds a queue of media periods, from the currently playing media period at the front to the
 * loading media period at the end of the queue, with methods for controlling loading and updating
 * the queue. Also has a reference to the media period currently being read.
 */
/* package */ final class MediaPeriodQueue {

  /**
   * Initial renderer position offset used for the first item in the queue, in microseconds.
   *
   * <p>Choosing a positive value, larger than any reasonable single media duration, ensures three
   * things:
   *
   * <ul>
   *   <li>Media that accidentally or intentionally starts with small negative timestamps doesn't
   *       send samples with negative timestamps to decoders. This makes rendering more robust as
   *       many decoders are known to have problems with negative timestamps.
   *   <li>Enqueueing media after the initial item with a non-zero start offset (e.g. content after
   *       ad breaks or live streams) is virtually guaranteed to stay in the positive timestamp
   *       range even when seeking back. This prevents renderer resets that are required if the
   *       allowed timestamp range may become negative.
   *   <li>Choosing a large value with zeros at all relevant digits simplifies debugging as the
   *       original timestamp of the media is still visible.
   * </ul>
   */
  public static final long INITIAL_RENDERER_POSITION_OFFSET_US = 1_000_000_000_000L;

  /**
   * Limits the maximum number of periods to buffer ahead of the current playing period. The
   * buffering policy normally prevents buffering too far ahead, but the policy could allow too many
   * small periods to be buffered if the period count were not limited.
   */
  private static final int MAXIMUM_BUFFER_AHEAD_PERIODS = 100;

  private final Timeline.Period period;
  private final Timeline.Window window;
  private final AnalyticsCollector analyticsCollector;
  private final HandlerWrapper analyticsCollectorHandler;

  private long nextWindowSequenceNumber;
  private @RepeatMode int repeatMode;
  private boolean shuffleModeEnabled;
  @Nullable private MediaPeriodHolder playing;
  @Nullable private MediaPeriodHolder reading;
  @Nullable private MediaPeriodHolder loading;
  private int length;
  @Nullable private Object oldFrontPeriodUid;
  private long oldFrontPeriodWindowSequenceNumber;

  /**
   * Creates a new media period queue.
   *
   * @param analyticsCollector An {@link AnalyticsCollector} to be informed of queue changes.
   * @param analyticsCollectorHandler The {@link Handler} to call {@link AnalyticsCollector} methods
   *     on.
   */
  public MediaPeriodQueue(
      AnalyticsCollector analyticsCollector, HandlerWrapper analyticsCollectorHandler) {
    this.analyticsCollector = analyticsCollector;
    this.analyticsCollectorHandler = analyticsCollectorHandler;
    period = new Timeline.Period();
    window = new Timeline.Window();
  }

  /**
   * Sets the {@link RepeatMode} and returns whether the repeat mode change has been fully handled.
   * If not, it is necessary to seek to the current playback position.
   *
   * @param timeline The current timeline.
   * @param repeatMode The new repeat mode.
   * @return Whether the repeat mode change has been fully handled.
   */
  public boolean updateRepeatMode(Timeline timeline, @RepeatMode int repeatMode) {
    this.repeatMode = repeatMode;
    return updateForPlaybackModeChange(timeline);
  }

  /**
   * Sets whether shuffling is enabled and returns whether the shuffle mode change has been fully
   * handled. If not, it is necessary to seek to the current playback position.
   *
   * @param timeline The current timeline.
   * @param shuffleModeEnabled Whether shuffling mode is enabled.
   * @return Whether the shuffle mode change has been fully handled.
   */
  public boolean updateShuffleModeEnabled(Timeline timeline, boolean shuffleModeEnabled) {
    this.shuffleModeEnabled = shuffleModeEnabled;
    return updateForPlaybackModeChange(timeline);
  }

  /** Returns whether {@code mediaPeriod} is the current loading media period. */
  public boolean isLoading(MediaPeriod mediaPeriod) {
    return loading != null && loading.mediaPeriod == mediaPeriod;
  }

  /**
   * If there is a loading period, reevaluates its buffer.
   *
   * @param rendererPositionUs The current renderer position.
   */
  public void reevaluateBuffer(long rendererPositionUs) {
    if (loading != null) {
      loading.reevaluateBuffer(rendererPositionUs);
    }
  }

  /** Returns whether a new loading media period should be enqueued, if available. */
  public boolean shouldLoadNextMediaPeriod() {
    return loading == null
        || (!loading.info.isFinal
            && loading.isFullyBuffered()
            && loading.info.durationUs != C.TIME_UNSET
            && length < MAXIMUM_BUFFER_AHEAD_PERIODS);
  }

  /**
   * Returns the {@link MediaPeriodInfo} for the next media period to load.
   *
   * @param rendererPositionUs The current renderer position.
   * @param playbackInfo The current playback information.
   * @return The {@link MediaPeriodInfo} for the next media period to load, or {@code null} if not
   *     yet known.
   */
  @Nullable
  public MediaPeriodInfo getNextMediaPeriodInfo(
      long rendererPositionUs, PlaybackInfo playbackInfo) {
    return loading == null
        ? getFirstMediaPeriodInfo(playbackInfo)
        : getFollowingMediaPeriodInfo(playbackInfo.timeline, loading, rendererPositionUs);
  }

  /**
   * Enqueues a new media period holder based on the specified information as the new loading media
   * period, and returns it.
   *
   * @param rendererCapabilities The renderer capabilities.
   * @param trackSelector The track selector.
   * @param allocator The allocator.
   * @param mediaSourceList The list of media sources.
   * @param info Information used to identify this media period in its timeline period.
   * @param emptyTrackSelectorResult A {@link TrackSelectorResult} with empty selections for each
   *     renderer.
   */
  public MediaPeriodHolder enqueueNextMediaPeriodHolder(
      RendererCapabilities[] rendererCapabilities,
      TrackSelector trackSelector,
      Allocator allocator,
      MediaSourceList mediaSourceList,
      MediaPeriodInfo info,
      TrackSelectorResult emptyTrackSelectorResult) {
    long rendererPositionOffsetUs =
        loading == null
            ? INITIAL_RENDERER_POSITION_OFFSET_US
            : (loading.getRendererOffset() + loading.info.durationUs - info.startPositionUs);
    MediaPeriodHolder newPeriodHolder =
        new MediaPeriodHolder(
            rendererCapabilities,
            rendererPositionOffsetUs,
            trackSelector,
            allocator,
            mediaSourceList,
            info,
            emptyTrackSelectorResult);
    if (loading != null) {
      loading.setNext(newPeriodHolder);
    } else {
      playing = newPeriodHolder;
      reading = newPeriodHolder;
    }
    oldFrontPeriodUid = null;
    loading = newPeriodHolder;
    length++;
    notifyQueueUpdate();
    return newPeriodHolder;
  }

  /**
   * Returns the loading period holder which is at the end of the queue, or null if the queue is
   * empty.
   */
  @Nullable
  public MediaPeriodHolder getLoadingPeriod() {
    return loading;
  }

  /**
   * Returns the playing period holder which is at the front of the queue, or null if the queue is
   * empty.
   */
  @Nullable
  public MediaPeriodHolder getPlayingPeriod() {
    return playing;
  }

  /** Returns the reading period holder, or null if the queue is empty. */
  @Nullable
  public MediaPeriodHolder getReadingPeriod() {
    return reading;
  }

  /**
   * Continues reading from the next period holder in the queue.
   *
   * @return The updated reading period holder.
   */
  public MediaPeriodHolder advanceReadingPeriod() {
    Assertions.checkState(reading != null && reading.getNext() != null);
    reading = reading.getNext();
    notifyQueueUpdate();
    return reading;
  }

  /**
   * Dequeues the playing period holder from the front of the queue and advances the playing period
   * holder to be the next item in the queue.
   *
   * @return The updated playing period holder, or null if the queue is or becomes empty.
   */
  @Nullable
  public MediaPeriodHolder advancePlayingPeriod() {
    if (playing == null) {
      return null;
    }
    if (playing == reading) {
      reading = playing.getNext();
    }
    playing.release();
    length--;
    if (length == 0) {
      loading = null;
      oldFrontPeriodUid = playing.uid;
      oldFrontPeriodWindowSequenceNumber = playing.info.id.windowSequenceNumber;
    }
    playing = playing.getNext();
    notifyQueueUpdate();
    return playing;
  }

  /**
   * Removes all period holders after the given period holder. This process may also remove the
   * currently reading period holder. If that is the case, the reading period holder is set to be
   * the same as the playing period holder at the front of the queue.
   *
   * @param mediaPeriodHolder The media period holder that shall be the new end of the queue.
   * @return Whether the reading period has been removed.
   */
  public boolean removeAfter(MediaPeriodHolder mediaPeriodHolder) {
    Assertions.checkState(mediaPeriodHolder != null);
    if (mediaPeriodHolder.equals(loading)) {
      return false;
    }
    boolean removedReading = false;
    loading = mediaPeriodHolder;
    while (mediaPeriodHolder.getNext() != null) {
      mediaPeriodHolder = mediaPeriodHolder.getNext();
      if (mediaPeriodHolder == reading) {
        reading = playing;
        removedReading = true;
      }
      mediaPeriodHolder.release();
      length--;
    }
    loading.setNext(null);
    notifyQueueUpdate();
    return removedReading;
  }

  /** Clears the queue. */
  public void clear() {
    if (length == 0) {
      return;
    }
    MediaPeriodHolder front = Assertions.checkStateNotNull(playing);
    oldFrontPeriodUid = front.uid;
    oldFrontPeriodWindowSequenceNumber = front.info.id.windowSequenceNumber;
    while (front != null) {
      front.release();
      front = front.getNext();
    }
    playing = null;
    loading = null;
    reading = null;
    length = 0;
    notifyQueueUpdate();
  }

  /**
   * Updates media periods in the queue to take into account the latest timeline, and returns
   * whether the timeline change has been fully handled. If not, it is necessary to seek to the
   * current playback position. The method assumes that the first media period in the queue is still
   * consistent with the new timeline.
   *
   * @param timeline The new timeline.
   * @param rendererPositionUs The current renderer position in microseconds.
   * @param maxRendererReadPositionUs The maximum renderer position up to which renderers have read
   *     the current reading media period in microseconds, or {@link C#TIME_END_OF_SOURCE} if they
   *     have read to the end.
   * @return Whether the timeline change has been handled completely.
   */
  public boolean updateQueuedPeriods(
      Timeline timeline, long rendererPositionUs, long maxRendererReadPositionUs) {
    // TODO: Merge this into setTimeline so that the queue gets updated as soon as the new timeline
    // is set, once all cases handled by ExoPlayerImplInternal.handleMediaSourceListInfoRefreshed
    // can be handled here.
    MediaPeriodHolder previousPeriodHolder = null;
    MediaPeriodHolder periodHolder = playing;
    while (periodHolder != null) {
      MediaPeriodInfo oldPeriodInfo = periodHolder.info;

      // Get period info based on new timeline.
      MediaPeriodInfo newPeriodInfo;
      if (previousPeriodHolder == null) {
        // The id and start position of the first period have already been verified by
        // ExoPlayerImplInternal.handleMediaSourceListInfoRefreshed. Just update duration,
        // isLastInTimeline and isLastInPeriod flags.
        newPeriodInfo = getUpdatedMediaPeriodInfo(timeline, oldPeriodInfo);
      } else {
        newPeriodInfo =
            getFollowingMediaPeriodInfo(timeline, previousPeriodHolder, rendererPositionUs);
        if (newPeriodInfo == null) {
          // We've loaded a next media period that is not in the new timeline.
          return !removeAfter(previousPeriodHolder);
        }
        if (!canKeepMediaPeriodHolder(oldPeriodInfo, newPeriodInfo)) {
          // The new media period has a different id or start position.
          return !removeAfter(previousPeriodHolder);
        }
      }

      // Use the new period info, but keep the old requested content position to avoid overriding it
      // by the default content position generated in getFollowingMediaPeriodInfo.
      periodHolder.info =
          newPeriodInfo.copyWithRequestedContentPositionUs(
              oldPeriodInfo.requestedContentPositionUs);

      if (!areDurationsCompatible(oldPeriodInfo.durationUs, newPeriodInfo.durationUs)) {
        // The period duration changed. Remove all subsequent periods and check whether we read
        // beyond the new duration.
        periodHolder.updateClipping();
        long newDurationInRendererTime =
            newPeriodInfo.durationUs == C.TIME_UNSET
                ? Long.MAX_VALUE
                : periodHolder.toRendererTime(newPeriodInfo.durationUs);
        boolean isReadingAndReadBeyondNewDuration =
            periodHolder == reading
                && !periodHolder.info.isFollowedByTransitionToSameStream
                && (maxRendererReadPositionUs == C.TIME_END_OF_SOURCE
                    || maxRendererReadPositionUs >= newDurationInRendererTime);
        boolean readingPeriodRemoved = removeAfter(periodHolder);
        return !readingPeriodRemoved && !isReadingAndReadBeyondNewDuration;
      }

      previousPeriodHolder = periodHolder;
      periodHolder = periodHolder.getNext();
    }
    return true;
  }

  /**
   * Returns new media period info based on specified {@code mediaPeriodInfo} but taking into
   * account the current timeline. This method must only be called if the period is still part of
   * the current timeline.
   *
   * @param timeline The current timeline used to update the media period.
   * @param info Media period info for a media period based on an old timeline.
   * @return The updated media period info for the current timeline.
   */
  public MediaPeriodInfo getUpdatedMediaPeriodInfo(Timeline timeline, MediaPeriodInfo info) {
    MediaPeriodId id = info.id;
    boolean isLastInPeriod = isLastInPeriod(id);
    boolean isLastInWindow = isLastInWindow(timeline, id);
    boolean isLastInTimeline = isLastInTimeline(timeline, id, isLastInPeriod);
    timeline.getPeriodByUid(info.id.periodUid, period);
    long endPositionUs =
        id.isAd() || id.nextAdGroupIndex == C.INDEX_UNSET
            ? C.TIME_UNSET
            : period.getAdGroupTimeUs(id.nextAdGroupIndex);
    long durationUs =
        id.isAd()
            ? period.getAdDurationUs(id.adGroupIndex, id.adIndexInAdGroup)
            : (endPositionUs == C.TIME_UNSET || endPositionUs == C.TIME_END_OF_SOURCE
                ? period.getDurationUs()
                : endPositionUs);
    boolean isFollowedByTransitionToSameStream =
        id.isAd()
            ? period.isServerSideInsertedAdGroup(id.adGroupIndex)
            : (id.nextAdGroupIndex != C.INDEX_UNSET
                && period.isServerSideInsertedAdGroup(id.nextAdGroupIndex));
    return new MediaPeriodInfo(
        id,
        info.startPositionUs,
        info.requestedContentPositionUs,
        endPositionUs,
        durationUs,
        isFollowedByTransitionToSameStream,
        isLastInPeriod,
        isLastInWindow,
        isLastInTimeline);
  }

  /**
   * Resolves the specified timeline period and position to a {@link MediaPeriodId} that should be
   * played, returning an identifier for an ad group if one needs to be played before the specified
   * position, or an identifier for a content media period if not.
   *
   * @param timeline The timeline the period is part of.
   * @param periodUid The uid of the timeline period to play.
   * @param positionUs The next content position in the period to play.
   * @return The identifier for the first media period to play, taking into account unplayed ads.
   */
  public MediaPeriodId resolveMediaPeriodIdForAds(
      Timeline timeline, Object periodUid, long positionUs) {
    long windowSequenceNumber = resolvePeriodIndexToWindowSequenceNumber(timeline, periodUid);
    return resolveMediaPeriodIdForAds(
        timeline, periodUid, positionUs, windowSequenceNumber, window, period);
  }

  /**
   * Resolves the specified timeline period and position to a {@link MediaPeriodId} that should be
   * played, returning an identifier for an ad group if one needs to be played before the specified
   * position, or an identifier for a content media period if not.
   *
   * @param timeline The timeline the period is part of.
   * @param periodUid The uid of the timeline period to play.
   * @param positionUs The next content position in the period to play.
   * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
   *     windows this period is part of.
   * @param period A scratch {@link Timeline.Period}.
   * @return The identifier for the first media period to play, taking into account unplayed ads.
   */
  private static MediaPeriodId resolveMediaPeriodIdForAds(
      Timeline timeline,
      Object periodUid,
      long positionUs,
      long windowSequenceNumber,
      Timeline.Window window,
      Timeline.Period period) {
    timeline.getPeriodByUid(periodUid, period);
    timeline.getWindow(period.windowIndex, window);
    int periodIndex = timeline.getIndexOfPeriod(periodUid);
    // Skip ignorable server side inserted ad periods.
    while ((period.durationUs == 0
            && period.getAdGroupCount() > 0
            && period.isServerSideInsertedAdGroup(period.getRemovedAdGroupCount())
            && period.getAdGroupIndexForPositionUs(0) == C.INDEX_UNSET)
        && periodIndex++ < window.lastPeriodIndex) {
      timeline.getPeriod(periodIndex, period, /* setIds= */ true);
      periodUid = checkNotNull(period.uid);
    }
    timeline.getPeriodByUid(periodUid, period);
    int adGroupIndex = period.getAdGroupIndexForPositionUs(positionUs);
    if (adGroupIndex == C.INDEX_UNSET) {
      int nextAdGroupIndex = period.getAdGroupIndexAfterPositionUs(positionUs);
      return new MediaPeriodId(periodUid, windowSequenceNumber, nextAdGroupIndex);
    } else {
      int adIndexInAdGroup = period.getFirstAdIndexToPlay(adGroupIndex);
      return new MediaPeriodId(periodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber);
    }
  }

  /**
   * Resolves the specified timeline period and position to a {@link MediaPeriodId} that should be
   * played after a period position change, returning an identifier for an ad group if one needs to
   * be played before the specified position, or an identifier for a content media period if not.
   *
   * @param timeline The timeline the period is part of.
   * @param periodUid The uid of the timeline period to play.
   * @param positionUs The next content position in the period to play.
   * @return The identifier for the first media period to play, taking into account unplayed ads.
   */
  public MediaPeriodId resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
      Timeline timeline, Object periodUid, long positionUs) {
    long windowSequenceNumber = resolvePeriodIndexToWindowSequenceNumber(timeline, periodUid);
    // Check for preceding ad periods in multi-period window.
    timeline.getPeriodByUid(periodUid, period);
    timeline.getWindow(period.windowIndex, window);
    Object periodUidToPlay = periodUid;
    boolean seenAdPeriod = false;
    for (int i = timeline.getIndexOfPeriod(periodUid); i >= window.firstPeriodIndex; i--) {
      timeline.getPeriod(/* periodIndex= */ i, period, /* setIds= */ true);
      boolean isAdPeriod = period.getAdGroupCount() > 0;
      seenAdPeriod |= isAdPeriod;
      if (period.getAdGroupIndexForPositionUs(period.durationUs) != C.INDEX_UNSET) {
        // Roll forward to preceding un-played ad period.
        periodUidToPlay = checkNotNull(period.uid);
      }
      if (seenAdPeriod && (!isAdPeriod || period.durationUs != 0)) {
        // Stop for any periods except un-played ads with no content.
        break;
      }
    }
    return resolveMediaPeriodIdForAds(
        timeline, periodUidToPlay, positionUs, windowSequenceNumber, window, period);
  }

  // Internal methods.

  private void notifyQueueUpdate() {
    ImmutableList.Builder<MediaPeriodId> builder = ImmutableList.builder();
    @Nullable MediaPeriodHolder period = playing;
    while (period != null) {
      builder.add(period.info.id);
      period = period.getNext();
    }
    @Nullable MediaPeriodId readingPeriodId = reading == null ? null : reading.info.id;
    analyticsCollectorHandler.post(
        () -> analyticsCollector.updateMediaPeriodQueueInfo(builder.build(), readingPeriodId));
  }

  /**
   * Resolves the specified period uid to a corresponding window sequence number. Either by reusing
   * the window sequence number of an existing matching media period or by creating a new window
   * sequence number.
   *
   * @param timeline The timeline the period is part of.
   * @param periodUid The uid of the timeline period.
   * @return A window sequence number for a media period created for this timeline period.
   */
  private long resolvePeriodIndexToWindowSequenceNumber(Timeline timeline, Object periodUid) {
    int windowIndex = timeline.getPeriodByUid(periodUid, period).windowIndex;
    if (oldFrontPeriodUid != null) {
      int oldFrontPeriodIndex = timeline.getIndexOfPeriod(oldFrontPeriodUid);
      if (oldFrontPeriodIndex != C.INDEX_UNSET) {
        int oldFrontWindowIndex = timeline.getPeriod(oldFrontPeriodIndex, period).windowIndex;
        if (oldFrontWindowIndex == windowIndex) {
          // Try to match old front uid after the queue has been cleared.
          return oldFrontPeriodWindowSequenceNumber;
        }
      }
    }
    MediaPeriodHolder mediaPeriodHolder = playing;
    while (mediaPeriodHolder != null) {
      if (mediaPeriodHolder.uid.equals(periodUid)) {
        // Reuse window sequence number of first exact period match.
        return mediaPeriodHolder.info.id.windowSequenceNumber;
      }
      mediaPeriodHolder = mediaPeriodHolder.getNext();
    }
    mediaPeriodHolder = playing;
    while (mediaPeriodHolder != null) {
      int indexOfHolderInTimeline = timeline.getIndexOfPeriod(mediaPeriodHolder.uid);
      if (indexOfHolderInTimeline != C.INDEX_UNSET) {
        int holderWindowIndex = timeline.getPeriod(indexOfHolderInTimeline, period).windowIndex;
        if (holderWindowIndex == windowIndex) {
          // As an alternative, try to match other periods of the same window.
          return mediaPeriodHolder.info.id.windowSequenceNumber;
        }
      }
      mediaPeriodHolder = mediaPeriodHolder.getNext();
    }
    // If no match is found, create new sequence number.
    long windowSequenceNumber = nextWindowSequenceNumber++;
    if (playing == null) {
      // If the queue is empty, save it as old front uid to allow later reuse.
      oldFrontPeriodUid = periodUid;
      oldFrontPeriodWindowSequenceNumber = windowSequenceNumber;
    }
    return windowSequenceNumber;
  }

  /**
   * Returns whether a period described by {@code oldInfo} can be kept for playing the media period
   * described by {@code newInfo}.
   */
  private boolean canKeepMediaPeriodHolder(MediaPeriodInfo oldInfo, MediaPeriodInfo newInfo) {
    return oldInfo.startPositionUs == newInfo.startPositionUs && oldInfo.id.equals(newInfo.id);
  }

  /**
   * Returns whether a duration change of a period is compatible with keeping the following periods.
   */
  private boolean areDurationsCompatible(long previousDurationUs, long newDurationUs) {
    return previousDurationUs == C.TIME_UNSET || previousDurationUs == newDurationUs;
  }

  /**
   * Updates the queue for any playback mode change, and returns whether the change was fully
   * handled. If not, it is necessary to seek to the current playback position.
   *
   * @param timeline The current timeline.
   */
  private boolean updateForPlaybackModeChange(Timeline timeline) {
    // Find the last existing period holder that matches the new period order.
    MediaPeriodHolder lastValidPeriodHolder = playing;
    if (lastValidPeriodHolder == null) {
      return true;
    }
    int currentPeriodIndex = timeline.getIndexOfPeriod(lastValidPeriodHolder.uid);
    while (true) {
      int nextPeriodIndex =
          timeline.getNextPeriodIndex(
              currentPeriodIndex, period, window, repeatMode, shuffleModeEnabled);
      while (lastValidPeriodHolder.getNext() != null
          && !lastValidPeriodHolder.info.isLastInTimelinePeriod) {
        lastValidPeriodHolder = lastValidPeriodHolder.getNext();
      }

      MediaPeriodHolder nextMediaPeriodHolder = lastValidPeriodHolder.getNext();
      if (nextPeriodIndex == C.INDEX_UNSET || nextMediaPeriodHolder == null) {
        break;
      }
      int nextPeriodHolderPeriodIndex = timeline.getIndexOfPeriod(nextMediaPeriodHolder.uid);
      if (nextPeriodHolderPeriodIndex != nextPeriodIndex) {
        break;
      }
      lastValidPeriodHolder = nextMediaPeriodHolder;
      currentPeriodIndex = nextPeriodIndex;
    }

    // Release any period holders that don't match the new period order.
    boolean readingPeriodRemoved = removeAfter(lastValidPeriodHolder);

    // Update the period info for the last holder, as it may now be the last period in the timeline.
    lastValidPeriodHolder.info = getUpdatedMediaPeriodInfo(timeline, lastValidPeriodHolder.info);

    // If renderers may have read from a period that's been removed, it is necessary to restart.
    return !readingPeriodRemoved;
  }

  /**
   * Returns the first {@link MediaPeriodInfo} to play, based on the specified playback position.
   */
  @Nullable
  private MediaPeriodInfo getFirstMediaPeriodInfo(PlaybackInfo playbackInfo) {
    return getMediaPeriodInfo(
        playbackInfo.timeline,
        playbackInfo.periodId,
        playbackInfo.requestedContentPositionUs,
        playbackInfo.positionUs);
  }

  /**
   * Returns the {@link MediaPeriodInfo} for the media period following {@code mediaPeriodHolder}'s
   * media period.
   *
   * @param timeline The current timeline.
   * @param mediaPeriodHolder The media period holder.
   * @param rendererPositionUs The current renderer position in microseconds.
   * @return The following media period's info, or {@code null} if it is not yet possible to get the
   *     next media period info.
   */
  @Nullable
  private MediaPeriodInfo getFollowingMediaPeriodInfo(
      Timeline timeline, MediaPeriodHolder mediaPeriodHolder, long rendererPositionUs) {
    // TODO: This method is called repeatedly from ExoPlayerImplInternal.maybeUpdateLoadingPeriod
    // but if the timeline is not ready to provide the next period it can't return a non-null value
    // until the timeline is updated. Store whether the next timeline period is ready when the
    // timeline is updated, to avoid repeatedly checking the same timeline.
    MediaPeriodInfo mediaPeriodInfo = mediaPeriodHolder.info;
    // The expected delay until playback transitions to the new period is equal the duration of
    // media that's currently buffered (assuming no interruptions). This is used to project forward
    // the start position for transitions to new windows.
    long bufferedDurationUs =
        mediaPeriodHolder.getRendererOffset() + mediaPeriodInfo.durationUs - rendererPositionUs;
    if (mediaPeriodInfo.isLastInTimelinePeriod) {
      int currentPeriodIndex = timeline.getIndexOfPeriod(mediaPeriodInfo.id.periodUid);
      int nextPeriodIndex =
          timeline.getNextPeriodIndex(
              currentPeriodIndex, period, window, repeatMode, shuffleModeEnabled);
      if (nextPeriodIndex == C.INDEX_UNSET) {
        // We can't create a next period yet.
        return null;
      }
      // We either start a new period in the same window or the first period in the next window.
      long startPositionUs = 0;
      long contentPositionUs = 0;
      int nextWindowIndex =
          timeline.getPeriod(nextPeriodIndex, period, /* setIds= */ true).windowIndex;
      Object nextPeriodUid = checkNotNull(period.uid);
      long windowSequenceNumber = mediaPeriodInfo.id.windowSequenceNumber;
      if (timeline.getWindow(nextWindowIndex, window).firstPeriodIndex == nextPeriodIndex) {
        // We're starting to buffer a new window. When playback transitions to this window we'll
        // want it to be from its default start position, so project the default start position
        // forward by the duration of the buffer, and start buffering from this point.
        contentPositionUs = C.TIME_UNSET;
        @Nullable
        Pair<Object, Long> defaultPositionUs =
            timeline.getPeriodPositionUs(
                window,
                period,
                nextWindowIndex,
                /* windowPositionUs= */ C.TIME_UNSET,
                /* defaultPositionProjectionUs= */ max(0, bufferedDurationUs));
        if (defaultPositionUs == null) {
          return null;
        }
        nextPeriodUid = defaultPositionUs.first;
        startPositionUs = defaultPositionUs.second;
        @Nullable MediaPeriodHolder nextMediaPeriodHolder = mediaPeriodHolder.getNext();
        if (nextMediaPeriodHolder != null && nextMediaPeriodHolder.uid.equals(nextPeriodUid)) {
          windowSequenceNumber = nextMediaPeriodHolder.info.id.windowSequenceNumber;
        } else {
          windowSequenceNumber = nextWindowSequenceNumber++;
        }
      }

      @Nullable
      MediaPeriodId periodId =
          resolveMediaPeriodIdForAds(
              timeline, nextPeriodUid, startPositionUs, windowSequenceNumber, window, period);
      if (contentPositionUs != C.TIME_UNSET
          && mediaPeriodInfo.requestedContentPositionUs != C.TIME_UNSET) {
        boolean isPrecedingPeriodAnAd =
            timeline.getPeriodByUid(mediaPeriodInfo.id.periodUid, period).getAdGroupCount() > 0
                && period.isServerSideInsertedAdGroup(period.getRemovedAdGroupCount());
        // Handle the requested content position for period transitions within the same window.
        if (periodId.isAd() && isPrecedingPeriodAnAd) {
          // Propagate the requested position to the following ad period in the same window.
          contentPositionUs = mediaPeriodInfo.requestedContentPositionUs;
        } else if (isPrecedingPeriodAnAd) {
          // Use the requested content position of the preceding ad period as the start position.
          startPositionUs = mediaPeriodInfo.requestedContentPositionUs;
        }
      }
      return getMediaPeriodInfo(timeline, periodId, contentPositionUs, startPositionUs);
    }

    MediaPeriodId currentPeriodId = mediaPeriodInfo.id;
    timeline.getPeriodByUid(currentPeriodId.periodUid, period);
    if (currentPeriodId.isAd()) {
      int adGroupIndex = currentPeriodId.adGroupIndex;
      int adCountInCurrentAdGroup = period.getAdCountInAdGroup(adGroupIndex);
      if (adCountInCurrentAdGroup == C.LENGTH_UNSET) {
        return null;
      }
      int nextAdIndexInAdGroup =
          period.getNextAdIndexToPlay(adGroupIndex, currentPeriodId.adIndexInAdGroup);
      if (nextAdIndexInAdGroup < adCountInCurrentAdGroup) {
        // Play the next ad in the ad group if it's available.
        return getMediaPeriodInfoForAd(
            timeline,
            currentPeriodId.periodUid,
            adGroupIndex,
            nextAdIndexInAdGroup,
            mediaPeriodInfo.requestedContentPositionUs,
            currentPeriodId.windowSequenceNumber);
      } else {
        // Play content from the ad group position.
        long startPositionUs = mediaPeriodInfo.requestedContentPositionUs;
        if (startPositionUs == C.TIME_UNSET) {
          // If we're transitioning from an ad group to content starting from its default position,
          // project the start position forward as if this were a transition to a new window.
          @Nullable
          Pair<Object, Long> defaultPositionUs =
              timeline.getPeriodPositionUs(
                  window,
                  period,
                  period.windowIndex,
                  /* windowPositionUs= */ C.TIME_UNSET,
                  /* defaultPositionProjectionUs= */ max(0, bufferedDurationUs));
          if (defaultPositionUs == null) {
            return null;
          }
          startPositionUs = defaultPositionUs.second;
        }
        long minStartPositionUs =
            getMinStartPositionAfterAdGroupUs(
                timeline, currentPeriodId.periodUid, currentPeriodId.adGroupIndex);
        return getMediaPeriodInfoForContent(
            timeline,
            currentPeriodId.periodUid,
            max(minStartPositionUs, startPositionUs),
            mediaPeriodInfo.requestedContentPositionUs,
            currentPeriodId.windowSequenceNumber);
      }
    } else {
      // Play the next ad group if it's still available.
      int adIndexInAdGroup = period.getFirstAdIndexToPlay(currentPeriodId.nextAdGroupIndex);
      boolean isPlayedServerSideInsertedAd =
          period.isServerSideInsertedAdGroup(currentPeriodId.nextAdGroupIndex)
              && period.getAdState(currentPeriodId.nextAdGroupIndex, adIndexInAdGroup)
                  == AdPlaybackState.AD_STATE_PLAYED;
      if (adIndexInAdGroup == period.getAdCountInAdGroup(currentPeriodId.nextAdGroupIndex)
          || isPlayedServerSideInsertedAd) {
        // The next ad group has no ads left to play or is a played SSAI ad group. Play content from
        // the end position instead.
        long startPositionUs =
            getMinStartPositionAfterAdGroupUs(
                timeline, currentPeriodId.periodUid, currentPeriodId.nextAdGroupIndex);
        return getMediaPeriodInfoForContent(
            timeline,
            currentPeriodId.periodUid,
            startPositionUs,
            /* requestedContentPositionUs= */ mediaPeriodInfo.durationUs,
            currentPeriodId.windowSequenceNumber);
      }
      return getMediaPeriodInfoForAd(
          timeline,
          currentPeriodId.periodUid,
          currentPeriodId.nextAdGroupIndex,
          adIndexInAdGroup,
          /* contentPositionUs= */ mediaPeriodInfo.durationUs,
          currentPeriodId.windowSequenceNumber);
    }
  }

  @Nullable
  private MediaPeriodInfo getMediaPeriodInfo(
      Timeline timeline, MediaPeriodId id, long requestedContentPositionUs, long startPositionUs) {
    timeline.getPeriodByUid(id.periodUid, period);
    if (id.isAd()) {
      return getMediaPeriodInfoForAd(
          timeline,
          id.periodUid,
          id.adGroupIndex,
          id.adIndexInAdGroup,
          requestedContentPositionUs,
          id.windowSequenceNumber);
    } else {
      return getMediaPeriodInfoForContent(
          timeline,
          id.periodUid,
          startPositionUs,
          requestedContentPositionUs,
          id.windowSequenceNumber);
    }
  }

  private MediaPeriodInfo getMediaPeriodInfoForAd(
      Timeline timeline,
      Object periodUid,
      int adGroupIndex,
      int adIndexInAdGroup,
      long contentPositionUs,
      long windowSequenceNumber) {
    MediaPeriodId id =
        new MediaPeriodId(periodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber);
    long durationUs =
        timeline
            .getPeriodByUid(id.periodUid, period)
            .getAdDurationUs(id.adGroupIndex, id.adIndexInAdGroup);
    long startPositionUs =
        adIndexInAdGroup == period.getFirstAdIndexToPlay(adGroupIndex)
            ? period.getAdResumePositionUs()
            : 0;
    boolean isFollowedByTransitionToSameStream =
        period.isServerSideInsertedAdGroup(id.adGroupIndex);
    if (durationUs != C.TIME_UNSET && startPositionUs >= durationUs) {
      // Ensure start position doesn't exceed duration.
      startPositionUs = max(0, durationUs - 1);
    }
    return new MediaPeriodInfo(
        id,
        startPositionUs,
        contentPositionUs,
        /* endPositionUs= */ C.TIME_UNSET,
        durationUs,
        isFollowedByTransitionToSameStream,
        /* isLastInTimelinePeriod= */ false,
        /* isLastInTimelineWindow= */ false,
        /* isFinal= */ false);
  }

  private MediaPeriodInfo getMediaPeriodInfoForContent(
      Timeline timeline,
      Object periodUid,
      long startPositionUs,
      long requestedContentPositionUs,
      long windowSequenceNumber) {
    timeline.getPeriodByUid(periodUid, period);
    int nextAdGroupIndex = period.getAdGroupIndexAfterPositionUs(startPositionUs);
    boolean clipPeriodAtContentDuration = false;
    if (nextAdGroupIndex == C.INDEX_UNSET) {
      // Clip SSAI streams when at the end of the period.
      clipPeriodAtContentDuration =
          period.getAdGroupCount() > 0
              && period.isServerSideInsertedAdGroup(period.getRemovedAdGroupCount());
    } else if (period.isServerSideInsertedAdGroup(nextAdGroupIndex)
        && period.getAdGroupTimeUs(nextAdGroupIndex) == period.durationUs) {
      if (period.hasPlayedAdGroup(nextAdGroupIndex)) {
        // Clip period before played SSAI post-rolls.
        nextAdGroupIndex = C.INDEX_UNSET;
        clipPeriodAtContentDuration = true;
      }
    }
    MediaPeriodId id = new MediaPeriodId(periodUid, windowSequenceNumber, nextAdGroupIndex);
    boolean isLastInPeriod = isLastInPeriod(id);
    boolean isLastInWindow = isLastInWindow(timeline, id);
    boolean isLastInTimeline = isLastInTimeline(timeline, id, isLastInPeriod);
    boolean isFollowedByTransitionToSameStream =
        nextAdGroupIndex != C.INDEX_UNSET && period.isServerSideInsertedAdGroup(nextAdGroupIndex);
    long endPositionUs =
        nextAdGroupIndex != C.INDEX_UNSET
            ? period.getAdGroupTimeUs(nextAdGroupIndex)
            : clipPeriodAtContentDuration ? period.durationUs : C.TIME_UNSET;
    long durationUs =
        endPositionUs == C.TIME_UNSET || endPositionUs == C.TIME_END_OF_SOURCE
            ? period.durationUs
            : endPositionUs;
    if (durationUs != C.TIME_UNSET && startPositionUs >= durationUs) {
      // Ensure start position doesn't exceed duration.
      boolean endAtLastFrame = isLastInTimeline || !clipPeriodAtContentDuration;
      startPositionUs = max(0, durationUs - (endAtLastFrame ? 1 : 0));
    }
    return new MediaPeriodInfo(
        id,
        startPositionUs,
        requestedContentPositionUs,
        endPositionUs,
        durationUs,
        isFollowedByTransitionToSameStream,
        isLastInPeriod,
        isLastInWindow,
        isLastInTimeline);
  }

  private boolean isLastInPeriod(MediaPeriodId id) {
    return !id.isAd() && id.nextAdGroupIndex == C.INDEX_UNSET;
  }

  private boolean isLastInWindow(Timeline timeline, MediaPeriodId id) {
    if (!isLastInPeriod(id)) {
      return false;
    }
    int windowIndex = timeline.getPeriodByUid(id.periodUid, period).windowIndex;
    int periodIndex = timeline.getIndexOfPeriod(id.periodUid);
    return timeline.getWindow(windowIndex, window).lastPeriodIndex == periodIndex;
  }

  private boolean isLastInTimeline(
      Timeline timeline, MediaPeriodId id, boolean isLastMediaPeriodInPeriod) {
    int periodIndex = timeline.getIndexOfPeriod(id.periodUid);
    int windowIndex = timeline.getPeriod(periodIndex, period).windowIndex;
    return !timeline.getWindow(windowIndex, window).isDynamic
        && timeline.isLastPeriod(periodIndex, period, window, repeatMode, shuffleModeEnabled)
        && isLastMediaPeriodInPeriod;
  }

  private long getMinStartPositionAfterAdGroupUs(
      Timeline timeline, Object periodUid, int adGroupIndex) {
    timeline.getPeriodByUid(periodUid, period);
    long startPositionUs = period.getAdGroupTimeUs(adGroupIndex);
    if (startPositionUs == C.TIME_END_OF_SOURCE) {
      return period.durationUs;
    }
    return startPositionUs + period.getContentResumeOffsetUs(adGroupIndex);
  }
}
