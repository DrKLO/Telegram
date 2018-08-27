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
package com.google.android.exoplayer2;

import android.support.annotation.Nullable;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;

/**
 * Information about an ongoing playback.
 */
/* package */ final class PlaybackInfo {

  /**
   * Dummy media period id used while the timeline is empty and no period id is specified. This id
   * is used when playback infos are created with {@link #createDummy(long, TrackSelectorResult)}.
   */
  public static final MediaPeriodId DUMMY_MEDIA_PERIOD_ID = new MediaPeriodId(/* periodIndex= */ 0);

  /** The current {@link Timeline}. */
  public final Timeline timeline;
  /** The current manifest. */
  public final @Nullable Object manifest;
  /** The {@link MediaPeriodId} of the currently playing media period in the {@link #timeline}. */
  public final MediaPeriodId periodId;
  /**
   * The start position at which playback started in {@link #periodId} relative to the start of the
   * associated period in the {@link #timeline}, in microseconds.
   */
  public final long startPositionUs;
  /**
   * If {@link #periodId} refers to an ad, the position of the suspended content relative to the
   * start of the associated period in the {@link #timeline}, in microseconds. {@link C#TIME_UNSET}
   * if {@link #periodId} does not refer to an ad.
   */
  public final long contentPositionUs;
  /** The current playback state. One of the {@link Player}.STATE_ constants. */
  public final int playbackState;
  /** Whether the player is currently loading. */
  public final boolean isLoading;
  /** The currently available track groups. */
  public final TrackGroupArray trackGroups;
  /** The result of the current track selection. */
  public final TrackSelectorResult trackSelectorResult;
  /** The {@link MediaPeriodId} of the currently loading media period in the {@link #timeline}. */
  public final MediaPeriodId loadingMediaPeriodId;

  /**
   * Position up to which media is buffered in {@link #loadingMediaPeriodId) relative to the start
   * of the associated period in the {@link #timeline}, in microseconds.
   */
  public volatile long bufferedPositionUs;
  /**
   * Total duration of buffered media from {@link #positionUs} to {@link #bufferedPositionUs}
   * including all ads.
   */
  public volatile long totalBufferedDurationUs;
  /**
   * Current playback position in {@link #periodId} relative to the start of the associated period
   * in the {@link #timeline}, in microseconds.
   */
  public volatile long positionUs;

  /**
   * Creates empty dummy playback info which can be used for masking as long as no real playback
   * info is available.
   *
   * @param startPositionUs The start position at which playback should start, in microseconds.
   * @param emptyTrackSelectorResult An empty track selector result with null entries for each
   *     renderer.
   * @return A dummy playback info.
   */
  public static PlaybackInfo createDummy(
      long startPositionUs, TrackSelectorResult emptyTrackSelectorResult) {
    return new PlaybackInfo(
        Timeline.EMPTY,
        /* manifest= */ null,
        DUMMY_MEDIA_PERIOD_ID,
        startPositionUs,
        /* contentPositionUs =*/ C.TIME_UNSET,
        Player.STATE_IDLE,
        /* isLoading= */ false,
        TrackGroupArray.EMPTY,
        emptyTrackSelectorResult,
        DUMMY_MEDIA_PERIOD_ID,
        startPositionUs,
        /* totalBufferedDurationUs= */ 0,
        startPositionUs);
  }

  public PlaybackInfo(
      Timeline timeline,
      @Nullable Object manifest,
      MediaPeriodId periodId,
      long startPositionUs,
      long contentPositionUs,
      int playbackState,
      boolean isLoading,
      TrackGroupArray trackGroups,
      TrackSelectorResult trackSelectorResult,
      MediaPeriodId loadingMediaPeriodId,
      long bufferedPositionUs,
      long totalBufferedDurationUs,
      long positionUs) {
    this.timeline = timeline;
    this.manifest = manifest;
    this.periodId = periodId;
    this.startPositionUs = startPositionUs;
    this.contentPositionUs = contentPositionUs;
    this.playbackState = playbackState;
    this.isLoading = isLoading;
    this.trackGroups = trackGroups;
    this.trackSelectorResult = trackSelectorResult;
    this.loadingMediaPeriodId = loadingMediaPeriodId;
    this.bufferedPositionUs = bufferedPositionUs;
    this.totalBufferedDurationUs = totalBufferedDurationUs;
    this.positionUs = positionUs;
  }

  public PlaybackInfo fromNewPosition(
      MediaPeriodId periodId, long startPositionUs, long contentPositionUs) {
    return new PlaybackInfo(
        timeline,
        manifest,
        periodId,
        startPositionUs,
        periodId.isAd() ? contentPositionUs : C.TIME_UNSET,
        playbackState,
        isLoading,
        trackGroups,
        trackSelectorResult,
        periodId,
        startPositionUs,
        /* totalBufferedDurationUs= */ 0,
        startPositionUs);
  }

  public PlaybackInfo copyWithPeriodIndex(int periodIndex) {
    return new PlaybackInfo(
        timeline,
        manifest,
        periodId.copyWithPeriodIndex(periodIndex),
        startPositionUs,
        contentPositionUs,
        playbackState,
        isLoading,
        trackGroups,
        trackSelectorResult,
        loadingMediaPeriodId,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs);
  }

  public PlaybackInfo copyWithTimeline(Timeline timeline, Object manifest) {
    return new PlaybackInfo(
        timeline,
        manifest,
        periodId,
        startPositionUs,
        contentPositionUs,
        playbackState,
        isLoading,
        trackGroups,
        trackSelectorResult,
        loadingMediaPeriodId,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs);
  }

  public PlaybackInfo copyWithPlaybackState(int playbackState) {
    return new PlaybackInfo(
        timeline,
        manifest,
        periodId,
        startPositionUs,
        contentPositionUs,
        playbackState,
        isLoading,
        trackGroups,
        trackSelectorResult,
        loadingMediaPeriodId,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs);
  }

  public PlaybackInfo copyWithIsLoading(boolean isLoading) {
    return new PlaybackInfo(
        timeline,
        manifest,
        periodId,
        startPositionUs,
        contentPositionUs,
        playbackState,
        isLoading,
        trackGroups,
        trackSelectorResult,
        loadingMediaPeriodId,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs);
  }

  public PlaybackInfo copyWithTrackInfo(
      TrackGroupArray trackGroups, TrackSelectorResult trackSelectorResult) {
    return new PlaybackInfo(
        timeline,
        manifest,
        periodId,
        startPositionUs,
        contentPositionUs,
        playbackState,
        isLoading,
        trackGroups,
        trackSelectorResult,
        loadingMediaPeriodId,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs);
  }

  public PlaybackInfo copyWithLoadingMediaPeriodId(MediaPeriodId loadingMediaPeriodId) {
    return new PlaybackInfo(
        timeline,
        manifest,
        periodId,
        startPositionUs,
        contentPositionUs,
        playbackState,
        isLoading,
        trackGroups,
        trackSelectorResult,
        loadingMediaPeriodId,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs);
  }
}
