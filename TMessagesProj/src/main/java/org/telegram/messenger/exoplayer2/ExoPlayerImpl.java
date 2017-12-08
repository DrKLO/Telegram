/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;
import org.telegram.messenger.exoplayer2.ExoPlayerImplInternal.PlaybackInfo;
import org.telegram.messenger.exoplayer2.ExoPlayerImplInternal.SourceInfo;
import org.telegram.messenger.exoplayer2.source.MediaSource;
import org.telegram.messenger.exoplayer2.source.MediaSource.MediaPeriodId;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelection;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelectionArray;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelector;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelectorResult;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.Util;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * An {@link ExoPlayer} implementation. Instances can be obtained from {@link ExoPlayerFactory}.
 */
/* package */ final class ExoPlayerImpl implements ExoPlayer {

  private static final String TAG = "ExoPlayerImpl";

  private final Renderer[] renderers;
  private final TrackSelector trackSelector;
  private final TrackSelectionArray emptyTrackSelections;
  private final Handler eventHandler;
  private final ExoPlayerImplInternal internalPlayer;
  private final CopyOnWriteArraySet<Player.EventListener> listeners;
  private final Timeline.Window window;
  private final Timeline.Period period;

  private boolean tracksSelected;
  private boolean playWhenReady;
  private @RepeatMode int repeatMode;
  private int playbackState;
  private int pendingSeekAcks;
  private int pendingPrepareAcks;
  private boolean isLoading;
  private Timeline timeline;
  private Object manifest;
  private TrackGroupArray trackGroups;
  private TrackSelectionArray trackSelections;
  private PlaybackParameters playbackParameters;

  // Playback information when there is no pending seek/set source operation.
  private PlaybackInfo playbackInfo;

  // Playback information when there is a pending seek/set source operation.
  private int maskingWindowIndex;
  private int maskingPeriodIndex;
  private long maskingWindowPositionMs;

  /**
   * Constructs an instance. Must be called from a thread that has an associated {@link Looper}.
   *
   * @param renderers The {@link Renderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   */
  @SuppressLint("HandlerLeak")
  public ExoPlayerImpl(Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl) {
    Log.i(TAG, "Init " + Integer.toHexString(System.identityHashCode(this)) + " ["
        + ExoPlayerLibraryInfo.VERSION_SLASHY + "] [" + Util.DEVICE_DEBUG_INFO + "]");
    Assertions.checkState(renderers.length > 0);
    this.renderers = Assertions.checkNotNull(renderers);
    this.trackSelector = Assertions.checkNotNull(trackSelector);
    this.playWhenReady = false;
    this.repeatMode = Player.REPEAT_MODE_OFF;
    this.playbackState = Player.STATE_IDLE;
    this.listeners = new CopyOnWriteArraySet<>();
    emptyTrackSelections = new TrackSelectionArray(new TrackSelection[renderers.length]);
    timeline = Timeline.EMPTY;
    window = new Timeline.Window();
    period = new Timeline.Period();
    trackGroups = TrackGroupArray.EMPTY;
    trackSelections = emptyTrackSelections;
    playbackParameters = PlaybackParameters.DEFAULT;
    Looper eventLooper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();
    eventHandler = new Handler(eventLooper) {
      @Override
      public void handleMessage(Message msg) {
        ExoPlayerImpl.this.handleEvent(msg);
      }
    };
    playbackInfo = new ExoPlayerImplInternal.PlaybackInfo(0, 0);
    internalPlayer = new ExoPlayerImplInternal(renderers, trackSelector, loadControl, playWhenReady,
        repeatMode, eventHandler, playbackInfo, this);
  }

  @Override
  public Looper getPlaybackLooper() {
    return internalPlayer.getPlaybackLooper();
  }

  @Override
  public void addListener(Player.EventListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Player.EventListener listener) {
    listeners.remove(listener);
  }

  @Override
  public int getPlaybackState() {
    return playbackState;
  }

  @Override
  public void prepare(MediaSource mediaSource) {
    prepare(mediaSource, true, true);
  }

  @Override
  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    if (resetState) {
      if (!timeline.isEmpty() || manifest != null) {
        timeline = Timeline.EMPTY;
        manifest = null;
        for (Player.EventListener listener : listeners) {
          listener.onTimelineChanged(timeline, manifest);
        }
      }
      if (tracksSelected) {
        tracksSelected = false;
        trackGroups = TrackGroupArray.EMPTY;
        trackSelections = emptyTrackSelections;
        trackSelector.onSelectionActivated(null);
        for (Player.EventListener listener : listeners) {
          listener.onTracksChanged(trackGroups, trackSelections);
        }
      }
    }
    pendingPrepareAcks++;
    internalPlayer.prepare(mediaSource, resetPosition);
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    if (this.playWhenReady != playWhenReady) {
      this.playWhenReady = playWhenReady;
      internalPlayer.setPlayWhenReady(playWhenReady);
      for (Player.EventListener listener : listeners) {
        listener.onPlayerStateChanged(playWhenReady, playbackState);
      }
    }
  }

  @Override
  public boolean getPlayWhenReady() {
    return playWhenReady;
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    if (this.repeatMode != repeatMode) {
      this.repeatMode = repeatMode;
      internalPlayer.setRepeatMode(repeatMode);
      for (Player.EventListener listener : listeners) {
        listener.onRepeatModeChanged(repeatMode);
      }
    }
  }

  @Override
  public @RepeatMode int getRepeatMode() {
    return repeatMode;
  }

  @Override
  public boolean isLoading() {
    return isLoading;
  }

  @Override
  public void seekToDefaultPosition() {
    seekToDefaultPosition(getCurrentWindowIndex());
  }

  @Override
  public void seekToDefaultPosition(int windowIndex) {
    seekTo(windowIndex, C.TIME_UNSET);
  }

  @Override
  public void seekTo(long positionMs) {
    seekTo(getCurrentWindowIndex(), positionMs);
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    if (windowIndex < 0 || (!timeline.isEmpty() && windowIndex >= timeline.getWindowCount())) {
      throw new IllegalSeekPositionException(timeline, windowIndex, positionMs);
    }
    pendingSeekAcks++;
    maskingWindowIndex = windowIndex;
    if (timeline.isEmpty()) {
      maskingPeriodIndex = 0;
    } else {
      timeline.getWindow(windowIndex, window);
      long resolvedPositionUs =
          positionMs == C.TIME_UNSET ? window.getDefaultPositionUs() : C.msToUs(positionMs);
      int periodIndex = window.firstPeriodIndex;
      long periodPositionUs = window.getPositionInFirstPeriodUs() + resolvedPositionUs;
      long periodDurationUs = timeline.getPeriod(periodIndex, period).getDurationUs();
      while (periodDurationUs != C.TIME_UNSET && periodPositionUs >= periodDurationUs
          && periodIndex < window.lastPeriodIndex) {
        periodPositionUs -= periodDurationUs;
        periodDurationUs = timeline.getPeriod(++periodIndex, period).getDurationUs();
      }
      maskingPeriodIndex = periodIndex;
    }
    if (positionMs == C.TIME_UNSET) {
      maskingWindowPositionMs = 0;
      internalPlayer.seekTo(timeline, windowIndex, C.TIME_UNSET);
    } else {
      maskingWindowPositionMs = positionMs;
      internalPlayer.seekTo(timeline, windowIndex, C.msToUs(positionMs));
      for (Player.EventListener listener : listeners) {
        listener.onPositionDiscontinuity();
      }
    }
  }

  @Override
  public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
    if (playbackParameters == null) {
      playbackParameters = PlaybackParameters.DEFAULT;
    }
    internalPlayer.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return playbackParameters;
  }

  @Override
  public void stop() {
    internalPlayer.stop();
  }

  @Override
  public void release() {
    Log.i(TAG, "Release " + Integer.toHexString(System.identityHashCode(this)) + " ["
        + ExoPlayerLibraryInfo.VERSION_SLASHY + "] [" + Util.DEVICE_DEBUG_INFO + "] ["
        + ExoPlayerLibraryInfo.registeredModules() + "]");
    internalPlayer.release();
    eventHandler.removeCallbacksAndMessages(null);
  }

  @Override
  public void sendMessages(ExoPlayerMessage... messages) {
    internalPlayer.sendMessages(messages);
  }

  @Override
  public void blockingSendMessages(ExoPlayerMessage... messages) {
    internalPlayer.blockingSendMessages(messages);
  }

  @Override
  public int getCurrentPeriodIndex() {
    if (timeline.isEmpty() || pendingSeekAcks > 0) {
      return maskingPeriodIndex;
    } else {
      return playbackInfo.periodId.periodIndex;
    }
  }

  @Override
  public int getCurrentWindowIndex() {
    if (timeline.isEmpty() || pendingSeekAcks > 0) {
      return maskingWindowIndex;
    } else {
      return timeline.getPeriod(playbackInfo.periodId.periodIndex, period).windowIndex;
    }
  }

  @Override
  public long getDuration() {
    if (timeline.isEmpty()) {
      return C.TIME_UNSET;
    }
    if (isPlayingAd()) {
      MediaPeriodId periodId = playbackInfo.periodId;
      timeline.getPeriod(periodId.periodIndex, period);
      long adDurationUs = period.getAdDurationUs(periodId.adGroupIndex, periodId.adIndexInAdGroup);
      return C.usToMs(adDurationUs);
    } else {
      return timeline.getWindow(getCurrentWindowIndex(), window).getDurationMs();
    }
  }

  @Override
  public long getCurrentPosition() {
    if (timeline.isEmpty() || pendingSeekAcks > 0) {
      return maskingWindowPositionMs;
    } else {
      return playbackInfoPositionUsToWindowPositionMs(playbackInfo.positionUs);
    }
  }

  @Override
  public long getBufferedPosition() {
    // TODO - Implement this properly.
    if (timeline.isEmpty() || pendingSeekAcks > 0) {
      return maskingWindowPositionMs;
    } else {
      return playbackInfoPositionUsToWindowPositionMs(playbackInfo.bufferedPositionUs);
    }
  }

  @Override
  public int getBufferedPercentage() {
    if (timeline.isEmpty()) {
      return 0;
    }
    long position = getBufferedPosition();
    long duration = getDuration();
    return position == C.TIME_UNSET || duration == C.TIME_UNSET ? 0
        : (duration == 0 ? 100 : Util.constrainValue((int) ((position * 100) / duration), 0, 100));
  }

  @Override
  public boolean isCurrentWindowDynamic() {
    return !timeline.isEmpty() && timeline.getWindow(getCurrentWindowIndex(), window).isDynamic;
  }

  @Override
  public boolean isCurrentWindowSeekable() {
    return !timeline.isEmpty() && timeline.getWindow(getCurrentWindowIndex(), window).isSeekable;
  }

  @Override
  public boolean isPlayingAd() {
    return !timeline.isEmpty() && pendingSeekAcks == 0 && playbackInfo.periodId.isAd();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return isPlayingAd() ? playbackInfo.periodId.adGroupIndex : C.INDEX_UNSET;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return isPlayingAd() ? playbackInfo.periodId.adIndexInAdGroup : C.INDEX_UNSET;
  }

  @Override
  public long getContentPosition() {
    if (isPlayingAd()) {
      timeline.getPeriod(playbackInfo.periodId.periodIndex, period);
      return period.getPositionInWindowMs() + C.usToMs(playbackInfo.contentPositionUs);
    } else {
      return getCurrentPosition();
    }
  }

  @Override
  public int getRendererCount() {
    return renderers.length;
  }

  @Override
  public int getRendererType(int index) {
    return renderers[index].getTrackType();
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    return trackGroups;
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    return trackSelections;
  }

  @Override
  public Timeline getCurrentTimeline() {
    return timeline;
  }

  @Override
  public Object getCurrentManifest() {
    return manifest;
  }

  // Not private so it can be called from an inner class without going through a thunk method.
  /* package */ void handleEvent(Message msg) {
    switch (msg.what) {
      case ExoPlayerImplInternal.MSG_PREPARE_ACK: {
        pendingPrepareAcks--;
        break;
      }
      case ExoPlayerImplInternal.MSG_STATE_CHANGED: {
        playbackState = msg.arg1;
        for (Player.EventListener listener : listeners) {
          listener.onPlayerStateChanged(playWhenReady, playbackState);
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_LOADING_CHANGED: {
        isLoading = msg.arg1 != 0;
        for (Player.EventListener listener : listeners) {
          listener.onLoadingChanged(isLoading);
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_TRACKS_CHANGED: {
        if (pendingPrepareAcks == 0) {
          TrackSelectorResult trackSelectorResult = (TrackSelectorResult) msg.obj;
          tracksSelected = true;
          trackGroups = trackSelectorResult.groups;
          trackSelections = trackSelectorResult.selections;
          trackSelector.onSelectionActivated(trackSelectorResult.info);
          for (Player.EventListener listener : listeners) {
            listener.onTracksChanged(trackGroups, trackSelections);
          }
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_SEEK_ACK: {
        if (--pendingSeekAcks == 0) {
          playbackInfo = (ExoPlayerImplInternal.PlaybackInfo) msg.obj;
          if (timeline.isEmpty()) {
            // Update the masking variables, which are used when the timeline is empty.
            maskingPeriodIndex = 0;
            maskingWindowIndex = 0;
            maskingWindowPositionMs = 0;
          }
          if (msg.arg1 != 0) {
            for (Player.EventListener listener : listeners) {
              listener.onPositionDiscontinuity();
            }
          }
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_POSITION_DISCONTINUITY: {
        if (pendingSeekAcks == 0) {
          playbackInfo = (ExoPlayerImplInternal.PlaybackInfo) msg.obj;
          for (Player.EventListener listener : listeners) {
            listener.onPositionDiscontinuity();
          }
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_SOURCE_INFO_REFRESHED: {
        SourceInfo sourceInfo = (SourceInfo) msg.obj;
        pendingSeekAcks -= sourceInfo.seekAcks;
        if (pendingPrepareAcks == 0) {
          timeline = sourceInfo.timeline;
          manifest = sourceInfo.manifest;
          playbackInfo = sourceInfo.playbackInfo;
          if (pendingSeekAcks == 0 && timeline.isEmpty()) {
            // Update the masking variables, which are used when the timeline is empty.
            maskingPeriodIndex = 0;
            maskingWindowIndex = 0;
            maskingWindowPositionMs = 0;
          }
          for (Player.EventListener listener : listeners) {
            listener.onTimelineChanged(timeline, manifest);
          }
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_PLAYBACK_PARAMETERS_CHANGED: {
        PlaybackParameters playbackParameters = (PlaybackParameters) msg.obj;
        if (!this.playbackParameters.equals(playbackParameters)) {
          this.playbackParameters = playbackParameters;
          for (Player.EventListener listener : listeners) {
            listener.onPlaybackParametersChanged(playbackParameters);
          }
        }
        break;
      }
      case ExoPlayerImplInternal.MSG_ERROR: {
        ExoPlaybackException exception = (ExoPlaybackException) msg.obj;
        for (Player.EventListener listener : listeners) {
          listener.onPlayerError(exception);
        }
        break;
      }
      default:
        throw new IllegalStateException();
    }
  }

  private long playbackInfoPositionUsToWindowPositionMs(long positionUs) {
    long positionMs = C.usToMs(positionUs);
    if (!playbackInfo.periodId.isAd()) {
      timeline.getPeriod(playbackInfo.periodId.periodIndex, period);
      positionMs += period.getPositionInWindowMs();
    }
    return positionMs;
  }

}
