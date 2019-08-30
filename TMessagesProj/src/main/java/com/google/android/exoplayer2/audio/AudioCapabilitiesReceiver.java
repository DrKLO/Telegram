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
package com.google.android.exoplayer2.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/**
 * Receives broadcast events indicating changes to the device's audio capabilities, notifying a
 * {@link Listener} when audio capability changes occur.
 */
public final class AudioCapabilitiesReceiver {

  /**
   * Listener notified when audio capabilities change.
   */
  public interface Listener {

    /**
     * Called when the audio capabilities change.
     *
     * @param audioCapabilities The current audio capabilities for the device.
     */
    void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities);

  }

  private final Context context;
  private final @Nullable Handler handler;
  private final Listener listener;
  private final @Nullable BroadcastReceiver receiver;

  /* package */ @Nullable AudioCapabilities audioCapabilities;

  /**
   * @param context A context for registering the receiver.
   * @param listener The listener to notify when audio capabilities change.
   */
  public AudioCapabilitiesReceiver(Context context, Listener listener) {
    this(context, /* handler= */ null, listener);
  }

  /**
   * @param context A context for registering the receiver.
   * @param handler The handler to which {@link Listener} events will be posted. If null, listener
   *     methods are invoked on the main thread.
   * @param listener The listener to notify when audio capabilities change.
   */
  public AudioCapabilitiesReceiver(Context context, @Nullable Handler handler, Listener listener) {
    this.context = Assertions.checkNotNull(context);
    this.handler = handler;
    this.listener = Assertions.checkNotNull(listener);
    this.receiver = Util.SDK_INT >= 21 ? new HdmiAudioPlugBroadcastReceiver() : null;
  }

  /**
   * Registers the receiver, meaning it will notify the listener when audio capability changes
   * occur. The current audio capabilities will be returned. It is important to call
   * {@link #unregister} when the receiver is no longer required.
   *
   * @return The current audio capabilities for the device.
   */
  @SuppressWarnings("InlinedApi")
  public AudioCapabilities register() {
    Intent stickyIntent = null;
    if (receiver != null) {
      IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG);
      if (handler != null) {
        stickyIntent =
            context.registerReceiver(
                receiver, intentFilter, /* broadcastPermission= */ null, handler);
      } else {
        stickyIntent = context.registerReceiver(receiver, intentFilter);
      }
    }
    audioCapabilities = AudioCapabilities.getCapabilities(stickyIntent);
    return audioCapabilities;
  }

  /**
   * Unregisters the receiver, meaning it will no longer notify the listener when audio capability
   * changes occur.
   */
  public void unregister() {
    if (receiver != null) {
      context.unregisterReceiver(receiver);
    }
  }

  private final class HdmiAudioPlugBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      if (!isInitialStickyBroadcast()) {
        AudioCapabilities newAudioCapabilities = AudioCapabilities.getCapabilities(intent);
        if (!newAudioCapabilities.equals(audioCapabilities)) {
          audioCapabilities = newAudioCapabilities;
          listener.onAudioCapabilitiesChanged(newAudioCapabilities);
        }
      }
    }

  }

}
