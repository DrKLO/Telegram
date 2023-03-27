/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.analytics;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.max;

import android.os.SystemClock;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.analytics.PlaybackStats.EventTimeAndException;
import com.google.android.exoplayer2.analytics.PlaybackStats.EventTimeAndFormat;
import com.google.android.exoplayer2.analytics.PlaybackStats.EventTimeAndPlaybackState;
import com.google.android.exoplayer2.analytics.PlaybackStats.PlaybackState;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AnalyticsListener} to gather {@link PlaybackStats} from the player.
 *
 * <p>For accurate measurements, the listener should be added to the player before loading media,
 * i.e., {@link Player#getPlaybackState()} should be {@link Player#STATE_IDLE}.
 *
 * <p>Playback stats are gathered separately for each playback session, i.e. each window in the
 * {@link Timeline} and each single ad.
 */
public final class PlaybackStatsListener
    implements AnalyticsListener, PlaybackSessionManager.Listener {

  /** A listener for {@link PlaybackStats} updates. */
  public interface Callback {

    /**
     * Called when a playback session ends and its {@link PlaybackStats} are ready.
     *
     * @param eventTime The {@link EventTime} at which the playback session started. Can be used to
     *     identify the playback session.
     * @param playbackStats The {@link PlaybackStats} for the ended playback session.
     */
    void onPlaybackStatsReady(EventTime eventTime, PlaybackStats playbackStats);
  }

  private final PlaybackSessionManager sessionManager;
  private final Map<String, PlaybackStatsTracker> playbackStatsTrackers;
  private final Map<String, EventTime> sessionStartEventTimes;
  @Nullable private final Callback callback;
  private final boolean keepHistory;
  private final Period period;

  private PlaybackStats finishedPlaybackStats;

  @Nullable private String discontinuityFromSession;
  private long discontinuityFromPositionMs;
  private @Player.DiscontinuityReason int discontinuityReason;
  private int droppedFrames;
  @Nullable private Exception nonFatalException;
  private long bandwidthTimeMs;
  private long bandwidthBytes;
  @Nullable private Format videoFormat;
  @Nullable private Format audioFormat;
  private VideoSize videoSize;

  /**
   * Creates listener for playback stats.
   *
   * @param keepHistory Whether the reported {@link PlaybackStats} should keep the full history of
   *     events.
   * @param callback An optional callback for finished {@link PlaybackStats}.
   */
  public PlaybackStatsListener(boolean keepHistory, @Nullable Callback callback) {
    this.callback = callback;
    this.keepHistory = keepHistory;
    sessionManager = new DefaultPlaybackSessionManager();
    playbackStatsTrackers = new HashMap<>();
    sessionStartEventTimes = new HashMap<>();
    finishedPlaybackStats = PlaybackStats.EMPTY;
    period = new Period();
    videoSize = VideoSize.UNKNOWN;
    sessionManager.setListener(this);
  }

  /**
   * Returns the combined {@link PlaybackStats} for all playback sessions this listener was and is
   * listening to.
   *
   * <p>Note that these {@link PlaybackStats} will not contain the full history of events.
   *
   * @return The combined {@link PlaybackStats} for all playback sessions.
   */
  public PlaybackStats getCombinedPlaybackStats() {
    PlaybackStats[] allPendingPlaybackStats = new PlaybackStats[playbackStatsTrackers.size() + 1];
    allPendingPlaybackStats[0] = finishedPlaybackStats;
    int index = 1;
    for (PlaybackStatsTracker tracker : playbackStatsTrackers.values()) {
      allPendingPlaybackStats[index++] = tracker.build(/* isFinal= */ false);
    }
    return PlaybackStats.merge(allPendingPlaybackStats);
  }

  /**
   * Returns the {@link PlaybackStats} for the currently playback session, or null if no session is
   * active.
   *
   * @return {@link PlaybackStats} for the current playback session.
   */
  @Nullable
  public PlaybackStats getPlaybackStats() {
    @Nullable String activeSessionId = sessionManager.getActiveSessionId();
    @Nullable
    PlaybackStatsTracker activeStatsTracker =
        activeSessionId == null ? null : playbackStatsTrackers.get(activeSessionId);
    return activeStatsTracker == null ? null : activeStatsTracker.build(/* isFinal= */ false);
  }

  // PlaybackSessionManager.Listener implementation.

  @Override
  public void onSessionCreated(EventTime eventTime, String sessionId) {
    PlaybackStatsTracker tracker = new PlaybackStatsTracker(keepHistory, eventTime);
    playbackStatsTrackers.put(sessionId, tracker);
    sessionStartEventTimes.put(sessionId, eventTime);
  }

  @Override
  public void onSessionActive(EventTime eventTime, String sessionId) {
    checkNotNull(playbackStatsTrackers.get(sessionId)).onForeground();
  }

  @Override
  public void onAdPlaybackStarted(
      EventTime eventTime, String contentSessionId, String adSessionId) {
    checkNotNull(playbackStatsTrackers.get(contentSessionId)).onInterruptedByAd();
  }

  @Override
  public void onSessionFinished(
      EventTime eventTime, String sessionId, boolean automaticTransitionToNextPlayback) {
    PlaybackStatsTracker tracker = checkNotNull(playbackStatsTrackers.remove(sessionId));
    EventTime startEventTime = checkNotNull(sessionStartEventTimes.remove(sessionId));
    long discontinuityFromPositionMs =
        sessionId.equals(discontinuityFromSession)
            ? this.discontinuityFromPositionMs
            : C.TIME_UNSET;
    tracker.onFinished(eventTime, automaticTransitionToNextPlayback, discontinuityFromPositionMs);
    PlaybackStats playbackStats = tracker.build(/* isFinal= */ true);
    finishedPlaybackStats = PlaybackStats.merge(finishedPlaybackStats, playbackStats);
    if (callback != null) {
      callback.onPlaybackStatsReady(startEventTime, playbackStats);
    }
  }

  // AnalyticsListener implementation.

  @Override
  public void onPositionDiscontinuity(
      EventTime eventTime,
      Player.PositionInfo oldPosition,
      Player.PositionInfo newPosition,
      @Player.DiscontinuityReason int reason) {
    if (discontinuityFromSession == null) {
      discontinuityFromSession = sessionManager.getActiveSessionId();
      discontinuityFromPositionMs = oldPosition.positionMs;
    }
    discontinuityReason = reason;
  }

  @Override
  public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
    this.droppedFrames = droppedFrames;
  }

  @Override
  public void onLoadError(
      EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {
    nonFatalException = error;
  }

  @Override
  public void onDrmSessionManagerError(EventTime eventTime, Exception error) {
    nonFatalException = error;
  }

  @Override
  public void onBandwidthEstimate(
      EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
    bandwidthTimeMs = totalLoadTimeMs;
    bandwidthBytes = totalBytesLoaded;
  }

  @Override
  public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
    if (mediaLoadData.trackType == C.TRACK_TYPE_VIDEO
        || mediaLoadData.trackType == C.TRACK_TYPE_DEFAULT) {
      videoFormat = mediaLoadData.trackFormat;
    } else if (mediaLoadData.trackType == C.TRACK_TYPE_AUDIO) {
      audioFormat = mediaLoadData.trackFormat;
    }
  }

  @Override
  public void onVideoSizeChanged(EventTime eventTime, VideoSize videoSize) {
    this.videoSize = videoSize;
  }

  @Override
  public void onEvents(Player player, Events events) {
    if (events.size() == 0) {
      return;
    }
    maybeAddSessions(events);
    for (String session : playbackStatsTrackers.keySet()) {
      Pair<EventTime, Boolean> eventTimeAndBelongsToPlayback = findBestEventTime(events, session);
      PlaybackStatsTracker tracker = playbackStatsTrackers.get(session);
      boolean hasDiscontinuityToPlayback = hasEvent(events, session, EVENT_POSITION_DISCONTINUITY);
      boolean hasDroppedFrames = hasEvent(events, session, EVENT_DROPPED_VIDEO_FRAMES);
      boolean hasAudioUnderrun = hasEvent(events, session, EVENT_AUDIO_UNDERRUN);
      boolean startedLoading = hasEvent(events, session, EVENT_LOAD_STARTED);
      boolean hasFatalError = hasEvent(events, session, EVENT_PLAYER_ERROR);
      boolean hasNonFatalException =
          hasEvent(events, session, EVENT_LOAD_ERROR)
              || hasEvent(events, session, EVENT_DRM_SESSION_MANAGER_ERROR);
      boolean hasBandwidthData = hasEvent(events, session, EVENT_BANDWIDTH_ESTIMATE);
      boolean hasFormatData = hasEvent(events, session, EVENT_DOWNSTREAM_FORMAT_CHANGED);
      boolean hasVideoSize = hasEvent(events, session, EVENT_VIDEO_SIZE_CHANGED);
      tracker.onEvents(
          player,
          /* eventTime= */ eventTimeAndBelongsToPlayback.first,
          /* belongsToPlayback= */ eventTimeAndBelongsToPlayback.second,
          session.equals(discontinuityFromSession) ? discontinuityFromPositionMs : C.TIME_UNSET,
          hasDiscontinuityToPlayback,
          hasDroppedFrames ? droppedFrames : 0,
          hasAudioUnderrun,
          startedLoading,
          hasFatalError ? player.getPlayerError() : null,
          hasNonFatalException ? nonFatalException : null,
          hasBandwidthData ? bandwidthTimeMs : 0,
          hasBandwidthData ? bandwidthBytes : 0,
          hasFormatData ? videoFormat : null,
          hasFormatData ? audioFormat : null,
          hasVideoSize ? videoSize : null);
    }
    videoFormat = null;
    audioFormat = null;
    discontinuityFromSession = null;
    if (events.contains(AnalyticsListener.EVENT_PLAYER_RELEASED)) {
      sessionManager.finishAllSessions(events.getEventTime(EVENT_PLAYER_RELEASED));
    }
  }

  private void maybeAddSessions(Events events) {
    for (int i = 0; i < events.size(); i++) {
      @EventFlags int event = events.get(i);
      EventTime eventTime = events.getEventTime(event);
      if (event == EVENT_TIMELINE_CHANGED) {
        sessionManager.updateSessionsWithTimelineChange(eventTime);
      } else if (event == EVENT_POSITION_DISCONTINUITY) {
        sessionManager.updateSessionsWithDiscontinuity(eventTime, discontinuityReason);
      } else {
        sessionManager.updateSessions(eventTime);
      }
    }
  }

  private Pair<EventTime, Boolean> findBestEventTime(Events events, String session) {
    @Nullable EventTime eventTime = null;
    boolean belongsToPlayback = false;
    for (int i = 0; i < events.size(); i++) {
      @EventFlags int event = events.get(i);
      EventTime newEventTime = events.getEventTime(event);
      boolean newBelongsToPlayback = sessionManager.belongsToSession(newEventTime, session);
      if (eventTime == null
          || (newBelongsToPlayback && !belongsToPlayback)
          || (newBelongsToPlayback == belongsToPlayback
              && newEventTime.realtimeMs > eventTime.realtimeMs)) {
        // Prefer event times for the current playback and prefer later timestamps.
        eventTime = newEventTime;
        belongsToPlayback = newBelongsToPlayback;
      }
    }
    checkNotNull(eventTime);
    if (!belongsToPlayback && eventTime.mediaPeriodId != null && eventTime.mediaPeriodId.isAd()) {
      // Replace ad event time with content event time unless it's for the ad playback itself.
      long contentPeriodPositionUs =
          eventTime
              .timeline
              .getPeriodByUid(eventTime.mediaPeriodId.periodUid, period)
              .getAdGroupTimeUs(eventTime.mediaPeriodId.adGroupIndex);
      if (contentPeriodPositionUs == C.TIME_END_OF_SOURCE) {
        contentPeriodPositionUs = period.durationUs;
      }
      long contentWindowPositionUs = contentPeriodPositionUs + period.getPositionInWindowUs();
      eventTime =
          new EventTime(
              eventTime.realtimeMs,
              eventTime.timeline,
              eventTime.windowIndex,
              new MediaPeriodId(
                  eventTime.mediaPeriodId.periodUid,
                  eventTime.mediaPeriodId.windowSequenceNumber,
                  eventTime.mediaPeriodId.adGroupIndex),
              /* eventPlaybackPositionMs= */ Util.usToMs(contentWindowPositionUs),
              eventTime.timeline,
              eventTime.currentWindowIndex,
              eventTime.currentMediaPeriodId,
              eventTime.currentPlaybackPositionMs,
              eventTime.totalBufferedDurationMs);
      belongsToPlayback = sessionManager.belongsToSession(eventTime, session);
    }
    return Pair.create(eventTime, belongsToPlayback);
  }

  private boolean hasEvent(Events events, String session, @EventFlags int event) {
    return events.contains(event)
        && sessionManager.belongsToSession(events.getEventTime(event), session);
  }

  /** Tracker for playback stats of a single playback. */
  private static final class PlaybackStatsTracker {

    // Final stats.
    private final boolean keepHistory;
    private final long[] playbackStateDurationsMs;
    private final List<EventTimeAndPlaybackState> playbackStateHistory;
    private final List<long[]> mediaTimeHistory;
    private final List<EventTimeAndFormat> videoFormatHistory;
    private final List<EventTimeAndFormat> audioFormatHistory;
    private final List<EventTimeAndException> fatalErrorHistory;
    private final List<EventTimeAndException> nonFatalErrorHistory;
    private final boolean isAd;

    private long firstReportedTimeMs;
    private boolean hasBeenReady;
    private boolean hasEnded;
    private boolean isJoinTimeInvalid;
    private int pauseCount;
    private int pauseBufferCount;
    private int seekCount;
    private int rebufferCount;
    private long maxRebufferTimeMs;
    private int initialVideoFormatHeight;
    private long initialVideoFormatBitrate;
    private long initialAudioFormatBitrate;
    private long videoFormatHeightTimeMs;
    private long videoFormatHeightTimeProduct;
    private long videoFormatBitrateTimeMs;
    private long videoFormatBitrateTimeProduct;
    private long audioFormatTimeMs;
    private long audioFormatBitrateTimeProduct;
    private long bandwidthTimeMs;
    private long bandwidthBytes;
    private long droppedFrames;
    private long audioUnderruns;
    private int fatalErrorCount;
    private int nonFatalErrorCount;

    // Current player state tracking.
    private @PlaybackState int currentPlaybackState;
    private long currentPlaybackStateStartTimeMs;
    private boolean isSeeking;
    private boolean isForeground;
    private boolean isInterruptedByAd;
    private boolean hasFatalError;
    private boolean startedLoading;
    private long lastRebufferStartTimeMs;
    @Nullable private Format currentVideoFormat;
    @Nullable private Format currentAudioFormat;
    private long lastVideoFormatStartTimeMs;
    private long lastAudioFormatStartTimeMs;
    private float currentPlaybackSpeed;

    /**
     * Creates a tracker for playback stats.
     *
     * @param keepHistory Whether to keep a full history of events.
     * @param startTime The {@link EventTime} at which the playback stats start.
     */
    public PlaybackStatsTracker(boolean keepHistory, EventTime startTime) {
      this.keepHistory = keepHistory;
      playbackStateDurationsMs = new long[PlaybackStats.PLAYBACK_STATE_COUNT];
      playbackStateHistory = keepHistory ? new ArrayList<>() : Collections.emptyList();
      mediaTimeHistory = keepHistory ? new ArrayList<>() : Collections.emptyList();
      videoFormatHistory = keepHistory ? new ArrayList<>() : Collections.emptyList();
      audioFormatHistory = keepHistory ? new ArrayList<>() : Collections.emptyList();
      fatalErrorHistory = keepHistory ? new ArrayList<>() : Collections.emptyList();
      nonFatalErrorHistory = keepHistory ? new ArrayList<>() : Collections.emptyList();
      currentPlaybackState = PlaybackStats.PLAYBACK_STATE_NOT_STARTED;
      currentPlaybackStateStartTimeMs = startTime.realtimeMs;
      firstReportedTimeMs = C.TIME_UNSET;
      maxRebufferTimeMs = C.TIME_UNSET;
      isAd = startTime.mediaPeriodId != null && startTime.mediaPeriodId.isAd();
      initialAudioFormatBitrate = C.LENGTH_UNSET;
      initialVideoFormatBitrate = C.LENGTH_UNSET;
      initialVideoFormatHeight = C.LENGTH_UNSET;
      currentPlaybackSpeed = 1f;
    }

    /** Notifies the tracker that the current playback became the active foreground playback. */
    public void onForeground() {
      isForeground = true;
    }

    /** Notifies the tracker that the current playback is interrupted by an ad. */
    public void onInterruptedByAd() {
      isInterruptedByAd = true;
      isSeeking = false;
    }

    /**
     * Notifies the tracker that the current playback has finished.
     *
     * @param eventTime The {@link EventTime}. Does not belong to this playback.
     * @param automaticTransition Whether the playback finished because of an automatic transition
     *     to the next playback item.
     * @param discontinuityFromPositionMs The position before the discontinuity from this playback,
     *     {@link C#TIME_UNSET} if no discontinuity started from this playback.
     */
    public void onFinished(
        EventTime eventTime, boolean automaticTransition, long discontinuityFromPositionMs) {
      // Simulate state change to ENDED to record natural ending of playback.
      @PlaybackState
      int finalPlaybackState =
          currentPlaybackState == PlaybackStats.PLAYBACK_STATE_ENDED || automaticTransition
              ? PlaybackStats.PLAYBACK_STATE_ENDED
              : PlaybackStats.PLAYBACK_STATE_ABANDONED;
      maybeUpdateMediaTimeHistory(eventTime.realtimeMs, discontinuityFromPositionMs);
      maybeRecordVideoFormatTime(eventTime.realtimeMs);
      maybeRecordAudioFormatTime(eventTime.realtimeMs);
      updatePlaybackState(finalPlaybackState, eventTime);
    }

    /**
     * Notifies the tracker of new events.
     *
     * @param player The {@link Player}.
     * @param eventTime The {@link EventTime} of the events.
     * @param belongsToPlayback Whether the {@code eventTime} belongs to this playback.
     * @param discontinuityFromPositionMs The position before the discontinuity from this playback,
     *     or {@link C#TIME_UNSET} if no discontinuity started from this playback.
     * @param hasDiscontinuity Whether a discontinuity to this playback occurred.
     * @param droppedFrameCount The number of newly dropped frames for this playback.
     * @param hasAudioUnderun Whether a new audio underrun occurred for this playback.
     * @param startedLoading Whether this playback started loading.
     * @param fatalError A fatal error for this playback, or null.
     * @param nonFatalException A non-fatal exception for this playback, or null.
     * @param bandwidthTimeMs The time in milliseconds spent loading for this playback.
     * @param bandwidthBytes The number of bytes loaded for this playback.
     * @param videoFormat A reported downstream video format for this playback, or null.
     * @param audioFormat A reported downstream audio format for this playback, or null.
     * @param videoSize The reported video size for this playback, or null.
     */
    public void onEvents(
        Player player,
        EventTime eventTime,
        boolean belongsToPlayback,
        long discontinuityFromPositionMs,
        boolean hasDiscontinuity,
        int droppedFrameCount,
        boolean hasAudioUnderun,
        boolean startedLoading,
        @Nullable PlaybackException fatalError,
        @Nullable Exception nonFatalException,
        long bandwidthTimeMs,
        long bandwidthBytes,
        @Nullable Format videoFormat,
        @Nullable Format audioFormat,
        @Nullable VideoSize videoSize) {
      if (discontinuityFromPositionMs != C.TIME_UNSET) {
        maybeUpdateMediaTimeHistory(eventTime.realtimeMs, discontinuityFromPositionMs);
        isSeeking = true;
      }
      if (player.getPlaybackState() != Player.STATE_BUFFERING) {
        isSeeking = false;
      }
      int playerPlaybackState = player.getPlaybackState();
      if (playerPlaybackState == Player.STATE_IDLE
          || playerPlaybackState == Player.STATE_ENDED
          || hasDiscontinuity) {
        isInterruptedByAd = false;
      }
      if (fatalError != null) {
        hasFatalError = true;
        fatalErrorCount++;
        if (keepHistory) {
          fatalErrorHistory.add(new EventTimeAndException(eventTime, fatalError));
        }
      } else if (player.getPlayerError() == null) {
        hasFatalError = false;
      }
      if (isForeground && !isInterruptedByAd) {
        Tracks currentTracks = player.getCurrentTracks();
        if (!currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) {
          maybeUpdateVideoFormat(eventTime, /* newFormat= */ null);
        }
        if (!currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO)) {
          maybeUpdateAudioFormat(eventTime, /* newFormat= */ null);
        }
      }
      if (videoFormat != null) {
        maybeUpdateVideoFormat(eventTime, videoFormat);
      }
      if (audioFormat != null) {
        maybeUpdateAudioFormat(eventTime, audioFormat);
      }
      if (currentVideoFormat != null
          && currentVideoFormat.height == Format.NO_VALUE
          && videoSize != null) {
        Format formatWithHeightAndWidth =
            currentVideoFormat
                .buildUpon()
                .setWidth(videoSize.width)
                .setHeight(videoSize.height)
                .build();
        maybeUpdateVideoFormat(eventTime, formatWithHeightAndWidth);
      }
      if (startedLoading) {
        this.startedLoading = true;
      }
      if (hasAudioUnderun) {
        audioUnderruns++;
      }
      this.droppedFrames += droppedFrameCount;
      this.bandwidthTimeMs += bandwidthTimeMs;
      this.bandwidthBytes += bandwidthBytes;
      if (nonFatalException != null) {
        nonFatalErrorCount++;
        if (keepHistory) {
          nonFatalErrorHistory.add(new EventTimeAndException(eventTime, nonFatalException));
        }
      }

      @PlaybackState int newPlaybackState = resolveNewPlaybackState(player);
      float newPlaybackSpeed = player.getPlaybackParameters().speed;
      if (currentPlaybackState != newPlaybackState || currentPlaybackSpeed != newPlaybackSpeed) {
        maybeUpdateMediaTimeHistory(
            eventTime.realtimeMs,
            belongsToPlayback ? eventTime.eventPlaybackPositionMs : C.TIME_UNSET);
        maybeRecordVideoFormatTime(eventTime.realtimeMs);
        maybeRecordAudioFormatTime(eventTime.realtimeMs);
      }
      currentPlaybackSpeed = newPlaybackSpeed;
      if (currentPlaybackState != newPlaybackState) {
        updatePlaybackState(newPlaybackState, eventTime);
      }
    }

    /**
     * Builds the playback stats.
     *
     * @param isFinal Whether this is the final build and no further events are expected.
     */
    public PlaybackStats build(boolean isFinal) {
      long[] playbackStateDurationsMs = this.playbackStateDurationsMs;
      List<long[]> mediaTimeHistory = this.mediaTimeHistory;
      if (!isFinal) {
        long buildTimeMs = SystemClock.elapsedRealtime();
        playbackStateDurationsMs =
            Arrays.copyOf(this.playbackStateDurationsMs, PlaybackStats.PLAYBACK_STATE_COUNT);
        long lastStateDurationMs = max(0, buildTimeMs - currentPlaybackStateStartTimeMs);
        playbackStateDurationsMs[currentPlaybackState] += lastStateDurationMs;
        maybeUpdateMaxRebufferTimeMs(buildTimeMs);
        maybeRecordVideoFormatTime(buildTimeMs);
        maybeRecordAudioFormatTime(buildTimeMs);
        mediaTimeHistory = new ArrayList<>(this.mediaTimeHistory);
        if (keepHistory && currentPlaybackState == PlaybackStats.PLAYBACK_STATE_PLAYING) {
          mediaTimeHistory.add(guessMediaTimeBasedOnElapsedRealtime(buildTimeMs));
        }
      }
      boolean isJoinTimeInvalid = this.isJoinTimeInvalid || !hasBeenReady;
      long validJoinTimeMs =
          isJoinTimeInvalid
              ? C.TIME_UNSET
              : playbackStateDurationsMs[PlaybackStats.PLAYBACK_STATE_JOINING_FOREGROUND];
      boolean hasBackgroundJoin =
          playbackStateDurationsMs[PlaybackStats.PLAYBACK_STATE_JOINING_BACKGROUND] > 0;
      List<EventTimeAndFormat> videoHistory =
          isFinal ? videoFormatHistory : new ArrayList<>(videoFormatHistory);
      List<EventTimeAndFormat> audioHistory =
          isFinal ? audioFormatHistory : new ArrayList<>(audioFormatHistory);
      return new PlaybackStats(
          /* playbackCount= */ 1,
          playbackStateDurationsMs,
          isFinal ? playbackStateHistory : new ArrayList<>(playbackStateHistory),
          mediaTimeHistory,
          firstReportedTimeMs,
          /* foregroundPlaybackCount= */ isForeground ? 1 : 0,
          /* abandonedBeforeReadyCount= */ hasBeenReady ? 0 : 1,
          /* endedCount= */ hasEnded ? 1 : 0,
          /* backgroundJoiningCount= */ hasBackgroundJoin ? 1 : 0,
          validJoinTimeMs,
          /* validJoinTimeCount= */ isJoinTimeInvalid ? 0 : 1,
          pauseCount,
          pauseBufferCount,
          seekCount,
          rebufferCount,
          maxRebufferTimeMs,
          /* adPlaybackCount= */ isAd ? 1 : 0,
          videoHistory,
          audioHistory,
          videoFormatHeightTimeMs,
          videoFormatHeightTimeProduct,
          videoFormatBitrateTimeMs,
          videoFormatBitrateTimeProduct,
          audioFormatTimeMs,
          audioFormatBitrateTimeProduct,
          /* initialVideoFormatHeightCount= */ initialVideoFormatHeight == C.LENGTH_UNSET ? 0 : 1,
          /* initialVideoFormatBitrateCount= */ initialVideoFormatBitrate == C.LENGTH_UNSET ? 0 : 1,
          initialVideoFormatHeight,
          initialVideoFormatBitrate,
          /* initialAudioFormatBitrateCount= */ initialAudioFormatBitrate == C.LENGTH_UNSET ? 0 : 1,
          initialAudioFormatBitrate,
          bandwidthTimeMs,
          bandwidthBytes,
          droppedFrames,
          audioUnderruns,
          /* fatalErrorPlaybackCount= */ fatalErrorCount > 0 ? 1 : 0,
          fatalErrorCount,
          nonFatalErrorCount,
          fatalErrorHistory,
          nonFatalErrorHistory);
    }

    private void updatePlaybackState(@PlaybackState int newPlaybackState, EventTime eventTime) {
      Assertions.checkArgument(eventTime.realtimeMs >= currentPlaybackStateStartTimeMs);
      long stateDurationMs = eventTime.realtimeMs - currentPlaybackStateStartTimeMs;
      playbackStateDurationsMs[currentPlaybackState] += stateDurationMs;
      if (firstReportedTimeMs == C.TIME_UNSET) {
        firstReportedTimeMs = eventTime.realtimeMs;
      }
      isJoinTimeInvalid |= isInvalidJoinTransition(currentPlaybackState, newPlaybackState);
      hasBeenReady |= isReadyState(newPlaybackState);
      hasEnded |= newPlaybackState == PlaybackStats.PLAYBACK_STATE_ENDED;
      if (!isPausedState(currentPlaybackState) && isPausedState(newPlaybackState)) {
        pauseCount++;
      }
      if (newPlaybackState == PlaybackStats.PLAYBACK_STATE_SEEKING) {
        seekCount++;
      }
      if (!isRebufferingState(currentPlaybackState) && isRebufferingState(newPlaybackState)) {
        rebufferCount++;
        lastRebufferStartTimeMs = eventTime.realtimeMs;
      }
      if (isRebufferingState(currentPlaybackState)
          && currentPlaybackState != PlaybackStats.PLAYBACK_STATE_PAUSED_BUFFERING
          && newPlaybackState == PlaybackStats.PLAYBACK_STATE_PAUSED_BUFFERING) {
        pauseBufferCount++;
      }
      maybeUpdateMaxRebufferTimeMs(eventTime.realtimeMs);

      currentPlaybackState = newPlaybackState;
      currentPlaybackStateStartTimeMs = eventTime.realtimeMs;
      if (keepHistory) {
        playbackStateHistory.add(new EventTimeAndPlaybackState(eventTime, currentPlaybackState));
      }
    }

    private @PlaybackState int resolveNewPlaybackState(Player player) {
      @Player.State int playerPlaybackState = player.getPlaybackState();
      if (isSeeking && isForeground) {
        // Seeking takes precedence over errors such that we report a seek while in error state.
        return PlaybackStats.PLAYBACK_STATE_SEEKING;
      } else if (hasFatalError) {
        return PlaybackStats.PLAYBACK_STATE_FAILED;
      } else if (!isForeground) {
        // Before the playback becomes foreground, only report background joining and not started.
        return startedLoading
            ? PlaybackStats.PLAYBACK_STATE_JOINING_BACKGROUND
            : PlaybackStats.PLAYBACK_STATE_NOT_STARTED;
      } else if (isInterruptedByAd) {
        return PlaybackStats.PLAYBACK_STATE_INTERRUPTED_BY_AD;
      } else if (playerPlaybackState == Player.STATE_ENDED) {
        return PlaybackStats.PLAYBACK_STATE_ENDED;
      } else if (playerPlaybackState == Player.STATE_BUFFERING) {
        if (currentPlaybackState == PlaybackStats.PLAYBACK_STATE_NOT_STARTED
            || currentPlaybackState == PlaybackStats.PLAYBACK_STATE_JOINING_BACKGROUND
            || currentPlaybackState == PlaybackStats.PLAYBACK_STATE_JOINING_FOREGROUND
            || currentPlaybackState == PlaybackStats.PLAYBACK_STATE_INTERRUPTED_BY_AD) {
          return PlaybackStats.PLAYBACK_STATE_JOINING_FOREGROUND;
        }
        if (!player.getPlayWhenReady()) {
          return PlaybackStats.PLAYBACK_STATE_PAUSED_BUFFERING;
        }
        return player.getPlaybackSuppressionReason() != Player.PLAYBACK_SUPPRESSION_REASON_NONE
            ? PlaybackStats.PLAYBACK_STATE_SUPPRESSED_BUFFERING
            : PlaybackStats.PLAYBACK_STATE_BUFFERING;
      } else if (playerPlaybackState == Player.STATE_READY) {
        if (!player.getPlayWhenReady()) {
          return PlaybackStats.PLAYBACK_STATE_PAUSED;
        }
        return player.getPlaybackSuppressionReason() != Player.PLAYBACK_SUPPRESSION_REASON_NONE
            ? PlaybackStats.PLAYBACK_STATE_SUPPRESSED
            : PlaybackStats.PLAYBACK_STATE_PLAYING;
      } else if (playerPlaybackState == Player.STATE_IDLE
          && currentPlaybackState != PlaybackStats.PLAYBACK_STATE_NOT_STARTED) {
        // This case only applies for calls to player.stop(). All other IDLE cases are handled by
        // !isForeground, hasFatalError or isSuspended. NOT_STARTED is deliberately ignored.
        return PlaybackStats.PLAYBACK_STATE_STOPPED;
      }
      return currentPlaybackState;
    }

    private void maybeUpdateMaxRebufferTimeMs(long nowMs) {
      if (isRebufferingState(currentPlaybackState)) {
        long rebufferDurationMs = nowMs - lastRebufferStartTimeMs;
        if (maxRebufferTimeMs == C.TIME_UNSET || rebufferDurationMs > maxRebufferTimeMs) {
          maxRebufferTimeMs = rebufferDurationMs;
        }
      }
    }

    private void maybeUpdateMediaTimeHistory(long realtimeMs, long mediaTimeMs) {
      if (!keepHistory) {
        return;
      }
      if (currentPlaybackState != PlaybackStats.PLAYBACK_STATE_PLAYING) {
        if (mediaTimeMs == C.TIME_UNSET) {
          return;
        }
        if (!mediaTimeHistory.isEmpty()) {
          long previousMediaTimeMs = mediaTimeHistory.get(mediaTimeHistory.size() - 1)[1];
          if (previousMediaTimeMs != mediaTimeMs) {
            mediaTimeHistory.add(new long[] {realtimeMs, previousMediaTimeMs});
          }
        }
      }

      if (mediaTimeMs != C.TIME_UNSET) {
        mediaTimeHistory.add(new long[] {realtimeMs, mediaTimeMs});
      } else if (!mediaTimeHistory.isEmpty()) {
        mediaTimeHistory.add(guessMediaTimeBasedOnElapsedRealtime(realtimeMs));
      }
    }

    private long[] guessMediaTimeBasedOnElapsedRealtime(long realtimeMs) {
      long[] previousKnownMediaTimeHistory = mediaTimeHistory.get(mediaTimeHistory.size() - 1);
      long previousRealtimeMs = previousKnownMediaTimeHistory[0];
      long previousMediaTimeMs = previousKnownMediaTimeHistory[1];
      long elapsedMediaTimeEstimateMs =
          (long) ((realtimeMs - previousRealtimeMs) * currentPlaybackSpeed);
      long mediaTimeEstimateMs = previousMediaTimeMs + elapsedMediaTimeEstimateMs;
      return new long[] {realtimeMs, mediaTimeEstimateMs};
    }

    private void maybeUpdateVideoFormat(EventTime eventTime, @Nullable Format newFormat) {
      if (Util.areEqual(currentVideoFormat, newFormat)) {
        return;
      }
      maybeRecordVideoFormatTime(eventTime.realtimeMs);
      if (newFormat != null) {
        if (initialVideoFormatHeight == C.LENGTH_UNSET && newFormat.height != Format.NO_VALUE) {
          initialVideoFormatHeight = newFormat.height;
        }
        if (initialVideoFormatBitrate == C.LENGTH_UNSET && newFormat.bitrate != Format.NO_VALUE) {
          initialVideoFormatBitrate = newFormat.bitrate;
        }
      }
      currentVideoFormat = newFormat;
      if (keepHistory) {
        videoFormatHistory.add(new EventTimeAndFormat(eventTime, currentVideoFormat));
      }
    }

    private void maybeUpdateAudioFormat(EventTime eventTime, @Nullable Format newFormat) {
      if (Util.areEqual(currentAudioFormat, newFormat)) {
        return;
      }
      maybeRecordAudioFormatTime(eventTime.realtimeMs);
      if (newFormat != null
          && initialAudioFormatBitrate == C.LENGTH_UNSET
          && newFormat.bitrate != Format.NO_VALUE) {
        initialAudioFormatBitrate = newFormat.bitrate;
      }
      currentAudioFormat = newFormat;
      if (keepHistory) {
        audioFormatHistory.add(new EventTimeAndFormat(eventTime, currentAudioFormat));
      }
    }

    private void maybeRecordVideoFormatTime(long nowMs) {
      if (currentPlaybackState == PlaybackStats.PLAYBACK_STATE_PLAYING
          && currentVideoFormat != null) {
        long mediaDurationMs = (long) ((nowMs - lastVideoFormatStartTimeMs) * currentPlaybackSpeed);
        if (currentVideoFormat.height != Format.NO_VALUE) {
          videoFormatHeightTimeMs += mediaDurationMs;
          videoFormatHeightTimeProduct += mediaDurationMs * currentVideoFormat.height;
        }
        if (currentVideoFormat.bitrate != Format.NO_VALUE) {
          videoFormatBitrateTimeMs += mediaDurationMs;
          videoFormatBitrateTimeProduct += mediaDurationMs * currentVideoFormat.bitrate;
        }
      }
      lastVideoFormatStartTimeMs = nowMs;
    }

    private void maybeRecordAudioFormatTime(long nowMs) {
      if (currentPlaybackState == PlaybackStats.PLAYBACK_STATE_PLAYING
          && currentAudioFormat != null
          && currentAudioFormat.bitrate != Format.NO_VALUE) {
        long mediaDurationMs = (long) ((nowMs - lastAudioFormatStartTimeMs) * currentPlaybackSpeed);
        audioFormatTimeMs += mediaDurationMs;
        audioFormatBitrateTimeProduct += mediaDurationMs * currentAudioFormat.bitrate;
      }
      lastAudioFormatStartTimeMs = nowMs;
    }

    private static boolean isReadyState(@PlaybackState int state) {
      return state == PlaybackStats.PLAYBACK_STATE_PLAYING
          || state == PlaybackStats.PLAYBACK_STATE_PAUSED
          || state == PlaybackStats.PLAYBACK_STATE_SUPPRESSED;
    }

    private static boolean isPausedState(@PlaybackState int state) {
      return state == PlaybackStats.PLAYBACK_STATE_PAUSED
          || state == PlaybackStats.PLAYBACK_STATE_PAUSED_BUFFERING;
    }

    private static boolean isRebufferingState(@PlaybackState int state) {
      return state == PlaybackStats.PLAYBACK_STATE_BUFFERING
          || state == PlaybackStats.PLAYBACK_STATE_PAUSED_BUFFERING
          || state == PlaybackStats.PLAYBACK_STATE_SUPPRESSED_BUFFERING;
    }

    private static boolean isInvalidJoinTransition(
        @PlaybackState int oldState, @PlaybackState int newState) {
      if (oldState != PlaybackStats.PLAYBACK_STATE_JOINING_BACKGROUND
          && oldState != PlaybackStats.PLAYBACK_STATE_JOINING_FOREGROUND
          && oldState != PlaybackStats.PLAYBACK_STATE_INTERRUPTED_BY_AD) {
        return false;
      }
      return newState != PlaybackStats.PLAYBACK_STATE_JOINING_BACKGROUND
          && newState != PlaybackStats.PLAYBACK_STATE_JOINING_FOREGROUND
          && newState != PlaybackStats.PLAYBACK_STATE_INTERRUPTED_BY_AD
          && newState != PlaybackStats.PLAYBACK_STATE_PLAYING
          && newState != PlaybackStats.PLAYBACK_STATE_PAUSED
          && newState != PlaybackStats.PLAYBACK_STATE_SUPPRESSED
          && newState != PlaybackStats.PLAYBACK_STATE_ENDED;
    }
  }
}
