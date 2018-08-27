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

import android.support.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.Player.RepeatMode;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;

/**
 * Holds a queue of media periods, from the currently playing media period at the front to the
 * loading media period at the end of the queue, with methods for controlling loading and updating
 * the queue. Also has a reference to the media period currently being read.
 */
@SuppressWarnings("UngroupedOverloads")
/* package */ final class MediaPeriodQueue {

  /**
   * Limits the maximum number of periods to buffer ahead of the current playing period. The
   * buffering policy normally prevents buffering too far ahead, but the policy could allow too many
   * small periods to be buffered if the period count were not limited.
   */
  private static final int MAXIMUM_BUFFER_AHEAD_PERIODS = 100;

  private final Timeline.Period period;
  private final Timeline.Window window;

  private long nextWindowSequenceNumber;
  private Timeline timeline;
  private @RepeatMode int repeatMode;
  private boolean shuffleModeEnabled;
  private @Nullable MediaPeriodHolder playing;
  private @Nullable MediaPeriodHolder reading;
  private @Nullable MediaPeriodHolder loading;
  private int length;
  private @Nullable Object oldFrontPeriodUid;
  private long oldFrontPeriodWindowSequenceNumber;

  /** Creates a new media period queue. */
  public MediaPeriodQueue() {
    period = new Timeline.Period();
    window = new Timeline.Window();
    timeline = Timeline.EMPTY;
  }

  /**
   * Sets the {@link Timeline}. Call {@link #updateQueuedPeriods(MediaPeriodId, long)} to update the
   * queued media periods to take into account the new timeline.
   */
  public void setTimeline(Timeline timeline) {
    this.timeline = timeline;
  }

  /**
   * Sets the {@link RepeatMode} and returns whether the repeat mode change has been fully handled.
   * If not, it is necessary to seek to the current playback position.
   */
  public boolean updateRepeatMode(@RepeatMode int repeatMode) {
    this.repeatMode = repeatMode;
    return updateForPlaybackModeChange();
  }

  /**
   * Sets whether shuffling is enabled and returns whether the shuffle mode change has been fully
   * handled. If not, it is necessary to seek to the current playback position.
   */
  public boolean updateShuffleModeEnabled(boolean shuffleModeEnabled) {
    this.shuffleModeEnabled = shuffleModeEnabled;
    return updateForPlaybackModeChange();
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
  public @Nullable MediaPeriodInfo getNextMediaPeriodInfo(
      long rendererPositionUs, PlaybackInfo playbackInfo) {
    return loading == null
        ? getFirstMediaPeriodInfo(playbackInfo)
        : getFollowingMediaPeriodInfo(loading, rendererPositionUs);
  }

  /**
   * Enqueues a new media period based on the specified information as the new loading media period,
   * and returns it.
   *
   * @param rendererCapabilities The renderer capabilities.
   * @param trackSelector The track selector.
   * @param allocator The allocator.
   * @param mediaSource The media source that produced the media period.
   * @param uid The unique identifier for the containing timeline period.
   * @param info Information used to identify this media period in its timeline period.
   */
  public MediaPeriod enqueueNextMediaPeriod(
      RendererCapabilities[] rendererCapabilities,
      TrackSelector trackSelector,
      Allocator allocator,
      MediaSource mediaSource,
      Object uid,
      MediaPeriodInfo info) {
    long rendererPositionOffsetUs =
        loading == null
            ? info.startPositionUs
            : (loading.getRendererOffset() + loading.info.durationUs);
    MediaPeriodHolder newPeriodHolder =
        new MediaPeriodHolder(
            rendererCapabilities,
            rendererPositionOffsetUs,
            trackSelector,
            allocator,
            mediaSource,
            uid,
            info);
    if (loading != null) {
      Assertions.checkState(hasPlayingPeriod());
      loading.next = newPeriodHolder;
    }
    oldFrontPeriodUid = null;
    loading = newPeriodHolder;
    length++;
    return newPeriodHolder.mediaPeriod;
  }

  /**
   * Returns the loading period holder which is at the end of the queue, or null if the queue is
   * empty.
   */
  public MediaPeriodHolder getLoadingPeriod() {
    return loading;
  }

  /**
   * Returns the playing period holder which is at the front of the queue, or null if the queue is
   * empty or hasn't started playing.
   */
  public MediaPeriodHolder getPlayingPeriod() {
    return playing;
  }

  /**
   * Returns the reading period holder, or null if the queue is empty or the player hasn't started
   * reading.
   */
  public MediaPeriodHolder getReadingPeriod() {
    return reading;
  }

  /**
   * Returns the period holder in the front of the queue which is the playing period holder when
   * playing, or null if the queue is empty.
   */
  public MediaPeriodHolder getFrontPeriod() {
    return hasPlayingPeriod() ? playing : loading;
  }

  /** Returns whether the reading and playing period holders are set. */
  public boolean hasPlayingPeriod() {
    return playing != null;
  }

  /**
   * Continues reading from the next period holder in the queue.
   *
   * @return The updated reading period holder.
   */
  public MediaPeriodHolder advanceReadingPeriod() {
    Assertions.checkState(reading != null && reading.next != null);
    reading = reading.next;
    return reading;
  }

  /**
   * Dequeues the playing period holder from the front of the queue and advances the playing period
   * holder to be the next item in the queue. If the playing period holder is unset, set it to the
   * item in the front of the queue.
   *
   * @return The updated playing period holder, or null if the queue is or becomes empty.
   */
  public MediaPeriodHolder advancePlayingPeriod() {
    if (playing != null) {
      if (playing == reading) {
        reading = playing.next;
      }
      playing.release();
      length--;
      if (length == 0) {
        loading = null;
        oldFrontPeriodUid = playing.uid;
        oldFrontPeriodWindowSequenceNumber = playing.info.id.windowSequenceNumber;
      }
      playing = playing.next;
    } else {
      playing = loading;
      reading = loading;
    }
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
    boolean removedReading = false;
    loading = mediaPeriodHolder;
    while (mediaPeriodHolder.next != null) {
      mediaPeriodHolder = mediaPeriodHolder.next;
      if (mediaPeriodHolder == reading) {
        reading = playing;
        removedReading = true;
      }
      mediaPeriodHolder.release();
      length--;
    }
    loading.next = null;
    return removedReading;
  }

  /**
   * Clears the queue.
   *
   * @param keepFrontPeriodUid Whether the queue should keep the id of the media period in the front
   *     of queue (typically the playing one) for later reuse.
   */
  public void clear(boolean keepFrontPeriodUid) {
    MediaPeriodHolder front = getFrontPeriod();
    if (front != null) {
      oldFrontPeriodUid = keepFrontPeriodUid ? front.uid : null;
      oldFrontPeriodWindowSequenceNumber = front.info.id.windowSequenceNumber;
      front.release();
      removeAfter(front);
    } else if (!keepFrontPeriodUid) {
      oldFrontPeriodUid = null;
    }
    playing = null;
    loading = null;
    reading = null;
    length = 0;
  }

  /**
   * Updates media periods in the queue to take into account the latest timeline, and returns
   * whether the timeline change has been fully handled. If not, it is necessary to seek to the
   * current playback position. The method assumes that the first media period in the queue is still
   * consistent with the new timeline.
   *
   * @param playingPeriodId The current playing media period identifier.
   * @param rendererPositionUs The current renderer position in microseconds.
   * @return Whether the timeline change has been handled completely.
   */
  public boolean updateQueuedPeriods(MediaPeriodId playingPeriodId, long rendererPositionUs) {
    // TODO: Merge this into setTimeline so that the queue gets updated as soon as the new timeline
    // is set, once all cases handled by ExoPlayerImplInternal.handleSourceInfoRefreshed can be
    // handled here.
    int periodIndex = playingPeriodId.periodIndex;
    // The front period is either playing now, or is being loaded and will become the playing
    // period.
    MediaPeriodHolder previousPeriodHolder = null;
    MediaPeriodHolder periodHolder = getFrontPeriod();
    while (periodHolder != null) {
      if (previousPeriodHolder == null) {
        periodHolder.info = getUpdatedMediaPeriodInfo(periodHolder.info, periodIndex);
      } else {
        // Check this period holder still follows the previous one, based on the new timeline.
        if (periodIndex == C.INDEX_UNSET
            || !periodHolder.uid.equals(timeline.getUidOfPeriod(periodIndex))) {
          // The holder uid is inconsistent with the new timeline.
          return !removeAfter(previousPeriodHolder);
        }
        MediaPeriodInfo periodInfo =
            getFollowingMediaPeriodInfo(previousPeriodHolder, rendererPositionUs);
        if (periodInfo == null) {
          // We've loaded a next media period that is not in the new timeline.
          return !removeAfter(previousPeriodHolder);
        }
        // Update the period index.
        periodHolder.info = getUpdatedMediaPeriodInfo(periodHolder.info, periodIndex);
        // Check the media period information matches the new timeline.
        if (!canKeepMediaPeriodHolder(periodHolder, periodInfo)) {
          return !removeAfter(previousPeriodHolder);
        }
      }

      if (periodHolder.info.isLastInTimelinePeriod) {
        // Move on to the next timeline period index, if there is one.
        periodIndex =
            timeline.getNextPeriodIndex(
                periodIndex, period, window, repeatMode, shuffleModeEnabled);
      }

      previousPeriodHolder = periodHolder;
      periodHolder = periodHolder.next;
    }
    return true;
  }

  /**
   * Returns new media period info based on specified {@code mediaPeriodInfo} but taking into
   * account the current timeline, and with the period index updated to {@code newPeriodIndex}.
   *
   * @param mediaPeriodInfo Media period info for a media period based on an old timeline.
   * @param newPeriodIndex The new period index in the new timeline for the existing media period.
   * @return The updated media period info for the current timeline.
   */
  public MediaPeriodInfo getUpdatedMediaPeriodInfo(
      MediaPeriodInfo mediaPeriodInfo, int newPeriodIndex) {
    return getUpdatedMediaPeriodInfo(
        mediaPeriodInfo, mediaPeriodInfo.id.copyWithPeriodIndex(newPeriodIndex));
  }

  /**
   * Resolves the specified timeline period and position to a {@link MediaPeriodId} that should be
   * played, returning an identifier for an ad group if one needs to be played before the specified
   * position, or an identifier for a content media period if not.
   *
   * @param periodIndex The index of the timeline period to play.
   * @param positionUs The next content position in the period to play.
   * @return The identifier for the first media period to play, taking into account unplayed ads.
   */
  public MediaPeriodId resolveMediaPeriodIdForAds(int periodIndex, long positionUs) {
    long windowSequenceNumber = resolvePeriodIndexToWindowSequenceNumber(periodIndex);
    return resolveMediaPeriodIdForAds(periodIndex, positionUs, windowSequenceNumber);
  }

  // Internal methods.

  /**
   * Resolves the specified timeline period and position to a {@link MediaPeriodId} that should be
   * played, returning an identifier for an ad group if one needs to be played before the specified
   * position, or an identifier for a content media period if not.
   *
   * @param periodIndex The index of the timeline period to play.
   * @param positionUs The next content position in the period to play.
   * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
   *     windows this period is part of.
   * @return The identifier for the first media period to play, taking into account unplayed ads.
   */
  private MediaPeriodId resolveMediaPeriodIdForAds(
      int periodIndex, long positionUs, long windowSequenceNumber) {
    timeline.getPeriod(periodIndex, period);
    int adGroupIndex = period.getAdGroupIndexForPositionUs(positionUs);
    if (adGroupIndex == C.INDEX_UNSET) {
      int nextAdGroupIndex = period.getAdGroupIndexAfterPositionUs(positionUs);
      long endPositionUs =
          nextAdGroupIndex == C.INDEX_UNSET
              ? C.TIME_END_OF_SOURCE
              : period.getAdGroupTimeUs(nextAdGroupIndex);
      return new MediaPeriodId(periodIndex, windowSequenceNumber, endPositionUs);
    } else {
      int adIndexInAdGroup = period.getFirstAdIndexToPlay(adGroupIndex);
      return new MediaPeriodId(periodIndex, adGroupIndex, adIndexInAdGroup, windowSequenceNumber);
    }
  }

  /**
   * Resolves the specified period index to a corresponding window sequence number. Either by
   * reusing the window sequence number of an existing matching media period or by creating a new
   * window sequence number.
   *
   * @param periodIndex The index of the timeline period.
   * @return A window sequence number for a media period created for this timeline period.
   */
  private long resolvePeriodIndexToWindowSequenceNumber(int periodIndex) {
    Object periodUid = timeline.getPeriod(periodIndex, period, /* setIds= */ true).uid;
    int windowIndex = period.windowIndex;
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
    MediaPeriodHolder mediaPeriodHolder = getFrontPeriod();
    while (mediaPeriodHolder != null) {
      if (mediaPeriodHolder.uid.equals(periodUid)) {
        // Reuse window sequence number of first exact period match.
        return mediaPeriodHolder.info.id.windowSequenceNumber;
      }
      mediaPeriodHolder = mediaPeriodHolder.next;
    }
    mediaPeriodHolder = getFrontPeriod();
    while (mediaPeriodHolder != null) {
      int indexOfHolderInTimeline = timeline.getIndexOfPeriod(mediaPeriodHolder.uid);
      if (indexOfHolderInTimeline != C.INDEX_UNSET) {
        int holderWindowIndex = timeline.getPeriod(indexOfHolderInTimeline, period).windowIndex;
        if (holderWindowIndex == windowIndex) {
          // As an alternative, try to match other periods of the same window.
          return mediaPeriodHolder.info.id.windowSequenceNumber;
        }
      }
      mediaPeriodHolder = mediaPeriodHolder.next;
    }
    // If no match is found, create new sequence number.
    return nextWindowSequenceNumber++;
  }

  /**
   * Returns whether {@code periodHolder} can be kept for playing the media period described by
   * {@code info}.
   */
  private boolean canKeepMediaPeriodHolder(MediaPeriodHolder periodHolder, MediaPeriodInfo info) {
    MediaPeriodInfo periodHolderInfo = periodHolder.info;
    return periodHolderInfo.startPositionUs == info.startPositionUs
        && periodHolderInfo.id.equals(info.id);
  }

  /**
   * Updates the queue for any playback mode change, and returns whether the change was fully
   * handled. If not, it is necessary to seek to the current playback position.
   */
  private boolean updateForPlaybackModeChange() {
    // Find the last existing period holder that matches the new period order.
    MediaPeriodHolder lastValidPeriodHolder = getFrontPeriod();
    if (lastValidPeriodHolder == null) {
      return true;
    }
    while (true) {
      int nextPeriodIndex =
          timeline.getNextPeriodIndex(
              lastValidPeriodHolder.info.id.periodIndex,
              period,
              window,
              repeatMode,
              shuffleModeEnabled);
      while (lastValidPeriodHolder.next != null
          && !lastValidPeriodHolder.info.isLastInTimelinePeriod) {
        lastValidPeriodHolder = lastValidPeriodHolder.next;
      }
      if (nextPeriodIndex == C.INDEX_UNSET
          || lastValidPeriodHolder.next == null
          || lastValidPeriodHolder.next.info.id.periodIndex != nextPeriodIndex) {
        break;
      }
      lastValidPeriodHolder = lastValidPeriodHolder.next;
    }

    // Release any period holders that don't match the new period order.
    boolean readingPeriodRemoved = removeAfter(lastValidPeriodHolder);

    // Update the period info for the last holder, as it may now be the last period in the timeline.
    lastValidPeriodHolder.info =
        getUpdatedMediaPeriodInfo(lastValidPeriodHolder.info, lastValidPeriodHolder.info.id);

    // If renderers may have read from a period that's been removed, it is necessary to restart.
    return !readingPeriodRemoved || !hasPlayingPeriod();
  }

  /**
   * Returns the first {@link MediaPeriodInfo} to play, based on the specified playback position.
   */
  private MediaPeriodInfo getFirstMediaPeriodInfo(PlaybackInfo playbackInfo) {
    return getMediaPeriodInfo(
        playbackInfo.periodId, playbackInfo.contentPositionUs, playbackInfo.startPositionUs);
  }

  /**
   * Returns the {@link MediaPeriodInfo} for the media period following {@code mediaPeriodHolder}'s
   * media period.
   *
   * @param mediaPeriodHolder The media period holder.
   * @param rendererPositionUs The current renderer position in microseconds.
   * @return The following media period's info, or {@code null} if it is not yet possible to get the
   *     next media period info.
   */
  private @Nullable MediaPeriodInfo getFollowingMediaPeriodInfo(
      MediaPeriodHolder mediaPeriodHolder, long rendererPositionUs) {
    // TODO: This method is called repeatedly from ExoPlayerImplInternal.maybeUpdateLoadingPeriod
    // but if the timeline is not ready to provide the next period it can't return a non-null value
    // until the timeline is updated. Store whether the next timeline period is ready when the
    // timeline is updated, to avoid repeatedly checking the same timeline.
    MediaPeriodInfo mediaPeriodInfo = mediaPeriodHolder.info;
    if (mediaPeriodInfo.isLastInTimelinePeriod) {
      int nextPeriodIndex =
          timeline.getNextPeriodIndex(
              mediaPeriodInfo.id.periodIndex, period, window, repeatMode, shuffleModeEnabled);
      if (nextPeriodIndex == C.INDEX_UNSET) {
        // We can't create a next period yet.
        return null;
      }

      long startPositionUs;
      int nextWindowIndex =
          timeline.getPeriod(nextPeriodIndex, period, /* setIds= */ true).windowIndex;
      Object nextPeriodUid = period.uid;
      long windowSequenceNumber = mediaPeriodInfo.id.windowSequenceNumber;
      if (timeline.getWindow(nextWindowIndex, window).firstPeriodIndex == nextPeriodIndex) {
        // We're starting to buffer a new window. When playback transitions to this window we'll
        // want it to be from its default start position. The expected delay until playback
        // transitions is equal the duration of media that's currently buffered (assuming no
        // interruptions). Hence we project the default start position forward by the duration of
        // the buffer, and start buffering from this point.
        long defaultPositionProjectionUs =
            mediaPeriodHolder.getRendererOffset() + mediaPeriodInfo.durationUs - rendererPositionUs;
        Pair<Integer, Long> defaultPosition =
            timeline.getPeriodPosition(
                window,
                period,
                nextWindowIndex,
                C.TIME_UNSET,
                Math.max(0, defaultPositionProjectionUs));
        if (defaultPosition == null) {
          return null;
        }
        nextPeriodIndex = defaultPosition.first;
        startPositionUs = defaultPosition.second;
        if (mediaPeriodHolder.next != null && mediaPeriodHolder.next.uid.equals(nextPeriodUid)) {
          windowSequenceNumber = mediaPeriodHolder.next.info.id.windowSequenceNumber;
        } else {
          windowSequenceNumber = nextWindowSequenceNumber++;
        }
      } else {
        startPositionUs = 0;
      }
      MediaPeriodId periodId =
          resolveMediaPeriodIdForAds(nextPeriodIndex, startPositionUs, windowSequenceNumber);
      return getMediaPeriodInfo(periodId, startPositionUs, startPositionUs);
    }

    MediaPeriodId currentPeriodId = mediaPeriodInfo.id;
    timeline.getPeriod(currentPeriodId.periodIndex, period);
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
        return !period.isAdAvailable(adGroupIndex, nextAdIndexInAdGroup)
            ? null
            : getMediaPeriodInfoForAd(
                currentPeriodId.periodIndex,
                adGroupIndex,
                nextAdIndexInAdGroup,
                mediaPeriodInfo.contentPositionUs,
                currentPeriodId.windowSequenceNumber);
      } else {
        // Play content from the ad group position.
        return getMediaPeriodInfoForContent(
            currentPeriodId.periodIndex,
            mediaPeriodInfo.contentPositionUs,
            currentPeriodId.windowSequenceNumber);
      }
    } else if (mediaPeriodInfo.id.endPositionUs != C.TIME_END_OF_SOURCE) {
      // Play the next ad group if it's available.
      int nextAdGroupIndex = period.getAdGroupIndexForPositionUs(mediaPeriodInfo.id.endPositionUs);
      if (nextAdGroupIndex == C.INDEX_UNSET) {
        // The next ad group can't be played. Play content from the ad group position instead.
        return getMediaPeriodInfoForContent(
            currentPeriodId.periodIndex,
            mediaPeriodInfo.id.endPositionUs,
            currentPeriodId.windowSequenceNumber);
      }
      int adIndexInAdGroup = period.getFirstAdIndexToPlay(nextAdGroupIndex);
      return !period.isAdAvailable(nextAdGroupIndex, adIndexInAdGroup)
          ? null
          : getMediaPeriodInfoForAd(
              currentPeriodId.periodIndex,
              nextAdGroupIndex,
              adIndexInAdGroup,
              mediaPeriodInfo.id.endPositionUs,
              currentPeriodId.windowSequenceNumber);
    } else {
      // Check if the postroll ad should be played.
      int adGroupCount = period.getAdGroupCount();
      if (adGroupCount == 0) {
        return null;
      }
      int adGroupIndex = adGroupCount - 1;
      if (period.getAdGroupTimeUs(adGroupIndex) != C.TIME_END_OF_SOURCE
          || period.hasPlayedAdGroup(adGroupIndex)) {
        return null;
      }
      int adIndexInAdGroup = period.getFirstAdIndexToPlay(adGroupIndex);
      if (!period.isAdAvailable(adGroupIndex, adIndexInAdGroup)) {
        return null;
      }
      long contentDurationUs = period.getDurationUs();
      return getMediaPeriodInfoForAd(
          currentPeriodId.periodIndex,
          adGroupIndex,
          adIndexInAdGroup,
          contentDurationUs,
          currentPeriodId.windowSequenceNumber);
    }
  }

  private MediaPeriodInfo getUpdatedMediaPeriodInfo(MediaPeriodInfo info, MediaPeriodId newId) {
    long startPositionUs = info.startPositionUs;
    boolean isLastInPeriod = isLastInPeriod(newId);
    boolean isLastInTimeline = isLastInTimeline(newId, isLastInPeriod);
    timeline.getPeriod(newId.periodIndex, period);
    long durationUs =
        newId.isAd()
            ? period.getAdDurationUs(newId.adGroupIndex, newId.adIndexInAdGroup)
            : (newId.endPositionUs == C.TIME_END_OF_SOURCE
                ? period.getDurationUs()
                : newId.endPositionUs);
    return new MediaPeriodInfo(
        newId,
        startPositionUs,
        info.contentPositionUs,
        durationUs,
        isLastInPeriod,
        isLastInTimeline);
  }

  private MediaPeriodInfo getMediaPeriodInfo(
      MediaPeriodId id, long contentPositionUs, long startPositionUs) {
    timeline.getPeriod(id.periodIndex, period);
    if (id.isAd()) {
      if (!period.isAdAvailable(id.adGroupIndex, id.adIndexInAdGroup)) {
        return null;
      }
      return getMediaPeriodInfoForAd(
          id.periodIndex,
          id.adGroupIndex,
          id.adIndexInAdGroup,
          contentPositionUs,
          id.windowSequenceNumber);
    } else {
      return getMediaPeriodInfoForContent(id.periodIndex, startPositionUs, id.windowSequenceNumber);
    }
  }

  private MediaPeriodInfo getMediaPeriodInfoForAd(
      int periodIndex,
      int adGroupIndex,
      int adIndexInAdGroup,
      long contentPositionUs,
      long windowSequenceNumber) {
    MediaPeriodId id =
        new MediaPeriodId(periodIndex, adGroupIndex, adIndexInAdGroup, windowSequenceNumber);
    boolean isLastInPeriod = isLastInPeriod(id);
    boolean isLastInTimeline = isLastInTimeline(id, isLastInPeriod);
    long durationUs =
        timeline
            .getPeriod(id.periodIndex, period)
            .getAdDurationUs(id.adGroupIndex, id.adIndexInAdGroup);
    long startPositionUs =
        adIndexInAdGroup == period.getFirstAdIndexToPlay(adGroupIndex)
            ? period.getAdResumePositionUs()
            : 0;
    return new MediaPeriodInfo(
        id,
        startPositionUs,
        contentPositionUs,
        durationUs,
        isLastInPeriod,
        isLastInTimeline);
  }

  private MediaPeriodInfo getMediaPeriodInfoForContent(
      int periodIndex, long startPositionUs, long windowSequenceNumber) {
    int nextAdGroupIndex = period.getAdGroupIndexAfterPositionUs(startPositionUs);
    long endPositionUs =
        nextAdGroupIndex == C.INDEX_UNSET
            ? C.TIME_END_OF_SOURCE
            : period.getAdGroupTimeUs(nextAdGroupIndex);
    MediaPeriodId id = new MediaPeriodId(periodIndex, windowSequenceNumber, endPositionUs);
    timeline.getPeriod(id.periodIndex, period);
    boolean isLastInPeriod = isLastInPeriod(id);
    boolean isLastInTimeline = isLastInTimeline(id, isLastInPeriod);
    long durationUs =
        endPositionUs == C.TIME_END_OF_SOURCE ? period.getDurationUs() : endPositionUs;
    return new MediaPeriodInfo(
        id, startPositionUs, C.TIME_UNSET, durationUs, isLastInPeriod, isLastInTimeline);
  }

  private boolean isLastInPeriod(MediaPeriodId id) {
    int adGroupCount = timeline.getPeriod(id.periodIndex, period).getAdGroupCount();
    if (adGroupCount == 0) {
      return true;
    }

    int lastAdGroupIndex = adGroupCount - 1;
    boolean isAd = id.isAd();
    if (period.getAdGroupTimeUs(lastAdGroupIndex) != C.TIME_END_OF_SOURCE) {
      // There's no postroll ad.
      return !isAd && id.endPositionUs == C.TIME_END_OF_SOURCE;
    }

    int postrollAdCount = period.getAdCountInAdGroup(lastAdGroupIndex);
    if (postrollAdCount == C.LENGTH_UNSET) {
      // We won't know if this is the last ad until we know how many postroll ads there are.
      return false;
    }

    boolean isLastAd =
        isAd && id.adGroupIndex == lastAdGroupIndex && id.adIndexInAdGroup == postrollAdCount - 1;
    return isLastAd || (!isAd && period.getFirstAdIndexToPlay(lastAdGroupIndex) == postrollAdCount);
  }

  private boolean isLastInTimeline(MediaPeriodId id, boolean isLastMediaPeriodInPeriod) {
    int windowIndex = timeline.getPeriod(id.periodIndex, period).windowIndex;
    return !timeline.getWindow(windowIndex, window).isDynamic
        && timeline.isLastPeriod(id.periodIndex, period, window, repeatMode, shuffleModeEnabled)
        && isLastMediaPeriodInPeriod;
  }
}
