/*
 * Copyright 2020 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;

/** A manager that wraps {@link AudioManager} to control/listen audio stream volume. */
/* package */ final class StreamVolumeManager {

  /** A listener for changes in the manager. */
  public interface Listener {

    /** Called when the audio stream type is changed. */
    void onStreamTypeChanged(@C.StreamType int streamType);

    /** Called when the audio stream volume or mute state is changed. */
    void onStreamVolumeChanged(int streamVolume, boolean streamMuted);
  }

  private static final String TAG = "StreamVolumeManager";

  // TODO(b/151280453): Replace the hidden intent action with an official one.
  // Copied from AudioManager#VOLUME_CHANGED_ACTION
  private static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";

  // TODO(b/153317944): Allow users to override these flags.
  private static final int VOLUME_FLAGS = AudioManager.FLAG_SHOW_UI;

  private final Context applicationContext;
  private final Handler eventHandler;
  private final Listener listener;
  private final AudioManager audioManager;

  @Nullable private VolumeChangeReceiver receiver;
  private @C.StreamType int streamType;
  private int volume;
  private boolean muted;

  /** Creates a manager. */
  public StreamVolumeManager(Context context, Handler eventHandler, Listener listener) {
    applicationContext = context.getApplicationContext();
    this.eventHandler = eventHandler;
    this.listener = listener;
    audioManager =
        Assertions.checkStateNotNull(
            (AudioManager) applicationContext.getSystemService(Context.AUDIO_SERVICE));

    streamType = C.STREAM_TYPE_DEFAULT;
    volume = getVolumeFromManager(audioManager, streamType);
    muted = getMutedFromManager(audioManager, streamType);

    VolumeChangeReceiver receiver = new VolumeChangeReceiver();
    IntentFilter filter = new IntentFilter(VOLUME_CHANGED_ACTION);
    try {
      Util.registerReceiverNotExported(applicationContext, receiver, filter);
      this.receiver = receiver;
    } catch (RuntimeException e) {
      Log.w(TAG, "Error registering stream volume receiver", e);
    }
  }

  /** Sets the audio stream type. */
  public void setStreamType(@C.StreamType int streamType) {
    if (this.streamType == streamType) {
      return;
    }
    this.streamType = streamType;

    updateVolumeAndNotifyIfChanged();
    listener.onStreamTypeChanged(streamType);
  }

  /**
   * Gets the minimum volume for the current audio stream. It can be changed if {@link
   * #setStreamType(int)} is called.
   */
  public int getMinVolume() {
    return Util.SDK_INT >= 28 ? audioManager.getStreamMinVolume(streamType) : 0;
  }

  /**
   * Gets the maximum volume for the current audio stream. It can be changed if {@link
   * #setStreamType(int)} is called.
   */
  public int getMaxVolume() {
    return audioManager.getStreamMaxVolume(streamType);
  }

  /** Gets the current volume for the current audio stream. */
  public int getVolume() {
    return volume;
  }

  /** Gets whether the current audio stream is muted or not. */
  public boolean isMuted() {
    return muted;
  }

  /**
   * Sets the volume with the given value for the current audio stream. The value should be between
   * {@link #getMinVolume()} and {@link #getMaxVolume()}, otherwise it will be ignored.
   */
  public void setVolume(int volume) {
    if (volume < getMinVolume() || volume > getMaxVolume()) {
      return;
    }
    audioManager.setStreamVolume(streamType, volume, VOLUME_FLAGS);
    updateVolumeAndNotifyIfChanged();
  }

  /**
   * Increases the volume by one for the current audio stream. It will be ignored if the current
   * volume is equal to {@link #getMaxVolume()}.
   */
  public void increaseVolume() {
    if (volume >= getMaxVolume()) {
      return;
    }
    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_RAISE, VOLUME_FLAGS);
    updateVolumeAndNotifyIfChanged();
  }

  /**
   * Decreases the volume by one for the current audio stream. It will be ignored if the current
   * volume is equal to {@link #getMinVolume()}.
   */
  public void decreaseVolume() {
    if (volume <= getMinVolume()) {
      return;
    }
    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_LOWER, VOLUME_FLAGS);
    updateVolumeAndNotifyIfChanged();
  }

  /** Sets the mute state of the current audio stream. */
  public void setMuted(boolean muted) {
    if (Util.SDK_INT >= 23) {
      audioManager.adjustStreamVolume(
          streamType, muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, VOLUME_FLAGS);
    } else {
      audioManager.setStreamMute(streamType, muted);
    }
    updateVolumeAndNotifyIfChanged();
  }

  /** Releases the manager. It must be called when the manager is no longer required. */
  public void release() {
    if (receiver != null) {
      try {
        applicationContext.unregisterReceiver(receiver);
      } catch (RuntimeException e) {
        Log.w(TAG, "Error unregistering stream volume receiver", e);
      }
      receiver = null;
    }
  }

  private void updateVolumeAndNotifyIfChanged() {
    int newVolume = getVolumeFromManager(audioManager, streamType);
    boolean newMuted = getMutedFromManager(audioManager, streamType);
    if (volume != newVolume || muted != newMuted) {
      volume = newVolume;
      muted = newMuted;
      listener.onStreamVolumeChanged(newVolume, newMuted);
    }
  }

  private static int getVolumeFromManager(AudioManager audioManager, @C.StreamType int streamType) {
    // AudioManager#getStreamVolume(int) throws an exception on some devices. See
    // https://github.com/google/ExoPlayer/issues/8191.
    try {
      return audioManager.getStreamVolume(streamType);
    } catch (RuntimeException e) {
      Log.w(TAG, "Could not retrieve stream volume for stream type " + streamType, e);
      return audioManager.getStreamMaxVolume(streamType);
    }
  }

  private static boolean getMutedFromManager(
      AudioManager audioManager, @C.StreamType int streamType) {
    if (Util.SDK_INT >= 23) {
      return audioManager.isStreamMute(streamType);
    } else {
      return getVolumeFromManager(audioManager, streamType) == 0;
    }
  }

  private final class VolumeChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      eventHandler.post(StreamVolumeManager.this::updateVolumeAndNotifyIfChanged);
    }
  }
}
