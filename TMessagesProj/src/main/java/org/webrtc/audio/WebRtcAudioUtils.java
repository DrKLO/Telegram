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

import static android.media.AudioManager.MODE_IN_CALL;
import static android.media.AudioManager.MODE_IN_COMMUNICATION;
import static android.media.AudioManager.MODE_NORMAL;
import static android.media.AudioManager.MODE_RINGTONE;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaRecorder.AudioSource;
import android.os.Build;
import java.lang.Thread;
import java.util.Arrays;
import org.webrtc.Logging;

final class WebRtcAudioUtils {
  private static final String TAG = "WebRtcAudioUtilsExternal";

  // Helper method for building a string of thread information.
  public static String getThreadInfo() {
    return "@[name=" + Thread.currentThread().getName() + ", id=" + Thread.currentThread().getId()
        + "]";
  }

  // Returns true if we're running on emulator.
  public static boolean runningOnEmulator() {
    return Build.HARDWARE.equals("goldfish") && Build.BRAND.startsWith("generic_");
  }

  // Information about the current build, taken from system properties.
  static void logDeviceInfo(String tag) {
    Logging.d(tag,
        "Android SDK: " + Build.VERSION.SDK_INT + ", "
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
  static void logAudioState(String tag, Context context, AudioManager audioManager) {
    logDeviceInfo(tag);
    logAudioStateBasic(tag, context, audioManager);
    logAudioStateVolume(tag, audioManager);
    logAudioDeviceInfo(tag, audioManager);
  }

  // Converts AudioDeviceInfo types to local string representation.
  static String deviceTypeToString(int type) {
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

  @TargetApi(Build.VERSION_CODES.N)
  public static String audioSourceToString(int source) {
    // AudioSource.UNPROCESSED requires API level 29. Use local define instead.
    final int VOICE_PERFORMANCE = 10;
    switch (source) {
      case AudioSource.DEFAULT:
        return "DEFAULT";
      case AudioSource.MIC:
        return "MIC";
      case AudioSource.VOICE_UPLINK:
        return "VOICE_UPLINK";
      case AudioSource.VOICE_DOWNLINK:
        return "VOICE_DOWNLINK";
      case AudioSource.VOICE_CALL:
        return "VOICE_CALL";
      case AudioSource.CAMCORDER:
        return "CAMCORDER";
      case AudioSource.VOICE_RECOGNITION:
        return "VOICE_RECOGNITION";
      case AudioSource.VOICE_COMMUNICATION:
        return "VOICE_COMMUNICATION";
      case AudioSource.UNPROCESSED:
        return "UNPROCESSED";
      case VOICE_PERFORMANCE:
        return "VOICE_PERFORMANCE";
      default:
        return "INVALID";
    }
  }

  public static String channelMaskToString(int mask) {
    // For input or AudioRecord, the mask should be AudioFormat#CHANNEL_IN_MONO or
    // AudioFormat#CHANNEL_IN_STEREO. AudioFormat#CHANNEL_IN_MONO is guaranteed to work on all
    // devices.
    switch (mask) {
      case AudioFormat.CHANNEL_IN_STEREO:
        return "IN_STEREO";
      case AudioFormat.CHANNEL_IN_MONO:
        return "IN_MONO";
      default:
        return "INVALID";
    }
  }

  @TargetApi(Build.VERSION_CODES.N)
  public static String audioEncodingToString(int enc) {
    switch (enc) {
      case AudioFormat.ENCODING_INVALID:
        return "INVALID";
      case AudioFormat.ENCODING_PCM_16BIT:
        return "PCM_16BIT";
      case AudioFormat.ENCODING_PCM_8BIT:
        return "PCM_8BIT";
      case AudioFormat.ENCODING_PCM_FLOAT:
        return "PCM_FLOAT";
      case AudioFormat.ENCODING_AC3:
        return "AC3";
      case AudioFormat.ENCODING_E_AC3:
        return "AC3";
      case AudioFormat.ENCODING_DTS:
        return "DTS";
      case AudioFormat.ENCODING_DTS_HD:
        return "DTS_HD";
      case AudioFormat.ENCODING_MP3:
        return "MP3";
      default:
        return "Invalid encoding: " + enc;
    }
  }

  // Reports basic audio statistics.
  private static void logAudioStateBasic(String tag, Context context, AudioManager audioManager) {
    Logging.d(tag,
        "Audio State: "
            + "audio mode: " + modeToString(audioManager.getMode()) + ", "
            + "has mic: " + hasMicrophone(context) + ", "
            + "mic muted: " + audioManager.isMicrophoneMute() + ", "
            + "music active: " + audioManager.isMusicActive() + ", "
            + "speakerphone: " + audioManager.isSpeakerphoneOn() + ", "
            + "BT SCO: " + audioManager.isBluetoothScoOn());
  }

  // Adds volume information for all possible stream types.
  private static void logAudioStateVolume(String tag, AudioManager audioManager) {
    final int[] streams = {AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING, AudioManager.STREAM_ALARM, AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM};
    Logging.d(tag, "Audio State: ");
    // Some devices may not have volume controls and might use a fixed volume.
    boolean fixedVolume = audioManager.isVolumeFixed();
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
    final AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
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
    switch (stream) {
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

  // Returns true if the device can record audio via a microphone.
  private static boolean hasMicrophone(Context context) {
    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
  }
}
