/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2;

import android.media.MediaPlayer;
import android.os.Looper;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** A {@link Player} wrapper for the legacy Android platform {@link MediaPlayer}. */
public final class LegacyMediaPlayerWrapper extends SimpleBasePlayer {

  private final MediaPlayer player;

  private boolean playWhenReady;

  /**
   * Creates the {@link MediaPlayer} wrapper.
   *
   * @param looper The {@link Looper} used to call all methods on.
   */
  public LegacyMediaPlayerWrapper(Looper looper) {
    super(looper);
    this.player = new MediaPlayer();
  }

  @Override
  protected State getState() {
    return new State.Builder()
        .setAvailableCommands(new Commands.Builder().addAll(Player.COMMAND_PLAY_PAUSE).build())
        .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .build();
  }

  @Override
  protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
    this.playWhenReady = playWhenReady;
    // TODO: Only call these methods if the player is in Started or Paused state.
    if (playWhenReady) {
      player.start();
    } else {
      player.pause();
    }
    return Futures.immediateVoidFuture();
  }
}
