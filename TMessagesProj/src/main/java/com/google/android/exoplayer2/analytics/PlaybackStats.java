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

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;

/** Statistics about playbacks. */
public final class PlaybackStats {

  /** Stores a playback state with the event time at which it became active. */
  public static final class EventTimeAndPlaybackState {
    /** The event time at which the playback state became active. */
    public final EventTime eventTime;
    /** The playback state that became active. */
    public final @PlaybackState int playbackState;

    /**
     * Creates a new timed playback state event.
     *
     * @param eventTime The event time at which the playback state became active.
     * @param playbackState The playback state that became active.
     */
    public EventTimeAndPlaybackState(EventTime eventTime, @PlaybackState int playbackState) {
      this.eventTime = eventTime;
      this.playbackState = playbackState;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      EventTimeAndPlaybackState that = (EventTimeAndPlaybackState) o;
      if (playbackState != that.playbackState) {
        return false;
      }
      return eventTime.equals(that.eventTime);
    }

    @Override
    public int hashCode() {
      int result = eventTime.hashCode();
      result = 31 * result + playbackState;
      return result;
    }
  }

  /**
   * Stores a format with the event time at which it started being used, or {@code null} to indicate
   * that no format was used.
   */
  public static final class EventTimeAndFormat {
    /** The event time associated with {@link #format}. */
    public final EventTime eventTime;
    /** The format that started being used, or {@code null} if no format was used. */
    @Nullable public final Format format;

    /**
     * Creates a new timed format event.
     *
     * @param eventTime The event time associated with {@code format}.
     * @param format The format that started being used, or {@code null} if no format was used.
     */
    public EventTimeAndFormat(EventTime eventTime, @Nullable Format format) {
      this.eventTime = eventTime;
      this.format = format;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      EventTimeAndFormat that = (EventTimeAndFormat) o;
      if (!eventTime.equals(that.eventTime)) {
        return false;
      }
      return format != null ? format.equals(that.format) : that.format == null;
    }

    @Override
    public int hashCode() {
      int result = eventTime.hashCode();
      result = 31 * result + (format != null ? format.hashCode() : 0);
      return result;
    }
  }

  /** Stores an exception with the event time at which it occurred. */
  public static final class EventTimeAndException {
    /** The event time at which the exception occurred. */
    public final EventTime eventTime;
    /** The exception that was thrown. */
    public final Exception exception;

    /**
     * Creates a new timed exception event.
     *
     * @param eventTime The event time at which the exception occurred.
     * @param exception The exception that was thrown.
     */
    public EventTimeAndException(EventTime eventTime, Exception exception) {
      this.eventTime = eventTime;
      this.exception = exception;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      EventTimeAndException that = (EventTimeAndException) o;
      if (!eventTime.equals(that.eventTime)) {
        return false;
      }
      return exception.equals(that.exception);
    }

    @Override
    public int hashCode() {
      int result = eventTime.hashCode();
      result = 31 * result + exception.hashCode();
      return result;
    }
  }

  /**
   * State of a playback. One of {@link #PLAYBACK_STATE_NOT_STARTED}, {@link
   * #PLAYBACK_STATE_JOINING_FOREGROUND}, {@link #PLAYBACK_STATE_JOINING_BACKGROUND}, {@link
   * #PLAYBACK_STATE_PLAYING}, {@link #PLAYBACK_STATE_PAUSED}, {@link #PLAYBACK_STATE_SEEKING},
   * {@link #PLAYBACK_STATE_BUFFERING}, {@link #PLAYBACK_STATE_PAUSED_BUFFERING}, {@link
   * #PLAYBACK_STATE_SUPPRESSED}, {@link #PLAYBACK_STATE_SUPPRESSED_BUFFERING}, {@link
   * #PLAYBACK_STATE_ENDED}, {@link #PLAYBACK_STATE_STOPPED}, {@link #PLAYBACK_STATE_FAILED}, {@link
   * #PLAYBACK_STATE_INTERRUPTED_BY_AD} or {@link #PLAYBACK_STATE_ABANDONED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    PLAYBACK_STATE_NOT_STARTED,
    PLAYBACK_STATE_JOINING_BACKGROUND,
    PLAYBACK_STATE_JOINING_FOREGROUND,
    PLAYBACK_STATE_PLAYING,
    PLAYBACK_STATE_PAUSED,
    PLAYBACK_STATE_SEEKING,
    PLAYBACK_STATE_BUFFERING,
    PLAYBACK_STATE_PAUSED_BUFFERING,
    PLAYBACK_STATE_SUPPRESSED,
    PLAYBACK_STATE_SUPPRESSED_BUFFERING,
    PLAYBACK_STATE_ENDED,
    PLAYBACK_STATE_STOPPED,
    PLAYBACK_STATE_FAILED,
    PLAYBACK_STATE_INTERRUPTED_BY_AD,
    PLAYBACK_STATE_ABANDONED
  })
  @interface PlaybackState {}
  /** Playback has not started (initial state). */
  public static final int PLAYBACK_STATE_NOT_STARTED = 0;
  /** Playback is buffering in the background for initial playback start. */
  public static final int PLAYBACK_STATE_JOINING_BACKGROUND = 1;
  /** Playback is buffering in the foreground for initial playback start. */
  public static final int PLAYBACK_STATE_JOINING_FOREGROUND = 2;
  /** Playback is actively playing. */
  public static final int PLAYBACK_STATE_PLAYING = 3;
  /** Playback is paused but ready to play. */
  public static final int PLAYBACK_STATE_PAUSED = 4;
  /** Playback is handling a seek. */
  public static final int PLAYBACK_STATE_SEEKING = 5;
  /** Playback is buffering to resume active playback. */
  public static final int PLAYBACK_STATE_BUFFERING = 6;
  /** Playback is buffering while paused. */
  public static final int PLAYBACK_STATE_PAUSED_BUFFERING = 7;
  /** Playback is suppressed (e.g. due to audio focus loss). */
  public static final int PLAYBACK_STATE_SUPPRESSED = 9;
  /** Playback is suppressed (e.g. due to audio focus loss) while buffering to resume a playback. */
  public static final int PLAYBACK_STATE_SUPPRESSED_BUFFERING = 10;
  /** Playback has reached the end of the media. */
  public static final int PLAYBACK_STATE_ENDED = 11;
  /** Playback is stopped and can be restarted. */
  public static final int PLAYBACK_STATE_STOPPED = 12;
  /** Playback is stopped due a fatal error and can be retried. */
  public static final int PLAYBACK_STATE_FAILED = 13;
  /** Playback is interrupted by an ad. */
  public static final int PLAYBACK_STATE_INTERRUPTED_BY_AD = 14;
  /** Playback is abandoned before reaching the end of the media. */
  public static final int PLAYBACK_STATE_ABANDONED = 15;
  /** Total number of playback states. */
  /* package */ static final int PLAYBACK_STATE_COUNT = 16;

  /** Empty playback stats. */
  public static final PlaybackStats EMPTY = merge(/* nothing */ );

  /**
   * Returns the combined {@link PlaybackStats} for all input {@link PlaybackStats}.
   *
   * <p>Note that the full history of events is not kept as the history only makes sense in the
   * context of a single playback.
   *
   * @param playbackStats Array of {@link PlaybackStats} to combine.
   * @return The combined {@link PlaybackStats}.
   */
  public static PlaybackStats merge(PlaybackStats... playbackStats) {
    int playbackCount = 0;
    long[] playbackStateDurationsMs = new long[PLAYBACK_STATE_COUNT];
    long firstReportedTimeMs = C.TIME_UNSET;
    int foregroundPlaybackCount = 0;
    int abandonedBeforeReadyCount = 0;
    int endedCount = 0;
    int backgroundJoiningCount = 0;
    long totalValidJoinTimeMs = C.TIME_UNSET;
    int validJoinTimeCount = 0;
    int totalPauseCount = 0;
    int totalPauseBufferCount = 0;
    int totalSeekCount = 0;
    int totalRebufferCount = 0;
    long maxRebufferTimeMs = C.TIME_UNSET;
    int adPlaybackCount = 0;
    long totalVideoFormatHeightTimeMs = 0;
    long totalVideoFormatHeightTimeProduct = 0;
    long totalVideoFormatBitrateTimeMs = 0;
    long totalVideoFormatBitrateTimeProduct = 0;
    long totalAudioFormatTimeMs = 0;
    long totalAudioFormatBitrateTimeProduct = 0;
    int initialVideoFormatHeightCount = 0;
    int initialVideoFormatBitrateCount = 0;
    int totalInitialVideoFormatHeight = C.LENGTH_UNSET;
    long totalInitialVideoFormatBitrate = C.LENGTH_UNSET;
    int initialAudioFormatBitrateCount = 0;
    long totalInitialAudioFormatBitrate = C.LENGTH_UNSET;
    long totalBandwidthTimeMs = 0;
    long totalBandwidthBytes = 0;
    long totalDroppedFrames = 0;
    long totalAudioUnderruns = 0;
    int fatalErrorPlaybackCount = 0;
    int fatalErrorCount = 0;
    int nonFatalErrorCount = 0;
    for (PlaybackStats stats : playbackStats) {
      playbackCount += stats.playbackCount;
      for (int i = 0; i < PLAYBACK_STATE_COUNT; i++) {
        playbackStateDurationsMs[i] += stats.playbackStateDurationsMs[i];
      }
      if (firstReportedTimeMs == C.TIME_UNSET) {
        firstReportedTimeMs = stats.firstReportedTimeMs;
      } else if (stats.firstReportedTimeMs != C.TIME_UNSET) {
        firstReportedTimeMs = min(firstReportedTimeMs, stats.firstReportedTimeMs);
      }
      foregroundPlaybackCount += stats.foregroundPlaybackCount;
      abandonedBeforeReadyCount += stats.abandonedBeforeReadyCount;
      endedCount += stats.endedCount;
      backgroundJoiningCount += stats.backgroundJoiningCount;
      if (totalValidJoinTimeMs == C.TIME_UNSET) {
        totalValidJoinTimeMs = stats.totalValidJoinTimeMs;
      } else if (stats.totalValidJoinTimeMs != C.TIME_UNSET) {
        totalValidJoinTimeMs += stats.totalValidJoinTimeMs;
      }
      validJoinTimeCount += stats.validJoinTimeCount;
      totalPauseCount += stats.totalPauseCount;
      totalPauseBufferCount += stats.totalPauseBufferCount;
      totalSeekCount += stats.totalSeekCount;
      totalRebufferCount += stats.totalRebufferCount;
      if (maxRebufferTimeMs == C.TIME_UNSET) {
        maxRebufferTimeMs = stats.maxRebufferTimeMs;
      } else if (stats.maxRebufferTimeMs != C.TIME_UNSET) {
        maxRebufferTimeMs = max(maxRebufferTimeMs, stats.maxRebufferTimeMs);
      }
      adPlaybackCount += stats.adPlaybackCount;
      totalVideoFormatHeightTimeMs += stats.totalVideoFormatHeightTimeMs;
      totalVideoFormatHeightTimeProduct += stats.totalVideoFormatHeightTimeProduct;
      totalVideoFormatBitrateTimeMs += stats.totalVideoFormatBitrateTimeMs;
      totalVideoFormatBitrateTimeProduct += stats.totalVideoFormatBitrateTimeProduct;
      totalAudioFormatTimeMs += stats.totalAudioFormatTimeMs;
      totalAudioFormatBitrateTimeProduct += stats.totalAudioFormatBitrateTimeProduct;
      initialVideoFormatHeightCount += stats.initialVideoFormatHeightCount;
      initialVideoFormatBitrateCount += stats.initialVideoFormatBitrateCount;
      if (totalInitialVideoFormatHeight == C.LENGTH_UNSET) {
        totalInitialVideoFormatHeight = stats.totalInitialVideoFormatHeight;
      } else if (stats.totalInitialVideoFormatHeight != C.LENGTH_UNSET) {
        totalInitialVideoFormatHeight += stats.totalInitialVideoFormatHeight;
      }
      if (totalInitialVideoFormatBitrate == C.LENGTH_UNSET) {
        totalInitialVideoFormatBitrate = stats.totalInitialVideoFormatBitrate;
      } else if (stats.totalInitialVideoFormatBitrate != C.LENGTH_UNSET) {
        totalInitialVideoFormatBitrate += stats.totalInitialVideoFormatBitrate;
      }
      initialAudioFormatBitrateCount += stats.initialAudioFormatBitrateCount;
      if (totalInitialAudioFormatBitrate == C.LENGTH_UNSET) {
        totalInitialAudioFormatBitrate = stats.totalInitialAudioFormatBitrate;
      } else if (stats.totalInitialAudioFormatBitrate != C.LENGTH_UNSET) {
        totalInitialAudioFormatBitrate += stats.totalInitialAudioFormatBitrate;
      }
      totalBandwidthTimeMs += stats.totalBandwidthTimeMs;
      totalBandwidthBytes += stats.totalBandwidthBytes;
      totalDroppedFrames += stats.totalDroppedFrames;
      totalAudioUnderruns += stats.totalAudioUnderruns;
      fatalErrorPlaybackCount += stats.fatalErrorPlaybackCount;
      fatalErrorCount += stats.fatalErrorCount;
      nonFatalErrorCount += stats.nonFatalErrorCount;
    }
    return new PlaybackStats(
        playbackCount,
        playbackStateDurationsMs,
        /* playbackStateHistory */ Collections.emptyList(),
        /* mediaTimeHistory= */ Collections.emptyList(),
        firstReportedTimeMs,
        foregroundPlaybackCount,
        abandonedBeforeReadyCount,
        endedCount,
        backgroundJoiningCount,
        totalValidJoinTimeMs,
        validJoinTimeCount,
        totalPauseCount,
        totalPauseBufferCount,
        totalSeekCount,
        totalRebufferCount,
        maxRebufferTimeMs,
        adPlaybackCount,
        /* videoFormatHistory= */ Collections.emptyList(),
        /* audioFormatHistory= */ Collections.emptyList(),
        totalVideoFormatHeightTimeMs,
        totalVideoFormatHeightTimeProduct,
        totalVideoFormatBitrateTimeMs,
        totalVideoFormatBitrateTimeProduct,
        totalAudioFormatTimeMs,
        totalAudioFormatBitrateTimeProduct,
        initialVideoFormatHeightCount,
        initialVideoFormatBitrateCount,
        totalInitialVideoFormatHeight,
        totalInitialVideoFormatBitrate,
        initialAudioFormatBitrateCount,
        totalInitialAudioFormatBitrate,
        totalBandwidthTimeMs,
        totalBandwidthBytes,
        totalDroppedFrames,
        totalAudioUnderruns,
        fatalErrorPlaybackCount,
        fatalErrorCount,
        nonFatalErrorCount,
        /* fatalErrorHistory= */ Collections.emptyList(),
        /* nonFatalErrorHistory= */ Collections.emptyList());
  }

  /** The number of individual playbacks for which these stats were collected. */
  public final int playbackCount;

  // Playback state stats.

  /**
   * The playback state history as {@link EventTimeAndPlaybackState EventTimeAndPlaybackStates}
   * ordered by {@code EventTime.realTimeMs}.
   */
  public final List<EventTimeAndPlaybackState> playbackStateHistory;
  /**
   * The media time history as an ordered list of long[2] arrays with [0] being the realtime as
   * returned by {@code SystemClock.elapsedRealtime()} and [1] being the media time at this
   * realtime, in milliseconds.
   */
  public final List<long[]> mediaTimeHistory;
  /**
   * The elapsed real-time as returned by {@code SystemClock.elapsedRealtime()} of the first
   * reported playback event, or {@link C#TIME_UNSET} if no event has been reported.
   */
  public final long firstReportedTimeMs;
  /** The number of playbacks which were the active foreground playback at some point. */
  public final int foregroundPlaybackCount;
  /** The number of playbacks which were abandoned before they were ready to play. */
  public final int abandonedBeforeReadyCount;
  /** The number of playbacks which reached the ended state at least once. */
  public final int endedCount;
  /** The number of playbacks which were pre-buffered in the background. */
  public final int backgroundJoiningCount;
  /**
   * The total time spent joining the playback, in milliseconds, or {@link C#TIME_UNSET} if no valid
   * join time could be determined.
   *
   * <p>Note that this does not include background joining time. A join time may be invalid if the
   * playback never reached {@link #PLAYBACK_STATE_PLAYING} or {@link #PLAYBACK_STATE_PAUSED}, or
   * joining was interrupted by a seek, stop, or error state.
   */
  public final long totalValidJoinTimeMs;
  /**
   * The number of playbacks with a valid join time as documented in {@link #totalValidJoinTimeMs}.
   */
  public final int validJoinTimeCount;
  /** The total number of times a playback has been paused. */
  public final int totalPauseCount;
  /** The total number of times a playback has been paused while rebuffering. */
  public final int totalPauseBufferCount;
  /**
   * The total number of times a seek occurred. This includes seeks happening before playback
   * resumed after another seek.
   */
  public final int totalSeekCount;
  /**
   * The total number of times a rebuffer occurred. This excludes initial joining and buffering
   * after seek.
   */
  public final int totalRebufferCount;
  /**
   * The maximum time spent during a single rebuffer, in milliseconds, or {@link C#TIME_UNSET} if no
   * rebuffer occurred.
   */
  public final long maxRebufferTimeMs;
  /** The number of ad playbacks. */
  public final int adPlaybackCount;

  // Format stats.

  /**
   * The video format history as {@link EventTimeAndFormat EventTimeAndFormats} ordered by {@code
   * EventTime.realTimeMs}. The {@link Format} may be null if no video format was used.
   */
  public final List<EventTimeAndFormat> videoFormatHistory;
  /**
   * The audio format history as {@link EventTimeAndFormat EventTimeAndFormats} ordered by {@code
   * EventTime.realTimeMs}. The {@link Format} may be null if no audio format was used.
   */
  public final List<EventTimeAndFormat> audioFormatHistory;
  /** The total media time for which video format height data is available, in milliseconds. */
  public final long totalVideoFormatHeightTimeMs;
  /**
   * The accumulated sum of all video format heights, in pixels, times the time the format was used
   * for playback, in milliseconds.
   */
  public final long totalVideoFormatHeightTimeProduct;
  /** The total media time for which video format bitrate data is available, in milliseconds. */
  public final long totalVideoFormatBitrateTimeMs;
  /**
   * The accumulated sum of all video format bitrates, in bits per second, times the time the format
   * was used for playback, in milliseconds.
   */
  public final long totalVideoFormatBitrateTimeProduct;
  /** The total media time for which audio format data is available, in milliseconds. */
  public final long totalAudioFormatTimeMs;
  /**
   * The accumulated sum of all audio format bitrates, in bits per second, times the time the format
   * was used for playback, in milliseconds.
   */
  public final long totalAudioFormatBitrateTimeProduct;
  /** The number of playbacks with initial video format height data. */
  public final int initialVideoFormatHeightCount;
  /** The number of playbacks with initial video format bitrate data. */
  public final int initialVideoFormatBitrateCount;
  /**
   * The total initial video format height for all playbacks, in pixels, or {@link C#LENGTH_UNSET}
   * if no initial video format data is available.
   */
  public final int totalInitialVideoFormatHeight;
  /**
   * The total initial video format bitrate for all playbacks, in bits per second, or {@link
   * C#LENGTH_UNSET} if no initial video format data is available.
   */
  public final long totalInitialVideoFormatBitrate;
  /** The number of playbacks with initial audio format bitrate data. */
  public final int initialAudioFormatBitrateCount;
  /**
   * The total initial audio format bitrate for all playbacks, in bits per second, or {@link
   * C#LENGTH_UNSET} if no initial audio format data is available.
   */
  public final long totalInitialAudioFormatBitrate;

  // Bandwidth stats.

  /** The total time for which bandwidth measurement data is available, in milliseconds. */
  public final long totalBandwidthTimeMs;
  /** The total bytes transferred during {@link #totalBandwidthTimeMs}. */
  public final long totalBandwidthBytes;

  // Renderer quality stats.

  /** The total number of dropped video frames. */
  public final long totalDroppedFrames;
  /** The total number of audio underruns. */
  public final long totalAudioUnderruns;

  // Error stats.

  /**
   * The total number of playback with at least one fatal error. Errors are fatal if playback
   * stopped due to this error.
   */
  public final int fatalErrorPlaybackCount;
  /** The total number of fatal errors. Errors are fatal if playback stopped due to this error. */
  public final int fatalErrorCount;
  /**
   * The total number of non-fatal errors. Error are non-fatal if playback can recover from the
   * error without stopping.
   */
  public final int nonFatalErrorCount;
  /**
   * The history of fatal errors as {@link EventTimeAndException EventTimeAndExceptions} ordered by
   * {@code EventTime.realTimeMs}. Errors are fatal if playback stopped due to this error.
   */
  public final List<EventTimeAndException> fatalErrorHistory;
  /**
   * The history of non-fatal errors as {@link EventTimeAndException EventTimeAndExceptions} ordered
   * by {@code EventTime.realTimeMs}. Errors are non-fatal if playback can recover from the error
   * without stopping.
   */
  public final List<EventTimeAndException> nonFatalErrorHistory;

  private final long[] playbackStateDurationsMs;

  /* package */ PlaybackStats(
      int playbackCount,
      long[] playbackStateDurationsMs,
      List<EventTimeAndPlaybackState> playbackStateHistory,
      List<long[]> mediaTimeHistory,
      long firstReportedTimeMs,
      int foregroundPlaybackCount,
      int abandonedBeforeReadyCount,
      int endedCount,
      int backgroundJoiningCount,
      long totalValidJoinTimeMs,
      int validJoinTimeCount,
      int totalPauseCount,
      int totalPauseBufferCount,
      int totalSeekCount,
      int totalRebufferCount,
      long maxRebufferTimeMs,
      int adPlaybackCount,
      List<EventTimeAndFormat> videoFormatHistory,
      List<EventTimeAndFormat> audioFormatHistory,
      long totalVideoFormatHeightTimeMs,
      long totalVideoFormatHeightTimeProduct,
      long totalVideoFormatBitrateTimeMs,
      long totalVideoFormatBitrateTimeProduct,
      long totalAudioFormatTimeMs,
      long totalAudioFormatBitrateTimeProduct,
      int initialVideoFormatHeightCount,
      int initialVideoFormatBitrateCount,
      int totalInitialVideoFormatHeight,
      long totalInitialVideoFormatBitrate,
      int initialAudioFormatBitrateCount,
      long totalInitialAudioFormatBitrate,
      long totalBandwidthTimeMs,
      long totalBandwidthBytes,
      long totalDroppedFrames,
      long totalAudioUnderruns,
      int fatalErrorPlaybackCount,
      int fatalErrorCount,
      int nonFatalErrorCount,
      List<EventTimeAndException> fatalErrorHistory,
      List<EventTimeAndException> nonFatalErrorHistory) {
    this.playbackCount = playbackCount;
    this.playbackStateDurationsMs = playbackStateDurationsMs;
    this.playbackStateHistory = Collections.unmodifiableList(playbackStateHistory);
    this.mediaTimeHistory = Collections.unmodifiableList(mediaTimeHistory);
    this.firstReportedTimeMs = firstReportedTimeMs;
    this.foregroundPlaybackCount = foregroundPlaybackCount;
    this.abandonedBeforeReadyCount = abandonedBeforeReadyCount;
    this.endedCount = endedCount;
    this.backgroundJoiningCount = backgroundJoiningCount;
    this.totalValidJoinTimeMs = totalValidJoinTimeMs;
    this.validJoinTimeCount = validJoinTimeCount;
    this.totalPauseCount = totalPauseCount;
    this.totalPauseBufferCount = totalPauseBufferCount;
    this.totalSeekCount = totalSeekCount;
    this.totalRebufferCount = totalRebufferCount;
    this.maxRebufferTimeMs = maxRebufferTimeMs;
    this.adPlaybackCount = adPlaybackCount;
    this.videoFormatHistory = Collections.unmodifiableList(videoFormatHistory);
    this.audioFormatHistory = Collections.unmodifiableList(audioFormatHistory);
    this.totalVideoFormatHeightTimeMs = totalVideoFormatHeightTimeMs;
    this.totalVideoFormatHeightTimeProduct = totalVideoFormatHeightTimeProduct;
    this.totalVideoFormatBitrateTimeMs = totalVideoFormatBitrateTimeMs;
    this.totalVideoFormatBitrateTimeProduct = totalVideoFormatBitrateTimeProduct;
    this.totalAudioFormatTimeMs = totalAudioFormatTimeMs;
    this.totalAudioFormatBitrateTimeProduct = totalAudioFormatBitrateTimeProduct;
    this.initialVideoFormatHeightCount = initialVideoFormatHeightCount;
    this.initialVideoFormatBitrateCount = initialVideoFormatBitrateCount;
    this.totalInitialVideoFormatHeight = totalInitialVideoFormatHeight;
    this.totalInitialVideoFormatBitrate = totalInitialVideoFormatBitrate;
    this.initialAudioFormatBitrateCount = initialAudioFormatBitrateCount;
    this.totalInitialAudioFormatBitrate = totalInitialAudioFormatBitrate;
    this.totalBandwidthTimeMs = totalBandwidthTimeMs;
    this.totalBandwidthBytes = totalBandwidthBytes;
    this.totalDroppedFrames = totalDroppedFrames;
    this.totalAudioUnderruns = totalAudioUnderruns;
    this.fatalErrorPlaybackCount = fatalErrorPlaybackCount;
    this.fatalErrorCount = fatalErrorCount;
    this.nonFatalErrorCount = nonFatalErrorCount;
    this.fatalErrorHistory = Collections.unmodifiableList(fatalErrorHistory);
    this.nonFatalErrorHistory = Collections.unmodifiableList(nonFatalErrorHistory);
  }

  /**
   * Returns the total time spent in a given {@link PlaybackState}, in milliseconds.
   *
   * @param playbackState A {@link PlaybackState}.
   * @return Total spent in the given playback state, in milliseconds
   */
  public long getPlaybackStateDurationMs(@PlaybackState int playbackState) {
    return playbackStateDurationsMs[playbackState];
  }

  /**
   * Returns the {@link PlaybackState} at the given time.
   *
   * @param realtimeMs The time as returned by {@link SystemClock#elapsedRealtime()}.
   * @return The {@link PlaybackState} at that time, or {@link #PLAYBACK_STATE_NOT_STARTED} if the
   *     given time is before the first known playback state in the history.
   */
  public @PlaybackState int getPlaybackStateAtTime(long realtimeMs) {
    @PlaybackState int state = PLAYBACK_STATE_NOT_STARTED;
    for (EventTimeAndPlaybackState timeAndState : playbackStateHistory) {
      if (timeAndState.eventTime.realtimeMs > realtimeMs) {
        break;
      }
      state = timeAndState.playbackState;
    }
    return state;
  }

  /**
   * Returns the estimated media time at the given realtime, in milliseconds, or {@link
   * C#TIME_UNSET} if the media time history is unknown.
   *
   * @param realtimeMs The realtime as returned by {@link SystemClock#elapsedRealtime()}.
   * @return The estimated media time in milliseconds at this realtime, {@link C#TIME_UNSET} if no
   *     estimate can be given.
   */
  public long getMediaTimeMsAtRealtimeMs(long realtimeMs) {
    if (mediaTimeHistory.isEmpty()) {
      return C.TIME_UNSET;
    }
    int nextIndex = 0;
    while (nextIndex < mediaTimeHistory.size()
        && mediaTimeHistory.get(nextIndex)[0] <= realtimeMs) {
      nextIndex++;
    }
    if (nextIndex == 0) {
      return mediaTimeHistory.get(0)[1];
    }
    if (nextIndex == mediaTimeHistory.size()) {
      return mediaTimeHistory.get(mediaTimeHistory.size() - 1)[1];
    }
    long prevRealtimeMs = mediaTimeHistory.get(nextIndex - 1)[0];
    long prevMediaTimeMs = mediaTimeHistory.get(nextIndex - 1)[1];
    long nextRealtimeMs = mediaTimeHistory.get(nextIndex)[0];
    long nextMediaTimeMs = mediaTimeHistory.get(nextIndex)[1];
    long realtimeDurationMs = nextRealtimeMs - prevRealtimeMs;
    if (realtimeDurationMs == 0) {
      return prevMediaTimeMs;
    }
    float fraction = (float) (realtimeMs - prevRealtimeMs) / realtimeDurationMs;
    return prevMediaTimeMs + (long) ((nextMediaTimeMs - prevMediaTimeMs) * fraction);
  }

  /**
   * Returns the mean time spent joining the playback, in milliseconds, or {@link C#TIME_UNSET} if
   * no valid join time is available. Only includes playbacks with valid join times as documented in
   * {@link #totalValidJoinTimeMs}.
   */
  public long getMeanJoinTimeMs() {
    return validJoinTimeCount == 0 ? C.TIME_UNSET : totalValidJoinTimeMs / validJoinTimeCount;
  }

  /**
   * Returns the total time spent joining the playback in foreground, in milliseconds. This does
   * include invalid join times where the playback never reached {@link #PLAYBACK_STATE_PLAYING} or
   * {@link #PLAYBACK_STATE_PAUSED}, or joining was interrupted by a seek, stop, or error state.
   */
  public long getTotalJoinTimeMs() {
    return getPlaybackStateDurationMs(PLAYBACK_STATE_JOINING_FOREGROUND);
  }

  /** Returns the total time spent actively playing, in milliseconds. */
  public long getTotalPlayTimeMs() {
    return getPlaybackStateDurationMs(PLAYBACK_STATE_PLAYING);
  }

  /**
   * Returns the mean time spent actively playing per foreground playback, in milliseconds, or
   * {@link C#TIME_UNSET} if no playback has been in foreground.
   */
  public long getMeanPlayTimeMs() {
    return foregroundPlaybackCount == 0
        ? C.TIME_UNSET
        : getTotalPlayTimeMs() / foregroundPlaybackCount;
  }

  /** Returns the total time spent in a paused state, in milliseconds. */
  public long getTotalPausedTimeMs() {
    return getPlaybackStateDurationMs(PLAYBACK_STATE_PAUSED)
        + getPlaybackStateDurationMs(PLAYBACK_STATE_PAUSED_BUFFERING);
  }

  /**
   * Returns the mean time spent in a paused state per foreground playback, in milliseconds, or
   * {@link C#TIME_UNSET} if no playback has been in foreground.
   */
  public long getMeanPausedTimeMs() {
    return foregroundPlaybackCount == 0
        ? C.TIME_UNSET
        : getTotalPausedTimeMs() / foregroundPlaybackCount;
  }

  /**
   * Returns the total time spent rebuffering, in milliseconds. This excludes initial join times,
   * buffer times after a seek and buffering while paused.
   */
  public long getTotalRebufferTimeMs() {
    return getPlaybackStateDurationMs(PLAYBACK_STATE_BUFFERING);
  }

  /**
   * Returns the mean time spent rebuffering per foreground playback, in milliseconds, or {@link
   * C#TIME_UNSET} if no playback has been in foreground. This excludes initial join times, buffer
   * times after a seek and buffering while paused.
   */
  public long getMeanRebufferTimeMs() {
    return foregroundPlaybackCount == 0
        ? C.TIME_UNSET
        : getTotalRebufferTimeMs() / foregroundPlaybackCount;
  }

  /**
   * Returns the mean time spent during a single rebuffer, in milliseconds, or {@link C#TIME_UNSET}
   * if no rebuffer was recorded. This excludes initial join times and buffer times after a seek.
   */
  public long getMeanSingleRebufferTimeMs() {
    return totalRebufferCount == 0
        ? C.TIME_UNSET
        : (getPlaybackStateDurationMs(PLAYBACK_STATE_BUFFERING)
                + getPlaybackStateDurationMs(PLAYBACK_STATE_PAUSED_BUFFERING))
            / totalRebufferCount;
  }

  /**
   * Returns the total time spent from the start of a seek until playback is ready again, in
   * milliseconds.
   */
  public long getTotalSeekTimeMs() {
    return getPlaybackStateDurationMs(PLAYBACK_STATE_SEEKING);
  }

  /**
   * Returns the mean time spent per foreground playback from the start of a seek until playback is
   * ready again, in milliseconds, or {@link C#TIME_UNSET} if no playback has been in foreground.
   */
  public long getMeanSeekTimeMs() {
    return foregroundPlaybackCount == 0
        ? C.TIME_UNSET
        : getTotalSeekTimeMs() / foregroundPlaybackCount;
  }

  /**
   * Returns the mean time spent from the start of a single seek until playback is ready again, in
   * milliseconds, or {@link C#TIME_UNSET} if no seek occurred.
   */
  public long getMeanSingleSeekTimeMs() {
    return totalSeekCount == 0 ? C.TIME_UNSET : getTotalSeekTimeMs() / totalSeekCount;
  }

  /**
   * Returns the total time spent actively waiting for playback, in milliseconds. This includes all
   * join times, rebuffer times and seek times, but excludes times without user intention to play,
   * e.g. all paused states.
   */
  public long getTotalWaitTimeMs() {
    return getPlaybackStateDurationMs(PLAYBACK_STATE_JOINING_FOREGROUND)
        + getPlaybackStateDurationMs(PLAYBACK_STATE_BUFFERING)
        + getPlaybackStateDurationMs(PLAYBACK_STATE_SEEKING);
  }

  /**
   * Returns the mean time spent actively waiting for playback per foreground playback, in
   * milliseconds, or {@link C#TIME_UNSET} if no playback has been in foreground. This includes all
   * join times, rebuffer times and seek times, but excludes times without user intention to play,
   * e.g. all paused states.
   */
  public long getMeanWaitTimeMs() {
    return foregroundPlaybackCount == 0
        ? C.TIME_UNSET
        : getTotalWaitTimeMs() / foregroundPlaybackCount;
  }

  /** Returns the total time spent playing or actively waiting for playback, in milliseconds. */
  public long getTotalPlayAndWaitTimeMs() {
    return getTotalPlayTimeMs() + getTotalWaitTimeMs();
  }

  /**
   * Returns the mean time spent playing or actively waiting for playback per foreground playback,
   * in milliseconds, or {@link C#TIME_UNSET} if no playback has been in foreground.
   */
  public long getMeanPlayAndWaitTimeMs() {
    return foregroundPlaybackCount == 0
        ? C.TIME_UNSET
        : getTotalPlayAndWaitTimeMs() / foregroundPlaybackCount;
  }

  /** Returns the total time covered by any playback state, in milliseconds. */
  public long getTotalElapsedTimeMs() {
    long totalTimeMs = 0;
    for (int i = 0; i < PLAYBACK_STATE_COUNT; i++) {
      totalTimeMs += playbackStateDurationsMs[i];
    }
    return totalTimeMs;
  }

  /**
   * Returns the mean time covered by any playback state per playback, in milliseconds, or {@link
   * C#TIME_UNSET} if no playback was recorded.
   */
  public long getMeanElapsedTimeMs() {
    return playbackCount == 0 ? C.TIME_UNSET : getTotalElapsedTimeMs() / playbackCount;
  }

  /**
   * Returns the ratio of foreground playbacks which were abandoned before they were ready to play,
   * or {@code 0.0} if no playback has been in foreground.
   */
  public float getAbandonedBeforeReadyRatio() {
    int foregroundAbandonedBeforeReady =
        abandonedBeforeReadyCount - (playbackCount - foregroundPlaybackCount);
    return foregroundPlaybackCount == 0
        ? 0f
        : (float) foregroundAbandonedBeforeReady / foregroundPlaybackCount;
  }

  /**
   * Returns the ratio of foreground playbacks which reached the ended state at least once, or
   * {@code 0.0} if no playback has been in foreground.
   */
  public float getEndedRatio() {
    return foregroundPlaybackCount == 0 ? 0f : (float) endedCount / foregroundPlaybackCount;
  }

  /**
   * Returns the mean number of times a playback has been paused per foreground playback, or {@code
   * 0.0} if no playback has been in foreground.
   */
  public float getMeanPauseCount() {
    return foregroundPlaybackCount == 0 ? 0f : (float) totalPauseCount / foregroundPlaybackCount;
  }

  /**
   * Returns the mean number of times a playback has been paused while rebuffering per foreground
   * playback, or {@code 0.0} if no playback has been in foreground.
   */
  public float getMeanPauseBufferCount() {
    return foregroundPlaybackCount == 0
        ? 0f
        : (float) totalPauseBufferCount / foregroundPlaybackCount;
  }

  /**
   * Returns the mean number of times a seek occurred per foreground playback, or {@code 0.0} if no
   * playback has been in foreground. This includes seeks happening before playback resumed after
   * another seek.
   */
  public float getMeanSeekCount() {
    return foregroundPlaybackCount == 0 ? 0f : (float) totalSeekCount / foregroundPlaybackCount;
  }

  /**
   * Returns the mean number of times a rebuffer occurred per foreground playback, or {@code 0.0} if
   * no playback has been in foreground. This excludes initial joining and buffering after seek.
   */
  public float getMeanRebufferCount() {
    return foregroundPlaybackCount == 0 ? 0f : (float) totalRebufferCount / foregroundPlaybackCount;
  }

  /**
   * Returns the ratio of wait times to the total time spent playing and waiting, or {@code 0.0} if
   * no time was spend playing or waiting. This is equivalent to {@link #getTotalWaitTimeMs()} /
   * {@link #getTotalPlayAndWaitTimeMs()} and also to {@link #getJoinTimeRatio()} + {@link
   * #getRebufferTimeRatio()} + {@link #getSeekTimeRatio()}.
   */
  public float getWaitTimeRatio() {
    long playAndWaitTimeMs = getTotalPlayAndWaitTimeMs();
    return playAndWaitTimeMs == 0 ? 0f : (float) getTotalWaitTimeMs() / playAndWaitTimeMs;
  }

  /**
   * Returns the ratio of foreground join time to the total time spent playing and waiting, or
   * {@code 0.0} if no time was spend playing or waiting. This is equivalent to {@link
   * #getTotalJoinTimeMs()} / {@link #getTotalPlayAndWaitTimeMs()}.
   */
  public float getJoinTimeRatio() {
    long playAndWaitTimeMs = getTotalPlayAndWaitTimeMs();
    return playAndWaitTimeMs == 0 ? 0f : (float) getTotalJoinTimeMs() / playAndWaitTimeMs;
  }

  /**
   * Returns the ratio of rebuffer time to the total time spent playing and waiting, or {@code 0.0}
   * if no time was spend playing or waiting. This is equivalent to {@link
   * #getTotalRebufferTimeMs()} / {@link #getTotalPlayAndWaitTimeMs()}.
   */
  public float getRebufferTimeRatio() {
    long playAndWaitTimeMs = getTotalPlayAndWaitTimeMs();
    return playAndWaitTimeMs == 0 ? 0f : (float) getTotalRebufferTimeMs() / playAndWaitTimeMs;
  }

  /**
   * Returns the ratio of seek time to the total time spent playing and waiting, or {@code 0.0} if
   * no time was spend playing or waiting. This is equivalent to {@link #getTotalSeekTimeMs()} /
   * {@link #getTotalPlayAndWaitTimeMs()}.
   */
  public float getSeekTimeRatio() {
    long playAndWaitTimeMs = getTotalPlayAndWaitTimeMs();
    return playAndWaitTimeMs == 0 ? 0f : (float) getTotalSeekTimeMs() / playAndWaitTimeMs;
  }

  /**
   * Returns the rate of rebuffer events, in rebuffers per play time second, or {@code 0.0} if no
   * time was spend playing. This is equivalent to 1.0 / {@link #getMeanTimeBetweenRebuffers()}.
   */
  public float getRebufferRate() {
    long playTimeMs = getTotalPlayTimeMs();
    return playTimeMs == 0 ? 0f : 1000f * totalRebufferCount / playTimeMs;
  }

  /**
   * Returns the mean play time between rebuffer events, in seconds. This is equivalent to 1.0 /
   * {@link #getRebufferRate()}. Note that this may return {@link Float#POSITIVE_INFINITY}.
   */
  public float getMeanTimeBetweenRebuffers() {
    return 1f / getRebufferRate();
  }

  /**
   * Returns the mean initial video format height, in pixels, or {@link C#LENGTH_UNSET} if no video
   * format data is available.
   */
  public int getMeanInitialVideoFormatHeight() {
    return initialVideoFormatHeightCount == 0
        ? C.LENGTH_UNSET
        : totalInitialVideoFormatHeight / initialVideoFormatHeightCount;
  }

  /**
   * Returns the mean initial video format bitrate, in bits per second, or {@link C#LENGTH_UNSET} if
   * no video format data is available.
   */
  public int getMeanInitialVideoFormatBitrate() {
    return initialVideoFormatBitrateCount == 0
        ? C.LENGTH_UNSET
        : (int) (totalInitialVideoFormatBitrate / initialVideoFormatBitrateCount);
  }

  /**
   * Returns the mean initial audio format bitrate, in bits per second, or {@link C#LENGTH_UNSET} if
   * no audio format data is available.
   */
  public int getMeanInitialAudioFormatBitrate() {
    return initialAudioFormatBitrateCount == 0
        ? C.LENGTH_UNSET
        : (int) (totalInitialAudioFormatBitrate / initialAudioFormatBitrateCount);
  }

  /**
   * Returns the mean video format height, in pixels, or {@link C#LENGTH_UNSET} if no video format
   * data is available. This is a weighted average taking the time the format was used for playback
   * into account.
   */
  public int getMeanVideoFormatHeight() {
    return totalVideoFormatHeightTimeMs == 0
        ? C.LENGTH_UNSET
        : (int) (totalVideoFormatHeightTimeProduct / totalVideoFormatHeightTimeMs);
  }

  /**
   * Returns the mean video format bitrate, in bits per second, or {@link C#LENGTH_UNSET} if no
   * video format data is available. This is a weighted average taking the time the format was used
   * for playback into account.
   */
  public int getMeanVideoFormatBitrate() {
    return totalVideoFormatBitrateTimeMs == 0
        ? C.LENGTH_UNSET
        : (int) (totalVideoFormatBitrateTimeProduct / totalVideoFormatBitrateTimeMs);
  }

  /**
   * Returns the mean audio format bitrate, in bits per second, or {@link C#LENGTH_UNSET} if no
   * audio format data is available. This is a weighted average taking the time the format was used
   * for playback into account.
   */
  public int getMeanAudioFormatBitrate() {
    return totalAudioFormatTimeMs == 0
        ? C.LENGTH_UNSET
        : (int) (totalAudioFormatBitrateTimeProduct / totalAudioFormatTimeMs);
  }

  /**
   * Returns the mean network bandwidth based on transfer measurements, in bits per second, or
   * {@link C#LENGTH_UNSET} if no transfer data is available.
   */
  public int getMeanBandwidth() {
    return totalBandwidthTimeMs == 0
        ? C.LENGTH_UNSET
        : (int) (totalBandwidthBytes * 8000 / totalBandwidthTimeMs);
  }

  /**
   * Returns the mean rate at which video frames are dropped, in dropped frames per play time
   * second, or {@code 0.0} if no time was spent playing.
   */
  public float getDroppedFramesRate() {
    long playTimeMs = getTotalPlayTimeMs();
    return playTimeMs == 0 ? 0f : 1000f * totalDroppedFrames / playTimeMs;
  }

  /**
   * Returns the mean rate at which audio underruns occurred, in underruns per play time second, or
   * {@code 0.0} if no time was spent playing.
   */
  public float getAudioUnderrunRate() {
    long playTimeMs = getTotalPlayTimeMs();
    return playTimeMs == 0 ? 0f : 1000f * totalAudioUnderruns / playTimeMs;
  }

  /**
   * Returns the ratio of foreground playbacks which experienced fatal errors, or {@code 0.0} if no
   * playback has been in foreground.
   */
  public float getFatalErrorRatio() {
    return foregroundPlaybackCount == 0
        ? 0f
        : (float) fatalErrorPlaybackCount / foregroundPlaybackCount;
  }

  /**
   * Returns the rate of fatal errors, in errors per play time second, or {@code 0.0} if no time was
   * spend playing. This is equivalent to 1.0 / {@link #getMeanTimeBetweenFatalErrors()}.
   */
  public float getFatalErrorRate() {
    long playTimeMs = getTotalPlayTimeMs();
    return playTimeMs == 0 ? 0f : 1000f * fatalErrorCount / playTimeMs;
  }

  /**
   * Returns the mean play time between fatal errors, in seconds. This is equivalent to 1.0 / {@link
   * #getFatalErrorRate()}. Note that this may return {@link Float#POSITIVE_INFINITY}.
   */
  public float getMeanTimeBetweenFatalErrors() {
    return 1f / getFatalErrorRate();
  }

  /**
   * Returns the mean number of non-fatal errors per foreground playback, or {@code 0.0} if no
   * playback has been in foreground.
   */
  public float getMeanNonFatalErrorCount() {
    return foregroundPlaybackCount == 0 ? 0f : (float) nonFatalErrorCount / foregroundPlaybackCount;
  }

  /**
   * Returns the rate of non-fatal errors, in errors per play time second, or {@code 0.0} if no time
   * was spend playing. This is equivalent to 1.0 / {@link #getMeanTimeBetweenNonFatalErrors()}.
   */
  public float getNonFatalErrorRate() {
    long playTimeMs = getTotalPlayTimeMs();
    return playTimeMs == 0 ? 0f : 1000f * nonFatalErrorCount / playTimeMs;
  }

  /**
   * Returns the mean play time between non-fatal errors, in seconds. This is equivalent to 1.0 /
   * {@link #getNonFatalErrorRate()}. Note that this may return {@link Float#POSITIVE_INFINITY}.
   */
  public float getMeanTimeBetweenNonFatalErrors() {
    return 1f / getNonFatalErrorRate();
  }
}
