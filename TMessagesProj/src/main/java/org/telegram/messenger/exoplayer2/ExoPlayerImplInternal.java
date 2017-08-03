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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import org.telegram.messenger.exoplayer2.ExoPlayer.ExoPlayerMessage;
import org.telegram.messenger.exoplayer2.source.MediaPeriod;
import org.telegram.messenger.exoplayer2.source.MediaSource;
import org.telegram.messenger.exoplayer2.source.SampleStream;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelection;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelectionArray;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelector;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelectorResult;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.MediaClock;
import org.telegram.messenger.exoplayer2.util.StandaloneMediaClock;
import org.telegram.messenger.exoplayer2.util.TraceUtil;
import java.io.IOException;

/**
 * Implements the internal behavior of {@link ExoPlayerImpl}.
 */
/* package */ final class ExoPlayerImplInternal implements Handler.Callback,
    MediaPeriod.Callback, TrackSelector.InvalidationListener, MediaSource.Listener {

  /**
   * Playback position information which is read on the application's thread by
   * {@link ExoPlayerImpl} and read/written internally on the player's thread.
   */
  public static final class PlaybackInfo {

    public final int periodIndex;
    public final long startPositionUs;

    public volatile long positionUs;
    public volatile long bufferedPositionUs;

    public PlaybackInfo(int periodIndex, long startPositionUs) {
      this.periodIndex = periodIndex;
      this.startPositionUs = startPositionUs;
      positionUs = startPositionUs;
      bufferedPositionUs = startPositionUs;
    }

    public PlaybackInfo copyWithPeriodIndex(int periodIndex) {
      PlaybackInfo playbackInfo = new PlaybackInfo(periodIndex, startPositionUs);
      playbackInfo.positionUs = positionUs;
      playbackInfo.bufferedPositionUs = bufferedPositionUs;
      return playbackInfo;
    }

  }

  public static final class SourceInfo {

    public final Timeline timeline;
    public final Object manifest;
    public final PlaybackInfo playbackInfo;
    public final int seekAcks;

    public SourceInfo(Timeline timeline, Object manifest, PlaybackInfo playbackInfo, int seekAcks) {
      this.timeline = timeline;
      this.manifest = manifest;
      this.playbackInfo = playbackInfo;
      this.seekAcks = seekAcks;
    }

  }

  private static final String TAG = "ExoPlayerImplInternal";

  // External messages
  public static final int MSG_PREPARE_ACK = 0;
  public static final int MSG_STATE_CHANGED = 1;
  public static final int MSG_LOADING_CHANGED = 2;
  public static final int MSG_TRACKS_CHANGED = 3;
  public static final int MSG_SEEK_ACK = 4;
  public static final int MSG_POSITION_DISCONTINUITY = 5;
  public static final int MSG_SOURCE_INFO_REFRESHED = 6;
  public static final int MSG_PLAYBACK_PARAMETERS_CHANGED = 7;
  public static final int MSG_ERROR = 8;

  // Internal messages
  private static final int MSG_PREPARE = 0;
  private static final int MSG_SET_PLAY_WHEN_READY = 1;
  private static final int MSG_DO_SOME_WORK = 2;
  private static final int MSG_SEEK_TO = 3;
  private static final int MSG_SET_PLAYBACK_PARAMETERS = 4;
  private static final int MSG_STOP = 5;
  private static final int MSG_RELEASE = 6;
  private static final int MSG_REFRESH_SOURCE_INFO = 7;
  private static final int MSG_PERIOD_PREPARED = 8;
  private static final int MSG_SOURCE_CONTINUE_LOADING_REQUESTED = 9;
  private static final int MSG_TRACK_SELECTION_INVALIDATED = 10;
  private static final int MSG_CUSTOM = 11;

  private static final int PREPARING_SOURCE_INTERVAL_MS = 10;
  private static final int RENDERING_INTERVAL_MS = 10;
  private static final int IDLE_INTERVAL_MS = 1000;

  /**
   * Limits the maximum number of periods to buffer ahead of the current playing period. The
   * buffering policy normally prevents buffering too far ahead, but the policy could allow too many
   * small periods to be buffered if the period count were not limited.
   */
  private static final int MAXIMUM_BUFFER_AHEAD_PERIODS = 100;

  /**
   * Offset added to all sample timestamps read by renderers to make them non-negative. This is
   * provided for convenience of sources that may return negative timestamps due to prerolling
   * samples from a keyframe before their first sample with timestamp zero, so it must be set to a
   * value greater than or equal to the maximum key-frame interval in seekable periods.
   */
  private static final int RENDERER_TIMESTAMP_OFFSET_US = 60000000;

  private final Renderer[] renderers;
  private final RendererCapabilities[] rendererCapabilities;
  private final TrackSelector trackSelector;
  private final LoadControl loadControl;
  private final StandaloneMediaClock standaloneMediaClock;
  private final Handler handler;
  private final HandlerThread internalPlaybackThread;
  private final Handler eventHandler;
  private final ExoPlayer player;
  private final Timeline.Window window;
  private final Timeline.Period period;

  private PlaybackInfo playbackInfo;
  private PlaybackParameters playbackParameters;
  private Renderer rendererMediaClockSource;
  private MediaClock rendererMediaClock;
  private MediaSource mediaSource;
  private Renderer[] enabledRenderers;
  private boolean released;
  private boolean playWhenReady;
  private boolean rebuffering;
  private boolean isLoading;
  private int state;
  private int customMessagesSent;
  private int customMessagesProcessed;
  private long elapsedRealtimeUs;

  private int pendingInitialSeekCount;
  private SeekPosition pendingSeekPosition;
  private long rendererPositionUs;

  private MediaPeriodHolder loadingPeriodHolder;
  private MediaPeriodHolder readingPeriodHolder;
  private MediaPeriodHolder playingPeriodHolder;

  private Timeline timeline;

  public ExoPlayerImplInternal(Renderer[] renderers, TrackSelector trackSelector,
      LoadControl loadControl, boolean playWhenReady, Handler eventHandler,
      PlaybackInfo playbackInfo, ExoPlayer player) {
    this.renderers = renderers;
    this.trackSelector = trackSelector;
    this.loadControl = loadControl;
    this.playWhenReady = playWhenReady;
    this.eventHandler = eventHandler;
    this.state = ExoPlayer.STATE_IDLE;
    this.playbackInfo = playbackInfo;
    this.player = player;

    rendererCapabilities = new RendererCapabilities[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      renderers[i].setIndex(i);
      rendererCapabilities[i] = renderers[i].getCapabilities();
    }
    standaloneMediaClock = new StandaloneMediaClock();
    enabledRenderers = new Renderer[0];
    window = new Timeline.Window();
    period = new Timeline.Period();
    trackSelector.init(this);
    playbackParameters = PlaybackParameters.DEFAULT;

    // Note: The documentation for Process.THREAD_PRIORITY_AUDIO that states "Applications can
    // not normally change to this priority" is incorrect.
    internalPlaybackThread = new HandlerThread("ExoPlayerImplInternal:Handler",
        Process.THREAD_PRIORITY_AUDIO);
    internalPlaybackThread.start();
    handler = new Handler(internalPlaybackThread.getLooper(), this);
  }

  public void prepare(MediaSource mediaSource, boolean resetPosition) {
    handler.obtainMessage(MSG_PREPARE, resetPosition ? 1 : 0, 0, mediaSource)
        .sendToTarget();
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    handler.obtainMessage(MSG_SET_PLAY_WHEN_READY, playWhenReady ? 1 : 0, 0).sendToTarget();
  }

  public void seekTo(Timeline timeline, int windowIndex, long positionUs) {
    handler.obtainMessage(MSG_SEEK_TO, new SeekPosition(timeline, windowIndex, positionUs))
        .sendToTarget();
  }

  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    handler.obtainMessage(MSG_SET_PLAYBACK_PARAMETERS, playbackParameters).sendToTarget();
  }

  public void stop() {
    handler.sendEmptyMessage(MSG_STOP);
  }

  public void sendMessages(ExoPlayerMessage... messages) {
    if (released) {
      Log.w(TAG, "Ignoring messages sent after release.");
      return;
    }
    customMessagesSent++;
    handler.obtainMessage(MSG_CUSTOM, messages).sendToTarget();
  }

  public synchronized void blockingSendMessages(ExoPlayerMessage... messages) {
    if (released) {
      Log.w(TAG, "Ignoring messages sent after release.");
      return;
    }
    int messageNumber = customMessagesSent++;
    handler.obtainMessage(MSG_CUSTOM, messages).sendToTarget();
    while (customMessagesProcessed <= messageNumber) {
      try {
        wait();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public synchronized void release() {
    if (released) {
      return;
    }
    handler.sendEmptyMessage(MSG_RELEASE);
    while (!released) {
      try {
        wait();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    internalPlaybackThread.quit();
  }

  // MediaSource.Listener implementation.

  @Override
  public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
    handler.obtainMessage(MSG_REFRESH_SOURCE_INFO, Pair.create(timeline, manifest)).sendToTarget();
  }

  // MediaPeriod.Callback implementation.

  @Override
  public void onPrepared(MediaPeriod source) {
    handler.obtainMessage(MSG_PERIOD_PREPARED, source).sendToTarget();
  }

  @Override
  public void onContinueLoadingRequested(MediaPeriod source) {
    handler.obtainMessage(MSG_SOURCE_CONTINUE_LOADING_REQUESTED, source).sendToTarget();
  }

  // TrackSelector.InvalidationListener implementation.

  @Override
  public void onTrackSelectionsInvalidated() {
    handler.sendEmptyMessage(MSG_TRACK_SELECTION_INVALIDATED);
  }

  // Handler.Callback implementation.

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    try {
      switch (msg.what) {
        case MSG_PREPARE: {
          prepareInternal((MediaSource) msg.obj, msg.arg1 != 0);
          return true;
        }
        case MSG_SET_PLAY_WHEN_READY: {
          setPlayWhenReadyInternal(msg.arg1 != 0);
          return true;
        }
        case MSG_DO_SOME_WORK: {
          doSomeWork();
          return true;
        }
        case MSG_SEEK_TO: {
          seekToInternal((SeekPosition) msg.obj);
          return true;
        }
        case MSG_SET_PLAYBACK_PARAMETERS: {
          setPlaybackParametersInternal((PlaybackParameters) msg.obj);
          return true;
        }
        case MSG_STOP: {
          stopInternal();
          return true;
        }
        case MSG_RELEASE: {
          releaseInternal();
          return true;
        }
        case MSG_PERIOD_PREPARED: {
          handlePeriodPrepared((MediaPeriod) msg.obj);
          return true;
        }
        case MSG_REFRESH_SOURCE_INFO: {
          handleSourceInfoRefreshed((Pair<Timeline, Object>) msg.obj);
          return true;
        }
        case MSG_SOURCE_CONTINUE_LOADING_REQUESTED: {
          handleContinueLoadingRequested((MediaPeriod) msg.obj);
          return true;
        }
        case MSG_TRACK_SELECTION_INVALIDATED: {
          reselectTracksInternal();
          return true;
        }
        case MSG_CUSTOM: {
          sendMessagesInternal((ExoPlayerMessage[]) msg.obj);
          return true;
        }
        default:
          return false;
      }
    } catch (ExoPlaybackException e) {
      Log.e(TAG, "Renderer error.", e);
      eventHandler.obtainMessage(MSG_ERROR, e).sendToTarget();
      stopInternal();
      return true;
    } catch (IOException e) {
      Log.e(TAG, "Source error.", e);
      eventHandler.obtainMessage(MSG_ERROR, ExoPlaybackException.createForSource(e)).sendToTarget();
      stopInternal();
      return true;
    } catch (RuntimeException e) {
      Log.e(TAG, "Internal runtime error.", e);
      eventHandler.obtainMessage(MSG_ERROR, ExoPlaybackException.createForUnexpected(e))
          .sendToTarget();
      stopInternal();
      return true;
    }
  }

  // Private methods.

  private void setState(int state) {
    if (this.state != state) {
      this.state = state;
      eventHandler.obtainMessage(MSG_STATE_CHANGED, state, 0).sendToTarget();
    }
  }

  private void setIsLoading(boolean isLoading) {
    if (this.isLoading != isLoading) {
      this.isLoading = isLoading;
      eventHandler.obtainMessage(MSG_LOADING_CHANGED, isLoading ? 1 : 0, 0).sendToTarget();
    }
  }

  private void prepareInternal(MediaSource mediaSource, boolean resetPosition) {
    eventHandler.sendEmptyMessage(MSG_PREPARE_ACK);
    resetInternal(true);
    loadControl.onPrepared();
    if (resetPosition) {
      playbackInfo = new PlaybackInfo(0, C.TIME_UNSET);
    }
    this.mediaSource = mediaSource;
    mediaSource.prepareSource(player, true, this);
    setState(ExoPlayer.STATE_BUFFERING);
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private void setPlayWhenReadyInternal(boolean playWhenReady) throws ExoPlaybackException {
    rebuffering = false;
    this.playWhenReady = playWhenReady;
    if (!playWhenReady) {
      stopRenderers();
      updatePlaybackPositions();
    } else {
      if (state == ExoPlayer.STATE_READY) {
        startRenderers();
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      } else if (state == ExoPlayer.STATE_BUFFERING) {
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      }
    }
  }

  private void startRenderers() throws ExoPlaybackException {
    rebuffering = false;
    standaloneMediaClock.start();
    for (Renderer renderer : enabledRenderers) {
      renderer.start();
    }
  }

  private void stopRenderers() throws ExoPlaybackException {
    standaloneMediaClock.stop();
    for (Renderer renderer : enabledRenderers) {
      ensureStopped(renderer);
    }
  }

  private void updatePlaybackPositions() throws ExoPlaybackException {
    if (playingPeriodHolder == null) {
      return;
    }

    // Update the playback position.
    long periodPositionUs = playingPeriodHolder.mediaPeriod.readDiscontinuity();
    if (periodPositionUs != C.TIME_UNSET) {
      resetRendererPosition(periodPositionUs);
    } else {
      if (rendererMediaClockSource != null && !rendererMediaClockSource.isEnded()) {
        rendererPositionUs = rendererMediaClock.getPositionUs();
        standaloneMediaClock.setPositionUs(rendererPositionUs);
      } else {
        rendererPositionUs = standaloneMediaClock.getPositionUs();
      }
      periodPositionUs = playingPeriodHolder.toPeriodTime(rendererPositionUs);
    }
    playbackInfo.positionUs = periodPositionUs;
    elapsedRealtimeUs = SystemClock.elapsedRealtime() * 1000;

    // Update the buffered position.
    long bufferedPositionUs = enabledRenderers.length == 0 ? C.TIME_END_OF_SOURCE
        : playingPeriodHolder.mediaPeriod.getBufferedPositionUs();
    playbackInfo.bufferedPositionUs = bufferedPositionUs == C.TIME_END_OF_SOURCE
        ? timeline.getPeriod(playingPeriodHolder.index, period).getDurationUs()
        : bufferedPositionUs;
  }

  private void doSomeWork() throws ExoPlaybackException, IOException {
    long operationStartTimeMs = SystemClock.elapsedRealtime();
    updatePeriods();
    if (playingPeriodHolder == null) {
      // We're still waiting for the first period to be prepared.
      maybeThrowPeriodPrepareError();
      scheduleNextWork(operationStartTimeMs, PREPARING_SOURCE_INTERVAL_MS);
      return;
    }

    TraceUtil.beginSection("doSomeWork");

    updatePlaybackPositions();
    playingPeriodHolder.mediaPeriod.discardBuffer(playbackInfo.positionUs);

    boolean allRenderersEnded = true;
    boolean allRenderersReadyOrEnded = true;
    for (Renderer renderer : enabledRenderers) {
      // TODO: Each renderer should return the maximum delay before which it wishes to be called
      // again. The minimum of these values should then be used as the delay before the next
      // invocation of this method.
      renderer.render(rendererPositionUs, elapsedRealtimeUs);
      allRenderersEnded = allRenderersEnded && renderer.isEnded();
      // Determine whether the renderer is ready (or ended). If it's not, throw an error that's
      // preventing the renderer from making progress, if such an error exists.
      boolean rendererReadyOrEnded = renderer.isReady() || renderer.isEnded();
      if (!rendererReadyOrEnded) {
        renderer.maybeThrowStreamError();
      }
      allRenderersReadyOrEnded = allRenderersReadyOrEnded && rendererReadyOrEnded;
    }

    if (!allRenderersReadyOrEnded) {
      maybeThrowPeriodPrepareError();
    }

    // The standalone media clock never changes playback parameters, so just check the renderer.
    if (rendererMediaClock != null) {
      PlaybackParameters playbackParameters = rendererMediaClock.getPlaybackParameters();
      if (!playbackParameters.equals(this.playbackParameters)) {
        // TODO: Make LoadControl, period transition position projection, adaptive track selection
        // and potentially any time-related code in renderers take into account the playback speed.
        this.playbackParameters = playbackParameters;
        standaloneMediaClock.synchronize(rendererMediaClock);
        eventHandler.obtainMessage(MSG_PLAYBACK_PARAMETERS_CHANGED, playbackParameters)
            .sendToTarget();
      }
    }

    long playingPeriodDurationUs = timeline.getPeriod(playingPeriodHolder.index, period)
        .getDurationUs();
    if (allRenderersEnded
        && (playingPeriodDurationUs == C.TIME_UNSET
        || playingPeriodDurationUs <= playbackInfo.positionUs)
        && playingPeriodHolder.isLast) {
      setState(ExoPlayer.STATE_ENDED);
      stopRenderers();
    } else if (state == ExoPlayer.STATE_BUFFERING) {
      boolean isNewlyReady = enabledRenderers.length > 0
          ? (allRenderersReadyOrEnded && haveSufficientBuffer(rebuffering))
          : isTimelineReady(playingPeriodDurationUs);
      if (isNewlyReady) {
        setState(ExoPlayer.STATE_READY);
        if (playWhenReady) {
          startRenderers();
        }
      }
    } else if (state == ExoPlayer.STATE_READY) {
      boolean isStillReady = enabledRenderers.length > 0 ? allRenderersReadyOrEnded
          : isTimelineReady(playingPeriodDurationUs);
      if (!isStillReady) {
        rebuffering = playWhenReady;
        setState(ExoPlayer.STATE_BUFFERING);
        stopRenderers();
      }
    }

    if (state == ExoPlayer.STATE_BUFFERING) {
      for (Renderer renderer : enabledRenderers) {
        renderer.maybeThrowStreamError();
      }
    }

    if ((playWhenReady && state == ExoPlayer.STATE_READY) || state == ExoPlayer.STATE_BUFFERING) {
      scheduleNextWork(operationStartTimeMs, RENDERING_INTERVAL_MS);
    } else if (enabledRenderers.length != 0) {
      scheduleNextWork(operationStartTimeMs, IDLE_INTERVAL_MS);
    } else {
      handler.removeMessages(MSG_DO_SOME_WORK);
    }

    TraceUtil.endSection();
  }

  private void scheduleNextWork(long thisOperationStartTimeMs, long intervalMs) {
    handler.removeMessages(MSG_DO_SOME_WORK);
    long nextOperationStartTimeMs = thisOperationStartTimeMs + intervalMs;
    long nextOperationDelayMs = nextOperationStartTimeMs - SystemClock.elapsedRealtime();
    if (nextOperationDelayMs <= 0) {
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    } else {
      handler.sendEmptyMessageDelayed(MSG_DO_SOME_WORK, nextOperationDelayMs);
    }
  }

  private void seekToInternal(SeekPosition seekPosition) throws ExoPlaybackException {
    if (timeline == null) {
      pendingInitialSeekCount++;
      pendingSeekPosition = seekPosition;
      return;
    }

    Pair<Integer, Long> periodPosition = resolveSeekPosition(seekPosition);
    if (periodPosition == null) {
      // The seek position was valid for the timeline that it was performed into, but the
      // timeline has changed and a suitable seek position could not be resolved in the new one.
      playbackInfo = new PlaybackInfo(0, 0);
      eventHandler.obtainMessage(MSG_SEEK_ACK, 1, 0, playbackInfo).sendToTarget();
      // Set the internal position to (0,TIME_UNSET) so that a subsequent seek to (0,0) isn't
      // ignored.
      playbackInfo = new PlaybackInfo(0, C.TIME_UNSET);
      setState(ExoPlayer.STATE_ENDED);
      // Reset, but retain the source so that it can still be used should a seek occur.
      resetInternal(false);
      return;
    }

    boolean seekPositionAdjusted = seekPosition.windowPositionUs == C.TIME_UNSET;
    int periodIndex = periodPosition.first;
    long periodPositionUs = periodPosition.second;

    try {
      if (periodIndex == playbackInfo.periodIndex
          && ((periodPositionUs / 1000) == (playbackInfo.positionUs / 1000))) {
        // Seek position equals the current position. Do nothing.
        return;
      }
      long newPeriodPositionUs = seekToPeriodPosition(periodIndex, periodPositionUs);
      seekPositionAdjusted |= periodPositionUs != newPeriodPositionUs;
      periodPositionUs = newPeriodPositionUs;
    } finally {
      playbackInfo = new PlaybackInfo(periodIndex, periodPositionUs);
      eventHandler.obtainMessage(MSG_SEEK_ACK, seekPositionAdjusted ? 1 : 0, 0, playbackInfo)
          .sendToTarget();
    }
  }

  private long seekToPeriodPosition(int periodIndex, long periodPositionUs)
      throws ExoPlaybackException {
    stopRenderers();
    rebuffering = false;
    setState(ExoPlayer.STATE_BUFFERING);

    MediaPeriodHolder newPlayingPeriodHolder = null;
    if (playingPeriodHolder == null) {
      // We're still waiting for the first period to be prepared.
      if (loadingPeriodHolder != null) {
        loadingPeriodHolder.release();
      }
    } else {
      // Clear the timeline, but keep the requested period if it is already prepared.
      MediaPeriodHolder periodHolder = playingPeriodHolder;
      while (periodHolder != null) {
        if (periodHolder.index == periodIndex && periodHolder.prepared) {
          newPlayingPeriodHolder = periodHolder;
        } else {
          periodHolder.release();
        }
        periodHolder = periodHolder.next;
      }
    }

    // Disable all the renderers if the period being played is changing, or if the renderers are
    // reading from a period other than the one being played.
    if (playingPeriodHolder != newPlayingPeriodHolder
        || playingPeriodHolder != readingPeriodHolder) {
      for (Renderer renderer : enabledRenderers) {
        renderer.disable();
      }
      enabledRenderers = new Renderer[0];
      rendererMediaClock = null;
      rendererMediaClockSource = null;
      playingPeriodHolder = null;
    }

    // Update the holders.
    if (newPlayingPeriodHolder != null) {
      newPlayingPeriodHolder.next = null;
      loadingPeriodHolder = newPlayingPeriodHolder;
      readingPeriodHolder = newPlayingPeriodHolder;
      setPlayingPeriodHolder(newPlayingPeriodHolder);
      if (playingPeriodHolder.hasEnabledTracks) {
        periodPositionUs = playingPeriodHolder.mediaPeriod.seekToUs(periodPositionUs);
      }
      resetRendererPosition(periodPositionUs);
      maybeContinueLoading();
    } else {
      loadingPeriodHolder = null;
      readingPeriodHolder = null;
      playingPeriodHolder = null;
      resetRendererPosition(periodPositionUs);
    }

    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    return periodPositionUs;
  }

  private void resetRendererPosition(long periodPositionUs) throws ExoPlaybackException {
    rendererPositionUs = playingPeriodHolder == null
        ? periodPositionUs + RENDERER_TIMESTAMP_OFFSET_US
        : playingPeriodHolder.toRendererTime(periodPositionUs);
    standaloneMediaClock.setPositionUs(rendererPositionUs);
    for (Renderer renderer : enabledRenderers) {
      renderer.resetPosition(rendererPositionUs);
    }
  }

  private void setPlaybackParametersInternal(PlaybackParameters playbackParameters) {
    playbackParameters = rendererMediaClock != null
        ? rendererMediaClock.setPlaybackParameters(playbackParameters)
        : standaloneMediaClock.setPlaybackParameters(playbackParameters);
    this.playbackParameters = playbackParameters;
    eventHandler.obtainMessage(MSG_PLAYBACK_PARAMETERS_CHANGED, playbackParameters).sendToTarget();
  }

  private void stopInternal() {
    resetInternal(true);
    loadControl.onStopped();
    setState(ExoPlayer.STATE_IDLE);
  }

  private void releaseInternal() {
    resetInternal(true);
    loadControl.onReleased();
    setState(ExoPlayer.STATE_IDLE);
    synchronized (this) {
      released = true;
      notifyAll();
    }
  }

  private void resetInternal(boolean releaseMediaSource) {
    handler.removeMessages(MSG_DO_SOME_WORK);
    rebuffering = false;
    standaloneMediaClock.stop();
    rendererMediaClock = null;
    rendererMediaClockSource = null;
    rendererPositionUs = RENDERER_TIMESTAMP_OFFSET_US;
    for (Renderer renderer : enabledRenderers) {
      try {
        ensureStopped(renderer);
        renderer.disable();
      } catch (ExoPlaybackException | RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Stop failed.", e);
      }
    }
    enabledRenderers = new Renderer[0];
    releasePeriodHoldersFrom(playingPeriodHolder != null ? playingPeriodHolder
        : loadingPeriodHolder);
    loadingPeriodHolder = null;
    readingPeriodHolder = null;
    playingPeriodHolder = null;
    setIsLoading(false);
    if (releaseMediaSource) {
      if (mediaSource != null) {
        mediaSource.releaseSource();
        mediaSource = null;
      }
      timeline = null;
    }
  }

  private void sendMessagesInternal(ExoPlayerMessage[] messages) throws ExoPlaybackException {
    try {
      for (ExoPlayerMessage message : messages) {
        message.target.handleMessage(message.messageType, message.message);
      }
      if (mediaSource != null) {
        // The message may have caused something to change that now requires us to do work.
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      }
    } finally {
      synchronized (this) {
        customMessagesProcessed++;
        notifyAll();
      }
    }
  }

  private void ensureStopped(Renderer renderer) throws ExoPlaybackException {
    if (renderer.getState() == Renderer.STATE_STARTED) {
      renderer.stop();
    }
  }

  private void reselectTracksInternal() throws ExoPlaybackException {
    if (playingPeriodHolder == null) {
      // We don't have tracks yet, so we don't care.
      return;
    }
    // Reselect tracks on each period in turn, until the selection changes.
    MediaPeriodHolder periodHolder = playingPeriodHolder;
    boolean selectionsChangedForReadPeriod = true;
    while (true) {
      if (periodHolder == null || !periodHolder.prepared) {
        // The reselection did not change any prepared periods.
        return;
      }
      if (periodHolder.selectTracks()) {
        // Selected tracks have changed for this period.
        break;
      }
      if (periodHolder == readingPeriodHolder) {
        // The track reselection didn't affect any period that has been read.
        selectionsChangedForReadPeriod = false;
      }
      periodHolder = periodHolder.next;
    }

    if (selectionsChangedForReadPeriod) {
      // Update streams and rebuffer for the new selection, recreating all streams if reading ahead.
      boolean recreateStreams = readingPeriodHolder != playingPeriodHolder;
      releasePeriodHoldersFrom(playingPeriodHolder.next);
      playingPeriodHolder.next = null;
      loadingPeriodHolder = playingPeriodHolder;
      readingPeriodHolder = playingPeriodHolder;

      boolean[] streamResetFlags = new boolean[renderers.length];
      long periodPositionUs = playingPeriodHolder.updatePeriodTrackSelection(
          playbackInfo.positionUs, recreateStreams, streamResetFlags);
      if (periodPositionUs != playbackInfo.positionUs) {
        playbackInfo.positionUs = periodPositionUs;
        resetRendererPosition(periodPositionUs);
      }

      int enabledRendererCount = 0;
      boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
      for (int i = 0; i < renderers.length; i++) {
        Renderer renderer = renderers[i];
        rendererWasEnabledFlags[i] = renderer.getState() != Renderer.STATE_DISABLED;
        SampleStream sampleStream = playingPeriodHolder.sampleStreams[i];
        if (sampleStream != null) {
          enabledRendererCount++;
        }
        if (rendererWasEnabledFlags[i]) {
          if (sampleStream != renderer.getStream()) {
            // We need to disable the renderer.
            if (renderer == rendererMediaClockSource) {
              // The renderer is providing the media clock.
              if (sampleStream == null) {
                // The renderer won't be re-enabled. Sync standaloneMediaClock so that it can take
                // over timing responsibilities.
                standaloneMediaClock.synchronize(rendererMediaClock);
              }
              rendererMediaClock = null;
              rendererMediaClockSource = null;
            }
            ensureStopped(renderer);
            renderer.disable();
          } else if (streamResetFlags[i]) {
            // The renderer will continue to consume from its current stream, but needs to be reset.
            renderer.resetPosition(rendererPositionUs);
          }
        }
      }
      eventHandler.obtainMessage(MSG_TRACKS_CHANGED, periodHolder.trackSelectorResult)
          .sendToTarget();
      enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
    } else {
      // Release and re-prepare/buffer periods after the one whose selection changed.
      loadingPeriodHolder = periodHolder;
      periodHolder = loadingPeriodHolder.next;
      while (periodHolder != null) {
        periodHolder.release();
        periodHolder = periodHolder.next;
      }
      loadingPeriodHolder.next = null;
      if (loadingPeriodHolder.prepared) {
        long loadingPeriodPositionUs = Math.max(loadingPeriodHolder.startPositionUs,
            loadingPeriodHolder.toPeriodTime(rendererPositionUs));
        loadingPeriodHolder.updatePeriodTrackSelection(loadingPeriodPositionUs, false);
      }
    }
    maybeContinueLoading();
    updatePlaybackPositions();
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private boolean isTimelineReady(long playingPeriodDurationUs) {
    return playingPeriodDurationUs == C.TIME_UNSET
        || playbackInfo.positionUs < playingPeriodDurationUs
        || (playingPeriodHolder.next != null && playingPeriodHolder.next.prepared);
  }

  private boolean haveSufficientBuffer(boolean rebuffering) {
    long loadingPeriodBufferedPositionUs = !loadingPeriodHolder.prepared
        ? loadingPeriodHolder.startPositionUs
        : loadingPeriodHolder.mediaPeriod.getBufferedPositionUs();
    if (loadingPeriodBufferedPositionUs == C.TIME_END_OF_SOURCE) {
      if (loadingPeriodHolder.isLast) {
        return true;
      }
      loadingPeriodBufferedPositionUs = timeline.getPeriod(loadingPeriodHolder.index, period)
          .getDurationUs();
    }
    return loadControl.shouldStartPlayback(
        loadingPeriodBufferedPositionUs - loadingPeriodHolder.toPeriodTime(rendererPositionUs),
        rebuffering);
  }

  private void maybeThrowPeriodPrepareError() throws IOException {
    if (loadingPeriodHolder != null && !loadingPeriodHolder.prepared
        && (readingPeriodHolder == null || readingPeriodHolder.next == loadingPeriodHolder)) {
      for (Renderer renderer : enabledRenderers) {
        if (!renderer.hasReadStreamToEnd()) {
          return;
        }
      }
      loadingPeriodHolder.mediaPeriod.maybeThrowPrepareError();
    }
  }

  private void handleSourceInfoRefreshed(Pair<Timeline, Object> timelineAndManifest)
      throws ExoPlaybackException {
    Timeline oldTimeline = timeline;
    timeline = timelineAndManifest.first;
    Object manifest = timelineAndManifest.second;

    int processedInitialSeekCount = 0;
    if (oldTimeline == null) {
      if (pendingInitialSeekCount > 0) {
        Pair<Integer, Long> periodPosition = resolveSeekPosition(pendingSeekPosition);
        processedInitialSeekCount = pendingInitialSeekCount;
        pendingInitialSeekCount = 0;
        pendingSeekPosition = null;
        if (periodPosition == null) {
          // The seek position was valid for the timeline that it was performed into, but the
          // timeline has changed and a suitable seek position could not be resolved in the new one.
          handleSourceInfoRefreshEndedPlayback(manifest, processedInitialSeekCount);
          return;
        }
        playbackInfo = new PlaybackInfo(periodPosition.first, periodPosition.second);
      } else if (playbackInfo.startPositionUs == C.TIME_UNSET) {
        if (timeline.isEmpty()) {
          handleSourceInfoRefreshEndedPlayback(manifest, processedInitialSeekCount);
          return;
        }
        Pair<Integer, Long> defaultPosition = getPeriodPosition(0, C.TIME_UNSET);
        playbackInfo = new PlaybackInfo(defaultPosition.first, defaultPosition.second);
      }
    }

    MediaPeriodHolder periodHolder = playingPeriodHolder != null ? playingPeriodHolder
        : loadingPeriodHolder;
    if (periodHolder == null) {
      // We don't have any period holders, so we're done.
      notifySourceInfoRefresh(manifest, processedInitialSeekCount);
      return;
    }

    int periodIndex = timeline.getIndexOfPeriod(periodHolder.uid);
    if (periodIndex == C.INDEX_UNSET) {
      // We didn't find the current period in the new timeline. Attempt to resolve a subsequent
      // period whose window we can restart from.
      int newPeriodIndex = resolveSubsequentPeriod(periodHolder.index, oldTimeline, timeline);
      if (newPeriodIndex == C.INDEX_UNSET) {
        // We failed to resolve a suitable restart position.
        handleSourceInfoRefreshEndedPlayback(manifest, processedInitialSeekCount);
        return;
      }
      // We resolved a subsequent period. Seek to the default position in the corresponding window.
      Pair<Integer, Long> defaultPosition = getPeriodPosition(
          timeline.getPeriod(newPeriodIndex, period).windowIndex, C.TIME_UNSET);
      newPeriodIndex = defaultPosition.first;
      long newPositionUs = defaultPosition.second;
      timeline.getPeriod(newPeriodIndex, period, true);
      // Clear the index of each holder that doesn't contain the default position. If a holder
      // contains the default position then update its index so it can be re-used when seeking.
      Object newPeriodUid = period.uid;
      periodHolder.index = C.INDEX_UNSET;
      while (periodHolder.next != null) {
        periodHolder = periodHolder.next;
        periodHolder.index = periodHolder.uid.equals(newPeriodUid) ? newPeriodIndex : C.INDEX_UNSET;
      }
      // Actually do the seek.
      newPositionUs = seekToPeriodPosition(newPeriodIndex, newPositionUs);
      playbackInfo = new PlaybackInfo(newPeriodIndex, newPositionUs);
      notifySourceInfoRefresh(manifest, processedInitialSeekCount);
      return;
    }

    // The current period is in the new timeline. Update the holder and playbackInfo.
    timeline.getPeriod(periodIndex, period);
    boolean isLastPeriod = periodIndex == timeline.getPeriodCount() - 1
        && !timeline.getWindow(period.windowIndex, window).isDynamic;
    periodHolder.setIndex(periodIndex, isLastPeriod);
    boolean seenReadingPeriod = periodHolder == readingPeriodHolder;
    if (periodIndex != playbackInfo.periodIndex) {
      playbackInfo = playbackInfo.copyWithPeriodIndex(periodIndex);
    }

    // If there are subsequent holders, update the index for each of them. If we find a holder
    // that's inconsistent with the new timeline then take appropriate action.
    while (periodHolder.next != null) {
      MediaPeriodHolder previousPeriodHolder = periodHolder;
      periodHolder = periodHolder.next;
      periodIndex++;
      timeline.getPeriod(periodIndex, period, true);
      isLastPeriod = periodIndex == timeline.getPeriodCount() - 1
          && !timeline.getWindow(period.windowIndex, window).isDynamic;
      if (periodHolder.uid.equals(period.uid)) {
        // The holder is consistent with the new timeline. Update its index and continue.
        periodHolder.setIndex(periodIndex, isLastPeriod);
        seenReadingPeriod |= (periodHolder == readingPeriodHolder);
      } else {
        // The holder is inconsistent with the new timeline.
        if (!seenReadingPeriod) {
          // Renderers may have read from a period that's been removed. Seek back to the current
          // position of the playing period to make sure none of the removed period is played.
          periodIndex = playingPeriodHolder.index;
          long newPositionUs = seekToPeriodPosition(periodIndex, playbackInfo.positionUs);
          playbackInfo = new PlaybackInfo(periodIndex, newPositionUs);
        } else {
          // Update the loading period to be the last period that's still valid, and release all
          // subsequent periods.
          loadingPeriodHolder = previousPeriodHolder;
          loadingPeriodHolder.next = null;
          // Release the rest of the timeline.
          releasePeriodHoldersFrom(periodHolder);
        }
        break;
      }
    }

    notifySourceInfoRefresh(manifest, processedInitialSeekCount);
  }

  private void handleSourceInfoRefreshEndedPlayback(Object manifest,
      int processedInitialSeekCount) {
    // Set the playback position to (0,0) for notifying the eventHandler.
    playbackInfo = new PlaybackInfo(0, 0);
    notifySourceInfoRefresh(manifest, processedInitialSeekCount);
    // Set the internal position to (0,TIME_UNSET) so that a subsequent seek to (0,0) isn't ignored.
    playbackInfo = new PlaybackInfo(0, C.TIME_UNSET);
    setState(ExoPlayer.STATE_ENDED);
    // Reset, but retain the source so that it can still be used should a seek occur.
    resetInternal(false);
  }

  private void notifySourceInfoRefresh(Object manifest, int processedInitialSeekCount) {
    eventHandler.obtainMessage(MSG_SOURCE_INFO_REFRESHED,
        new SourceInfo(timeline, manifest, playbackInfo, processedInitialSeekCount)).sendToTarget();
  }

  /**
   * Given a period index into an old timeline, finds the first subsequent period that also exists
   * in a new timeline. The index of this period in the new timeline is returned.
   *
   * @param oldPeriodIndex The index of the period in the old timeline.
   * @param oldTimeline The old timeline.
   * @param newTimeline The new timeline.
   * @return The index in the new timeline of the first subsequent period, or {@link C#INDEX_UNSET}
   *     if no such period was found.
   */
  private int resolveSubsequentPeriod(int oldPeriodIndex, Timeline oldTimeline,
      Timeline newTimeline) {
    int newPeriodIndex = C.INDEX_UNSET;
    while (newPeriodIndex == C.INDEX_UNSET && oldPeriodIndex < oldTimeline.getPeriodCount() - 1) {
      newPeriodIndex = newTimeline.getIndexOfPeriod(
          oldTimeline.getPeriod(++oldPeriodIndex, period, true).uid);
    }
    return newPeriodIndex;
  }

  /**
   * Converts a {@link SeekPosition} into the corresponding (periodIndex, periodPositionUs) for the
   * internal timeline.
   *
   * @param seekPosition The position to resolve.
   * @return The resolved position, or null if resolution was not successful.
   * @throws IllegalSeekPositionException If the window index of the seek position is outside the
   *     bounds of the timeline.
   */
  private Pair<Integer, Long> resolveSeekPosition(SeekPosition seekPosition) {
    Timeline seekTimeline = seekPosition.timeline;
    if (seekTimeline.isEmpty()) {
      // The application performed a blind seek without a non-empty timeline (most likely based on
      // knowledge of what the future timeline will be). Use the internal timeline.
      seekTimeline = timeline;
    }
    // Map the SeekPosition to a position in the corresponding timeline.
    Pair<Integer, Long> periodPosition;
    try {
      periodPosition = getPeriodPosition(seekTimeline, seekPosition.windowIndex,
          seekPosition.windowPositionUs);
    } catch (IndexOutOfBoundsException e) {
      // The window index of the seek position was outside the bounds of the timeline.
      throw new IllegalSeekPositionException(timeline, seekPosition.windowIndex,
          seekPosition.windowPositionUs);
    }
    if (timeline == seekTimeline) {
      // Our internal timeline is the seek timeline, so the mapped position is correct.
      return periodPosition;
    }
    // Attempt to find the mapped period in the internal timeline.
    int periodIndex = timeline.getIndexOfPeriod(
        seekTimeline.getPeriod(periodPosition.first, period, true).uid);
    if (periodIndex != C.INDEX_UNSET) {
      // We successfully located the period in the internal timeline.
      return Pair.create(periodIndex, periodPosition.second);
    }
    // Try and find a subsequent period from the seek timeline in the internal timeline.
    periodIndex = resolveSubsequentPeriod(periodPosition.first, seekTimeline, timeline);
    if (periodIndex != C.INDEX_UNSET) {
      // We found one. Map the SeekPosition onto the corresponding default position.
      return getPeriodPosition(timeline.getPeriod(periodIndex, period).windowIndex, C.TIME_UNSET);
    }
    // We didn't find one. Give up.
    return null;
  }

  /**
   * Calls {@link #getPeriodPosition(Timeline, int, long)} using the current timeline.
   */
  private Pair<Integer, Long> getPeriodPosition(int windowIndex, long windowPositionUs) {
    return getPeriodPosition(timeline, windowIndex, windowPositionUs);
  }

  /**
   * Calls {@link #getPeriodPosition(Timeline, int, long, long)} with a zero default position
   * projection.
   */
  private Pair<Integer, Long> getPeriodPosition(Timeline timeline, int windowIndex,
      long windowPositionUs) {
    return getPeriodPosition(timeline, windowIndex, windowPositionUs, 0);
  }

  /**
   * Converts (windowIndex, windowPositionUs) to the corresponding (periodIndex, periodPositionUs).
   *
   * @param timeline The timeline containing the window.
   * @param windowIndex The window index.
   * @param windowPositionUs The window time, or {@link C#TIME_UNSET} to use the window's default
   *     start position.
   * @param defaultPositionProjectionUs If {@code windowPositionUs} is {@link C#TIME_UNSET}, the
   *     duration into the future by which the window's position should be projected.
   * @return The corresponding (periodIndex, periodPositionUs), or null if {@code #windowPositionUs}
   *     is {@link C#TIME_UNSET}, {@code defaultPositionProjectionUs} is non-zero, and the window's
   *     position could not be projected by {@code defaultPositionProjectionUs}.
   */
  private Pair<Integer, Long> getPeriodPosition(Timeline timeline, int windowIndex,
      long windowPositionUs, long defaultPositionProjectionUs) {
    Assertions.checkIndex(windowIndex, 0, timeline.getWindowCount());
    timeline.getWindow(windowIndex, window, false, defaultPositionProjectionUs);
    if (windowPositionUs == C.TIME_UNSET) {
      windowPositionUs = window.getDefaultPositionUs();
      if (windowPositionUs == C.TIME_UNSET) {
        return null;
      }
    }
    int periodIndex = window.firstPeriodIndex;
    long periodPositionUs = window.getPositionInFirstPeriodUs() + windowPositionUs;
    long periodDurationUs = timeline.getPeriod(periodIndex, period).getDurationUs();
    while (periodDurationUs != C.TIME_UNSET && periodPositionUs >= periodDurationUs
        && periodIndex < window.lastPeriodIndex) {
      periodPositionUs -= periodDurationUs;
      periodDurationUs = timeline.getPeriod(++periodIndex, period).getDurationUs();
    }
    return Pair.create(periodIndex, periodPositionUs);
  }

  private void updatePeriods() throws ExoPlaybackException, IOException {
    if (timeline == null) {
      // We're waiting to get information about periods.
      mediaSource.maybeThrowSourceInfoRefreshError();
      return;
    }

    // Update the loading period if required.
    maybeUpdateLoadingPeriod();
    if (loadingPeriodHolder == null || loadingPeriodHolder.isFullyBuffered()) {
      setIsLoading(false);
    } else if (loadingPeriodHolder != null && loadingPeriodHolder.needsContinueLoading) {
      maybeContinueLoading();
    }

    if (playingPeriodHolder == null) {
      // We're waiting for the first period to be prepared.
      return;
    }

    // Update the playing and reading periods.
    while (playingPeriodHolder != readingPeriodHolder
        && rendererPositionUs >= playingPeriodHolder.next.rendererPositionOffsetUs) {
      // All enabled renderers' streams have been read to the end, and the playback position reached
      // the end of the playing period, so advance playback to the next period.
      playingPeriodHolder.release();
      setPlayingPeriodHolder(playingPeriodHolder.next);
      playbackInfo = new PlaybackInfo(playingPeriodHolder.index,
          playingPeriodHolder.startPositionUs);
      updatePlaybackPositions();
      eventHandler.obtainMessage(MSG_POSITION_DISCONTINUITY, playbackInfo).sendToTarget();
    }

    if (readingPeriodHolder.isLast) {
      for (int i = 0; i < renderers.length; i++) {
        Renderer renderer = renderers[i];
        SampleStream sampleStream = readingPeriodHolder.sampleStreams[i];
        // Defer setting the stream as final until the renderer has actually consumed the whole
        // stream in case of playlist changes that cause the stream to be no longer final.
        if (sampleStream != null && renderer.getStream() == sampleStream
            && renderer.hasReadStreamToEnd()) {
          renderer.setCurrentStreamFinal();
        }
      }
      return;
    }

    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      SampleStream sampleStream = readingPeriodHolder.sampleStreams[i];
      if (renderer.getStream() != sampleStream
          || (sampleStream != null && !renderer.hasReadStreamToEnd())) {
        return;
      }
    }

    if (readingPeriodHolder.next != null && readingPeriodHolder.next.prepared) {
      TrackSelectorResult oldTrackSelectorResult = readingPeriodHolder.trackSelectorResult;
      readingPeriodHolder = readingPeriodHolder.next;
      TrackSelectorResult newTrackSelectorResult = readingPeriodHolder.trackSelectorResult;

      boolean initialDiscontinuity =
          readingPeriodHolder.mediaPeriod.readDiscontinuity() != C.TIME_UNSET;
      for (int i = 0; i < renderers.length; i++) {
        Renderer renderer = renderers[i];
        TrackSelection oldSelection = oldTrackSelectorResult.selections.get(i);
        if (oldSelection == null) {
          // The renderer has no current stream and will be enabled when we play the next period.
        } else if (initialDiscontinuity) {
          // The new period starts with a discontinuity, so the renderer will play out all data then
          // be disabled and re-enabled when it starts playing the next period.
          renderer.setCurrentStreamFinal();
        } else if (!renderer.isCurrentStreamFinal()) {
          TrackSelection newSelection = newTrackSelectorResult.selections.get(i);
          RendererConfiguration oldConfig = oldTrackSelectorResult.rendererConfigurations[i];
          RendererConfiguration newConfig = newTrackSelectorResult.rendererConfigurations[i];
          if (newSelection != null && newConfig.equals(oldConfig)) {
            // Replace the renderer's SampleStream so the transition to playing the next period can
            // be seamless.
            Format[] formats = new Format[newSelection.length()];
            for (int j = 0; j < formats.length; j++) {
              formats[j] = newSelection.getFormat(j);
            }
            renderer.replaceStream(formats, readingPeriodHolder.sampleStreams[i],
                readingPeriodHolder.getRendererOffset());
          } else {
            // The renderer will be disabled when transitioning to playing the next period, either
            // because there's no new selection or because a configuration change is required. Mark
            // the SampleStream as final to play out any remaining data.
            renderer.setCurrentStreamFinal();
          }
        }
      }
    }
  }

  private void maybeUpdateLoadingPeriod() throws IOException {
    int newLoadingPeriodIndex;
    if (loadingPeriodHolder == null) {
      newLoadingPeriodIndex = playbackInfo.periodIndex;
    } else {
      int loadingPeriodIndex = loadingPeriodHolder.index;
      if (loadingPeriodHolder.isLast || !loadingPeriodHolder.isFullyBuffered()
          || timeline.getPeriod(loadingPeriodIndex, period).getDurationUs() == C.TIME_UNSET) {
        // Either the existing loading period is the last period, or we are not ready to advance to
        // loading the next period because it hasn't been fully buffered or its duration is unknown.
        return;
      }
      if (playingPeriodHolder != null
          && loadingPeriodIndex - playingPeriodHolder.index == MAXIMUM_BUFFER_AHEAD_PERIODS) {
        // We are already buffering the maximum number of periods ahead.
        return;
      }
      newLoadingPeriodIndex = loadingPeriodHolder.index + 1;
    }

    if (newLoadingPeriodIndex >= timeline.getPeriodCount()) {
      // The next period is not available yet.
      mediaSource.maybeThrowSourceInfoRefreshError();
      return;
    }

    long newLoadingPeriodStartPositionUs;
    if (loadingPeriodHolder == null) {
      newLoadingPeriodStartPositionUs = playbackInfo.positionUs;
    } else {
      int newLoadingWindowIndex = timeline.getPeriod(newLoadingPeriodIndex, period).windowIndex;
      if (newLoadingPeriodIndex
          != timeline.getWindow(newLoadingWindowIndex, window).firstPeriodIndex) {
        // We're starting to buffer a new period in the current window. Always start from the
        // beginning of the period.
        newLoadingPeriodStartPositionUs = 0;
      } else {
        // We're starting to buffer a new window. When playback transitions to this window we'll
        // want it to be from its default start position. The expected delay until playback
        // transitions is equal the duration of media that's currently buffered (assuming no
        // interruptions). Hence we project the default start position forward by the duration of
        // the buffer, and start buffering from this point.
        long defaultPositionProjectionUs = loadingPeriodHolder.getRendererOffset()
            + timeline.getPeriod(loadingPeriodHolder.index, period).getDurationUs()
            - rendererPositionUs;
        Pair<Integer, Long> defaultPosition = getPeriodPosition(timeline, newLoadingWindowIndex,
            C.TIME_UNSET, Math.max(0, defaultPositionProjectionUs));
        if (defaultPosition == null) {
          return;
        }

        newLoadingPeriodIndex = defaultPosition.first;
        newLoadingPeriodStartPositionUs = defaultPosition.second;
      }
    }

    long rendererPositionOffsetUs = loadingPeriodHolder == null
        ? newLoadingPeriodStartPositionUs + RENDERER_TIMESTAMP_OFFSET_US
        : (loadingPeriodHolder.getRendererOffset()
            + timeline.getPeriod(loadingPeriodHolder.index, period).getDurationUs());
    timeline.getPeriod(newLoadingPeriodIndex, period, true);
    boolean isLastPeriod = newLoadingPeriodIndex == timeline.getPeriodCount() - 1
        && !timeline.getWindow(period.windowIndex, window).isDynamic;
    MediaPeriodHolder newPeriodHolder = new MediaPeriodHolder(renderers, rendererCapabilities,
        rendererPositionOffsetUs, trackSelector, loadControl, mediaSource, period.uid,
        newLoadingPeriodIndex, isLastPeriod, newLoadingPeriodStartPositionUs);
    if (loadingPeriodHolder != null) {
      loadingPeriodHolder.next = newPeriodHolder;
    }
    loadingPeriodHolder = newPeriodHolder;
    loadingPeriodHolder.mediaPeriod.prepare(this);
    setIsLoading(true);
  }

  private void handlePeriodPrepared(MediaPeriod period) throws ExoPlaybackException {
    if (loadingPeriodHolder == null || loadingPeriodHolder.mediaPeriod != period) {
      // Stale event.
      return;
    }
    loadingPeriodHolder.handlePrepared();
    if (playingPeriodHolder == null) {
      // This is the first prepared period, so start playing it.
      readingPeriodHolder = loadingPeriodHolder;
      resetRendererPosition(readingPeriodHolder.startPositionUs);
      setPlayingPeriodHolder(readingPeriodHolder);
    }
    maybeContinueLoading();
  }

  private void handleContinueLoadingRequested(MediaPeriod period) {
    if (loadingPeriodHolder == null || loadingPeriodHolder.mediaPeriod != period) {
      // Stale event.
      return;
    }
    maybeContinueLoading();
  }

  private void maybeContinueLoading() {
    long nextLoadPositionUs = !loadingPeriodHolder.prepared ? 0
        : loadingPeriodHolder.mediaPeriod.getNextLoadPositionUs();
    if (nextLoadPositionUs == C.TIME_END_OF_SOURCE) {
      setIsLoading(false);
    } else {
      long loadingPeriodPositionUs = loadingPeriodHolder.toPeriodTime(rendererPositionUs);
      long bufferedDurationUs = nextLoadPositionUs - loadingPeriodPositionUs;
      boolean continueLoading = loadControl.shouldContinueLoading(bufferedDurationUs);
      setIsLoading(continueLoading);
      if (continueLoading) {
        loadingPeriodHolder.needsContinueLoading = false;
        loadingPeriodHolder.mediaPeriod.continueLoading(loadingPeriodPositionUs);
      } else {
        loadingPeriodHolder.needsContinueLoading = true;
      }
    }
  }

  private void releasePeriodHoldersFrom(MediaPeriodHolder periodHolder) {
    while (periodHolder != null) {
      periodHolder.release();
      periodHolder = periodHolder.next;
    }
  }

  private void setPlayingPeriodHolder(MediaPeriodHolder periodHolder) throws ExoPlaybackException {
    if (playingPeriodHolder == periodHolder) {
      return;
    }

    int enabledRendererCount = 0;
    boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      rendererWasEnabledFlags[i] = renderer.getState() != Renderer.STATE_DISABLED;
      TrackSelection newSelection = periodHolder.trackSelectorResult.selections.get(i);
      if (newSelection != null) {
        enabledRendererCount++;
      }
      if (rendererWasEnabledFlags[i] && (newSelection == null
          || (renderer.isCurrentStreamFinal()
          && renderer.getStream() == playingPeriodHolder.sampleStreams[i]))) {
        // The renderer should be disabled before playing the next period, either because it's not
        // needed to play the next period, or because we need to re-enable it as its current stream
        // is final and it's not reading ahead.
        if (renderer == rendererMediaClockSource) {
          // Sync standaloneMediaClock so that it can take over timing responsibilities.
          standaloneMediaClock.synchronize(rendererMediaClock);
          rendererMediaClock = null;
          rendererMediaClockSource = null;
        }
        ensureStopped(renderer);
        renderer.disable();
      }
    }

    playingPeriodHolder = periodHolder;
    eventHandler.obtainMessage(MSG_TRACKS_CHANGED, periodHolder.trackSelectorResult).sendToTarget();
    enableRenderers(rendererWasEnabledFlags, enabledRendererCount);
  }

  private void enableRenderers(boolean[] rendererWasEnabledFlags, int enabledRendererCount)
      throws ExoPlaybackException {
    enabledRenderers = new Renderer[enabledRendererCount];
    enabledRendererCount = 0;
    for (int i = 0; i < renderers.length; i++) {
      Renderer renderer = renderers[i];
      TrackSelection newSelection = playingPeriodHolder.trackSelectorResult.selections.get(i);
      if (newSelection != null) {
        enabledRenderers[enabledRendererCount++] = renderer;
        if (renderer.getState() == Renderer.STATE_DISABLED) {
          RendererConfiguration rendererConfiguration =
              playingPeriodHolder.trackSelectorResult.rendererConfigurations[i];
          // The renderer needs enabling with its new track selection.
          boolean playing = playWhenReady && state == ExoPlayer.STATE_READY;
          // Consider as joining only if the renderer was previously disabled.
          boolean joining = !rendererWasEnabledFlags[i] && playing;
          // Build an array of formats contained by the selection.
          Format[] formats = new Format[newSelection.length()];
          for (int j = 0; j < formats.length; j++) {
            formats[j] = newSelection.getFormat(j);
          }
          // Enable the renderer.
          renderer.enable(rendererConfiguration, formats, playingPeriodHolder.sampleStreams[i],
              rendererPositionUs, joining, playingPeriodHolder.getRendererOffset());
          MediaClock mediaClock = renderer.getMediaClock();
          if (mediaClock != null) {
            if (rendererMediaClock != null) {
              throw ExoPlaybackException.createForUnexpected(
                  new IllegalStateException("Multiple renderer media clocks enabled."));
            }
            rendererMediaClock = mediaClock;
            rendererMediaClockSource = renderer;
            rendererMediaClock.setPlaybackParameters(playbackParameters);
          }
          // Start the renderer if playing.
          if (playing) {
            renderer.start();
          }
        }
      }
    }
  }

  /**
   * Holds a {@link MediaPeriod} with information required to play it as part of a timeline.
   */
  private static final class MediaPeriodHolder {

    public final MediaPeriod mediaPeriod;
    public final Object uid;
    public final SampleStream[] sampleStreams;
    public final boolean[] mayRetainStreamFlags;
    public final long rendererPositionOffsetUs;

    public int index;
    public long startPositionUs;
    public boolean isLast;
    public boolean prepared;
    public boolean hasEnabledTracks;
    public MediaPeriodHolder next;
    public boolean needsContinueLoading;
    public TrackSelectorResult trackSelectorResult;

    private final Renderer[] renderers;
    private final RendererCapabilities[] rendererCapabilities;
    private final TrackSelector trackSelector;
    private final LoadControl loadControl;
    private final MediaSource mediaSource;

    private TrackSelectorResult periodTrackSelectorResult;

    public MediaPeriodHolder(Renderer[] renderers, RendererCapabilities[] rendererCapabilities,
        long rendererPositionOffsetUs, TrackSelector trackSelector, LoadControl loadControl,
        MediaSource mediaSource, Object periodUid, int periodIndex, boolean isLastPeriod,
        long startPositionUs) {
      this.renderers = renderers;
      this.rendererCapabilities = rendererCapabilities;
      this.rendererPositionOffsetUs = rendererPositionOffsetUs;
      this.trackSelector = trackSelector;
      this.loadControl = loadControl;
      this.mediaSource = mediaSource;
      this.uid = Assertions.checkNotNull(periodUid);
      this.index = periodIndex;
      this.isLast = isLastPeriod;
      this.startPositionUs = startPositionUs;
      sampleStreams = new SampleStream[renderers.length];
      mayRetainStreamFlags = new boolean[renderers.length];
      mediaPeriod = mediaSource.createPeriod(periodIndex, loadControl.getAllocator(),
          startPositionUs);
    }

    public long toRendererTime(long periodTimeUs) {
      return periodTimeUs + getRendererOffset();
    }

    public long toPeriodTime(long rendererTimeUs) {
      return rendererTimeUs - getRendererOffset();
    }

    public long getRendererOffset() {
      return rendererPositionOffsetUs - startPositionUs;
    }

    public void setIndex(int index, boolean isLast) {
      this.index = index;
      this.isLast = isLast;
    }

    public boolean isFullyBuffered() {
      return prepared
          && (!hasEnabledTracks || mediaPeriod.getBufferedPositionUs() == C.TIME_END_OF_SOURCE);
    }

    public void handlePrepared() throws ExoPlaybackException {
      prepared = true;
      selectTracks();
      startPositionUs = updatePeriodTrackSelection(startPositionUs, false);
    }

    public boolean selectTracks() throws ExoPlaybackException {
      TrackSelectorResult selectorResult = trackSelector.selectTracks(rendererCapabilities,
          mediaPeriod.getTrackGroups());
      if (selectorResult.isEquivalent(periodTrackSelectorResult)) {
        return false;
      }
      trackSelectorResult = selectorResult;
      return true;
    }

    public long updatePeriodTrackSelection(long positionUs, boolean forceRecreateStreams) {
      return updatePeriodTrackSelection(positionUs, forceRecreateStreams,
          new boolean[renderers.length]);
    }

    public long updatePeriodTrackSelection(long positionUs, boolean forceRecreateStreams,
        boolean[] streamResetFlags) {
      TrackSelectionArray trackSelections = trackSelectorResult.selections;
      for (int i = 0; i < trackSelections.length; i++) {
        mayRetainStreamFlags[i] = !forceRecreateStreams
            && trackSelectorResult.isEquivalent(periodTrackSelectorResult, i);
      }

      // Disable streams on the period and get new streams for updated/newly-enabled tracks.
      positionUs = mediaPeriod.selectTracks(trackSelections.getAll(), mayRetainStreamFlags,
          sampleStreams, streamResetFlags, positionUs);
      periodTrackSelectorResult = trackSelectorResult;

      // Update whether we have enabled tracks and sanity check the expected streams are non-null.
      hasEnabledTracks = false;
      for (int i = 0; i < sampleStreams.length; i++) {
        if (sampleStreams[i] != null) {
          Assertions.checkState(trackSelections.get(i) != null);
          hasEnabledTracks = true;
        } else {
          Assertions.checkState(trackSelections.get(i) == null);
        }
      }

      // The track selection has changed.
      loadControl.onTracksSelected(renderers, trackSelectorResult.groups, trackSelections);
      return positionUs;
    }

    public void release() {
      try {
        mediaSource.releasePeriod(mediaPeriod);
      } catch (RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Period release failed.", e);
      }
    }

  }

  private static final class SeekPosition {

    public final Timeline timeline;
    public final int windowIndex;
    public final long windowPositionUs;

    public SeekPosition(Timeline timeline, int windowIndex, long windowPositionUs) {
      this.timeline = timeline;
      this.windowIndex = windowIndex;
      this.windowPositionUs = windowPositionUs;
    }

  }

}
