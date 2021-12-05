/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc.voiceengine;

import static android.media.AudioManager.MODE_IN_CALL;
import static android.media.AudioManager.MODE_IN_COMMUNICATION;
import static android.media.AudioManager.MODE_NORMAL;
import static android.media.AudioManager.MODE_RINGTONE;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import java.lang.Thread;
import java.util.Arrays;
import java.util.List;
import org.webrtc.ContextUtils;
import org.webrtc.Logging;

public final class WebRtcAudioUtils {
  private static final String TAG = "WebRtcAudioUtils";

  // List of devices where we have seen issues (e.g. bad audio quality) using
  // the low latency output mode in combination with OpenSL ES.
  // The device name is given by Build.MODEL.
  private static final String[] BLACKLISTED_OPEN_SL_ES_MODELS = new String[] {
      // It is recommended to maintain a list of blacklisted models outside
      // this package and instead call
      // WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true)
      // from the client for devices where OpenSL ES shall be disabled.
  };

  // List of devices where it has been verified that the built-in effect
  // bad and where it makes sense to avoid using it and instead rely on the
  // native WebRTC version instead. The device name is given by Build.MODEL.
  private static final String[] BLACKLISTED_AEC_MODELS = new String[] {
      // It is recommended to maintain a list of blacklisted models outside
      // this package and instead call setWebRtcBasedAcousticEchoCanceler(true)
      // from the client for devices where the built-in AEC shall be disabled.
  };
  private static final String[] BLACKLISTED_NS_MODELS = new String[] {
    // It is recommended to maintain a list of blacklisted models outside
    // this package and instead call setWebRtcBasedNoiseSuppressor(true)
    // from the client for devices where the built-in NS shall be disabled.
  };

  // Use 16kHz as the default sample rate. A higher sample rate might prevent
  // us from supporting communication mode on some older (e.g. ICS) devices.
  private static final int DEFAULT_SAMPLE_RATE_HZ = 16000;
  private static int defaultSampleRateHz = DEFAULT_SAMPLE_RATE_HZ;
  // Set to true if setDefaultSampleRateHz() has been called.
  private static boolean isDefaultSampleRateOverridden;

  // By default, utilize hardware based audio effects for AEC and NS when
  // available.
  private static boolean useWebRtcBasedAcousticEchoCanceler;
  private static boolean useWebRtcBasedNoiseSuppressor;

  // Call these methods if any hardware based effect shall be replaced by a
  // software based version provided by the WebRTC stack instead.
  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized void setWebRtcBasedAcousticEchoCanceler(boolean enable) {
    useWebRtcBasedAcousticEchoCanceler = enable;
  }

    // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized void setWebRtcBasedNoiseSuppressor(boolean enable) {
    useWebRtcBasedNoiseSuppressor = enable;
  }

  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized void setWebRtcBasedAutomaticGainControl(boolean enable) {
    // TODO(henrika): deprecated; remove when no longer used by any client.
    Logging.w(TAG, "setWebRtcBasedAutomaticGainControl() is deprecated");
  }

  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized boolean useWebRtcBasedAcousticEchoCanceler() {
    if (useWebRtcBasedAcousticEchoCanceler) {
      Logging.w(TAG, "Overriding default behavior; now using WebRTC AEC!");
    }
    return useWebRtcBasedAcousticEchoCanceler;
  }

  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized boolean useWebRtcBasedNoiseSuppressor() {
    if (useWebRtcBasedNoiseSuppressor) {
      Logging.w(TAG, "Overriding default behavior; now using WebRTC NS!");
    }
    return useWebRtcBasedNoiseSuppressor;
  }

  // TODO(henrika): deprecated; remove when no longer used by any client.
  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized boolean useWebRtcBasedAutomaticGainControl() {
    // Always return true here to avoid trying to use any built-in AGC.
    return true;
  }

  // Returns true if the device supports an audio effect (AEC or NS).
  // Four conditions must be fulfilled if functions are to return true:
  // 1) the platform must support the built-in (HW) effect,
  // 2) explicit use (override) of a WebRTC based version must not be set,
  // 3) the device must not be blacklisted for use of the effect, and
  // 4) the UUID of the effect must be approved (some UUIDs can be excluded).
  public static boolean isAcousticEchoCancelerSupported() {
    return WebRtcAudioEffects.canUseAcousticEchoCanceler();
  }
  public static boolean isNoiseSuppressorSupported() {
    return WebRtcAudioEffects.canUseNoiseSuppressor();
  }
  // TODO(henrika): deprecated; remove when no longer used by any client.
  public static boolean isAutomaticGainControlSupported() {
    // Always return false here to avoid trying to use any built-in AGC.
    return false;
  }

  // Call this method if the default handling of querying the native sample
  // rate shall be overridden. Can be useful on some devices where the
  // available Android APIs are known to return invalid results.
  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized void setDefaultSampleRateHz(int sampleRateHz) {
    isDefaultSampleRateOverridden = true;
    defaultSampleRateHz = sampleRateHz;
  }

  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized boolean isDefaultSampleRateOverridden() {
    return isDefaultSampleRateOverridden;
  }

  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized int getDefaultSampleRateHz() {
    return defaultSampleRateHz;
  }

  public static List<String> getBlackListedModelsForAecUsage() {
    return Arrays.asList(WebRtcAudioUtils.BLACKLISTED_AEC_MODELS);
  }

  public static List<String> getBlackListedModelsForNsUsage() {
    return Arrays.asList(WebRtcAudioUtils.BLACKLISTED_NS_MODELS);
  }

  // Helper method for building a string of thread information.
  public static String getThreadInfo() {
    return "@[name=" + Thread.currentThread().getName() + ", id=" + Thread.currentThread().getId()
        + "]";
  }

  // Returns true if we're running on emulator.
  public static boolean runningOnEmulator() {
    return Build.HARDWARE.equals("goldfish") && Build.BRAND.startsWith("generic_");
  }

  // Returns true if the device is blacklisted for OpenSL ES usage.
  public static boolean deviceIsBlacklistedForOpenSLESUsage() {
    List<String> blackListedModels = Arrays.asList(BLACKLISTED_OPEN_SL_ES_MODELS);
    return blackListedModels.contains(Build.MODEL);
  }

  // Information about the current build, taken from system properties.
  static void logDeviceInfo(String tag) {
    Logging.d(tag, "Android SDK: " + Build.VERSION.SDK_INT + ", "
            + "Release: " + Build.VERSION.RELEASE + ", "
            + "Brand: " + Build.BRAND + ", "
            + "Device: " + Build.DEVICE + ", "
            + "Id: " + Build.ID + ", "
            + "Hardware: " + Build.HARDWARE + ", "
            + "Manufacturer: " + Build.MANUFACTURER + ", "
            + "Model: " + Build.MODEL + ", "
            + "Product: " + Build.PRODUCT);
  }

  // Logs information about the current audio state. The idea is to call this
  // method when errors are detected to log under what conditions the error
  // occurred. Hopefully it will provide clues to what might be the root cause.
  static void logAudioState(String tag) {
    logDeviceInfo(tag);
    final Context context = ContextUtils.getApplicationContext();
    final AudioManager audioManager =
        (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    logAudioStateBasic(tag, audioManager);
    logAudioStateVolume(tag, audioManager);
    logAudioDeviceInfo(tag, audioManager);
  }

  // Reports basic audio statistics.
  private static void logAudioStateBasic(String tag, AudioManager audioManager) {
    Logging.d(tag, "Audio State: "
            + "audio mode: " + modeToString(audioManager.getMode()) + ", "
            + "has mic: " + hasMicrophone() + ", "
            + "mic muted: " + audioManager.isMicrophoneMute() + ", "
            + "music active: " + audioManager.isMusicActive() + ", "
            + "speakerphone: " + audioManager.isSpeakerphoneOn() + ", "
            + "BT SCO: " + audioManager.isBluetoothScoOn());
  }

  private static boolean isVolumeFixed(AudioManager audioManager) {
    if (Build.VERSION.SDK_INT < 21) {
      return false;
    }
    return audioManager.isVolumeFixed();
  }

  // Adds volume information for all possible stream types.
  private static void logAudioStateVolume(String tag, AudioManager audioManager) {
    final int[] streams = {
        AudioManager.STREAM_VOICE_CALL,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM
    };
    Logging.d(tag, "Audio State: ");
    // Some devices may not have volume controls and might use a fixed volume.
    boolean fixedVolume = isVolumeFixed(audioManager);
    Logging.d(tag, "  fixed volume=" + fixedVolume);
    if (!fixedVolume) {
      for (int stream : streams) {
        StringBuilder info = new StringBuilder();
        info.append("  " + streamTypeToString(stream) + ": ");
        info.append("volume=").append(audioManager.getStreamVolume(stream));
        info.append(", max=").append(audioManager.getStreamMaxVolume(stream));
        logIsStreamMute(tag, audioManager, stream, info);
        Logging.d(tag, info.toString());
      }
    }
  }

  private static void logIsStreamMute(
      String tag, AudioManager audioManager, int stream, StringBuilder info) {
    if (Build.VERSION.SDK_INT >= 23) {
      info.append(", muted=").append(audioManager.isStreamMute(stream));
    }
  }

  private static void logAudioDeviceInfo(String tag, AudioManager audioManager) {
    if (Build.VERSION.SDK_INT < 23) {
      return;
    }
    final AudioDeviceInfo[] devices =
        audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
    if (devices.length == 0) {
      return;
    }
    Logging.d(tag, "Audio Devices: ");
    for (AudioDeviceInfo device : devices) {
      StringBuilder info = new StringBuilder();
      info.append("  ").append(deviceTypeToString(device.getType()));
      info.append(device.isSource() ? "(in): " : "(out): ");
      // An empty array indicates that the device supports arbitrary channel counts.
      if (device.getChannelCounts().length > 0) {
        info.append("channels=").append(Arrays.toString(device.getChannelCounts()));
        info.append(", ");
      }
      if (device.getEncodings().length > 0) {
        // Examples: ENCODING_PCM_16BIT = 2, ENCODING_PCM_FLOAT = 4.
        info.append("encodings=").append(Arrays.toString(device.getEncodings()));
        info.append(", ");
      }
      if (device.getSampleRates().length > 0) {
        info.append("sample rates=").append(Arrays.toString(device.getSampleRates()));
        info.append(", ");
      }
      info.append("id=").append(device.getId());
      Logging.d(tag, info.toString());
    }
  }

  // Converts media.AudioManager modes into local string representation.
  static String modeToString(int mode) {
    switch (mode) {
      case MODE_IN_CALL:
        return "MODE_IN_CALL";
      case MODE_IN_COMMUNICATION:
        return "MODE_IN_COMMUNICATION";
      case MODE_NORMAL:
        return "MODE_NORMAL";
      case MODE_RINGTONE:
        return "MODE_RINGTONE";
      default:
        return "MODE_INVALID";
    }
  }

  private static String streamTypeToString(int stream) {
    switch(stream) {
      case AudioManager.STREAM_VOICE_CALL:
        return "STREAM_VOICE_CALL";
      case AudioManager.STREAM_MUSIC:
        return "STREAM_MUSIC";
      case AudioManager.STREAM_RING:
        return "STREAM_RING";
      case AudioManager.STREAM_ALARM:
        return "STREAM_ALARM";
      case AudioManager.STREAM_NOTIFICATION:
        return "STREAM_NOTIFICATION";
      case AudioManager.STREAM_SYSTEM:
        return "STREAM_SYSTEM";
      default:
        return "STREAM_INVALID";
    }
  }

  // Converts AudioDeviceInfo types to local string representation.
  private static String deviceTypeToString(int type) {
    switch (type) {
      case AudioDeviceInfo.TYPE_UNKNOWN:
        return "TYPE_UNKNOWN";
      case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
        return "TYPE_BUILTIN_EARPIECE";
      case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
        return "TYPE_BUILTIN_SPEAKER";
      case AudioDeviceInfo.TYPE_WIRED_HEADSET:
        return "TYPE_WIRED_HEADSET";
      case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
        return "TYPE_WIRED_HEADPHONES";
      case AudioDeviceInfo.TYPE_LINE_ANALOG:
        return "TYPE_LINE_ANALOG";
      case AudioDeviceInfo.TYPE_LINE_DIGITAL:
        return "TYPE_LINE_DIGITAL";
      case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
        return "TYPE_BLUETOOTH_SCO";
      case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
        return "TYPE_BLUETOOTH_A2DP";
      case AudioDeviceInfo.TYPE_HDMI:
        return "TYPE_HDMI";
      case AudioDeviceInfo.TYPE_HDMI_ARC:
        return "TYPE_HDMI_ARC";
      case AudioDeviceInfo.TYPE_USB_DEVICE:
        return "TYPE_USB_DEVICE";
      case AudioDeviceInfo.TYPE_USB_ACCESSORY:
        return "TYPE_USB_ACCESSORY";
      case AudioDeviceInfo.TYPE_DOCK:
        return "TYPE_DOCK";
      case AudioDeviceInfo.TYPE_FM:
        return "TYPE_FM";
      case AudioDeviceInfo.TYPE_BUILTIN_MIC:
        return "TYPE_BUILTIN_MIC";
      case AudioDeviceInfo.TYPE_FM_TUNER:
        return "TYPE_FM_TUNER";
      case AudioDeviceInfo.TYPE_TV_TUNER:
        return "TYPE_TV_TUNER";
      case AudioDeviceInfo.TYPE_TELEPHONY:
        return "TYPE_TELEPHONY";
      case AudioDeviceInfo.TYPE_AUX_LINE:
        return "TYPE_AUX_LINE";
      case AudioDeviceInfo.TYPE_IP:
        return "TYPE_IP";
      case AudioDeviceInfo.TYPE_BUS:
        return "TYPE_BUS";
      case AudioDeviceInfo.TYPE_USB_HEADSET:
        return "TYPE_USB_HEADSET";
      default:
        return "TYPE_UNKNOWN";
    }
  }

  // Returns true if the device can record audio via a microphone.
  private static boolean hasMicrophone() {
    return ContextUtils.getApplicationContext().getPackageManager().hasSystemFeature(
        PackageManager.FEATURE_MICROPHONE);
  }
}
