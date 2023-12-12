/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.google.android.exoplayer2.util.Util;

/* package */ final class AudioBecomingNoisyManager {

  private final Context context;
  private final AudioBecomingNoisyReceiver receiver;
  private boolean receiverRegistered;

  public interface EventListener {
    void onAudioBecomingNoisy();
  }

  public AudioBecomingNoisyManager(Context context, Handler eventHandler, EventListener listener) {
    this.context = context.getApplicationContext();
    this.receiver = new AudioBecomingNoisyReceiver(eventHandler, listener);
  }

  /**
   * Enables the {@link AudioBecomingNoisyManager} which calls {@link
   * EventListener#onAudioBecomingNoisy()} upon receiving an intent of {@link
   * AudioManager#ACTION_AUDIO_BECOMING_NOISY}.
   *
   * @param enabled True if the listener should be notified when audio is becoming noisy.
   */
  public void setEnabled(boolean enabled) {
    if (enabled && !receiverRegistered) {
      Util.registerReceiverNotExported(
          context, receiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
      receiverRegistered = true;
    } else if (!enabled && receiverRegistered) {
      context.unregisterReceiver(receiver);
      receiverRegistered = false;
    }
  }

  private final class AudioBecomingNoisyReceiver extends BroadcastReceiver implements Runnable {
    private final EventListener listener;
    private final Handler eventHandler;

    public AudioBecomingNoisyReceiver(Handler eventHandler, EventListener listener) {
      this.eventHandler = eventHandler;
      this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
        eventHandler.post(this);
      }
    }

    @Override
    public void run() {
      if (receiverRegistered) {
        listener.onAudioBecomingNoisy();
      }
    }
  }
}
