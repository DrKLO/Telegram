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
package org.telegram.messenger.exoplayer2.ui;

import android.widget.TextView;
import org.telegram.messenger.exoplayer2.ExoPlaybackException;
import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.SimpleExoPlayer;
import org.telegram.messenger.exoplayer2.Timeline;
import org.telegram.messenger.exoplayer2.decoder.DecoderCounters;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelectionArray;

/**
 * A helper class for periodically updating a {@link TextView} with debug information obtained from
 * a {@link SimpleExoPlayer}.
 */
public final class DebugTextViewHelper implements Runnable, ExoPlayer.EventListener {

  private static final int REFRESH_INTERVAL_MS = 1000;

  private final SimpleExoPlayer player;
  private final TextView textView;

  private boolean started;

  /**
   * @param player The {@link SimpleExoPlayer} from which debug information should be obtained.
   * @param textView The {@link TextView} that should be updated to display the information.
   */
  public DebugTextViewHelper(SimpleExoPlayer player, TextView textView) {
    this.player = player;
    this.textView = textView;
  }

  /**
   * Starts periodic updates of the {@link TextView}. Must be called from the application's main
   * thread.
   */
  public void start() {
    if (started) {
      return;
    }
    started = true;
    player.addListener(this);
    updateAndPost();
  }

  /**
   * Stops periodic updates of the {@link TextView}. Must be called from the application's main
   * thread.
   */
  public void stop() {
    if (!started) {
      return;
    }
    started = false;
    player.removeListener(this);
    textView.removeCallbacks(this);
  }

  // ExoPlayer.EventListener implementation.

  @Override
  public void onLoadingChanged(boolean isLoading) {
    // Do nothing.
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    updateAndPost();
  }

  @Override
  public void onPositionDiscontinuity() {
    updateAndPost();
  }

  @Override
  public void onTimelineChanged(Timeline timeline, Object manifest) {
    // Do nothing.
  }

  @Override
  public void onPlayerError(ExoPlaybackException error) {
    // Do nothing.
  }

  @Override
  public void onTracksChanged(TrackGroupArray tracks, TrackSelectionArray selections) {
    // Do nothing.
  }

  // Runnable implementation.

  @Override
  public void run() {
    updateAndPost();
  }

  // Private methods.

  private void updateAndPost() {
    textView.setText(getPlayerStateString() + getPlayerWindowIndexString() + getVideoString()
        + getAudioString());
    textView.removeCallbacks(this);
    textView.postDelayed(this, REFRESH_INTERVAL_MS);
  }

  private String getPlayerStateString() {
    String text = "playWhenReady:" + player.getPlayWhenReady() + " playbackState:";
    switch (player.getPlaybackState()) {
      case ExoPlayer.STATE_BUFFERING:
        text += "buffering";
        break;
      case ExoPlayer.STATE_ENDED:
        text += "ended";
        break;
      case ExoPlayer.STATE_IDLE:
        text += "idle";
        break;
      case ExoPlayer.STATE_READY:
        text += "ready";
        break;
      default:
        text += "unknown";
        break;
    }
    return text;
  }

  private String getPlayerWindowIndexString() {
    return " window:" + player.getCurrentWindowIndex();
  }

  private String getVideoString() {
    Format format = player.getVideoFormat();
    if (format == null) {
      return "";
    }
    return "\n" + format.sampleMimeType + "(id:" + format.id + " r:" + format.width + "x"
        + format.height + getDecoderCountersBufferCountString(player.getVideoDecoderCounters())
        + ")";
  }

  private String getAudioString() {
    Format format = player.getAudioFormat();
    if (format == null) {
      return "";
    }
    return "\n" + format.sampleMimeType + "(id:" + format.id + " hz:" + format.sampleRate + " ch:"
        + format.channelCount
        + getDecoderCountersBufferCountString(player.getAudioDecoderCounters()) + ")";
  }

  private static String getDecoderCountersBufferCountString(DecoderCounters counters) {
    if (counters == null) {
      return "";
    }
    counters.ensureUpdated();
    return " rb:" + counters.renderedOutputBufferCount
        + " sb:" + counters.skippedOutputBufferCount
        + " db:" + counters.droppedOutputBufferCount
        + " mcdb:" + counters.maxConsecutiveDroppedOutputBufferCount;
  }

}
