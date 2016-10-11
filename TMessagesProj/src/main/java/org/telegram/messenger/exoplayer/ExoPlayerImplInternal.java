/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import org.telegram.messenger.exoplayer.ExoPlayer.ExoPlayerComponent;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.PriorityHandlerThread;
import org.telegram.messenger.exoplayer.util.TraceUtil;
import org.telegram.messenger.exoplayer.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements the internal behavior of {@link ExoPlayerImpl}.
 */
/* package */ final class ExoPlayerImplInternal implements Handler.Callback {

  private static final String TAG = "ExoPlayerImplInternal";

  // External messages
  public static final int MSG_PREPARED = 1;
  public static final int MSG_STATE_CHANGED = 2;
  public static final int MSG_SET_PLAY_WHEN_READY_ACK = 3;
  public static final int MSG_ERROR = 4;

  // Internal messages
  private static final int MSG_PREPARE = 1;
  private static final int MSG_INCREMENTAL_PREPARE = 2;
  private static final int MSG_SET_PLAY_WHEN_READY = 3;
  private static final int MSG_STOP = 4;
  private static final int MSG_RELEASE = 5;
  private static final int MSG_SEEK_TO = 6;
  private static final int MSG_DO_SOME_WORK = 7;
  private static final int MSG_SET_RENDERER_SELECTED_TRACK = 8;
  private static final int MSG_CUSTOM = 9;

  private static final int PREPARE_INTERVAL_MS = 10;
  private static final int RENDERING_INTERVAL_MS = 10;
  private static final int IDLE_INTERVAL_MS = 1000;

  private final Handler handler;
  private final HandlerThread internalPlaybackThread;
  private final Handler eventHandler;
  private final StandaloneMediaClock standaloneMediaClock;
  private final AtomicInteger pendingSeekCount;
  private final List<TrackRenderer> enabledRenderers;
  private final MediaFormat[][] trackFormats;
  private final int[] selectedTrackIndices;
  private final long minBufferUs;
  private final long minRebufferUs;

  private TrackRenderer[] renderers;
  private TrackRenderer rendererMediaClockSource;
  private MediaClock rendererMediaClock;

  private boolean released;
  private boolean playWhenReady;
  private boolean rebuffering;
  private int state;
  private int customMessagesSent = 0;
  private int customMessagesProcessed = 0;
  private long lastSeekPositionMs;
  private long elapsedRealtimeUs;

  private volatile long durationUs;
  private volatile long positionUs;
  private volatile long bufferedPositionUs;

  public ExoPlayerImplInternal(Handler eventHandler, boolean playWhenReady,
      int[] selectedTrackIndices, int minBufferMs, int minRebufferMs) {
    this.eventHandler = eventHandler;
    this.playWhenReady = playWhenReady;
    this.minBufferUs = minBufferMs * 1000L;
    this.minRebufferUs = minRebufferMs * 1000L;
    this.selectedTrackIndices = Arrays.copyOf(selectedTrackIndices, selectedTrackIndices.length);
    this.state = ExoPlayer.STATE_IDLE;
    this.durationUs = TrackRenderer.UNKNOWN_TIME_US;
    this.bufferedPositionUs = TrackRenderer.UNKNOWN_TIME_US;

    standaloneMediaClock = new StandaloneMediaClock();
    pendingSeekCount = new AtomicInteger();
    enabledRenderers = new ArrayList<>(selectedTrackIndices.length);
    trackFormats = new MediaFormat[selectedTrackIndices.length][];
    // Note: The documentation for Process.THREAD_PRIORITY_AUDIO that states "Applications can
    // not normally change to this priority" is incorrect.
    internalPlaybackThread = new PriorityHandlerThread("ExoPlayerImplInternal:Handler",
        Process.THREAD_PRIORITY_AUDIO);
    internalPlaybackThread.start();
    handler = new Handler(internalPlaybackThread.getLooper(), this);
  }

  public Looper getPlaybackLooper() {
    return internalPlaybackThread.getLooper();
  }

  public long getCurrentPosition() {
    return pendingSeekCount.get() > 0 ? lastSeekPositionMs : (positionUs / 1000);
  }

  public long getBufferedPosition() {
    return bufferedPositionUs == TrackRenderer.UNKNOWN_TIME_US ? ExoPlayer.UNKNOWN_TIME
        : bufferedPositionUs / 1000;
  }

  public long getDuration() {
    return durationUs == TrackRenderer.UNKNOWN_TIME_US ? ExoPlayer.UNKNOWN_TIME
        : durationUs / 1000;
  }

  public void prepare(TrackRenderer... renderers) {
    handler.obtainMessage(MSG_PREPARE, renderers).sendToTarget();
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    handler.obtainMessage(MSG_SET_PLAY_WHEN_READY, playWhenReady ? 1 : 0, 0).sendToTarget();
  }

  public void seekTo(long positionMs) {
    lastSeekPositionMs = positionMs;
    pendingSeekCount.incrementAndGet();
    handler.obtainMessage(MSG_SEEK_TO, Util.getTopInt(positionMs),
        Util.getBottomInt(positionMs)).sendToTarget();
  }

  public void stop() {
    handler.sendEmptyMessage(MSG_STOP);
  }

  public void setRendererSelectedTrack(int rendererIndex, int trackIndex) {
    handler.obtainMessage(MSG_SET_RENDERER_SELECTED_TRACK, rendererIndex, trackIndex)
        .sendToTarget();
  }

  public void sendMessage(ExoPlayerComponent target, int messageType, Object message) {
    customMessagesSent++;
    handler.obtainMessage(MSG_CUSTOM, messageType, 0, Pair.create(target, message)).sendToTarget();
  }

  public synchronized void blockingSendMessage(ExoPlayerComponent target, int messageType,
      Object message) {
    if (released) {
      Log.w(TAG, "Sent message(" + messageType + ") after release. Message ignored.");
      return;
    }
    int messageNumber = customMessagesSent++;
    handler.obtainMessage(MSG_CUSTOM, messageType, 0, Pair.create(target, message)).sendToTarget();
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

  @Override
  public boolean handleMessage(Message msg) {
    try {
      switch (msg.what) {
        case MSG_PREPARE: {
          prepareInternal((TrackRenderer[]) msg.obj);
          return true;
        }
        case MSG_INCREMENTAL_PREPARE: {
          incrementalPrepareInternal();
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
          seekToInternal(Util.getLong(msg.arg1, msg.arg2));
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
        case MSG_CUSTOM: {
          sendMessageInternal(msg.arg1, msg.obj);
          return true;
        }
        case MSG_SET_RENDERER_SELECTED_TRACK: {
          setRendererSelectedTrackInternal(msg.arg1, msg.arg2);
          return true;
        }
        default:
          return false;
      }
    } catch (ExoPlaybackException e) {
      Log.e(TAG, "Internal track renderer error.", e);
      eventHandler.obtainMessage(MSG_ERROR, e).sendToTarget();
      stopInternal();
      return true;
    } catch (RuntimeException e) {
      Log.e(TAG, "Internal runtime error.", e);
      eventHandler.obtainMessage(MSG_ERROR, new ExoPlaybackException(e, true)).sendToTarget();
      stopInternal();
      return true;
    }
  }

  private void setState(int state) {
    if (this.state != state) {
      this.state = state;
      eventHandler.obtainMessage(MSG_STATE_CHANGED, state, 0).sendToTarget();
    }
  }

  private void prepareInternal(TrackRenderer[] renderers) throws ExoPlaybackException {
    resetInternal();
    this.renderers = renderers;
    Arrays.fill(trackFormats, null);
    setState(ExoPlayer.STATE_PREPARING);
    incrementalPrepareInternal();
  }

  private void incrementalPrepareInternal() throws ExoPlaybackException {
    long operationStartTimeMs = SystemClock.elapsedRealtime();
    boolean prepared = true;
    for (int rendererIndex = 0; rendererIndex < renderers.length; rendererIndex++) {
      TrackRenderer renderer = renderers[rendererIndex];
      if (renderer.getState() == TrackRenderer.STATE_UNPREPARED) {
        int state = renderer.prepare(positionUs);
        if (state == TrackRenderer.STATE_UNPREPARED) {
          renderer.maybeThrowError();
          prepared = false;
        }
      }
    }

    if (!prepared) {
      // We're still waiting for some sources to be prepared.
      scheduleNextOperation(MSG_INCREMENTAL_PREPARE, operationStartTimeMs, PREPARE_INTERVAL_MS);
      return;
    }

    long durationUs = 0;
    boolean allRenderersEnded = true;
    boolean allRenderersReadyOrEnded = true;
    for (int rendererIndex = 0; rendererIndex < renderers.length; rendererIndex++) {
      TrackRenderer renderer = renderers[rendererIndex];
      int rendererTrackCount = renderer.getTrackCount();
      MediaFormat[] rendererTrackFormats = new MediaFormat[rendererTrackCount];
      for (int trackIndex = 0; trackIndex < rendererTrackCount; trackIndex++) {
        rendererTrackFormats[trackIndex] = renderer.getFormat(trackIndex);
      }
      trackFormats[rendererIndex] = rendererTrackFormats;
      if (rendererTrackCount > 0) {
        if (durationUs == TrackRenderer.UNKNOWN_TIME_US) {
          // We've already encountered a track for which the duration is unknown, so the media
          // duration is unknown regardless of the duration of this track.
        } else {
          long trackDurationUs = renderer.getDurationUs();
          if (trackDurationUs == TrackRenderer.UNKNOWN_TIME_US) {
            durationUs = TrackRenderer.UNKNOWN_TIME_US;
          } else if (trackDurationUs == TrackRenderer.MATCH_LONGEST_US) {
            // Do nothing.
          } else {
            durationUs = Math.max(durationUs, trackDurationUs);
          }
        }
        int trackIndex = selectedTrackIndices[rendererIndex];
        if (0 <= trackIndex && trackIndex < rendererTrackFormats.length) {
          enableRenderer(renderer, trackIndex, false);
          allRenderersEnded = allRenderersEnded && renderer.isEnded();
          allRenderersReadyOrEnded = allRenderersReadyOrEnded && rendererReadyOrEnded(renderer);
        }
      }
    }
    this.durationUs = durationUs;

    if (allRenderersEnded
        && (durationUs == TrackRenderer.UNKNOWN_TIME_US || durationUs <= positionUs)) {
      // We don't expect this case, but handle it anyway.
      state = ExoPlayer.STATE_ENDED;
    } else {
      state = allRenderersReadyOrEnded ? ExoPlayer.STATE_READY : ExoPlayer.STATE_BUFFERING;
    }

    // Fire an event indicating that the player has been prepared, passing the initial state and
    // renderer track information.
    eventHandler.obtainMessage(MSG_PREPARED, state, 0, trackFormats).sendToTarget();

    // Start the renderers if required, and schedule the first piece of work.
    if (playWhenReady && state == ExoPlayer.STATE_READY) {
      startRenderers();
    }
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private void enableRenderer(TrackRenderer renderer, int trackIndex, boolean joining)
      throws ExoPlaybackException {
    renderer.enable(trackIndex, positionUs, joining);
    enabledRenderers.add(renderer);
    MediaClock mediaClock = renderer.getMediaClock();
    if (mediaClock != null) {
      Assertions.checkState(rendererMediaClock == null);
      rendererMediaClock = mediaClock;
      rendererMediaClockSource = renderer;
    }
  }

  private boolean rendererReadyOrEnded(TrackRenderer renderer) {
    if (renderer.isEnded()) {
      return true;
    }
    if (!renderer.isReady()) {
      return false;
    }
    if (state == ExoPlayer.STATE_READY) {
      return true;
    }
    long rendererDurationUs = renderer.getDurationUs();
    long rendererBufferedPositionUs = renderer.getBufferedPositionUs();
    long minBufferDurationUs = rebuffering ? minRebufferUs : minBufferUs;
    return minBufferDurationUs <= 0
        || rendererBufferedPositionUs == TrackRenderer.UNKNOWN_TIME_US
        || rendererBufferedPositionUs == TrackRenderer.END_OF_TRACK_US
        || rendererBufferedPositionUs >= positionUs + minBufferDurationUs
        || (rendererDurationUs != TrackRenderer.UNKNOWN_TIME_US
            && rendererDurationUs != TrackRenderer.MATCH_LONGEST_US
            && rendererBufferedPositionUs >= rendererDurationUs);
  }

  private void setPlayWhenReadyInternal(boolean playWhenReady) throws ExoPlaybackException {
    try {
      rebuffering = false;
      this.playWhenReady = playWhenReady;
      if (!playWhenReady) {
        stopRenderers();
        updatePositionUs();
      } else {
        if (state == ExoPlayer.STATE_READY) {
          startRenderers();
          handler.sendEmptyMessage(MSG_DO_SOME_WORK);
        } else if (state == ExoPlayer.STATE_BUFFERING) {
          handler.sendEmptyMessage(MSG_DO_SOME_WORK);
        }
      }
    } finally {
      eventHandler.obtainMessage(MSG_SET_PLAY_WHEN_READY_ACK).sendToTarget();
    }
  }

  private void startRenderers() throws ExoPlaybackException {
    rebuffering = false;
    standaloneMediaClock.start();
    for (int i = 0; i < enabledRenderers.size(); i++) {
      enabledRenderers.get(i).start();
    }
  }

  private void stopRenderers() throws ExoPlaybackException {
    standaloneMediaClock.stop();
    for (int i = 0; i < enabledRenderers.size(); i++) {
      ensureStopped(enabledRenderers.get(i));
    }
  }

  private void updatePositionUs() {
    if (rendererMediaClock != null && enabledRenderers.contains(rendererMediaClockSource)
        && !rendererMediaClockSource.isEnded()) {
      positionUs = rendererMediaClock.getPositionUs();
      standaloneMediaClock.setPositionUs(positionUs);
    } else {
      positionUs = standaloneMediaClock.getPositionUs();
    }
    elapsedRealtimeUs = SystemClock.elapsedRealtime() * 1000;
  }

  private void doSomeWork() throws ExoPlaybackException {
    TraceUtil.beginSection("doSomeWork");
    long operationStartTimeMs = SystemClock.elapsedRealtime();
    long bufferedPositionUs = durationUs != TrackRenderer.UNKNOWN_TIME_US ? durationUs
        : Long.MAX_VALUE;
    boolean allRenderersEnded = true;
    boolean allRenderersReadyOrEnded = true;
    updatePositionUs();
    for (int i = 0; i < enabledRenderers.size(); i++) {
      TrackRenderer renderer = enabledRenderers.get(i);
      // TODO: Each renderer should return the maximum delay before which it wishes to be
      // invoked again. The minimum of these values should then be used as the delay before the next
      // invocation of this method.
      renderer.doSomeWork(positionUs, elapsedRealtimeUs);
      allRenderersEnded = allRenderersEnded && renderer.isEnded();

      // Determine whether the renderer is ready (or ended). If it's not, throw an error that's
      // preventing the renderer from making progress, if such an error exists.
      boolean rendererReadyOrEnded = rendererReadyOrEnded(renderer);
      if (!rendererReadyOrEnded) {
        renderer.maybeThrowError();
      }
      allRenderersReadyOrEnded = allRenderersReadyOrEnded && rendererReadyOrEnded;

      if (bufferedPositionUs == TrackRenderer.UNKNOWN_TIME_US) {
        // We've already encountered a track for which the buffered position is unknown. Hence the
        // media buffer position unknown regardless of the buffered position of this track.
      } else {
        long rendererDurationUs = renderer.getDurationUs();
        long rendererBufferedPositionUs = renderer.getBufferedPositionUs();
        if (rendererBufferedPositionUs == TrackRenderer.UNKNOWN_TIME_US) {
          bufferedPositionUs = TrackRenderer.UNKNOWN_TIME_US;
        } else if (rendererBufferedPositionUs == TrackRenderer.END_OF_TRACK_US
            || (rendererDurationUs != TrackRenderer.UNKNOWN_TIME_US
                && rendererDurationUs != TrackRenderer.MATCH_LONGEST_US
                && rendererBufferedPositionUs >= rendererDurationUs)) {
          // This track is fully buffered.
        } else {
          bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
        }
      }
    }
    this.bufferedPositionUs = bufferedPositionUs;

    if (allRenderersEnded
        && (durationUs == TrackRenderer.UNKNOWN_TIME_US || durationUs <= positionUs)) {
      setState(ExoPlayer.STATE_ENDED);
      stopRenderers();
    } else if (state == ExoPlayer.STATE_BUFFERING && allRenderersReadyOrEnded) {
      setState(ExoPlayer.STATE_READY);
      if (playWhenReady) {
        startRenderers();
      }
    } else if (state == ExoPlayer.STATE_READY && !allRenderersReadyOrEnded) {
      rebuffering = playWhenReady;
      setState(ExoPlayer.STATE_BUFFERING);
      stopRenderers();
    }

    handler.removeMessages(MSG_DO_SOME_WORK);
    if ((playWhenReady && state == ExoPlayer.STATE_READY) || state == ExoPlayer.STATE_BUFFERING) {
      scheduleNextOperation(MSG_DO_SOME_WORK, operationStartTimeMs, RENDERING_INTERVAL_MS);
    } else if (!enabledRenderers.isEmpty()) {
      scheduleNextOperation(MSG_DO_SOME_WORK, operationStartTimeMs, IDLE_INTERVAL_MS);
    }

    TraceUtil.endSection();
  }

  private void scheduleNextOperation(int operationType, long thisOperationStartTimeMs,
      long intervalMs) {
    long nextOperationStartTimeMs = thisOperationStartTimeMs + intervalMs;
    long nextOperationDelayMs = nextOperationStartTimeMs - SystemClock.elapsedRealtime();
    if (nextOperationDelayMs <= 0) {
      handler.sendEmptyMessage(operationType);
    } else {
      handler.sendEmptyMessageDelayed(operationType, nextOperationDelayMs);
    }
  }

  private void seekToInternal(long positionMs) throws ExoPlaybackException {
    try {
      if (positionMs == (positionUs / 1000)) {
        // Seek is to the current position. Do nothing.
        return;
      }

      rebuffering = false;
      positionUs = positionMs * 1000;
      standaloneMediaClock.stop();
      standaloneMediaClock.setPositionUs(positionUs);
      if (state == ExoPlayer.STATE_IDLE || state == ExoPlayer.STATE_PREPARING) {
        return;
      }
      for (int i = 0; i < enabledRenderers.size(); i++) {
        TrackRenderer renderer = enabledRenderers.get(i);
        ensureStopped(renderer);
        renderer.seekTo(positionUs);
      }
      setState(ExoPlayer.STATE_BUFFERING);
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    } finally {
      pendingSeekCount.decrementAndGet();
    }
  }

  private void stopInternal() {
    resetInternal();
    setState(ExoPlayer.STATE_IDLE);
  }

  private void releaseInternal() {
    resetInternal();
    setState(ExoPlayer.STATE_IDLE);
    synchronized (this) {
      released = true;
      notifyAll();
    }
  }

  private void resetInternal() {
    handler.removeMessages(MSG_DO_SOME_WORK);
    handler.removeMessages(MSG_INCREMENTAL_PREPARE);
    rebuffering = false;
    standaloneMediaClock.stop();
    if (renderers == null) {
      return;
    }
    for (int i = 0; i < renderers.length; i++) {
      TrackRenderer renderer = renderers[i];
      stopAndDisable(renderer);
      release(renderer);
    }
    renderers = null;
    rendererMediaClock = null;
    rendererMediaClockSource = null;
    enabledRenderers.clear();
  }

  private void stopAndDisable(TrackRenderer renderer) {
    try {
      ensureDisabled(renderer);
    } catch (ExoPlaybackException e) {
      // There's nothing we can do.
      Log.e(TAG, "Stop failed.", e);
    } catch (RuntimeException e) {
      // Ditto.
      Log.e(TAG, "Stop failed.", e);
    }
  }

  private void release(TrackRenderer renderer) {
    try {
      renderer.release();
    } catch (ExoPlaybackException e) {
      // There's nothing we can do.
      Log.e(TAG, "Release failed.", e);
    } catch (RuntimeException e) {
      // Ditto.
      Log.e(TAG, "Release failed.", e);
    }
  }

  private <T> void sendMessageInternal(int what, Object obj)
      throws ExoPlaybackException {
    try {
      @SuppressWarnings("unchecked")
      Pair<ExoPlayerComponent, Object> targetAndMessage = (Pair<ExoPlayerComponent, Object>) obj;
      targetAndMessage.first.handleMessage(what, targetAndMessage.second);
      if (state != ExoPlayer.STATE_IDLE && state != ExoPlayer.STATE_PREPARING) {
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

  private void setRendererSelectedTrackInternal(int rendererIndex, int trackIndex)
      throws ExoPlaybackException {
    if (selectedTrackIndices[rendererIndex] == trackIndex) {
      return;
    }

    selectedTrackIndices[rendererIndex] = trackIndex;
    if (state == ExoPlayer.STATE_IDLE || state == ExoPlayer.STATE_PREPARING) {
      return;
    }

    TrackRenderer renderer = renderers[rendererIndex];
    int rendererState = renderer.getState();
    if (rendererState == TrackRenderer.STATE_UNPREPARED
        || rendererState == TrackRenderer.STATE_RELEASED
        || renderer.getTrackCount() == 0) {
      return;
    }

    boolean isEnabled = rendererState == TrackRenderer.STATE_ENABLED
        || rendererState == TrackRenderer.STATE_STARTED;
    boolean shouldEnable = 0 <= trackIndex && trackIndex < trackFormats[rendererIndex].length;

    if (isEnabled) {
      // The renderer is currently enabled. We need to disable it, so that we can either re-enable
      // it with the newly selected track (if shouldEnable is true) or because we want to leave it
      // disabled (if shouldEnable is false).
      if (!shouldEnable && renderer == rendererMediaClockSource) {
        // We've been using rendererMediaClockSource to advance the current position, but it's being
        // disabled and won't be re-enabled. Sync standaloneMediaClock so that it can take over
        // timing responsibilities.
        standaloneMediaClock.setPositionUs(rendererMediaClock.getPositionUs());
      }
      ensureDisabled(renderer);
      enabledRenderers.remove(renderer);
    }

    if (shouldEnable) {
      // Re-enable the renderer with the newly selected track.
      boolean playing = playWhenReady && state == ExoPlayer.STATE_READY;
      // Consider as joining if the renderer was previously disabled, but not when switching tracks.
      boolean joining = !isEnabled && playing;
      enableRenderer(renderer, trackIndex, joining);
      if (playing) {
        renderer.start();
      }
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    }
  }

  private void ensureStopped(TrackRenderer renderer) throws ExoPlaybackException {
    if (renderer.getState() == TrackRenderer.STATE_STARTED) {
      renderer.stop();
    }
  }

  private void ensureDisabled(TrackRenderer renderer) throws ExoPlaybackException {
    ensureStopped(renderer);
    if (renderer.getState() == TrackRenderer.STATE_ENABLED) {
      renderer.disable();
      if (renderer == rendererMediaClockSource) {
        rendererMediaClock = null;
        rendererMediaClockSource = null;
      }
    }
  }
}
