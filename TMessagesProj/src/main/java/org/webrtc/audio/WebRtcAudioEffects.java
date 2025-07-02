/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc.audio;

import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.AudioEffect.Descriptor;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import androidx.annotation.Nullable;
import java.util.UUID;
import org.webrtc.Logging;

// This class wraps control of three different platform effects. Supported
// effects are: AcousticEchoCanceler (AEC) and NoiseSuppressor (NS).
// Calling enable() will active all effects that are
// supported by the device if the corresponding `shouldEnableXXX` member is set.
class WebRtcAudioEffects {
  private static final boolean DEBUG = false;

  private static final String TAG = "WebRtcAudioEffectsExternal";

  // UUIDs for Software Audio Effects that we want to avoid using.
  // The implementor field will be set to "The Android Open Source Project".
  private static final UUID AOSP_ACOUSTIC_ECHO_CANCELER =
      UUID.fromString("bb392ec0-8d4d-11e0-a896-0002a5d5c51b");
  private static final UUID AOSP_NOISE_SUPPRESSOR =
      UUID.fromString("c06c8400-8e06-11e0-9cb6-0002a5d5c51b");

  // Contains the available effect descriptors returned from the
  // AudioEffect.getEffects() call. This result is cached to avoid doing the
  // slow OS call multiple times.
  private static @Nullable Descriptor[] cachedEffects;

  // Contains the audio effect objects. Created in enable() and destroyed
  // in release().
  private @Nullable AcousticEchoCanceler aec;
  private @Nullable NoiseSuppressor ns;

  // Affects the final state given to the setEnabled() method on each effect.
  // The default state is set to "disabled" but each effect can also be enabled
  // by calling setAEC() and setNS().
  private boolean shouldEnableAec;
  private boolean shouldEnableNs;

  // Returns true if all conditions for supporting HW Acoustic Echo Cancellation (AEC) are
  // fulfilled.
  public static boolean isAcousticEchoCancelerSupported() {
    return isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_AEC, AOSP_ACOUSTIC_ECHO_CANCELER);
  }

  // Returns true if all conditions for supporting HW Noise Suppression (NS) are fulfilled.
  public static boolean isNoiseSuppressorSupported() {
    return isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_NS, AOSP_NOISE_SUPPRESSOR);
  }

  public WebRtcAudioEffects() {
    Logging.d(TAG, "ctor" + WebRtcAudioUtils.getThreadInfo());
  }

  // Call this method to enable or disable the platform AEC. It modifies
  // `shouldEnableAec` which is used in enable() where the actual state
  // of the AEC effect is modified. Returns true if HW AEC is supported and
  // false otherwise.
  public boolean setAEC(boolean enable) {
    Logging.d(TAG, "setAEC(" + enable + ")");
    if (!isAcousticEchoCancelerSupported()) {
      Logging.w(TAG, "Platform AEC is not supported");
      shouldEnableAec = false;
      return false;
    }
    if (aec != null && (enable != shouldEnableAec)) {
      Logging.e(TAG, "Platform AEC state can't be modified while recording");
      return false;
    }
    shouldEnableAec = enable;
    return true;
  }

  // Call this method to enable or disable the platform NS. It modifies
  // `shouldEnableNs` which is used in enable() where the actual state
  // of the NS effect is modified. Returns true if HW NS is supported and
  // false otherwise.
  public boolean setNS(boolean enable) {
    Logging.d(TAG, "setNS(" + enable + ")");
    if (!isNoiseSuppressorSupported()) {
      Logging.w(TAG, "Platform NS is not supported");
      shouldEnableNs = false;
      return false;
    }
    if (ns != null && (enable != shouldEnableNs)) {
      Logging.e(TAG, "Platform NS state can't be modified while recording");
      return false;
    }
    shouldEnableNs = enable;
    return true;
  }

  // Toggles an existing NoiseSuppressor to be enabled or disabled.
  // Returns true if the toggling was successful, otherwise false is returned (this is also the case
  // if no NoiseSuppressor was present).
  public boolean toggleNS(boolean enable) {
    if (ns == null) {
      Logging.e(TAG, "Attempting to enable or disable nonexistent NoiseSuppressor.");
      return false;
    }
    Logging.d(TAG, "toggleNS(" + enable + ")");
    boolean toggling_succeeded = ns.setEnabled(enable) == AudioEffect.SUCCESS;
    return toggling_succeeded;
  }

  public void enable(int audioSession) {
    Logging.d(TAG, "enable(audioSession=" + audioSession + ")");
    assertTrue(aec == null);
    assertTrue(ns == null);

    if (DEBUG) {
      // Add logging of supported effects but filter out "VoIP effects", i.e.,
      // AEC, AEC and NS. Avoid calling AudioEffect.queryEffects() unless the
      // DEBUG flag is set since we have seen crashes in this API.
      for (Descriptor d : AudioEffect.queryEffects()) {
        if (effectTypeIsVoIP(d.type)) {
          Logging.d(TAG,
              "name: " + d.name + ", "
                  + "mode: " + d.connectMode + ", "
                  + "implementor: " + d.implementor + ", "
                  + "UUID: " + d.uuid);
        }
      }
    }

    if (isAcousticEchoCancelerSupported()) {
      // Create an AcousticEchoCanceler and attach it to the AudioRecord on
      // the specified audio session.
      aec = AcousticEchoCanceler.create(audioSession);
      if (aec != null) {
        boolean enabled = aec.getEnabled();
        boolean enable = shouldEnableAec && isAcousticEchoCancelerSupported();
        if (aec.setEnabled(enable) != AudioEffect.SUCCESS) {
          Logging.e(TAG, "Failed to set the AcousticEchoCanceler state");
        }
        Logging.d(TAG,
            "AcousticEchoCanceler: was " + (enabled ? "enabled" : "disabled") + ", enable: "
                + enable + ", is now: " + (aec.getEnabled() ? "enabled" : "disabled"));
      } else {
        Logging.e(TAG, "Failed to create the AcousticEchoCanceler instance");
      }
    }

    if (isNoiseSuppressorSupported()) {
      // Create an NoiseSuppressor and attach it to the AudioRecord on the
      // specified audio session.
      ns = NoiseSuppressor.create(audioSession);
      if (ns != null) {
        boolean enabled = ns.getEnabled();
        boolean enable = shouldEnableNs && isNoiseSuppressorSupported();
        if (ns.setEnabled(enable) != AudioEffect.SUCCESS) {
          Logging.e(TAG, "Failed to set the NoiseSuppressor state");
        }
        Logging.d(TAG,
            "NoiseSuppressor: was " + (enabled ? "enabled" : "disabled") + ", enable: " + enable
                + ", is now: " + (ns.getEnabled() ? "enabled" : "disabled"));
      } else {
        Logging.e(TAG, "Failed to create the NoiseSuppressor instance");
      }
    }
  }

  // Releases all native audio effect resources. It is a good practice to
  // release the effect engine when not in use as control can be returned
  // to other applications or the native resources released.
  public void release() {
    Logging.d(TAG, "release");
    if (aec != null) {
      aec.release();
      aec = null;
    }
    if (ns != null) {
      ns.release();
      ns = null;
    }
  }

  // Returns true for effect types in `type` that are of "VoIP" types:
  // Acoustic Echo Canceler (AEC) or Automatic Gain Control (AGC) or
  // Noise Suppressor (NS). Note that, an extra check for support is needed
  // in each comparison since some devices includes effects in the
  // AudioEffect.Descriptor array that are actually not available on the device.
  // As an example: Samsung Galaxy S6 includes an AGC in the descriptor but
  // AutomaticGainControl.isAvailable() returns false.
  private boolean effectTypeIsVoIP(UUID type) {
    return (AudioEffect.EFFECT_TYPE_AEC.equals(type) && isAcousticEchoCancelerSupported())
        || (AudioEffect.EFFECT_TYPE_NS.equals(type) && isNoiseSuppressorSupported());
  }

  // Helper method which throws an exception when an assertion has failed.
  private static void assertTrue(boolean condition) {
    if (!condition) {
      throw new AssertionError("Expected condition to be true");
    }
  }

  // Returns the cached copy of the audio effects array, if available, or
  // queries the operating system for the list of effects.
  private static @Nullable Descriptor[] getAvailableEffects() {
    if (cachedEffects != null) {
      return cachedEffects;
    }
    // The caching is best effort only - if this method is called from several
    // threads in parallel, they may end up doing the underlying OS call
    // multiple times. It's normally only called on one thread so there's no
    // real need to optimize for the multiple threads case.
    cachedEffects = AudioEffect.queryEffects();
    return cachedEffects;
  }

  // Returns true if an effect of the specified type is available. Functionally
  // equivalent to (NoiseSuppressor`AutomaticGainControl`...).isAvailable(), but
  // faster as it avoids the expensive OS call to enumerate effects.
  private static boolean isEffectTypeAvailable(UUID effectType, UUID blockListedUuid) {
    Descriptor[] effects = getAvailableEffects();
    if (effects == null) {
      return false;
    }
    for (Descriptor d : effects) {
      if (d.type.equals(effectType)) {
        return !d.uuid.equals(blockListedUuid);
      }
    }
    return false;
  }
}
