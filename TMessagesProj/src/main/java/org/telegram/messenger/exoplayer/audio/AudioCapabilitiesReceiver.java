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
package org.telegram.messenger.exoplayer.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.Util;

/**
 * Notifies a listener when the audio playback capabilities change. Call {@link #register} to start
 * (or resume) receiving notifications, and {@link #unregister} to stop.
 */
public final class AudioCapabilitiesReceiver {

  /**
   * Listener notified when audio capabilities change.
   */
  public interface Listener {

    /**
     * Called when the audio capabilities change.
     *
     * @param audioCapabilities Current audio capabilities for the device.
     */
    void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities);

  }

  private final Context context;
  private final Listener listener;
  private final BroadcastReceiver receiver;

  /* package */ AudioCapabilities audioCapabilities;

  /**
   * Constructs a new audio capabilities receiver.
   *
   * @param context Context for registering to receive broadcasts.
   * @param listener Listener to notify when audio capabilities change.
   */
  public AudioCapabilitiesReceiver(Context context, Listener listener) {
    this.context = Assertions.checkNotNull(context);
    this.listener = Assertions.checkNotNull(listener);
    this.receiver = Util.SDK_INT >= 21 ? new HdmiAudioPlugBroadcastReceiver() : null;
  }

  /**
   * Registers to notify the listener when audio capabilities change. The current capabilities will
   * be returned. It is important to call {@link #unregister} so that the listener can be garbage
   * collected.
   *
   * @return Current audio capabilities for the device.
   */
  @SuppressWarnings("InlinedApi")
  public AudioCapabilities register() {
    Intent stickyIntent = receiver == null ? null
        : context.registerReceiver(receiver, new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG));
    audioCapabilities = AudioCapabilities.getCapabilities(stickyIntent);
    return audioCapabilities;
  }

  /**
   * Unregisters to stop notifying the listener when audio capabilities change.
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
