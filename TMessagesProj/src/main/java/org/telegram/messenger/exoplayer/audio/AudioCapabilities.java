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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import java.util.Arrays;

/**
 * Represents the set of audio formats a device is capable of playing back.
 */
@TargetApi(21)
public final class AudioCapabilities {

  /**
   * The minimum audio capabilities supported by all devices.
   */
  public static final AudioCapabilities DEFAULT_AUDIO_CAPABILITIES =
      new AudioCapabilities(new int[] {AudioFormat.ENCODING_PCM_16BIT}, 2);

  /**
   * Gets the current audio capabilities. Note that to be notified when audio capabilities change,
   * you can create an instance of {@link AudioCapabilitiesReceiver} and register a listener.
   *
   * @param context Context for receiving the initial broadcast.
   * @return Current audio capabilities for the device.
   */
  @SuppressWarnings("InlinedApi")
  public static AudioCapabilities getCapabilities(Context context) {
    return getCapabilities(
        context.registerReceiver(null, new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG)));
  }

  @SuppressLint("InlinedApi")
  /* package */ static AudioCapabilities getCapabilities(Intent intent) {
    if (intent == null || intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 0) {
      return DEFAULT_AUDIO_CAPABILITIES;
    }
    return new AudioCapabilities(intent.getIntArrayExtra(AudioManager.EXTRA_ENCODINGS),
        intent.getIntExtra(AudioManager.EXTRA_MAX_CHANNEL_COUNT, 0));
  }

  private final int[] supportedEncodings;
  private final int maxChannelCount;

  /**
   * Constructs new audio capabilities based on a set of supported encodings and a maximum channel
   * count.
   *
   * @param supportedEncodings Supported audio encodings from {@link android.media.AudioFormat}'s
   *     {@code ENCODING_*} constants.
   * @param maxChannelCount The maximum number of audio channels that can be played simultaneously.
   */
  /* package */ AudioCapabilities(int[] supportedEncodings, int maxChannelCount) {
    if (supportedEncodings != null) {
      this.supportedEncodings = Arrays.copyOf(supportedEncodings, supportedEncodings.length);
      Arrays.sort(this.supportedEncodings);
    } else {
      this.supportedEncodings = new int[0];
    }
    this.maxChannelCount = maxChannelCount;
  }

  /**
   * Returns whether this device supports playback of the specified audio {@code encoding}.
   *
   * @param encoding One of {@link android.media.AudioFormat}'s {@code ENCODING_*} constants.
   * @return Whether this device supports playback the specified audio {@code encoding}.
   */
  public boolean supportsEncoding(int encoding) {
    return Arrays.binarySearch(supportedEncodings, encoding) >= 0;
  }

  /** Returns the maximum number of channels the device can play at the same time. */
  public int getMaxChannelCount() {
    return maxChannelCount;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof AudioCapabilities)) {
      return false;
    }
    AudioCapabilities audioCapabilities = (AudioCapabilities) other;
    return Arrays.equals(supportedEncodings, audioCapabilities.supportedEncodings)
        && maxChannelCount == audioCapabilities.maxChannelCount;
  }

  @Override
  public int hashCode() {
    return maxChannelCount + 31 * Arrays.hashCode(supportedEncodings);
  }

  @Override
  public String toString() {
    return "AudioCapabilities[maxChannelCount=" + maxChannelCount
        + ", supportedEncodings=" + Arrays.toString(supportedEncodings) + "]";
  }

}
