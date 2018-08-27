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
package com.google.android.exoplayer2.analytics;

import android.graphics.SurfaceTexture;
import android.support.annotation.Nullable;
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DefaultDrmSessionEventListener;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Data collector which is able to forward analytics events to {@link AnalyticsListener}s by
 * listening to all available ExoPlayer listeners.
 */
public class AnalyticsCollector
    implements Player.EventListener,
        MetadataOutput,
        AudioRendererEventListener,
        VideoRendererEventListener,
        MediaSourceEventListener,
        BandwidthMeter.EventListener,
        DefaultDrmSessionEventListener,
        VideoListener,
        AudioListener {

  /** Factory for an analytics collector. */
  public static class Factory {

    /**
     * Creates an analytics collector for the specified player.
     *
     * @param player The {@link Player} for which data will be collected. Can be null, if the player
     *     is set by calling {@link AnalyticsCollector#setPlayer(Player)} before using the analytics
     *     collector.
     * @param clock A {@link Clock} used to generate timestamps.
     * @return An analytics collector.
     */
    public AnalyticsCollector createAnalyticsCollector(@Nullable Player player, Clock clock) {
      return new AnalyticsCollector(player, clock);
    }
  }

  private final CopyOnWriteArraySet<AnalyticsListener> listeners;
  private final Clock clock;
  private final Window window;
  private final MediaPeriodQueueTracker mediaPeriodQueueTracker;

  private @MonotonicNonNull Player player;

  /**
   * Creates an analytics collector for the specified player.
   *
   * @param player The {@link Player} for which data will be collected. Can be null, if the player
   *     is set by calling {@link AnalyticsCollector#setPlayer(Player)} before using the analytics
   *     collector.
   * @param clock A {@link Clock} used to generate timestamps.
   */
  protected AnalyticsCollector(@Nullable Player player, Clock clock) {
    this.player = player;
    this.clock = Assertions.checkNotNull(clock);
    listeners = new CopyOnWriteArraySet<>();
    mediaPeriodQueueTracker = new MediaPeriodQueueTracker();
    window = new Window();
  }

  /**
   * Adds a listener for analytics events.
   *
   * @param listener The listener to add.
   */
  public void addListener(AnalyticsListener listener) {
    listeners.add(listener);
  }

  /**
   * Removes a previously added analytics event listener.
   *
   * @param listener The listener to remove.
   */
  public void removeListener(AnalyticsListener listener) {
    listeners.remove(listener);
  }

  /**
   * Sets the player for which data will be collected. Must only be called if no player has been set
   * yet.
   *
   * @param player The {@link Player} for which data will be collected.
   */
  public void setPlayer(Player player) {
    Assertions.checkState(this.player == null);
    this.player = Assertions.checkNotNull(player);
  }

  // External events.

  /**
   * Notify analytics collector that a seek operation will start. Should be called before the player
   * adjusts its state and position to the seek.
   */
  public final void notifySeekStarted() {
    if (!mediaPeriodQueueTracker.isSeeking()) {
      EventTime eventTime = generatePlayingMediaPeriodEventTime();
      mediaPeriodQueueTracker.onSeekStarted();
      for (AnalyticsListener listener : listeners) {
        listener.onSeekStarted(eventTime);
      }
    }
  }

  /**
   * Resets the analytics collector for a new media source. Should be called before the player is
   * prepared with a new media source.
   */
  public final void resetForNewMediaSource() {
    // Copying the list is needed because onMediaPeriodReleased will modify the list.
    List<WindowAndMediaPeriodId> activeMediaPeriods =
        new ArrayList<>(mediaPeriodQueueTracker.activeMediaPeriods);
    for (WindowAndMediaPeriodId mediaPeriod : activeMediaPeriods) {
      onMediaPeriodReleased(mediaPeriod.windowIndex, mediaPeriod.mediaPeriodId);
    }
  }

  // MetadataOutput implementation.

  @Override
  public final void onMetadata(Metadata metadata) {
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onMetadata(eventTime, metadata);
    }
  }

  // AudioRendererEventListener implementation.

  @Override
  public final void onAudioEnabled(DecoderCounters counters) {
    // The renderers are only enabled after we changed the playing media period.
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderEnabled(eventTime, C.TRACK_TYPE_AUDIO, counters);
    }
  }

  @Override
  public final void onAudioDecoderInitialized(
      String decoderName, long initializedTimestampMs, long initializationDurationMs) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderInitialized(
          eventTime, C.TRACK_TYPE_AUDIO, decoderName, initializationDurationMs);
    }
  }

  @Override
  public final void onAudioInputFormatChanged(Format format) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderInputFormatChanged(eventTime, C.TRACK_TYPE_AUDIO, format);
    }
  }

  @Override
  public final void onAudioSinkUnderrun(
      int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onAudioUnderrun(eventTime, bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
    }
  }

  @Override
  public final void onAudioDisabled(DecoderCounters counters) {
    // The renderers are disabled after we changed the playing media period on the playback thread
    // but before this change is reported to the app thread.
    EventTime eventTime = generateLastReportedPlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderDisabled(eventTime, C.TRACK_TYPE_AUDIO, counters);
    }
  }

  // AudioListener implementation.

  @Override
  public final void onAudioSessionId(int audioSessionId) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onAudioSessionId(eventTime, audioSessionId);
    }
  }

  @Override
  public void onAudioAttributesChanged(AudioAttributes audioAttributes) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onAudioAttributesChanged(eventTime, audioAttributes);
    }
  }

  @Override
  public void onVolumeChanged(float audioVolume) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onVolumeChanged(eventTime, audioVolume);
    }
  }

  // VideoRendererEventListener implementation.

  @Override
  public final void onVideoEnabled(DecoderCounters counters) {
    // The renderers are only enabled after we changed the playing media period.
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderEnabled(eventTime, C.TRACK_TYPE_VIDEO, counters);
    }
  }

  @Override
  public final void onVideoDecoderInitialized(
      String decoderName, long initializedTimestampMs, long initializationDurationMs) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderInitialized(
          eventTime, C.TRACK_TYPE_VIDEO, decoderName, initializationDurationMs);
    }
  }

  @Override
  public final void onVideoInputFormatChanged(Format format) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderInputFormatChanged(eventTime, C.TRACK_TYPE_VIDEO, format);
    }
  }

  @Override
  public final void onDroppedFrames(int count, long elapsedMs) {
    EventTime eventTime = generateLastReportedPlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDroppedVideoFrames(eventTime, count, elapsedMs);
    }
  }

  @Override
  public final void onVideoDisabled(DecoderCounters counters) {
    // The renderers are disabled after we changed the playing media period on the playback thread
    // but before this change is reported to the app thread.
    EventTime eventTime = generateLastReportedPlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderDisabled(eventTime, C.TRACK_TYPE_VIDEO, counters);
    }
  }

  @Override
  public final void onRenderedFirstFrame(Surface surface) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onRenderedFirstFrame(eventTime, surface);
    }
  }

  // VideoListener implementation.

  @Override
  public final void onVideoSizeChanged(
      int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onVideoSizeChanged(
          eventTime, width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
    }
  }

  @Override
  public void onSurfaceSizeChanged(int width, int height) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onSurfaceSizeChanged(eventTime, width, height);
    }
  }

  @Override
  public final void onRenderedFirstFrame() {
    // Do nothing. Already reported in VideoRendererEventListener.onRenderedFirstFrame.
  }

  // MediaSourceEventListener implementation.

  @Override
  public final void onMediaPeriodCreated(int windowIndex, MediaPeriodId mediaPeriodId) {
    mediaPeriodQueueTracker.onMediaPeriodCreated(windowIndex, mediaPeriodId);
    EventTime eventTime = generateEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onMediaPeriodCreated(eventTime);
    }
  }

  @Override
  public final void onMediaPeriodReleased(int windowIndex, MediaPeriodId mediaPeriodId) {
    mediaPeriodQueueTracker.onMediaPeriodReleased(windowIndex, mediaPeriodId);
    EventTime eventTime = generateEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onMediaPeriodReleased(eventTime);
    }
  }

  @Override
  public final void onLoadStarted(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    EventTime eventTime = generateEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onLoadStarted(eventTime, loadEventInfo, mediaLoadData);
    }
  }

  @Override
  public final void onLoadCompleted(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    EventTime eventTime = generateEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onLoadCompleted(eventTime, loadEventInfo, mediaLoadData);
    }
  }

  @Override
  public final void onLoadCanceled(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    EventTime eventTime = generateEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onLoadCanceled(eventTime, loadEventInfo, mediaLoadData);
    }
  }

  @Override
  public final void onLoadError(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {
    EventTime eventTime = generateEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onLoadError(eventTime, loadEventInfo, mediaLoadData, error, wasCanceled);
    }
  }

  @Override
  public final void onReadingStarted(int windowIndex, MediaPeriodId mediaPeriodId) {
    mediaPeriodQueueTracker.onReadingStarted(windowIndex, mediaPeriodId);
    EventTime eventTime = generateEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onReadingStarted(eventTime);
    }
  }

  @Override
  public final void onUpstreamDiscarded(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
    EventTime eventTime = generateEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onUpstreamDiscarded(eventTime, mediaLoadData);
    }
  }

  @Override
  public final void onDownstreamFormatChanged(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
    EventTime eventTime = generateEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onDownstreamFormatChanged(eventTime, mediaLoadData);
    }
  }

  // Player.EventListener implementation.

  // TODO: Add onFinishedReportingChanges to Player.EventListener to know when a set of simultaneous
  // callbacks finished. This helps to assign exactly the same EventTime to all of them instead of
  // having slightly different real times.

  @Override
  public final void onTimelineChanged(
      Timeline timeline, Object manifest, @Player.TimelineChangeReason int reason) {
    mediaPeriodQueueTracker.onTimelineChanged(timeline);
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onTimelineChanged(eventTime, reason);
    }
  }

  @Override
  public final void onTracksChanged(
      TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onTracksChanged(eventTime, trackGroups, trackSelections);
    }
  }

  @Override
  public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
    return false;
  }

  @Override
  public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

  }

  @Override
  public final void onLoadingChanged(boolean isLoading) {
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onLoadingChanged(eventTime, isLoading);
    }
  }

  @Override
  public final void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onPlayerStateChanged(eventTime, playWhenReady, playbackState);
    }
  }

  @Override
  public final void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onRepeatModeChanged(eventTime, repeatMode);
    }
  }

  @Override
  public final void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onShuffleModeChanged(eventTime, shuffleModeEnabled);
    }
  }

  @Override
  public final void onPlayerError(ExoPlaybackException error) {
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onPlayerError(eventTime, error);
    }
  }

  @Override
  public final void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
    mediaPeriodQueueTracker.onPositionDiscontinuity(reason);
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onPositionDiscontinuity(eventTime, reason);
    }
  }

  @Override
  public final void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onPlaybackParametersChanged(eventTime, playbackParameters);
    }
  }

  @Override
  public final void onSeekProcessed() {
    if (mediaPeriodQueueTracker.isSeeking()) {
      mediaPeriodQueueTracker.onSeekProcessed();
      EventTime eventTime = generatePlayingMediaPeriodEventTime();
      for (AnalyticsListener listener : listeners) {
        listener.onSeekProcessed(eventTime);
      }
    }
  }

  // BandwidthMeter.Listener implementation.

  @Override
  public final void onBandwidthSample(int elapsedMs, long bytes, long bitrate) {
    EventTime eventTime = generateLoadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onBandwidthEstimate(eventTime, elapsedMs, bytes, bitrate);
    }
  }

  // DefaultDrmSessionManager.EventListener implementation.

  @Override
  public final void onDrmKeysLoaded() {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDrmKeysLoaded(eventTime);
    }
  }

  @Override
  public final void onDrmSessionManagerError(Exception error) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDrmSessionManagerError(eventTime, error);
    }
  }

  @Override
  public final void onDrmKeysRestored() {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDrmKeysRestored(eventTime);
    }
  }

  @Override
  public final void onDrmKeysRemoved() {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDrmKeysRemoved(eventTime);
    }
  }

  // Internal methods.

  /** Returns read-only set of registered listeners. */
  protected Set<AnalyticsListener> getListeners() {
    return Collections.unmodifiableSet(listeners);
  }

  /** Returns a new {@link EventTime} for the specified window index and media period id. */
  protected EventTime generateEventTime(int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
    Assertions.checkNotNull(player);
    long realtimeMs = clock.elapsedRealtime();
    Timeline timeline = player.getCurrentTimeline();
    long eventPositionMs;
    if (windowIndex == player.getCurrentWindowIndex()) {
      if (mediaPeriodId != null && mediaPeriodId.isAd()) {
        // This event is for an ad in the currently playing window.
        eventPositionMs =
            player.getCurrentAdGroupIndex() == mediaPeriodId.adGroupIndex
                    && player.getCurrentAdIndexInAdGroup() == mediaPeriodId.adIndexInAdGroup
                ? player.getCurrentPosition()
                : 0 /* Assume start position of 0 for a future ad. */;
      } else {
        // This event is for content in the currently playing window.
        eventPositionMs = player.getContentPosition();
      }
    } else if (windowIndex >= timeline.getWindowCount()
        || (mediaPeriodId != null && mediaPeriodId.isAd())) {
      // This event is for an unknown future window or for an ad in a future window.
      // Assume start position of zero.
      eventPositionMs = 0;
    } else {
      // This event is for content in a future window. Assume default start position.
      eventPositionMs = timeline.getWindow(windowIndex, window).getDefaultPositionMs();
    }
    return new EventTime(
        realtimeMs,
        timeline,
        windowIndex,
        mediaPeriodId,
        eventPositionMs,
        player.getCurrentPosition(),
        player.getTotalBufferedDuration());
  }

  private EventTime generateEventTime(@Nullable WindowAndMediaPeriodId mediaPeriod) {
    if (mediaPeriod == null) {
      int windowIndex = Assertions.checkNotNull(player).getCurrentWindowIndex();
      MediaPeriodId mediaPeriodId = mediaPeriodQueueTracker.tryResolveWindowIndex(windowIndex);
      return generateEventTime(windowIndex, mediaPeriodId);
    }
    return generateEventTime(mediaPeriod.windowIndex, mediaPeriod.mediaPeriodId);
  }

  private EventTime generateLastReportedPlayingMediaPeriodEventTime() {
    return generateEventTime(mediaPeriodQueueTracker.getLastReportedPlayingMediaPeriod());
  }

  private EventTime generatePlayingMediaPeriodEventTime() {
    return generateEventTime(mediaPeriodQueueTracker.getPlayingMediaPeriod());
  }

  private EventTime generateReadingMediaPeriodEventTime() {
    return generateEventTime(mediaPeriodQueueTracker.getReadingMediaPeriod());
  }

  private EventTime generateLoadingMediaPeriodEventTime() {
    return generateEventTime(mediaPeriodQueueTracker.getLoadingMediaPeriod());
  }

  /** Keeps track of the active media periods and currently playing and reading media period. */
  private static final class MediaPeriodQueueTracker {

    // TODO: Investigate reporting MediaPeriodId in renderer events and adding a listener of queue
    // changes, which would hopefully remove the need to track the queue here.

    private final ArrayList<WindowAndMediaPeriodId> activeMediaPeriods;
    private final Period period;

    private WindowAndMediaPeriodId lastReportedPlayingMediaPeriod;
    private WindowAndMediaPeriodId readingMediaPeriod;
    private Timeline timeline;
    private boolean isSeeking;

    public MediaPeriodQueueTracker() {
      activeMediaPeriods = new ArrayList<>();
      period = new Period();
      timeline = Timeline.EMPTY;
    }

    /**
     * Returns the {@link WindowAndMediaPeriodId} of the media period in the front of the queue.
     * This is the playing media period unless the player hasn't started playing yet (in which case
     * it is the loading media period or null). While the player is seeking or preparing, this
     * method will always return null to reflect the uncertainty about the current playing period.
     * May also be null, if the timeline is empty or no media period is active yet.
     */
    public @Nullable WindowAndMediaPeriodId getPlayingMediaPeriod() {
      return activeMediaPeriods.isEmpty() || timeline.isEmpty() || isSeeking
          ? null
          : activeMediaPeriods.get(0);
    }

    /**
     * Returns the {@link WindowAndMediaPeriodId} of the currently playing media period. This is the
     * publicly reported period which should always match {@link Player#getCurrentPeriodIndex()}
     * unless the player is currently seeking or being prepared in which case the previous period is
     * reported until the seek or preparation is processed. May be null, if no media period is
     * active yet.
     */
    public @Nullable WindowAndMediaPeriodId getLastReportedPlayingMediaPeriod() {
      return lastReportedPlayingMediaPeriod;
    }

    /**
     * Returns the {@link WindowAndMediaPeriodId} of the media period currently being read by the
     * player. May be null, if the player is not reading a media period.
     */
    public @Nullable WindowAndMediaPeriodId getReadingMediaPeriod() {
      return readingMediaPeriod;
    }

    /**
     * Returns the {@link MediaPeriodId} of the media period at the end of the queue which is
     * currently loading or will be the next one loading. May be null, if no media period is active
     * yet.
     */
    public @Nullable WindowAndMediaPeriodId getLoadingMediaPeriod() {
      return activeMediaPeriods.isEmpty()
          ? null
          : activeMediaPeriods.get(activeMediaPeriods.size() - 1);
    }

    /** Returns whether the player is currently seeking. */
    public boolean isSeeking() {
      return isSeeking;
    }

    /**
     * Tries to find an existing media period id from the specified window index. Only returns a
     * non-null media period id if there is a unique, unambiguous match.
     */
    public @Nullable MediaPeriodId tryResolveWindowIndex(int windowIndex) {
      MediaPeriodId match = null;
      if (timeline != null) {
        int timelinePeriodCount = timeline.getPeriodCount();
        for (int i = 0; i < activeMediaPeriods.size(); i++) {
          WindowAndMediaPeriodId mediaPeriod = activeMediaPeriods.get(i);
          int periodIndex = mediaPeriod.mediaPeriodId.periodIndex;
          if (periodIndex < timelinePeriodCount
              && timeline.getPeriod(periodIndex, period).windowIndex == windowIndex) {
            if (match != null) {
              // Ambiguous match.
              return null;
            }
            match = mediaPeriod.mediaPeriodId;
          }
        }
      }
      return match;
    }

    /** Updates the queue with a reported position discontinuity . */
    public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
      updateLastReportedPlayingMediaPeriod();
    }

    /** Updates the queue with a reported timeline change. */
    public void onTimelineChanged(Timeline timeline) {
      for (int i = 0; i < activeMediaPeriods.size(); i++) {
        activeMediaPeriods.set(
            i, updateMediaPeriodToNewTimeline(activeMediaPeriods.get(i), timeline));
      }
      if (readingMediaPeriod != null) {
        readingMediaPeriod = updateMediaPeriodToNewTimeline(readingMediaPeriod, timeline);
      }
      this.timeline = timeline;
      updateLastReportedPlayingMediaPeriod();
    }

    /** Updates the queue with a reported start of seek. */
    public void onSeekStarted() {
      isSeeking = true;
    }

    /** Updates the queue with a reported processed seek. */
    public void onSeekProcessed() {
      isSeeking = false;
      updateLastReportedPlayingMediaPeriod();
    }

    /** Updates the queue with a newly created media period. */
    public void onMediaPeriodCreated(int windowIndex, MediaPeriodId mediaPeriodId) {
      activeMediaPeriods.add(new WindowAndMediaPeriodId(windowIndex, mediaPeriodId));
      if (activeMediaPeriods.size() == 1 && !timeline.isEmpty()) {
        updateLastReportedPlayingMediaPeriod();
      }
    }

    /** Updates the queue with a released media period. */
    public void onMediaPeriodReleased(int windowIndex, MediaPeriodId mediaPeriodId) {
      WindowAndMediaPeriodId mediaPeriod = new WindowAndMediaPeriodId(windowIndex, mediaPeriodId);
      activeMediaPeriods.remove(mediaPeriod);
      if (mediaPeriod.equals(readingMediaPeriod)) {
        readingMediaPeriod = activeMediaPeriods.isEmpty() ? null : activeMediaPeriods.get(0);
      }
    }

    /** Update the queue with a change in the reading media period. */
    public void onReadingStarted(int windowIndex, MediaPeriodId mediaPeriodId) {
      readingMediaPeriod = new WindowAndMediaPeriodId(windowIndex, mediaPeriodId);
    }

    private void updateLastReportedPlayingMediaPeriod() {
      if (!activeMediaPeriods.isEmpty()) {
        lastReportedPlayingMediaPeriod = activeMediaPeriods.get(0);
      }
    }

    private WindowAndMediaPeriodId updateMediaPeriodToNewTimeline(
        WindowAndMediaPeriodId mediaPeriod, Timeline newTimeline) {
      if (newTimeline.isEmpty() || timeline.isEmpty()) {
        return mediaPeriod;
      }
      Object uid = timeline.getUidOfPeriod(mediaPeriod.mediaPeriodId.periodIndex);
      int newPeriodIndex = newTimeline.getIndexOfPeriod(uid);
      if (newPeriodIndex == C.INDEX_UNSET) {
        return mediaPeriod;
      }
      int newWindowIndex = newTimeline.getPeriod(newPeriodIndex, period).windowIndex;
      return new WindowAndMediaPeriodId(
          newWindowIndex, mediaPeriod.mediaPeriodId.copyWithPeriodIndex(newPeriodIndex));
    }
  }

  private static final class WindowAndMediaPeriodId {

    public final int windowIndex;
    public final MediaPeriodId mediaPeriodId;

    public WindowAndMediaPeriodId(int windowIndex, MediaPeriodId mediaPeriodId) {
      this.windowIndex = windowIndex;
      this.mediaPeriodId = mediaPeriodId;
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      WindowAndMediaPeriodId that = (WindowAndMediaPeriodId) other;
      return windowIndex == that.windowIndex && mediaPeriodId.equals(that.mediaPeriodId);
    }

    @Override
    public int hashCode() {
      return 31 * windowIndex + mediaPeriodId.hashCode();
    }
  }
}
