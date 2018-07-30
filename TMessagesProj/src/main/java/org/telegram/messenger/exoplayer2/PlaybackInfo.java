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

import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.source.MediaSource.MediaPeriodId;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelectorResult;

/**
 * Information about an ongoing playback.
 */
/* package */ final class PlaybackInfo {

  public final Timeline timeline;
  public final @Nullable Object manifest;
  public final MediaPeriodId periodId;
  public final long startPositionUs;
  public final long contentPositionUs;
  public final int playbackState;
  public final boolean isLoading;
  public final TrackGroupArray trackGroups;
  public final TrackSelectorResult trackSelectorResult;

  public volatile long positionUs;
  public volatile long bufferedPositionUs;

  public PlaybackInfo(
      Timeline timeline,
      long startPositionUs,
      TrackGroupArray trackGroups,
      TrackSelectorResult trackSelectorResult) {
    this(
        timeline,
        /* manifest= */ null,
        new MediaPeriodId(/* periodIndex= */ 0),
        startPositionUs,
        /* contentPositionUs =*/ C.TIME_UNSET,
        Player.STATE_IDLE,
        /* isLoading= */ false,
        trackGroups,
        trackSelectorResult);
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
      TrackSelectorResult trackSelectorResult) {
    this.timeline = timeline;
    this.manifest = manifest;
    this.periodId = periodId;
    this.startPositionUs = startPositionUs;
    this.contentPositionUs = contentPositionUs;
    this.positionUs = startPositionUs;
    this.bufferedPositionUs = startPositionUs;
    this.playbackState = playbackState;
    this.isLoading = isLoading;
    this.trackGroups = trackGroups;
    this.trackSelectorResult = trackSelectorResult;
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
        trackSelectorResult);
  }

  public PlaybackInfo copyWithPeriodIndex(int periodIndex) {
    PlaybackInfo playbackInfo =
        new PlaybackInfo(
            timeline,
            manifest,
            periodId.copyWithPeriodIndex(periodIndex),
            startPositionUs,
            contentPositionUs,
            playbackState,
            isLoading,
            trackGroups,
            trackSelectorResult);
    copyMutablePositions(this, playbackInfo);
    return playbackInfo;
  }

  public PlaybackInfo copyWithTimeline(Timeline timeline, Object manifest) {
    PlaybackInfo playbackInfo =
        new PlaybackInfo(
            timeline,
            manifest,
            periodId,
            startPositionUs,
            contentPositionUs,
            playbackState,
            isLoading,
            trackGroups,
            trackSelectorResult);
    copyMutablePositions(this, playbackInfo);
    return playbackInfo;
  }

  public PlaybackInfo copyWithPlaybackState(int playbackState) {
    PlaybackInfo playbackInfo =
        new PlaybackInfo(
            timeline,
            manifest,
            periodId,
            startPositionUs,
            contentPositionUs,
            playbackState,
            isLoading,
            trackGroups,
            trackSelectorResult);
    copyMutablePositions(this, playbackInfo);
    return playbackInfo;
  }

  public PlaybackInfo copyWithIsLoading(boolean isLoading) {
    PlaybackInfo playbackInfo =
        new PlaybackInfo(
            timeline,
            manifest,
            periodId,
            startPositionUs,
            contentPositionUs,
            playbackState,
            isLoading,
            trackGroups,
            trackSelectorResult);
    copyMutablePositions(this, playbackInfo);
    return playbackInfo;
  }

  public PlaybackInfo copyWithTrackInfo(
      TrackGroupArray trackGroups, TrackSelectorResult trackSelectorResult) {
    PlaybackInfo playbackInfo =
        new PlaybackInfo(
            timeline,
            manifest,
            periodId,
            startPositionUs,
            contentPositionUs,
            playbackState,
            isLoading,
            trackGroups,
            trackSelectorResult);
    copyMutablePositions(this, playbackInfo);
    return playbackInfo;
  }

  private static void copyMutablePositions(PlaybackInfo from, PlaybackInfo to) {
    to.positionUs = from.positionUs;
    to.bufferedPositionUs = from.bufferedPositionUs;
  }

}
