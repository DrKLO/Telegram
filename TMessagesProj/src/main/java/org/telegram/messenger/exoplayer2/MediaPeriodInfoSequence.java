/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.util.Pair;
import org.telegram.messenger.exoplayer2.ExoPlayerImplInternal.PlaybackInfo;
import org.telegram.messenger.exoplayer2.Player.RepeatMode;
import org.telegram.messenger.exoplayer2.source.MediaPeriod;
import org.telegram.messenger.exoplayer2.source.MediaSource.MediaPeriodId;

/**
 * Provides a sequence of {@link MediaPeriodInfo}s to the player, determining the order and
 * start/end positions for {@link MediaPeriod}s to load and play.
 */
/* package */ final class MediaPeriodInfoSequence {

  // TODO: Consider merging this class with the MediaPeriodHolder queue in ExoPlayerImplInternal.

  /**
   * Stores the information required to load and play a {@link MediaPeriod}.
   */
  public static final class MediaPeriodInfo {

    /**
     * The media period's identifier.
     */
    public final MediaPeriodId id;
    /**
     * The start position of the media to play within the media period, in microseconds.
     */
    public final long startPositionUs;
    /**
     * The end position of the media to play within the media period, in microseconds, or
     * {@link C#TIME_END_OF_SOURCE} if the end position is the end of the media period.
     */
    public final long endPositionUs;
    /**
     * If this is an ad, the position to play in the next content media period. {@link C#TIME_UNSET}
     * otherwise.
     */
    public final long contentPositionUs;
    /**
     * The duration of the media to play within the media period, in microseconds, or
     * {@link C#TIME_UNSET} if not known.
     */
    public final long durationUs;
    /**
     * Whether this is the last media period in its timeline period (e.g., a postroll ad, or a media
     * period corresponding to a timeline period without ads).
     */
    public final boolean isLastInTimelinePeriod;
    /**
     * Whether this is the last media period in the entire timeline. If true,
     * {@link #isLastInTimelinePeriod} will also be true.
     */
    public final boolean isFinal;

    private MediaPeriodInfo(MediaPeriodId id, long startPositionUs, long endPositionUs,
        long contentPositionUs, long durationUs, boolean isLastInTimelinePeriod, boolean isFinal) {
      this.id = id;
      this.startPositionUs = startPositionUs;
      this.endPositionUs = endPositionUs;
      this.contentPositionUs = contentPositionUs;
      this.durationUs = durationUs;
      this.isLastInTimelinePeriod = isLastInTimelinePeriod;
      this.isFinal = isFinal;
    }

    /**
     * Returns a copy of this instance with the period identifier's period index set to the
     * specified value.
     */
    public MediaPeriodInfo copyWithPeriodIndex(int periodIndex) {
      return new MediaPeriodInfo(id.copyWithPeriodIndex(periodIndex), startPositionUs,
          endPositionUs, contentPositionUs, durationUs, isLastInTimelinePeriod, isFinal);
    }

    /**
     * Returns a copy of this instance with the start position set to the specified value.
     */
    public MediaPeriodInfo copyWithStartPositionUs(long startPositionUs) {
      return new MediaPeriodInfo(id, startPositionUs, endPositionUs, contentPositionUs, durationUs,
          isLastInTimelinePeriod, isFinal);
    }

  }

  private final Timeline.Period period;
  private final Timeline.Window window;

  private Timeline timeline;
  @RepeatMode
  private int repeatMode;

  /**
   * Creates a new media period info sequence.
   */
  public MediaPeriodInfoSequence() {
    period = new Timeline.Period();
    window = new Timeline.Window();
  }

  /**
   * Sets the {@link Timeline}. Call {@link #getUpdatedMediaPeriodInfo} to update period information
   * taking into account the new timeline.
   */
  public void setTimeline(Timeline timeline) {
    this.timeline = timeline;
  }

  /**
   * Sets the {@link RepeatMode}. Call {@link #getUpdatedMediaPeriodInfo} to update period
   * information taking into account the new repeat mode.
   */
  public void setRepeatMode(@RepeatMode int repeatMode) {
    this.repeatMode = repeatMode;
  }

  /**
   * Returns the first {@link MediaPeriodInfo} to play, based on the specified playback position.
   */
  public MediaPeriodInfo getFirstMediaPeriodInfo(PlaybackInfo playbackInfo) {
    return getMediaPeriodInfo(playbackInfo.periodId, playbackInfo.contentPositionUs,
        playbackInfo.startPositionUs);
  }

  /**
   * Returns the {@link MediaPeriodInfo} following {@code currentMediaPeriodInfo}.
   *
   * @param currentMediaPeriodInfo The current media period info.
   * @param rendererOffsetUs The current renderer offset in microseconds.
   * @param rendererPositionUs The current renderer position in microseconds.
   * @return The following media period info, or {@code null} if it is not yet possible to get the
   *     next media period info.
   */
  public MediaPeriodInfo getNextMediaPeriodInfo(MediaPeriodInfo currentMediaPeriodInfo,
      long rendererOffsetUs, long rendererPositionUs) {
    // TODO: This method is called repeatedly from ExoPlayerImplInternal.maybeUpdateLoadingPeriod
    // but if the timeline is not ready to provide the next period it can't return a non-null value
    // until the timeline is updated. Store whether the next timeline period is ready when the
    // timeline is updated, to avoid repeatedly checking the same timeline.
    if (currentMediaPeriodInfo.isLastInTimelinePeriod) {
      int nextPeriodIndex = timeline.getNextPeriodIndex(currentMediaPeriodInfo.id.periodIndex,
          period, window, repeatMode);
      if (nextPeriodIndex == C.INDEX_UNSET) {
        // We can't create a next period yet.
        return null;
      }

      long startPositionUs;
      int nextWindowIndex = timeline.getPeriod(nextPeriodIndex, period).windowIndex;
      if (timeline.getWindow(nextWindowIndex, window).firstPeriodIndex == nextPeriodIndex) {
        // We're starting to buffer a new window. When playback transitions to this window we'll
        // want it to be from its default start position. The expected delay until playback
        // transitions is equal the duration of media that's currently buffered (assuming no
        // interruptions). Hence we project the default start position forward by the duration of
        // the buffer, and start buffering from this point.
        long defaultPositionProjectionUs =
            rendererOffsetUs + currentMediaPeriodInfo.durationUs - rendererPositionUs;
        Pair<Integer, Long> defaultPosition = timeline.getPeriodPosition(window, period,
            nextWindowIndex, C.TIME_UNSET, Math.max(0, defaultPositionProjectionUs));
        if (defaultPosition == null) {
          return null;
        }
        nextPeriodIndex = defaultPosition.first;
        startPositionUs = defaultPosition.second;
      } else {
        startPositionUs = 0;
      }
      MediaPeriodId periodId = resolvePeriodPositionForAds(nextPeriodIndex, startPositionUs);
      return getMediaPeriodInfo(periodId, startPositionUs, startPositionUs);
    }

    MediaPeriodId currentPeriodId = currentMediaPeriodInfo.id;
    if (currentPeriodId.isAd()) {
      int currentAdGroupIndex = currentPeriodId.adGroupIndex;
      timeline.getPeriod(currentPeriodId.periodIndex, period);
      int adCountInCurrentAdGroup = period.getAdCountInAdGroup(currentAdGroupIndex);
      if (adCountInCurrentAdGroup == C.LENGTH_UNSET) {
        return null;
      }
      int nextAdIndexInAdGroup = currentPeriodId.adIndexInAdGroup + 1;
      if (nextAdIndexInAdGroup < adCountInCurrentAdGroup) {
        // Play the next ad in the ad group if it's available.
        return !period.isAdAvailable(currentAdGroupIndex, nextAdIndexInAdGroup) ? null
            : getMediaPeriodInfoForAd(currentPeriodId.periodIndex, currentAdGroupIndex,
                nextAdIndexInAdGroup, currentMediaPeriodInfo.contentPositionUs);
      } else {
        // Play content from the ad group position.
        int nextAdGroupIndex =
            period.getAdGroupIndexAfterPositionUs(currentMediaPeriodInfo.contentPositionUs);
        long endUs = nextAdGroupIndex == C.INDEX_UNSET ? C.TIME_END_OF_SOURCE
            : period.getAdGroupTimeUs(nextAdGroupIndex);
        return getMediaPeriodInfoForContent(currentPeriodId.periodIndex,
            currentMediaPeriodInfo.contentPositionUs, endUs);
      }
    } else if (currentMediaPeriodInfo.endPositionUs != C.TIME_END_OF_SOURCE) {
      // Play the next ad group if it's available.
      int nextAdGroupIndex =
          period.getAdGroupIndexForPositionUs(currentMediaPeriodInfo.endPositionUs);
      return !period.isAdAvailable(nextAdGroupIndex, 0) ? null
          : getMediaPeriodInfoForAd(currentPeriodId.periodIndex, nextAdGroupIndex, 0,
              currentMediaPeriodInfo.endPositionUs);
    } else {
      // Check if the postroll ad should be played.
      int adGroupCount = period.getAdGroupCount();
      if (adGroupCount == 0
          || period.getAdGroupTimeUs(adGroupCount - 1) != C.TIME_END_OF_SOURCE
          || period.hasPlayedAdGroup(adGroupCount - 1)
          || !period.isAdAvailable(adGroupCount - 1, 0)) {
        return null;
      }
      long contentDurationUs = period.getDurationUs();
      return getMediaPeriodInfoForAd(currentPeriodId.periodIndex, adGroupCount - 1, 0,
          contentDurationUs);
    }
  }

  /**
   * Resolves the specified timeline period and position to a {@link MediaPeriodId} that should be
   * played, returning an identifier for an ad group if one needs to be played before the specified
   * position, or an identifier for a content media period if not.
   */
  public MediaPeriodId resolvePeriodPositionForAds(int periodIndex, long positionUs) {
    timeline.getPeriod(periodIndex, period);
    int adGroupIndex = period.getAdGroupIndexForPositionUs(positionUs);
    if (adGroupIndex == C.INDEX_UNSET) {
      return new MediaPeriodId(periodIndex);
    } else {
      int adIndexInAdGroup = period.getPlayedAdCount(adGroupIndex);
      return new MediaPeriodId(periodIndex, adGroupIndex, adIndexInAdGroup);
    }
  }

  /**
   * Returns the {@code mediaPeriodInfo} updated to take into account the current timeline.
   */
  public MediaPeriodInfo getUpdatedMediaPeriodInfo(MediaPeriodInfo mediaPeriodInfo) {
    return getUpdatedMediaPeriodInfo(mediaPeriodInfo, mediaPeriodInfo.id);
  }

  /**
   * Returns the {@code mediaPeriodInfo} updated to take into account the current timeline,
   * resetting the identifier of the media period to the specified {@code newPeriodIndex}.
   */
  public MediaPeriodInfo getUpdatedMediaPeriodInfo(MediaPeriodInfo mediaPeriodInfo,
      int newPeriodIndex) {
    return getUpdatedMediaPeriodInfo(mediaPeriodInfo,
        mediaPeriodInfo.id.copyWithPeriodIndex(newPeriodIndex));
  }

  // Internal methods.

  private MediaPeriodInfo getUpdatedMediaPeriodInfo(MediaPeriodInfo info, MediaPeriodId newId) {
    long startPositionUs = info.startPositionUs;
    long endPositionUs = info.endPositionUs;
    boolean isLastInPeriod = isLastInPeriod(newId, endPositionUs);
    boolean isLastInTimeline = isLastInTimeline(newId, isLastInPeriod);
    timeline.getPeriod(newId.periodIndex, period);
    long durationUs = newId.isAd()
        ? period.getAdDurationUs(newId.adGroupIndex, newId.adIndexInAdGroup)
        : (endPositionUs == C.TIME_END_OF_SOURCE ? period.getDurationUs() : endPositionUs);
    return new MediaPeriodInfo(newId, startPositionUs, endPositionUs, info.contentPositionUs,
        durationUs, isLastInPeriod, isLastInTimeline);
  }

  private MediaPeriodInfo getMediaPeriodInfo(MediaPeriodId id, long contentPositionUs,
      long startPositionUs) {
    timeline.getPeriod(id.periodIndex, period);
    if (id.isAd()) {
      if (!period.isAdAvailable(id.adGroupIndex, id.adIndexInAdGroup)) {
        return null;
      }
      return getMediaPeriodInfoForAd(id.periodIndex, id.adGroupIndex, id.adIndexInAdGroup,
          contentPositionUs);
    } else {
      int nextAdGroupIndex = period.getAdGroupIndexAfterPositionUs(startPositionUs);
      long endUs = nextAdGroupIndex == C.INDEX_UNSET ? C.TIME_END_OF_SOURCE
          : period.getAdGroupTimeUs(nextAdGroupIndex);
      return getMediaPeriodInfoForContent(id.periodIndex, startPositionUs, endUs);
    }
  }

  private MediaPeriodInfo getMediaPeriodInfoForAd(int periodIndex, int adGroupIndex,
      int adIndexInAdGroup, long contentPositionUs) {
    MediaPeriodId id = new MediaPeriodId(periodIndex, adGroupIndex, adIndexInAdGroup);
    boolean isLastInPeriod = isLastInPeriod(id, C.TIME_END_OF_SOURCE);
    boolean isLastInTimeline = isLastInTimeline(id, isLastInPeriod);
    long durationUs = timeline.getPeriod(id.periodIndex, period)
        .getAdDurationUs(id.adGroupIndex, id.adIndexInAdGroup);
    long startPositionUs = adIndexInAdGroup == period.getPlayedAdCount(adGroupIndex)
        ? period.getAdResumePositionUs() : 0;
    return new MediaPeriodInfo(id, startPositionUs, C.TIME_END_OF_SOURCE, contentPositionUs,
        durationUs, isLastInPeriod, isLastInTimeline);
  }

  private MediaPeriodInfo getMediaPeriodInfoForContent(int periodIndex, long startPositionUs,
      long endUs) {
    MediaPeriodId id = new MediaPeriodId(periodIndex);
    boolean isLastInPeriod = isLastInPeriod(id, endUs);
    boolean isLastInTimeline = isLastInTimeline(id, isLastInPeriod);
    timeline.getPeriod(id.periodIndex, period);
    long durationUs = endUs == C.TIME_END_OF_SOURCE ? period.getDurationUs() : endUs;
    return new MediaPeriodInfo(id, startPositionUs, endUs, C.TIME_UNSET, durationUs, isLastInPeriod,
        isLastInTimeline);
  }

  private boolean isLastInPeriod(MediaPeriodId id, long endPositionUs) {
    int adGroupCount = timeline.getPeriod(id.periodIndex, period).getAdGroupCount();
    if (adGroupCount == 0) {
      return true;
    }

    int lastAdGroupIndex = adGroupCount - 1;
    boolean isAd = id.isAd();
    if (period.getAdGroupTimeUs(lastAdGroupIndex) != C.TIME_END_OF_SOURCE) {
      // There's no postroll ad.
      return !isAd && endPositionUs == C.TIME_END_OF_SOURCE;
    }

    int postrollAdCount = period.getAdCountInAdGroup(lastAdGroupIndex);
    if (postrollAdCount == C.LENGTH_UNSET) {
      // We won't know if this is the last ad until we know how many postroll ads there are.
      return false;
    }

    boolean isLastAd = isAd && id.adGroupIndex == lastAdGroupIndex
        && id.adIndexInAdGroup == postrollAdCount - 1;
    return isLastAd || (!isAd && period.getPlayedAdCount(lastAdGroupIndex) == postrollAdCount);
  }

  private boolean isLastInTimeline(MediaPeriodId id, boolean isLastMediaPeriodInPeriod) {
    int windowIndex = timeline.getPeriod(id.periodIndex, period).windowIndex;
    return !timeline.getWindow(windowIndex, window).isDynamic
        && timeline.isLastPeriod(id.periodIndex, period, window, repeatMode)
        && isLastMediaPeriodInPeriod;
  }

}
