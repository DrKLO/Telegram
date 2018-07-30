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
package org.telegram.messenger.exoplayer2.analytics;

import android.net.NetworkInfo;
import android.view.Surface;
import org.telegram.messenger.exoplayer2.ExoPlaybackException;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.PlaybackParameters;
import org.telegram.messenger.exoplayer2.decoder.DecoderCounters;
import org.telegram.messenger.exoplayer2.metadata.Metadata;
import org.telegram.messenger.exoplayer2.source.MediaSourceEventListener.LoadEventInfo;
import org.telegram.messenger.exoplayer2.source.MediaSourceEventListener.MediaLoadData;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelectionArray;
import java.io.IOException;

/**
 * {@link AnalyticsListener} allowing selective overrides. All methods are implemented as no-ops.
 */
public abstract class DefaultAnalyticsListener implements AnalyticsListener {

  @Override
  public void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int playbackState) {}

  @Override
  public void onTimelineChanged(EventTime eventTime, int reason) {}

  @Override
  public void onPositionDiscontinuity(EventTime eventTime, int reason) {}

  @Override
  public void onSeekStarted(EventTime eventTime) {}

  @Override
  public void onSeekProcessed(EventTime eventTime) {}

  @Override
  public void onPlaybackParametersChanged(
      EventTime eventTime, PlaybackParameters playbackParameters) {}

  @Override
  public void onRepeatModeChanged(EventTime eventTime, int repeatMode) {}

  @Override
  public void onShuffleModeChanged(EventTime eventTime, boolean shuffleModeEnabled) {}

  @Override
  public void onLoadingChanged(EventTime eventTime, boolean isLoading) {}

  @Override
  public void onPlayerError(EventTime eventTime, ExoPlaybackException error) {}

  @Override
  public void onTracksChanged(
      EventTime eventTime, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {}

  @Override
  public void onLoadStarted(
      EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {}

  @Override
  public void onLoadCompleted(
      EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {}

  @Override
  public void onLoadCanceled(
      EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {}

  @Override
  public void onLoadError(
      EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {}

  @Override
  public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {}

  @Override
  public void onUpstreamDiscarded(EventTime eventTime, MediaLoadData mediaLoadData) {}

  @Override
  public void onMediaPeriodCreated(EventTime eventTime) {}

  @Override
  public void onMediaPeriodReleased(EventTime eventTime) {}

  @Override
  public void onReadingStarted(EventTime eventTime) {}

  @Override
  public void onBandwidthEstimate(
      EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {}

  @Override
  public void onViewportSizeChange(EventTime eventTime, int width, int height) {}

  @Override
  public void onNetworkTypeChanged(EventTime eventTime, NetworkInfo networkInfo) {}

  @Override
  public void onMetadata(EventTime eventTime, Metadata metadata) {}

  @Override
  public void onDecoderEnabled(
      EventTime eventTime, int trackType, DecoderCounters decoderCounters) {}

  @Override
  public void onDecoderInitialized(
      EventTime eventTime, int trackType, String decoderName, long initializationDurationMs) {}

  @Override
  public void onDecoderInputFormatChanged(EventTime eventTime, int trackType, Format format) {}

  @Override
  public void onDecoderDisabled(
      EventTime eventTime, int trackType, DecoderCounters decoderCounters) {}

  @Override
  public void onAudioSessionId(EventTime eventTime, int audioSessionId) {}

  @Override
  public void onAudioUnderrun(
      EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {}

  @Override
  public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {}

  @Override
  public void onVideoSizeChanged(
      EventTime eventTime,
      int width,
      int height,
      int unappliedRotationDegrees,
      float pixelWidthHeightRatio) {}

  @Override
  public void onRenderedFirstFrame(EventTime eventTime, Surface surface) {}

  @Override
  public void onDrmKeysLoaded(EventTime eventTime) {}

  @Override
  public void onDrmSessionManagerError(EventTime eventTime, Exception error) {}

  @Override
  public void onDrmKeysRestored(EventTime eventTime) {}

  @Override
  public void onDrmKeysRemoved(EventTime eventTime) {}
}
