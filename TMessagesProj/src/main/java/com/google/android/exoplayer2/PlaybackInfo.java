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

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Player.PlaybackSuppressionReason;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** Information about an ongoing playback. */
/* package */ final class PlaybackInfo {

  /**
   * Placeholder media period id used while the timeline is empty and no period id is specified.
   * This id is used when playback infos are created with {@link #createDummy(TrackSelectorResult)}.
   */
  private static final MediaPeriodId PLACEHOLDER_MEDIA_PERIOD_ID =
      new MediaPeriodId(/* periodUid= */ new Object());

  /** The current {@link Timeline}. */
  public final Timeline timeline;
  /** The {@link MediaPeriodId} of the currently playing media period in the {@link #timeline}. */
  public final MediaPeriodId periodId;
  /**
   * The requested next start position for the current period in the {@link #timeline}, in
   * microseconds, or {@link C#TIME_UNSET} if the period was requested to start at its default
   * position.
   *
   * <p>Note that if {@link #periodId} refers to an ad, this is the requested start position for the
   * suspended content.
   */
  public final long requestedContentPositionUs;
  /** The start position after a reported position discontinuity, in microseconds. */
  public final long discontinuityStartPositionUs;
  /** The current playback state. One of the {@link Player}.STATE_ constants. */
  public final @Player.State int playbackState;
  /** The current playback error, or null if this is not an error state. */
  @Nullable public final ExoPlaybackException playbackError;
  /** Whether the player is currently loading. */
  public final boolean isLoading;
  /** The currently available track groups. */
  public final TrackGroupArray trackGroups;
  /** The result of the current track selection. */
  public final TrackSelectorResult trackSelectorResult;
  /** The current static metadata of the track selections. */
  public final List<Metadata> staticMetadata;
  /** The {@link MediaPeriodId} of the currently loading media period in the {@link #timeline}. */
  public final MediaPeriodId loadingMediaPeriodId;
  /** Whether playback should proceed when {@link #playbackState} == {@link Player#STATE_READY}. */
  public final boolean playWhenReady;
  /** Reason why playback is suppressed even though {@link #playWhenReady} is {@code true}. */
  public final @PlaybackSuppressionReason int playbackSuppressionReason;
  /** The playback parameters. */
  public final PlaybackParameters playbackParameters;
  /** Whether the main player loop is sleeping, while using offload scheduling. */
  public final boolean sleepingForOffload;

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
   * Creates an empty placeholder playback info which can be used for masking as long as no real
   * playback info is available.
   *
   * @param emptyTrackSelectorResult An empty track selector result with null entries for each
   *     renderer.
   * @return A placeholder playback info.
   */
  public static PlaybackInfo createDummy(TrackSelectorResult emptyTrackSelectorResult) {
    return new PlaybackInfo(
        Timeline.EMPTY,
        PLACEHOLDER_MEDIA_PERIOD_ID,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* discontinuityStartPositionUs= */ 0,
        Player.STATE_IDLE,
        /* playbackError= */ null,
        /* isLoading= */ false,
        TrackGroupArray.EMPTY,
        emptyTrackSelectorResult,
        /* staticMetadata= */ ImmutableList.of(),
        PLACEHOLDER_MEDIA_PERIOD_ID,
        /* playWhenReady= */ false,
        Player.PLAYBACK_SUPPRESSION_REASON_NONE,
        PlaybackParameters.DEFAULT,
        /* bufferedPositionUs= */ 0,
        /* totalBufferedDurationUs= */ 0,
        /* positionUs= */ 0,
        /* sleepingForOffload= */ false);
  }

  /**
   * Create playback info.
   *
   * @param timeline See {@link #timeline}.
   * @param periodId See {@link #periodId}.
   * @param requestedContentPositionUs See {@link #requestedContentPositionUs}.
   * @param playbackState See {@link #playbackState}.
   * @param playbackError See {@link #playbackError}.
   * @param isLoading See {@link #isLoading}.
   * @param trackGroups See {@link #trackGroups}.
   * @param trackSelectorResult See {@link #trackSelectorResult}.
   * @param staticMetadata See {@link #staticMetadata}.
   * @param loadingMediaPeriodId See {@link #loadingMediaPeriodId}.
   * @param playWhenReady See {@link #playWhenReady}.
   * @param playbackSuppressionReason See {@link #playbackSuppressionReason}.
   * @param playbackParameters See {@link #playbackParameters}.
   * @param bufferedPositionUs See {@link #bufferedPositionUs}.
   * @param totalBufferedDurationUs See {@link #totalBufferedDurationUs}.
   * @param positionUs See {@link #positionUs}.
   * @param sleepingForOffload See {@link #sleepingForOffload}.
   */
  public PlaybackInfo(
      Timeline timeline,
      MediaPeriodId periodId,
      long requestedContentPositionUs,
      long discontinuityStartPositionUs,
      @Player.State int playbackState,
      @Nullable ExoPlaybackException playbackError,
      boolean isLoading,
      TrackGroupArray trackGroups,
      TrackSelectorResult trackSelectorResult,
      List<Metadata> staticMetadata,
      MediaPeriodId loadingMediaPeriodId,
      boolean playWhenReady,
      @PlaybackSuppressionReason int playbackSuppressionReason,
      PlaybackParameters playbackParameters,
      long bufferedPositionUs,
      long totalBufferedDurationUs,
      long positionUs,
      boolean sleepingForOffload) {
    this.timeline = timeline;
    this.periodId = periodId;
    this.requestedContentPositionUs = requestedContentPositionUs;
    this.discontinuityStartPositionUs = discontinuityStartPositionUs;
    this.playbackState = playbackState;
    this.playbackError = playbackError;
    this.isLoading = isLoading;
    this.trackGroups = trackGroups;
    this.trackSelectorResult = trackSelectorResult;
    this.staticMetadata = staticMetadata;
    this.loadingMediaPeriodId = loadingMediaPeriodId;
    this.playWhenReady = playWhenReady;
    this.playbackSuppressionReason = playbackSuppressionReason;
    this.playbackParameters = playbackParameters;
    this.bufferedPositionUs = bufferedPositionUs;
    this.totalBufferedDurationUs = totalBufferedDurationUs;
    this.positionUs = positionUs;
    this.sleepingForOffload = sleepingForOffload;
  }

  /** Returns a placeholder period id for an empty timeline. */
  public static MediaPeriodId getDummyPeriodForEmptyTimeline() {
    return PLACEHOLDER_MEDIA_PERIOD_ID;
  }

  /**
   * Copies playback info with new playing position.
   *
   * @param periodId New playing media period. See {@link #periodId}.
   * @param positionUs New position. See {@link #positionUs}.
   * @param requestedContentPositionUs New requested content position. See {@link
   *     #requestedContentPositionUs}.
   * @param totalBufferedDurationUs New buffered duration. See {@link #totalBufferedDurationUs}.
   * @param trackGroups The track groups for the new position. See {@link #trackGroups}.
   * @param trackSelectorResult The track selector result for the new position. See {@link
   *     #trackSelectorResult}.
   * @param staticMetadata The static metadata for the track selections. See {@link
   *     #staticMetadata}.
   * @return Copied playback info with new playing position.
   */
  @CheckResult
  public PlaybackInfo copyWithNewPosition(
      MediaPeriodId periodId,
      long positionUs,
      long requestedContentPositionUs,
      long discontinuityStartPositionUs,
      long totalBufferedDurationUs,
      TrackGroupArray trackGroups,
      TrackSelectorResult trackSelectorResult,
      List<Metadata> staticMetadata) {
    return new PlaybackInfo(
        timeline,
        periodId,
        requestedContentPositionUs,
        discontinuityStartPositionUs,
        playbackState,
        playbackError,
        isLoading,
        trackGroups,
        trackSelectorResult,
        staticMetadata,
        loadingMediaPeriodId,
        playWhenReady,
        playbackSuppressionReason,
        playbackParameters,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs,
        sleepingForOffload);
  }

  /**
   * Copies playback info with the new timeline.
   *
   * @param timeline New timeline. See {@link #timeline}.
   * @return Copied playback info with the new timeline.
   */
  @CheckResult
  public PlaybackInfo copyWithTimeline(Timeline timeline) {
    return new PlaybackInfo(
        timeline,
        periodId,
        requestedContentPositionUs,
        discontinuityStartPositionUs,
        playbackState,
        playbackError,
        isLoading,
        trackGroups,
        trackSelectorResult,
        staticMetadata,
        loadingMediaPeriodId,
        playWhenReady,
        playbackSuppressionReason,
        playbackParameters,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs,
        sleepingForOffload);
  }

  /**
   * Copies playback info with new playback state.
   *
   * @param playbackState New playback state. See {@link #playbackState}.
   * @return Copied playback info with new playback state.
   */
  @CheckResult
  public PlaybackInfo copyWithPlaybackState(int playbackState) {
    return new PlaybackInfo(
        timeline,
        periodId,
        requestedContentPositionUs,
        discontinuityStartPositionUs,
        playbackState,
        playbackError,
        isLoading,
        trackGroups,
        trackSelectorResult,
        staticMetadata,
        loadingMediaPeriodId,
        playWhenReady,
        playbackSuppressionReason,
        playbackParameters,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs,
        sleepingForOffload);
  }

  /**
   * Copies playback info with a playback error.
   *
   * @param playbackError The error. See {@link #playbackError}.
   * @return Copied playback info with the playback error.
   */
  @CheckResult
  public PlaybackInfo copyWithPlaybackError(@Nullable ExoPlaybackException playbackError) {
    return new PlaybackInfo(
        timeline,
        periodId,
        requestedContentPositionUs,
        discontinuityStartPositionUs,
        playbackState,
        playbackError,
        isLoading,
        trackGroups,
        trackSelectorResult,
        staticMetadata,
        loadingMediaPeriodId,
        playWhenReady,
        playbackSuppressionReason,
        playbackParameters,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs,
        sleepingForOffload);
  }

  /**
   * Copies playback info with new loading state.
   *
   * @param isLoading New loading state. See {@link #isLoading}.
   * @return Copied playback info with new loading state.
   */
  @CheckResult
  public PlaybackInfo copyWithIsLoading(boolean isLoading) {
    return new PlaybackInfo(
        timeline,
        periodId,
        requestedContentPositionUs,
        discontinuityStartPositionUs,
        playbackState,
        playbackError,
        isLoading,
        trackGroups,
        trackSelectorResult,
        staticMetadata,
        loadingMediaPeriodId,
        playWhenReady,
        playbackSuppressionReason,
        playbackParameters,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs,
        sleepingForOffload);
  }

  /**
   * Copies playback info with new loading media period.
   *
   * @param loadingMediaPeriodId New loading media period id. See {@link #loadingMediaPeriodId}.
   * @return Copied playback info with new loading media period.
   */
  @CheckResult
  public PlaybackInfo copyWithLoadingMediaPeriodId(MediaPeriodId loadingMediaPeriodId) {
    return new PlaybackInfo(
        timeline,
        periodId,
        requestedContentPositionUs,
        discontinuityStartPositionUs,
        playbackState,
        playbackError,
        isLoading,
        trackGroups,
        trackSelectorResult,
        staticMetadata,
        loadingMediaPeriodId,
        playWhenReady,
        playbackSuppressionReason,
        playbackParameters,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs,
        sleepingForOffload);
  }

  /**
   * Copies playback info with new information about whether playback should proceed when ready.
   *
   * @param playWhenReady Whether playback should proceed when {@link #playbackState} == {@link
   *     Player#STATE_READY}.
   * @param playbackSuppressionReason Reason why playback is suppressed even though {@link
   *     #playWhenReady} is {@code true}.
   * @return Copied playback info with new information.
   */
  @CheckResult
  public PlaybackInfo copyWithPlayWhenReady(
      boolean playWhenReady, @PlaybackSuppressionReason int playbackSuppressionReason) {
    return new PlaybackInfo(
        timeline,
        periodId,
        requestedContentPositionUs,
        discontinuityStartPositionUs,
        playbackState,
        playbackError,
        isLoading,
        trackGroups,
        trackSelectorResult,
        staticMetadata,
        loadingMediaPeriodId,
        playWhenReady,
        playbackSuppressionReason,
        playbackParameters,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs,
        sleepingForOffload);
  }

  /**
   * Copies playback info with new playback parameters.
   *
   * @param playbackParameters New playback parameters. See {@link #playbackParameters}.
   * @return Copied playback info with new playback parameters.
   */
  @CheckResult
  public PlaybackInfo copyWithPlaybackParameters(PlaybackParameters playbackParameters) {
    return new PlaybackInfo(
        timeline,
        periodId,
        requestedContentPositionUs,
        discontinuityStartPositionUs,
        playbackState,
        playbackError,
        isLoading,
        trackGroups,
        trackSelectorResult,
        staticMetadata,
        loadingMediaPeriodId,
        playWhenReady,
        playbackSuppressionReason,
        playbackParameters,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs,
        sleepingForOffload);
  }

  /**
   * Copies playback info with new sleepingForOffload.
   *
   * @param sleepingForOffload New main player loop sleeping state. See {@link #sleepingForOffload}.
   * @return Copied playback info with new main player loop sleeping state.
   */
  @CheckResult
  public PlaybackInfo copyWithSleepingForOffload(boolean sleepingForOffload) {
    return new PlaybackInfo(
        timeline,
        periodId,
        requestedContentPositionUs,
        discontinuityStartPositionUs,
        playbackState,
        playbackError,
        isLoading,
        trackGroups,
        trackSelectorResult,
        staticMetadata,
        loadingMediaPeriodId,
        playWhenReady,
        playbackSuppressionReason,
        playbackParameters,
        bufferedPositionUs,
        totalBufferedDurationUs,
        positionUs,
        sleepingForOffload);
  }
}
