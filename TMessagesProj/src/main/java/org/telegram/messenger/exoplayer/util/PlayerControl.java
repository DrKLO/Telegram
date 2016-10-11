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
package org.telegram.messenger.exoplayer.util;

import android.widget.MediaController.MediaPlayerControl;
import org.telegram.messenger.exoplayer.ExoPlayer;

/**
 * An implementation of {@link MediaPlayerControl} for controlling an {@link ExoPlayer} instance.
 * <p>
 * This class is provided for convenience, however it is expected that most applications will
 * implement their own player controls and therefore not require this class.
 */
public class PlayerControl implements MediaPlayerControl {

  private final ExoPlayer exoPlayer;

  public PlayerControl(ExoPlayer exoPlayer) {
    this.exoPlayer = exoPlayer;
  }

  @Override
  public boolean canPause() {
    return true;
  }

  @Override
  public boolean canSeekBackward() {
    return true;
  }

  @Override
  public boolean canSeekForward() {
    return true;
  }

  /**
   * This is an unsupported operation.
   * <p>
   * Application of audio effects is dependent on the audio renderer used. When using
   * {@link org.telegram.messenger.exoplayer.MediaCodecAudioTrackRenderer}, the recommended approach is
   * to extend the class and override
   * {@link org.telegram.messenger.exoplayer.MediaCodecAudioTrackRenderer#onAudioSessionId}.
   *
   * @throws UnsupportedOperationException Always thrown.
   */
  @Override
  public int getAudioSessionId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getBufferPercentage() {
    return exoPlayer.getBufferedPercentage();
  }

  @Override
  public int getCurrentPosition() {
    return exoPlayer.getDuration() == ExoPlayer.UNKNOWN_TIME ? 0
        : (int) exoPlayer.getCurrentPosition();
  }

  @Override
  public int getDuration() {
    return exoPlayer.getDuration() == ExoPlayer.UNKNOWN_TIME ? 0
        : (int) exoPlayer.getDuration();
  }

  @Override
  public boolean isPlaying() {
    return exoPlayer.getPlayWhenReady();
  }

  @Override
  public void start() {
    exoPlayer.setPlayWhenReady(true);
  }

  @Override
  public void pause() {
    exoPlayer.setPlayWhenReady(false);
  }

  @Override
  public void seekTo(int timeMillis) {
    long seekPosition = exoPlayer.getDuration() == ExoPlayer.UNKNOWN_TIME ? 0
        : Math.min(Math.max(0, timeMillis), getDuration());
    exoPlayer.seekTo(seekPosition);
  }

}
