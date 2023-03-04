/*
 * Copyright 2021 The Android Open Source Project
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
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.DeniedByServerException;
import android.media.MediaCodec;
import android.media.MediaDrm;
import android.media.MediaDrmResetException;
import android.media.NotProvisionedException;
import android.media.metrics.LogSessionId;
import android.media.metrics.MediaMetricsManager;
import android.media.metrics.NetworkEvent;
import android.media.metrics.PlaybackErrorEvent;
import android.media.metrics.PlaybackMetrics;
import android.media.metrics.PlaybackSession;
import android.media.metrics.PlaybackStateEvent;
import android.media.metrics.TrackChangeEvent;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.ContentType;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.mediacodec.MediaCodecDecoderException;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.UdpDataSource;
import com.google.android.exoplayer2.util.NetworkTypeObserver;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.common.collect.ImmutableList;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.UUID;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * An {@link AnalyticsListener} that interacts with the Android {@link MediaMetricsManager}.
 *
 * <p>It listens to playback events and forwards them to a {@link PlaybackSession}. The {@link
 * LogSessionId} of the playback session can be obtained with {@link #getLogSessionId()}.
 */
@RequiresApi(31)
public final class MediaMetricsListener
    implements AnalyticsListener, PlaybackSessionManager.Listener {

  /**
   * Creates a media metrics listener.
   *
   * @param context A context.
   * @return The {@link MediaMetricsListener}, or null if the {@link Context#MEDIA_METRICS_SERVICE
   *     media metrics service} isn't available.
   */
  @Nullable
  public static MediaMetricsListener create(Context context) {
    @Nullable
    MediaMetricsManager mediaMetricsManager =
        (MediaMetricsManager) context.getSystemService(Context.MEDIA_METRICS_SERVICE);
    return mediaMetricsManager == null
        ? null
        : new MediaMetricsListener(context, mediaMetricsManager.createPlaybackSession());
  }

  private final Context context;
  private final PlaybackSessionManager sessionManager;
  private final PlaybackSession playbackSession;
  private final long startTimeMs;
  private final Timeline.Window window;
  private final Timeline.Period period;
  private final HashMap<String, Long> bandwidthTimeMs;
  private final HashMap<String, Long> bandwidthBytes;

  @Nullable private String activeSessionId;
  @Nullable private PlaybackMetrics.Builder metricsBuilder;
  private @Player.DiscontinuityReason int discontinuityReason;
  private int currentPlaybackState;
  private int currentNetworkType;
  @Nullable private PlaybackException pendingPlayerError;
  @Nullable private PendingFormatUpdate pendingVideoFormat;
  @Nullable private PendingFormatUpdate pendingAudioFormat;
  @Nullable private PendingFormatUpdate pendingTextFormat;
  @Nullable private Format currentVideoFormat;
  @Nullable private Format currentAudioFormat;
  @Nullable private Format currentTextFormat;
  private boolean isSeeking;
  private int ioErrorType;
  private boolean hasFatalError;
  private int droppedFrames;
  private int playedFrames;
  private int audioUnderruns;
  private boolean reportedEventsForCurrentSession;

  /**
   * Creates the listener.
   *
   * @param context A {@link Context}.
   */
  private MediaMetricsListener(Context context, PlaybackSession playbackSession) {
    context = context.getApplicationContext();
    this.context = context;
    this.playbackSession = playbackSession;
    window = new Timeline.Window();
    period = new Timeline.Period();
    bandwidthBytes = new HashMap<>();
    bandwidthTimeMs = new HashMap<>();
    startTimeMs = SystemClock.elapsedRealtime();
    currentPlaybackState = PlaybackStateEvent.STATE_NOT_STARTED;
    currentNetworkType = NetworkEvent.NETWORK_TYPE_UNKNOWN;
    sessionManager = new DefaultPlaybackSessionManager();
    sessionManager.setListener(this);
  }

  /** Returns the {@link LogSessionId} used by this listener. */
  public LogSessionId getLogSessionId() {
    return playbackSession.getSessionId();
  }

  // PlaybackSessionManager.Listener implementation.

  @Override
  public void onSessionCreated(EventTime eventTime, String sessionId) {}

  @Override
  public void onSessionActive(EventTime eventTime, String sessionId) {
    if (eventTime.mediaPeriodId != null && eventTime.mediaPeriodId.isAd()) {
      // Ignore ad sessions.
      return;
    }
    finishCurrentSession();
    activeSessionId = sessionId;
    metricsBuilder =
        new PlaybackMetrics.Builder()
            .setPlayerName(ExoPlayerLibraryInfo.TAG)
            .setPlayerVersion(ExoPlayerLibraryInfo.VERSION);
    maybeUpdateTimelineMetadata(eventTime.timeline, eventTime.mediaPeriodId);
  }

  @Override
  public void onAdPlaybackStarted(
      EventTime eventTime, String contentSessionId, String adSessionId) {}

  @Override
  public void onSessionFinished(
      EventTime eventTime, String sessionId, boolean automaticTransitionToNextPlayback) {
    if ((eventTime.mediaPeriodId != null && eventTime.mediaPeriodId.isAd())
        || !sessionId.equals(activeSessionId)) {
      // Ignore ad sessions and other sessions that are finished before becoming active.
    } else {
      finishCurrentSession();
    }
    bandwidthTimeMs.remove(sessionId);
    bandwidthBytes.remove(sessionId);
  }

  // AnalyticsListener implementation.

  @Override
  public void onPositionDiscontinuity(
      EventTime eventTime,
      Player.PositionInfo oldPosition,
      Player.PositionInfo newPosition,
      @Player.DiscontinuityReason int reason) {
    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
      isSeeking = true;
    }
    discontinuityReason = reason;
  }

  @Override
  public void onVideoDisabled(EventTime eventTime, DecoderCounters decoderCounters) {
    // TODO(b/181122234): DecoderCounters are not re-reported at period boundaries.
    droppedFrames += decoderCounters.droppedBufferCount;
    playedFrames += decoderCounters.renderedOutputBufferCount;
  }

  @Override
  public void onBandwidthEstimate(
      EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
    if (eventTime.mediaPeriodId != null) {
      String sessionId =
          sessionManager.getSessionForMediaPeriodId(
              eventTime.timeline, checkNotNull(eventTime.mediaPeriodId));
      @Nullable Long prevBandwidthBytes = bandwidthBytes.get(sessionId);
      @Nullable Long prevBandwidthTimeMs = bandwidthTimeMs.get(sessionId);
      bandwidthBytes.put(
          sessionId, (prevBandwidthBytes == null ? 0 : prevBandwidthBytes) + totalBytesLoaded);
      bandwidthTimeMs.put(
          sessionId, (prevBandwidthTimeMs == null ? 0 : prevBandwidthTimeMs) + totalLoadTimeMs);
    }
  }

  @Override
  public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
    if (eventTime.mediaPeriodId == null) {
      // This event arrived after the media has been removed from the playlist or a custom
      // MediaSource forgot to set the right id. Ignore the track change in these cases.
      return;
    }
    PendingFormatUpdate update =
        new PendingFormatUpdate(
            checkNotNull(mediaLoadData.trackFormat),
            mediaLoadData.trackSelectionReason,
            sessionManager.getSessionForMediaPeriodId(
                eventTime.timeline, checkNotNull(eventTime.mediaPeriodId)));
    switch (mediaLoadData.trackType) {
      case C.TRACK_TYPE_VIDEO:
      case C.TRACK_TYPE_DEFAULT:
        pendingVideoFormat = update;
        break;
      case C.TRACK_TYPE_AUDIO:
        pendingAudioFormat = update;
        break;
      case C.TRACK_TYPE_TEXT:
        pendingTextFormat = update;
        break;
      default:
        // Other track type. Ignore.
    }
  }

  @Override
  public void onVideoSizeChanged(EventTime eventTime, VideoSize videoSize) {
    @Nullable PendingFormatUpdate pendingVideoFormat = this.pendingVideoFormat;
    if (pendingVideoFormat != null && pendingVideoFormat.format.height == Format.NO_VALUE) {
      Format formatWithHeightAndWidth =
          pendingVideoFormat
              .format
              .buildUpon()
              .setWidth(videoSize.width)
              .setHeight(videoSize.height)
              .build();
      this.pendingVideoFormat =
          new PendingFormatUpdate(
              formatWithHeightAndWidth,
              pendingVideoFormat.selectionReason,
              pendingVideoFormat.sessionId);
    }
  }

  @Override
  public void onLoadError(
      EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {
    ioErrorType = mediaLoadData.dataType;
  }

  @Override
  public void onPlayerError(EventTime eventTime, PlaybackException error) {
    pendingPlayerError = error;
  }

  @Override
  public void onEvents(Player player, Events events) {
    if (events.size() == 0) {
      return;
    }
    maybeAddSessions(events);

    long realtimeMs = SystemClock.elapsedRealtime();
    maybeUpdateMetricsBuilderValues(player, events);
    maybeReportPlaybackError(realtimeMs);
    maybeReportTrackChanges(player, events, realtimeMs);
    maybeReportNetworkChange(realtimeMs);
    maybeReportPlaybackStateChange(player, events, realtimeMs);

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

  private void maybeUpdateMetricsBuilderValues(Player player, Events events) {
    if (events.contains(EVENT_TIMELINE_CHANGED)) {
      EventTime eventTime = events.getEventTime(EVENT_TIMELINE_CHANGED);
      if (metricsBuilder != null) {
        maybeUpdateTimelineMetadata(eventTime.timeline, eventTime.mediaPeriodId);
      }
    }
    if (events.contains(EVENT_TRACKS_CHANGED) && metricsBuilder != null) {
      @Nullable DrmInitData drmInitData = getDrmInitData(player.getCurrentTracks().getGroups());
      if (drmInitData != null) {
        castNonNull(metricsBuilder).setDrmType(getDrmType(drmInitData));
      }
    }
    if (events.contains(EVENT_AUDIO_UNDERRUN)) {
      audioUnderruns++;
    }
  }

  private void maybeReportPlaybackError(long realtimeMs) {
    @Nullable PlaybackException error = pendingPlayerError;
    if (error == null) {
      return;
    }
    ErrorInfo errorInfo =
        getErrorInfo(
            error, context, /* lastIoErrorForManifest= */ ioErrorType == C.DATA_TYPE_MANIFEST);
    playbackSession.reportPlaybackErrorEvent(
        new PlaybackErrorEvent.Builder()
            .setTimeSinceCreatedMillis(realtimeMs - startTimeMs)
            .setErrorCode(errorInfo.errorCode)
            .setSubErrorCode(errorInfo.subErrorCode)
            .setException(error)
            .build());
    reportedEventsForCurrentSession = true;
    pendingPlayerError = null;
  }

  private void maybeReportTrackChanges(Player player, Events events, long realtimeMs) {
    if (events.contains(EVENT_TRACKS_CHANGED)) {
      Tracks tracks = player.getCurrentTracks();
      boolean isVideoSelected = tracks.isTypeSelected(C.TRACK_TYPE_VIDEO);
      boolean isAudioSelected = tracks.isTypeSelected(C.TRACK_TYPE_AUDIO);
      boolean isTextSelected = tracks.isTypeSelected(C.TRACK_TYPE_TEXT);
      if (isVideoSelected || isAudioSelected || isTextSelected) {
        // Ignore updates with insufficient information where no tracks are selected.
        if (!isVideoSelected) {
          maybeUpdateVideoFormat(realtimeMs, /* videoFormat= */ null, C.SELECTION_REASON_UNKNOWN);
        }
        if (!isAudioSelected) {
          maybeUpdateAudioFormat(realtimeMs, /* audioFormat= */ null, C.SELECTION_REASON_UNKNOWN);
        }
        if (!isTextSelected) {
          maybeUpdateTextFormat(realtimeMs, /* textFormat= */ null, C.SELECTION_REASON_UNKNOWN);
        }
      }
    }
    if (canReportPendingFormatUpdate(pendingVideoFormat)
        && pendingVideoFormat.format.height != Format.NO_VALUE) {
      maybeUpdateVideoFormat(
          realtimeMs, pendingVideoFormat.format, pendingVideoFormat.selectionReason);
      pendingVideoFormat = null;
    }
    if (canReportPendingFormatUpdate(pendingAudioFormat)) {
      maybeUpdateAudioFormat(
          realtimeMs, pendingAudioFormat.format, pendingAudioFormat.selectionReason);
      pendingAudioFormat = null;
    }
    if (canReportPendingFormatUpdate(pendingTextFormat)) {
      maybeUpdateTextFormat(
          realtimeMs, pendingTextFormat.format, pendingTextFormat.selectionReason);
      pendingTextFormat = null;
    }
  }

  @EnsuresNonNullIf(result = true, expression = "#1")
  private boolean canReportPendingFormatUpdate(@Nullable PendingFormatUpdate pendingFormatUpdate) {
    return pendingFormatUpdate != null
        && pendingFormatUpdate.sessionId.equals(sessionManager.getActiveSessionId());
  }

  private void maybeReportNetworkChange(long realtimeMs) {
    int networkType = getNetworkType(context);
    if (networkType != currentNetworkType) {
      currentNetworkType = networkType;
      playbackSession.reportNetworkEvent(
          new NetworkEvent.Builder()
              .setNetworkType(networkType)
              .setTimeSinceCreatedMillis(realtimeMs - startTimeMs)
              .build());
    }
  }

  private void maybeReportPlaybackStateChange(Player player, Events events, long realtimeMs) {
    if (player.getPlaybackState() != Player.STATE_BUFFERING) {
      isSeeking = false;
    }
    if (player.getPlayerError() == null) {
      hasFatalError = false;
    } else if (events.contains(EVENT_PLAYER_ERROR)) {
      hasFatalError = true;
    }
    int newPlaybackState = resolveNewPlaybackState(player);
    if (currentPlaybackState != newPlaybackState) {
      currentPlaybackState = newPlaybackState;
      reportedEventsForCurrentSession = true;
      playbackSession.reportPlaybackStateEvent(
          new PlaybackStateEvent.Builder()
              .setState(currentPlaybackState)
              .setTimeSinceCreatedMillis(realtimeMs - startTimeMs)
              .build());
    }
  }

  private int resolveNewPlaybackState(Player player) {
    @Player.State int playerPlaybackState = player.getPlaybackState();
    if (isSeeking) {
      // Seeking takes precedence over errors such that we report a seek while in error state.
      return PlaybackStateEvent.STATE_SEEKING;
    } else if (hasFatalError) {
      return PlaybackStateEvent.STATE_FAILED;
    } else if (playerPlaybackState == Player.STATE_ENDED) {
      return PlaybackStateEvent.STATE_ENDED;
    } else if (playerPlaybackState == Player.STATE_BUFFERING) {
      if (currentPlaybackState == PlaybackStateEvent.STATE_NOT_STARTED
          || currentPlaybackState == PlaybackStateEvent.STATE_JOINING_FOREGROUND) {
        return PlaybackStateEvent.STATE_JOINING_FOREGROUND;
      }
      if (!player.getPlayWhenReady()) {
        return PlaybackStateEvent.STATE_PAUSED_BUFFERING;
      }
      return player.getPlaybackSuppressionReason() != Player.PLAYBACK_SUPPRESSION_REASON_NONE
          ? PlaybackStateEvent.STATE_SUPPRESSED_BUFFERING
          : PlaybackStateEvent.STATE_BUFFERING;
    } else if (playerPlaybackState == Player.STATE_READY) {
      if (!player.getPlayWhenReady()) {
        return PlaybackStateEvent.STATE_PAUSED;
      }
      return player.getPlaybackSuppressionReason() != Player.PLAYBACK_SUPPRESSION_REASON_NONE
          ? PlaybackStateEvent.STATE_SUPPRESSED
          : PlaybackStateEvent.STATE_PLAYING;
    } else if (playerPlaybackState == Player.STATE_IDLE
        && currentPlaybackState != PlaybackStateEvent.STATE_NOT_STARTED) {
      // This case only applies for calls to player.stop(). All other IDLE cases are handled by
      // !isForeground, hasFatalError or isSuspended. NOT_STARTED is deliberately ignored.
      return PlaybackStateEvent.STATE_STOPPED;
    }
    return currentPlaybackState;
  }

  private void maybeUpdateVideoFormat(
      long realtimeMs, @Nullable Format videoFormat, @C.SelectionReason int trackSelectionReason) {
    if (Util.areEqual(currentVideoFormat, videoFormat)) {
      return;
    }
    if (currentVideoFormat == null && trackSelectionReason == C.SELECTION_REASON_UNKNOWN) {
      trackSelectionReason = C.SELECTION_REASON_INITIAL;
    }
    currentVideoFormat = videoFormat;
    reportTrackChangeEvent(
        TrackChangeEvent.TRACK_TYPE_VIDEO, realtimeMs, videoFormat, trackSelectionReason);
  }

  private void maybeUpdateAudioFormat(
      long realtimeMs, @Nullable Format audioFormat, @C.SelectionReason int trackSelectionReason) {
    if (Util.areEqual(currentAudioFormat, audioFormat)) {
      return;
    }
    if (currentAudioFormat == null && trackSelectionReason == C.SELECTION_REASON_UNKNOWN) {
      trackSelectionReason = C.SELECTION_REASON_INITIAL;
    }
    currentAudioFormat = audioFormat;
    reportTrackChangeEvent(
        TrackChangeEvent.TRACK_TYPE_AUDIO, realtimeMs, audioFormat, trackSelectionReason);
  }

  private void maybeUpdateTextFormat(
      long realtimeMs, @Nullable Format textFormat, @C.SelectionReason int trackSelectionReason) {
    if (Util.areEqual(currentTextFormat, textFormat)) {
      return;
    }
    if (currentTextFormat == null && trackSelectionReason == C.SELECTION_REASON_UNKNOWN) {
      trackSelectionReason = C.SELECTION_REASON_INITIAL;
    }
    currentTextFormat = textFormat;
    reportTrackChangeEvent(
        TrackChangeEvent.TRACK_TYPE_TEXT, realtimeMs, textFormat, trackSelectionReason);
  }

  private void reportTrackChangeEvent(
      int type,
      long realtimeMs,
      @Nullable Format format,
      @C.SelectionReason int trackSelectionReason) {
    TrackChangeEvent.Builder builder =
        new TrackChangeEvent.Builder(type).setTimeSinceCreatedMillis(realtimeMs - startTimeMs);
    if (format != null) {
      builder.setTrackState(TrackChangeEvent.TRACK_STATE_ON);
      builder.setTrackChangeReason(getTrackChangeReason(trackSelectionReason));
      if (format.containerMimeType != null) {
        // TODO(b/181121074): Progressive container mime type is not filled in by MediaSource.
        builder.setContainerMimeType(format.containerMimeType);
      }
      if (format.sampleMimeType != null) {
        builder.setSampleMimeType(format.sampleMimeType);
      }
      if (format.codecs != null) {
        builder.setCodecName(format.codecs);
      }
      if (format.bitrate != Format.NO_VALUE) {
        builder.setBitrate(format.bitrate);
      }
      if (format.width != Format.NO_VALUE) {
        builder.setWidth(format.width);
      }
      if (format.height != Format.NO_VALUE) {
        builder.setHeight(format.height);
      }
      if (format.channelCount != Format.NO_VALUE) {
        builder.setChannelCount(format.channelCount);
      }
      if (format.sampleRate != Format.NO_VALUE) {
        builder.setAudioSampleRate(format.sampleRate);
      }
      if (format.language != null) {
        Pair<String, @NullableType String> languageAndRegion =
            getLanguageAndRegion(format.language);
        builder.setLanguage(languageAndRegion.first);
        if (languageAndRegion.second != null) {
          builder.setLanguageRegion(languageAndRegion.second);
        }
      }
      if (format.frameRate != Format.NO_VALUE) {
        builder.setVideoFrameRate(format.frameRate);
      }
    } else {
      builder.setTrackState(TrackChangeEvent.TRACK_STATE_OFF);
    }
    reportedEventsForCurrentSession = true;
    playbackSession.reportTrackChangeEvent(builder.build());
  }

  @RequiresNonNull("metricsBuilder")
  private void maybeUpdateTimelineMetadata(
      Timeline timeline, @Nullable MediaSource.MediaPeriodId mediaPeriodId) {
    PlaybackMetrics.Builder metricsBuilder = this.metricsBuilder;
    if (mediaPeriodId == null) {
      return;
    }
    int periodIndex = timeline.getIndexOfPeriod(mediaPeriodId.periodUid);
    if (periodIndex == C.INDEX_UNSET) {
      return;
    }
    timeline.getPeriod(periodIndex, period);
    timeline.getWindow(period.windowIndex, window);
    metricsBuilder.setStreamType(getStreamType(window.mediaItem));
    if (window.durationUs != C.TIME_UNSET
        && !window.isPlaceholder
        && !window.isDynamic
        && !window.isLive()) {
      metricsBuilder.setMediaDurationMillis(window.getDurationMs());
    }
    metricsBuilder.setPlaybackType(
        window.isLive() ? PlaybackMetrics.PLAYBACK_TYPE_LIVE : PlaybackMetrics.PLAYBACK_TYPE_VOD);
    reportedEventsForCurrentSession = true;
  }

  private void finishCurrentSession() {
    if (metricsBuilder != null && reportedEventsForCurrentSession) {
      metricsBuilder.setAudioUnderrunCount(audioUnderruns);
      metricsBuilder.setVideoFramesDropped(droppedFrames);
      metricsBuilder.setVideoFramesPlayed(playedFrames);
      @Nullable Long networkTimeMs = bandwidthTimeMs.get(activeSessionId);
      metricsBuilder.setNetworkTransferDurationMillis(networkTimeMs == null ? 0 : networkTimeMs);
      // TODO(b/181121847): Report localBytesRead. This requires additional callbacks or plumbing.
      @Nullable Long networkBytes = bandwidthBytes.get(activeSessionId);
      metricsBuilder.setNetworkBytesRead(networkBytes == null ? 0 : networkBytes);
      // TODO(b/181121847): Detect stream sources mixed and local depending on localBytesRead.
      metricsBuilder.setStreamSource(
          networkBytes != null && networkBytes > 0
              ? PlaybackMetrics.STREAM_SOURCE_NETWORK
              : PlaybackMetrics.STREAM_SOURCE_UNKNOWN);
      playbackSession.reportPlaybackMetrics(metricsBuilder.build());
    }
    metricsBuilder = null;
    activeSessionId = null;
    audioUnderruns = 0;
    droppedFrames = 0;
    playedFrames = 0;
    currentVideoFormat = null;
    currentAudioFormat = null;
    currentTextFormat = null;
    reportedEventsForCurrentSession = false;
  }

  private static int getTrackChangeReason(@C.SelectionReason int trackSelectionReason) {
    switch (trackSelectionReason) {
      case C.SELECTION_REASON_INITIAL:
        return TrackChangeEvent.TRACK_CHANGE_REASON_INITIAL;
      case C.SELECTION_REASON_ADAPTIVE:
        return TrackChangeEvent.TRACK_CHANGE_REASON_ADAPTIVE;
      case C.SELECTION_REASON_MANUAL:
        return TrackChangeEvent.TRACK_CHANGE_REASON_MANUAL;
      case C.SELECTION_REASON_TRICK_PLAY:
      case C.SELECTION_REASON_UNKNOWN:
      default:
        return TrackChangeEvent.TRACK_CHANGE_REASON_OTHER;
    }
  }

  private static Pair<String, @NullableType String> getLanguageAndRegion(String languageCode) {
    String[] parts = Util.split(languageCode, "-");
    return Pair.create(parts[0], parts.length >= 2 ? parts[1] : null);
  }

  private static int getNetworkType(Context context) {
    switch (NetworkTypeObserver.getInstance(context).getNetworkType()) {
      case C.NETWORK_TYPE_WIFI:
        return NetworkEvent.NETWORK_TYPE_WIFI;
      case C.NETWORK_TYPE_2G:
        return NetworkEvent.NETWORK_TYPE_2G;
      case C.NETWORK_TYPE_3G:
        return NetworkEvent.NETWORK_TYPE_3G;
      case C.NETWORK_TYPE_4G:
        return NetworkEvent.NETWORK_TYPE_4G;
      case C.NETWORK_TYPE_5G_SA:
        return NetworkEvent.NETWORK_TYPE_5G_SA;
      case C.NETWORK_TYPE_5G_NSA:
        return NetworkEvent.NETWORK_TYPE_5G_NSA;
      case C.NETWORK_TYPE_ETHERNET:
        return NetworkEvent.NETWORK_TYPE_ETHERNET;
      case C.NETWORK_TYPE_OFFLINE:
        return NetworkEvent.NETWORK_TYPE_OFFLINE;
      case C.NETWORK_TYPE_UNKNOWN:
        return NetworkEvent.NETWORK_TYPE_UNKNOWN;
      default:
        return NetworkEvent.NETWORK_TYPE_OTHER;
    }
  }

  private static int getStreamType(MediaItem mediaItem) {
    if (mediaItem.localConfiguration == null) {
      return PlaybackMetrics.STREAM_TYPE_UNKNOWN;
    }
    @ContentType
    int contentType =
        Util.inferContentTypeForUriAndMimeType(
            mediaItem.localConfiguration.uri, mediaItem.localConfiguration.mimeType);
    switch (contentType) {
      case C.CONTENT_TYPE_HLS:
        return PlaybackMetrics.STREAM_TYPE_HLS;
      case C.CONTENT_TYPE_DASH:
        return PlaybackMetrics.STREAM_TYPE_DASH;
      case C.CONTENT_TYPE_SS:
        return PlaybackMetrics.STREAM_TYPE_SS;
      case C.CONTENT_TYPE_RTSP:
      default:
        return PlaybackMetrics.STREAM_TYPE_OTHER;
    }
  }

  private static ErrorInfo getErrorInfo(
      PlaybackException error, Context context, boolean lastIoErrorForManifest) {
    if (error.errorCode == PlaybackException.ERROR_CODE_REMOTE_ERROR) {
      return new ErrorInfo(PlaybackErrorEvent.ERROR_PLAYER_REMOTE, /* subErrorCode= */ 0);
    }
    // Unpack the PlaybackException.
    // TODO(b/190203080): Use error codes instead of the Exception's cause where possible.
    boolean isRendererExoPlaybackException = false;
    int rendererFormatSupport = C.FORMAT_UNSUPPORTED_TYPE;
    if (error instanceof ExoPlaybackException) {
      ExoPlaybackException exoPlaybackException = (ExoPlaybackException) error;
      isRendererExoPlaybackException =
          exoPlaybackException.type == ExoPlaybackException.TYPE_RENDERER;
      rendererFormatSupport = exoPlaybackException.rendererFormatSupport;
    }
    Throwable cause = checkNotNull(error.getCause());
    if (cause instanceof IOException) {
      if (cause instanceof HttpDataSource.InvalidResponseCodeException) {
        int responseCode = ((HttpDataSource.InvalidResponseCodeException) cause).responseCode;
        return new ErrorInfo(
            PlaybackErrorEvent.ERROR_IO_BAD_HTTP_STATUS, /* subErrorCode= */ responseCode);
      } else if (cause instanceof HttpDataSource.InvalidContentTypeException
          || cause instanceof ParserException) {
        return new ErrorInfo(
            lastIoErrorForManifest
                ? PlaybackErrorEvent.ERROR_PARSING_MANIFEST_MALFORMED
                : PlaybackErrorEvent.ERROR_PARSING_CONTAINER_MALFORMED,
            /* subErrorCode= */ 0);
      } else if (cause instanceof HttpDataSource.HttpDataSourceException
          || cause instanceof UdpDataSource.UdpDataSourceException) {
        if (NetworkTypeObserver.getInstance(context).getNetworkType() == C.NETWORK_TYPE_OFFLINE) {
          return new ErrorInfo(
              PlaybackErrorEvent.ERROR_IO_NETWORK_UNAVAILABLE, /* subErrorCode= */ 0);
        } else {
          @Nullable Throwable detailedCause = cause.getCause();
          if (detailedCause instanceof UnknownHostException) {
            return new ErrorInfo(PlaybackErrorEvent.ERROR_IO_DNS_FAILED, /* subErrorCode= */ 0);
          } else if (detailedCause instanceof SocketTimeoutException) {
            return new ErrorInfo(
                PlaybackErrorEvent.ERROR_IO_CONNECTION_TIMEOUT, /* subErrorCode= */ 0);
          } else if (cause instanceof HttpDataSource.HttpDataSourceException
              && ((HttpDataSource.HttpDataSourceException) cause).type
                  == HttpDataSource.HttpDataSourceException.TYPE_OPEN) {
            return new ErrorInfo(
                PlaybackErrorEvent.ERROR_IO_NETWORK_CONNECTION_FAILED, /* subErrorCode= */ 0);
          } else {
            return new ErrorInfo(
                PlaybackErrorEvent.ERROR_IO_CONNECTION_CLOSED, /* subErrorCode= */ 0);
          }
        }
      } else if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
        return new ErrorInfo(
            PlaybackErrorEvent.ERROR_PLAYER_BEHIND_LIVE_WINDOW, /* subErrorCode= */ 0);
      } else if (cause instanceof DrmSession.DrmSessionException) {
        // Unpack DrmSessionException.
        cause = checkNotNull(cause.getCause());
        if (Util.SDK_INT >= 21 && cause instanceof MediaDrm.MediaDrmStateException) {
          String diagnosticsInfo = ((MediaDrm.MediaDrmStateException) cause).getDiagnosticInfo();
          int subErrorCode = Util.getErrorCodeFromPlatformDiagnosticsInfo(diagnosticsInfo);
          int errorCode = getDrmErrorCode(subErrorCode);
          return new ErrorInfo(errorCode, subErrorCode);
        } else if (Util.SDK_INT >= 23 && cause instanceof MediaDrmResetException) {
          return new ErrorInfo(PlaybackErrorEvent.ERROR_DRM_SYSTEM_ERROR, /* subErrorCode= */ 0);
        } else if (Util.SDK_INT >= 18 && cause instanceof NotProvisionedException) {
          return new ErrorInfo(
              PlaybackErrorEvent.ERROR_DRM_PROVISIONING_FAILED, /* subErrorCode= */ 0);
        } else if (Util.SDK_INT >= 18 && cause instanceof DeniedByServerException) {
          return new ErrorInfo(PlaybackErrorEvent.ERROR_DRM_DEVICE_REVOKED, /* subErrorCode= */ 0);
        } else if (cause instanceof UnsupportedDrmException) {
          return new ErrorInfo(
              PlaybackErrorEvent.ERROR_DRM_SCHEME_UNSUPPORTED, /* subErrorCode= */ 0);
        } else if (cause instanceof DefaultDrmSessionManager.MissingSchemeDataException) {
          return new ErrorInfo(PlaybackErrorEvent.ERROR_DRM_CONTENT_ERROR, /* subErrorCode= */ 0);
        } else {
          return new ErrorInfo(PlaybackErrorEvent.ERROR_DRM_OTHER, /* subErrorCode= */ 0);
        }
      } else if (cause instanceof FileDataSource.FileDataSourceException
          && cause.getCause() instanceof FileNotFoundException) {
        @Nullable Throwable notFoundCause = checkNotNull(cause.getCause()).getCause();
        if (Util.SDK_INT >= 21
            && notFoundCause instanceof ErrnoException
            && ((ErrnoException) notFoundCause).errno == OsConstants.EACCES) {
          return new ErrorInfo(PlaybackErrorEvent.ERROR_IO_NO_PERMISSION, /* subErrorCode= */ 0);
        } else {
          return new ErrorInfo(PlaybackErrorEvent.ERROR_IO_FILE_NOT_FOUND, /* subErrorCode= */ 0);
        }
      } else {
        return new ErrorInfo(PlaybackErrorEvent.ERROR_IO_OTHER, /* subErrorCode= */ 0);
      }
    } else if (isRendererExoPlaybackException
        && (rendererFormatSupport == C.FORMAT_UNSUPPORTED_TYPE
            || rendererFormatSupport == C.FORMAT_UNSUPPORTED_SUBTYPE)) {
      return new ErrorInfo(
          PlaybackErrorEvent.ERROR_DECODING_FORMAT_UNSUPPORTED, /* subErrorCode= */ 0);
    } else if (isRendererExoPlaybackException
        && rendererFormatSupport == C.FORMAT_EXCEEDS_CAPABILITIES) {
      return new ErrorInfo(
          PlaybackErrorEvent.ERROR_DECODING_FORMAT_EXCEEDS_CAPABILITIES, /* subErrorCode= */ 0);
    } else if (isRendererExoPlaybackException
        && rendererFormatSupport == C.FORMAT_UNSUPPORTED_DRM) {
      return new ErrorInfo(PlaybackErrorEvent.ERROR_DRM_SCHEME_UNSUPPORTED, /* subErrorCode= */ 0);
    } else if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
      @Nullable
      String diagnosticsInfo =
          ((MediaCodecRenderer.DecoderInitializationException) cause).diagnosticInfo;
      int subErrorCode = Util.getErrorCodeFromPlatformDiagnosticsInfo(diagnosticsInfo);
      return new ErrorInfo(PlaybackErrorEvent.ERROR_DECODER_INIT_FAILED, subErrorCode);
    } else if (cause instanceof MediaCodecDecoderException) {
      @Nullable String diagnosticsInfo = ((MediaCodecDecoderException) cause).diagnosticInfo;
      int subErrorCode = Util.getErrorCodeFromPlatformDiagnosticsInfo(diagnosticsInfo);
      return new ErrorInfo(PlaybackErrorEvent.ERROR_DECODING_FAILED, subErrorCode);
    } else if (cause instanceof OutOfMemoryError) {
      return new ErrorInfo(PlaybackErrorEvent.ERROR_DECODING_FAILED, /* subErrorCode= */ 0);
    } else if (cause instanceof AudioSink.InitializationException) {
      int subErrorCode = ((AudioSink.InitializationException) cause).audioTrackState;
      return new ErrorInfo(PlaybackErrorEvent.ERROR_AUDIO_TRACK_INIT_FAILED, subErrorCode);
    } else if (cause instanceof AudioSink.WriteException) {
      int subErrorCode = ((AudioSink.WriteException) cause).errorCode;
      return new ErrorInfo(PlaybackErrorEvent.ERROR_AUDIO_TRACK_WRITE_FAILED, subErrorCode);
    } else if (Util.SDK_INT >= 16 && cause instanceof MediaCodec.CryptoException) {
      int subErrorCode = ((MediaCodec.CryptoException) cause).getErrorCode();
      int errorCode = getDrmErrorCode(subErrorCode);
      return new ErrorInfo(errorCode, subErrorCode);
    } else {
      return new ErrorInfo(PlaybackErrorEvent.ERROR_PLAYER_OTHER, /* subErrorCode= */ 0);
    }
  }

  @Nullable
  private static DrmInitData getDrmInitData(ImmutableList<Tracks.Group> trackGroups) {
    for (Tracks.Group trackGroup : trackGroups) {
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (trackGroup.isTrackSelected(trackIndex)) {
          @Nullable DrmInitData drmInitData = trackGroup.getTrackFormat(trackIndex).drmInitData;
          if (drmInitData != null) {
            return drmInitData;
          }
        }
      }
    }
    return null;
  }

  private static int getDrmType(DrmInitData drmInitData) {
    for (int i = 0; i < drmInitData.schemeDataCount; i++) {
      UUID uuid = drmInitData.get(i).uuid;
      if (uuid.equals(C.WIDEVINE_UUID)) {
        // TODO(b/77625596): Forward MediaDrm metrics to distinguish between L1 and L3 and to set
        //  the drm session id.
        return PlaybackMetrics.DRM_TYPE_WIDEVINE_L1;
      }
      if (uuid.equals(C.PLAYREADY_UUID)) {
        return PlaybackMetrics.DRM_TYPE_PLAY_READY;
      }
      if (uuid.equals(C.CLEARKEY_UUID)) {
        return PlaybackMetrics.DRM_TYPE_CLEARKEY;
      }
    }
    return PlaybackMetrics.DRM_TYPE_OTHER;
  }

  @SuppressLint("SwitchIntDef") // Only DRM error codes are relevant here.
  private static int getDrmErrorCode(int mediaDrmErrorCode) {
    switch (Util.getErrorCodeForMediaDrmErrorCode(mediaDrmErrorCode)) {
      case PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED:
        return PlaybackErrorEvent.ERROR_DRM_PROVISIONING_FAILED;
      case PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED:
        return PlaybackErrorEvent.ERROR_DRM_LICENSE_ACQUISITION_FAILED;
      case PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION:
        return PlaybackErrorEvent.ERROR_DRM_DISALLOWED_OPERATION;
      case PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR:
        return PlaybackErrorEvent.ERROR_DRM_CONTENT_ERROR;
      case PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR:
      default:
        return PlaybackErrorEvent.ERROR_DRM_SYSTEM_ERROR;
    }
  }

  private static final class ErrorInfo {

    public final int errorCode;
    public final int subErrorCode;

    public ErrorInfo(int errorCode, int subErrorCode) {
      this.errorCode = errorCode;
      this.subErrorCode = subErrorCode;
    }
  }

  private static final class PendingFormatUpdate {

    public final Format format;
    public final @C.SelectionReason int selectionReason;
    public final String sessionId;

    public PendingFormatUpdate(
        Format format, @C.SelectionReason int selectionReason, String sessionId) {
      this.format = format;
      this.selectionReason = selectionReason;
      this.sessionId = sessionId;
    }
  }
}
