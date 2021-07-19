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

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build;
import org.webrtc.Logging;
import org.webrtc.CalledByNative;

/**
 * This class contains static functions to query sample rate and input/output audio buffer sizes.
 */
class WebRtcAudioManager {
  private static final String TAG = "WebRtcAudioManagerExternal";

  private static final int DEFAULT_SAMPLE_RATE_HZ = 16000;

  // Default audio data format is PCM 16 bit per sample.
  // Guaranteed to be supported by all devices.
  private static final int BITS_PER_SAMPLE = 16;

  private static final int DEFAULT_FRAME_PER_BUFFER = 256;

  @CalledByNative
  static AudioManager getAudioManager(Context context) {
    return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
  }

  @CalledByNative
  static int getOutputBufferSize(
      Context context, AudioManager audioManager, int sampleRate, int numberOfOutputChannels) {
    return isLowLatencyOutputSupported(context)
        ? getLowLatencyFramesPerBuffer(audioManager)
        : getMinOutputFrameSize(sampleRate, numberOfOutputChannels);
  }

  @CalledByNative
  static int getInputBufferSize(
      Context context, AudioManager audioManager, int sampleRate, int numberOfInputChannels) {
    return isLowLatencyInputSupported(context)
        ? getLowLatencyFramesPerBuffer(audioManager)
        : getMinInputFrameSize(sampleRate, numberOfInputChannels);
  }

  private static boolean isLowLatencyOutputSupported(Context context) {
    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY);
  }

  private static boolean isLowLatencyInputSupported(Context context) {
    // TODO(henrika): investigate if some sort of device list is needed here
    // as well. The NDK doc states that: "As of API level 21, lower latency
    // audio input is supported on select devices. To take advantage of this
    // feature, first confirm that lower latency output is available".
    return Build.VERSION.SDK_INT >= 21 && isLowLatencyOutputSupported(context);
  }

  /**
   * Returns the native input/output sample rate for this device's output stream.
   */
  @CalledByNative
  static int getSampleRate(AudioManager audioManager) {
    // Override this if we're running on an old emulator image which only
    // supports 8 kHz and doesn't support PROPERTY_OUTPUT_SAMPLE_RATE.
    if (WebRtcAudioUtils.runningOnEmulator()) {
      Logging.d(TAG, "Running emulator, overriding sample rate to 8 kHz.");
      return 8000;
    }
    // Deliver best possible estimate based on default Android AudioManager APIs.
    final int sampleRateHz = getSampleRateForApiLevel(audioManager);
    Logging.d(TAG, "Sample rate is set to " + sampleRateHz + " Hz");
    return sampleRateHz;
  }

  private static int getSampleRateForApiLevel(AudioManager audioManager) {
    if (Build.VERSION.SDK_INT < 17) {
      return DEFAULT_SAMPLE_RATE_HZ;
    }
    String sampleRateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
    return (sampleRateString == null) ? DEFAULT_SAMPLE_RATE_HZ : Integer.parseInt(sampleRateString);
  }

  // Returns the native output buffer size for low-latency output streams.
  private static int getLowLatencyFramesPerBuffer(AudioManager audioManager) {
    if (Build.VERSION.SDK_INT < 17) {
      return DEFAULT_FRAME_PER_BUFFER;
    }
    String framesPerBuffer =
        audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
    return framesPerBuffer == null ? DEFAULT_FRAME_PER_BUFFER : Integer.parseInt(framesPerBuffer);
  }

  // Returns the minimum output buffer size for Java based audio (AudioTrack).
  // This size can also be used for OpenSL ES implementations on devices that
  // lacks support of low-latency output.
  private static int getMinOutputFrameSize(int sampleRateInHz, int numChannels) {
    final int bytesPerFrame = numChannels * (BITS_PER_SAMPLE / 8);
    final int channelConfig =
        (numChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO);
    return AudioTrack.getMinBufferSize(
               sampleRateInHz, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
        / bytesPerFrame;
  }

  // Returns the minimum input buffer size for Java based audio (AudioRecord).
  // This size can calso be used for OpenSL ES implementations on devices that
  // lacks support of low-latency input.
  private static int getMinInputFrameSize(int sampleRateInHz, int numChannels) {
    final int bytesPerFrame = numChannels * (BITS_PER_SAMPLE / 8);
    final int channelConfig =
        (numChannels == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO);
    return AudioRecord.getMinBufferSize(
               sampleRateInHz, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
        / bytesPerFrame;
  }
}
