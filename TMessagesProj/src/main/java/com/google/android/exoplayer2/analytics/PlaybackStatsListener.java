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

import android.os.SystemClock;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.analytics.PlaybackStats.PlaybackState;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaSourceEventListener.MediaLoadData;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.compatqual.NullableType;

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
  @Nullable private String activeContentPlayback;
  @Nullable private String activeAdPlayback;
  private boolean playWhenReady;
  @Player.State private int playbackState;
  private boolean isSuppressed;
  private float playbackSpeed;

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
    playWhenReady = false;
    playbackState = Player.STATE_IDLE;
    playbackSpeed = 1f;
    period = new Period();
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
    PlaybackStatsTracker activeStatsTracker =
        activeAdPlayback != null
            ? playbackStatsTrackers.get(activeAdPlayback)
            : activeContentPlayback != null
                ? playbackStatsTrackers.get(activeContentPlayback)
                : null;
    return activeStatsTracker == null ? null : activeStatsTracker.build(/* isFinal= */ false);
  }

  /**
   * Finishes all pending playback sessions. Should be called when the listener is removed from the
   * player or when the player is released.
   */
  public void finishAllSessions() {
    // TODO: Add AnalyticsListener.onAttachedToPlayer and onDetachedFromPlayer to auto-release with
    // an actual EventTime. Should also simplify other cases where the listener needs to be released
    // separately from the player.
    EventTime dummyEventTime =
        new EventTime(
            SystemClock.elapsedRealtime(),
            Timeline.EMPTY,
            /* windowIndex= */ 0,
            /* mediaPeriodId= */ null,
            /* eventPlaybackPositionMs= */ 0,
            /* currentPlaybackPositionMs= */ 0,
            /* totalBufferedDurationMs= */ 0);
    sessionManager.finishAllSessions(dummyEventTime);
  }

  // PlaybackSessionManager.Listener implementation.

  @Override
  public void onSessionCreated(EventTime eventTime, String session) {
    PlaybackStatsTracker tracker = new PlaybackStatsTracker(keepHistory, eventTime);
    tracker.onPlayerStateChanged(
        eventTime, playWhenReady, playbackState, /* belongsToPlayback= */ true);
    tracker.onIsSuppressedChanged(eventTime, isSuppressed, /* belongsToPlayback= */ true);
    tracker.onPlaybackSpeedChanged(eventTime, playbackSpeed);
    playbackStatsTrackers.put(session, tracker);
    sessionStartEventTimes.put(session, eventTime);
  }

  @Override
  public void onSessionActive(EventTime eventTime, String session) {
    Assertions.checkNotNull(playbackStatsTrackers.get(session)).onForeground(eventTime);
    if (eventTime.mediaPeriodId != null && eventTime.mediaPeriodId.isAd()) {
      activeAdPlayback = session;
    } else {
      activeContentPlayback = session;
    }
  }

  @Override
  public void onAdPlaybackStarted(EventTime eventTime, String contentSession, String adSession) {
    Assertions.checkState(Assertions.checkNotNull(eventTime.mediaPeriodId).isAd());
    long contentPeriodPositionUs =
        eventTime
            .timeline
            .getPeriodByUid(eventTime.mediaPeriodId.periodUid, period)
            .getAdGroupTimeUs(eventTime.mediaPeriodId.adGroupIndex);
    long contentWindowPositionUs =
        contentPeriodPositionUs == C.TIME_END_OF_SOURCE
            ? C.TIME_END_OF_SOURCE
            : contentPeriodPositionUs + period.getPositionInWindowUs();
    EventTime contentEventTime =
        new EventTime(
            eventTime.realtimeMs,
            eventTime.timeline,
            eventTime.windowIndex,
            new MediaPeriodId(
                eventTime.mediaPeriodId.periodUid,
                eventTime.mediaPeriodId.windowSequenceNumber,
                eventTime.mediaPeriodId.adGroupIndex),
            /* eventPlaybackPositionMs= */ C.usToMs(contentWindowPositionUs),
            eventTime.currentPlaybackPositionMs,
            eventTime.totalBufferedDurationMs);
    Assertions.checkNotNull(playbackStatsTrackers.get(contentSession))
        .onInterruptedByAd(contentEventTime);
  }

  @Override
  public void onSessionFinished(EventTime eventTime, String session, boolean automaticTransition) {
    if (session.equals(activeAdPlayback)) {
      activeAdPlayback = null;
    } else if (session.equals(activeContentPlayback)) {
      activeContentPlayback = null;
    }
    PlaybackStatsTracker tracker = Assertions.checkNotNull(playbackStatsTrackers.remove(session));
    EventTime startEventTime = Assertions.checkNotNull(sessionStartEventTimes.remove(session));
    if (automaticTransition) {
      // Simulate ENDED state to record natural ending of playback.
      tracker.onPlayerStateChanged(
          eventTime, /* playWhenReady= */ true, Player.STATE_ENDED, /* belongsToPlayback= */ false);
    }
    tracker.onFinished(eventTime);
    PlaybackStats playbackStats = tracker.build(/* isFinal= */ true);
    finishedPlaybackStats = PlaybackStats.merge(finishedPlaybackStats, playbackStats);
    if (callback != null) {
      callback.onPlaybackStatsReady(startEventTime, playbackStats);
    }
  }

  // AnalyticsListener implementation.

  @Override
  public void onPlayerStateChanged(
      EventTime eventTime, boolean playWhenReady, @Player.State int playbackState) {
    this.playWhenReady = playWhenReady;
    this.playbackState = playbackState;
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      boolean belongsToPlayback = sessionManager.belongsToSession(eventTime, session);
      playbackStatsTrackers
          .get(session)
          .onPlayerStateChanged(eventTime, playWhenReady, playbackState, belongsToPlayback);
    }
  }

  @Override
  public void onPlaybackSuppressionReasonChanged(
      EventTime eventTime, int playbackSuppressionReason) {
    isSuppressed = playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE;
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      boolean belongsToPlayback = sessionManager.belongsToSession(eventTime, session);
      playbackStatsTrackers
          .get(session)
          .onIsSuppressedChanged(eventTime, isSuppressed, belongsToPlayback);
    }
  }

  @Override
  public void onTimelineChanged(EventTime eventTime, int reason) {
    sessionManager.handleTimelineUpdate(eventTime);
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onPositionDiscontinuity(eventTime);
      }
    }
  }

  @Override
  public void onPositionDiscontinuity(EventTime eventTime, int reason) {
    sessionManager.handlePositionDiscontinuity(eventTime, reason);
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onPositionDiscontinuity(eventTime);
      }
    }
  }

  @Override
  public void onSeekStarted(EventTime eventTime) {
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onSeekStarted(eventTime);
      }
    }
  }

  @Override
  public void onSeekProcessed(EventTime eventTime) {
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onSeekProcessed(eventTime);
      }
    }
  }

  @Override
  public void onPlayerError(EventTime eventTime, ExoPlaybackException error) {
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onFatalError(eventTime, error);
      }
    }
  }

  @Override
  public void onPlaybackParametersChanged(
      EventTime eventTime, PlaybackParameters playbackParameters) {
    playbackSpeed = playbackParameters.speed;
    maybeAddSession(eventTime);
    for (PlaybackStatsTracker tracker : playbackStatsTrackers.values()) {
      tracker.onPlaybackSpeedChanged(eventTime, playbackSpeed);
    }
  }

  @Override
  public void onTracksChanged(
      EventTime eventTime, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onTracksChanged(eventTime, trackSelections);
      }
    }
  }

  @Override
  public void onLoadStarted(
      EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onLoadStarted(eventTime);
      }
    }
  }

  @Override
  public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onDownstreamFormatChanged(eventTime, mediaLoadData);
      }
    }
  }

  @Override
  public void onVideoSizeChanged(
      EventTime eventTime,
      int width,
      int height,
      int unappliedRotationDegrees,
      float pixelWidthHeightRatio) {
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onVideoSizeChanged(eventTime, width, height);
      }
    }
  }

  @Override
  public void onBandwidthEstimate(
      EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onBandwidthData(totalLoadTimeMs, totalBytesLoaded);
      }
    }
  }

  @Override
  public void onAudioUnderrun(
      EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onAudioUnderrun();
      }
    }
  }

  @Override
  public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onDroppedVideoFrames(droppedFrames);
      }
    }
  }

  @Override
  public void onLoadError(
      EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onNonFatalError(eventTime, error);
      }
    }
  }

  @Override
  public void onDrmSessionManagerError(EventTime eventTime, Exception error) {
    maybeAddSession(eventTime);
    for (String session : playbackStatsTrackers.keySet()) {
      if (sessionManager.belongsToSession(eventTime, session)) {
        playbackStatsTrackers.get(session).onNonFatalError(eventTime, error);
      }
    }
  }

  private void maybeAddSession(EventTime eventTime) {
    boolean isCompletelyIdle = eventTime.timeline.isEmpty() && playbackState == Player.STATE_IDLE;
    if (!isCompletelyIdle) {
      sessionManager.updateSessions(eventTime);
    }
  }

  /** Tracker for playback stats of a single playback. */
  private static final class PlaybackStatsTracker {

    // Final stats.
    private final boolean keepHistory;
    private final long[] playbackStateDurationsMs;
    private final List<Pair<EventTime, @PlaybackState Integer>> playbackStateHistory;
    private final List<long[]> mediaTimeHistory;
    private final List<Pair<EventTime, @NullableType Format>> videoFormatHistory;
    private final List<Pair<EventTime, @NullableType Format>> audioFormatHistory;
    private final List<Pair<EventTime, Exception>> fatalErrorHistory;
    private final List<Pair<EventTime, Exception>> nonFatalErrorHistory;
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
    private boolean isFinished;
    private boolean playWhenReady;
    @Player.State private int playerPlaybackState;
    private boolean isSuppressed;
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
      playerPlaybackState = Player.STATE_IDLE;
      firstReportedTimeMs = C.TIME_UNSET;
      maxRebufferTimeMs = C.TIME_UNSET;
      isAd = startTime.mediaPeriodId != null && startTime.mediaPeriodId.isAd();
      initialAudioFormatBitrate = C.LENGTH_UNSET;
      initialVideoFormatBitrate = C.LENGTH_UNSET;
      initialVideoFormatHeight = C.LENGTH_UNSET;
      currentPlaybackSpeed = 1f;
    }

    /**
     * Notifies the tracker of a player state change event, including all player state changes while
     * the playback is not in the foreground.
     *
     * @param eventTime The {@link EventTime}.
     * @param playWhenReady Whether the playback will proceed when ready.
     * @param playbackState The current {@link Player.State}.
     * @param belongsToPlayback Whether the {@code eventTime} belongs to the current playback.
     */
    public void onPlayerStateChanged(
        EventTime eventTime,
        boolean playWhenReady,
        @Player.State int playbackState,
        boolean belongsToPlayback) {
      this.playWhenReady = playWhenReady;
      playerPlaybackState = playbackState;
      if (playbackState != Player.STATE_IDLE) {
        hasFatalError = false;
      }
      if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        isInterruptedByAd = false;
      }
      maybeUpdatePlaybackState(eventTime, belongsToPlayback);
    }

    /**
     * Notifies the tracker of a change to the playback suppression (e.g. due to audio focus loss),
     * including all updates while the playback is not in the foreground.
     *
     * @param eventTime The {@link EventTime}.
     * @param isSuppressed Whether playback is suppressed.
     * @param belongsToPlayback Whether the {@code eventTime} belongs to the current playback.
     */
    public void onIsSuppressedChanged(
        EventTime eventTime, boolean isSuppressed, boolean belongsToPlayback) {
      this.isSuppressed = isSuppressed;
      maybeUpdatePlaybackState(eventTime, belongsToPlayback);
    }

    /**
     * Notifies the tracker of a position discontinuity or timeline update for the current playback.
     *
     * @param eventTime The {@link EventTime}.
     */
    public void onPositionDiscontinuity(EventTime eventTime) {
      isInterruptedByAd = false;
      maybeUpdatePlaybackState(eventTime, /* belongsToPlayback= */ true);
    }

    /**
     * Notifies the tracker of the start of a seek in the current playback.
     *
     * @param eventTime The {@link EventTime}.
     */
    public void onSeekStarted(EventTime eventTime) {
      isSeeking = true;
      maybeUpdatePlaybackState(eventTime, /* belongsToPlayback= */ true);
    }

    /**
     * Notifies the tracker of a seek has been processed in the current playback.
     *
     * @param eventTime The {@link EventTime}.
     */
    public void onSeekProcessed(EventTime eventTime) {
      isSeeking = false;
      maybeUpdatePlaybackState(eventTime, /* belongsToPlayback= */ true);
    }

    /**
     * Notifies the tracker of fatal player error in the current playback.
     *
     * @param eventTime The {@link EventTime}.
     */
    public void onFatalError(EventTime eventTime, Exception error) {
      fatalErrorCount++;
      if (keepHistory) {
        fatalErrorHistory.add(Pair.create(eventTime, error));
      }
      hasFatalError = true;
      isInterruptedByAd = false;
      isSeeking = false;
      maybeUpdatePlaybackState(eventTime, /* belongsToPlayback= */ true);
    }

    /**
     * Notifies the tracker that a load for the current playback has started.
     *
     * @param eventTime The {@link EventTime}.
     */
    public void onLoadStarted(EventTime eventTime) {
      startedLoading = true;
      maybeUpdatePlaybackState(eventTime, /* belongsToPlayback= */ true);
    }

    /**
     * Notifies the tracker that the current playback became the active foreground playback.
     *
     * @param eventTime The {@link EventTime}.
     */
    public void onForeground(EventTime eventTime) {
      isForeground = true;
      maybeUpdatePlaybackState(eventTime, /* belongsToPlayback= */ true);
    }

    /**
     * Notifies the tracker that the current playback has been interrupted for ad playback.
     *
     * @param eventTime The {@link EventTime}.
     */
    public void onInterruptedByAd(EventTime eventTime) {
      isInterruptedByAd = true;
      isSeeking = false;
      maybeUpdatePlaybackState(eventTime, /* belongsToPlayback= */ true);
    }

    /**
     * Notifies the tracker that the current playback has finished.
     *
     * @param eventTime The {@link EventTime}. Not guaranteed to belong to the current playback.
     */
    public void onFinished(EventTime eventTime) {
      isFinished = true;
      maybeUpdatePlaybackState(eventTime, /* belongsToPlayback= */ false);
    }

    /**
     * Notifies the tracker that the track selection for the current playback changed.
     *
     * @param eventTime The {@link EventTime}.
     * @param trackSelections The new {@link TrackSelectionArray}.
     */
    public void onTracksChanged(EventTime eventTime, TrackSelectionArray trackSelections) {
      boolean videoEnabled = false;
      boolean audioEnabled = false;
      for (TrackSelection trackSelection : trackSelections.getAll()) {
        if (trackSelection != null && trackSelection.length() > 0) {
          int trackType = MimeTypes.getTrackType(trackSelection.getFormat(0).sampleMimeType);
          if (trackType == C.TRACK_TYPE_VIDEO) {
            videoEnabled = true;
          } else if (trackType == C.TRACK_TYPE_AUDIO) {
            audioEnabled = true;
          }
        }
      }
      if (!videoEnabled) {
        maybeUpdateVideoFormat(eventTime, /* newFormat= */ null);
      }
      if (!audioEnabled) {
        maybeUpdateAudioFormat(eventTime, /* newFormat= */ null);
      }
    }

    /**
     * Notifies the tracker that a format being read by the renderers for the current playback
     * changed.
     *
     * @param eventTime The {@link EventTime}.
     * @param mediaLoadData The {@link MediaLoadData} describing the format change.
     */
    public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
      if (mediaLoadData.trackType == C.TRACK_TYPE_VIDEO
          || mediaLoadData.trackType == C.TRACK_TYPE_DEFAULT) {
        maybeUpdateVideoFormat(eventTime, mediaLoadData.trackFormat);
      } else if (mediaLoadData.trackType == C.TRACK_TYPE_AUDIO) {
        maybeUpdateAudioFormat(eventTime, mediaLoadData.trackFormat);
      }
    }

    /**
     * Notifies the tracker that the video size for the current playback changed.
     *
     * @param eventTime The {@link EventTime}.
     * @param width The video width in pixels.
     * @param height The video height in pixels.
     */
    public void onVideoSizeChanged(EventTime eventTime, int width, int height) {
      if (currentVideoFormat != null && currentVideoFormat.height == Format.NO_VALUE) {
        Format formatWithHeight = currentVideoFormat.copyWithVideoSize(width, height);
        maybeUpdateVideoFormat(eventTime, formatWithHeight);
      }
    }

    /**
     * Notifies the tracker of a playback speed change, including all playback speed changes while
     * the playback is not in the foreground.
     *
     * @param eventTime The {@link EventTime}.
     * @param playbackSpeed The new playback speed.
     */
    public void onPlaybackSpeedChanged(EventTime eventTime, float playbackSpeed) {
      maybeUpdateMediaTimeHistory(eventTime.realtimeMs, eventTime.eventPlaybackPositionMs);
      maybeRecordVideoFormatTime(eventTime.realtimeMs);
      maybeRecordAudioFormatTime(eventTime.realtimeMs);
      currentPlaybackSpeed = playbackSpeed;
    }

    /** Notifies the builder of an audio underrun for the current playback. */
    public void onAudioUnderrun() {
      audioUnderruns++;
    }

    /**
     * Notifies the tracker of dropped video frames for the current playback.
     *
     * @param droppedFrames The number of dropped video frames.
     */
    public void onDroppedVideoFrames(int droppedFrames) {
      this.droppedFrames += droppedFrames;
    }

    /**
     * Notifies the tracker of bandwidth measurement data for the current playback.
     *
     * @param timeMs The time for which bandwidth measurement data is available, in milliseconds.
     * @param bytes The bytes transferred during {@code timeMs}.
     */
    public void onBandwidthData(long timeMs, long bytes) {
      bandwidthTimeMs += timeMs;
      bandwidthBytes += bytes;
    }

    /**
     * Notifies the tracker of a non-fatal error in the current playback.
     *
     * @param eventTime The {@link EventTime}.
     * @param error The error.
     */
    public void onNonFatalError(EventTime eventTime, Exception error) {
      nonFatalErrorCount++;
      if (keepHistory) {
        nonFatalErrorHistory.add(Pair.create(eventTime, error));
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
        long lastStateDurationMs = Math.max(0, buildTimeMs - currentPlaybackStateStartTimeMs);
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
      List<Pair<EventTime, @NullableType Format>> videoHistory =
          isFinal ? videoFormatHistory : new ArrayList<>(videoFormatHistory);
      List<Pair<EventTime, @NullableType Format>> audioHistory =
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

    private void maybeUpdatePlaybackState(EventTime eventTime, boolean belongsToPlayback) {
      @PlaybackState int newPlaybackState = resolveNewPlaybackState();
      if (newPlaybackState == currentPlaybackState) {
        return;
      }
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

      maybeUpdateMediaTimeHistory(
          eventTime.realtimeMs,
          /* mediaTimeMs= */ belongsToPlayback ? eventTime.eventPlaybackPositionMs : C.TIME_UNSET);
      maybeUpdateMaxRebufferTimeMs(eventTime.realtimeMs);
      maybeRecordVideoFormatTime(eventTime.realtimeMs);
      maybeRecordAudioFormatTime(eventTime.realtimeMs);

      currentPlaybackState = newPlaybackState;
      currentPlaybackStateStartTimeMs = eventTime.realtimeMs;
      if (keepHistory) {
        playbackStateHistory.add(Pair.create(eventTime, currentPlaybackState));
      }
    }

    private @PlaybackState int resolveNewPlaybackState() {
      if (isFinished) {
        // Keep VIDEO_STATE_ENDED if playback naturally ended (or progressed to next item).
        return currentPlaybackState == PlaybackStats.PLAYBACK_STATE_ENDED
            ? PlaybackStats.PLAYBACK_STATE_ENDED
            : PlaybackStats.PLAYBACK_STATE_ABANDONED;
      } else if (isSeeking) {
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
        if (currentPlaybackState == PlaybackStats.PLAYBACK_STATE_SEEKING
            || currentPlaybackState == PlaybackStats.PLAYBACK_STATE_SEEK_BUFFERING) {
          return PlaybackStats.PLAYBACK_STATE_SEEK_BUFFERING;
        }
        if (!playWhenReady) {
          return PlaybackStats.PLAYBACK_STATE_PAUSED_BUFFERING;
        }
        return isSuppressed
            ? PlaybackStats.PLAYBACK_STATE_SUPPRESSED_BUFFERING
            : PlaybackStats.PLAYBACK_STATE_BUFFERING;
      } else if (playerPlaybackState == Player.STATE_READY) {
        if (!playWhenReady) {
          return PlaybackStats.PLAYBACK_STATE_PAUSED;
        }
        return isSuppressed
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
      mediaTimeHistory.add(
          mediaTimeMs == C.TIME_UNSET
              ? guessMediaTimeBasedOnElapsedRealtime(realtimeMs)
              : new long[] {realtimeMs, mediaTimeMs});
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
        videoFormatHistory.add(Pair.create(eventTime, currentVideoFormat));
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
        audioFormatHistory.add(Pair.create(eventTime, currentAudioFormat));
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
