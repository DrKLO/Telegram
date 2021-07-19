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

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build;
import androidx.annotation.Nullable;
import java.util.Timer;
import java.util.TimerTask;
import org.webrtc.ContextUtils;
import org.webrtc.Logging;

// WebRtcAudioManager handles tasks that uses android.media.AudioManager.
// At construction, storeAudioParameters() is called and it retrieves
// fundamental audio parameters like native sample rate and number of channels.
// The result is then provided to the caller by nativeCacheAudioParameters().
// It is also possible to call init() to set up the audio environment for best
// possible "VoIP performance". All settings done in init() are reverted by
// dispose(). This class can also be used without calling init() if the user
// prefers to set up the audio environment separately. However, it is
// recommended to always use AudioManager.MODE_IN_COMMUNICATION.
public class WebRtcAudioManager {
  private static final boolean DEBUG = false;

  private static final String TAG = "WebRtcAudioManager";

  // TODO(bugs.webrtc.org/8914): disabled by default until AAudio support has
  // been completed. Goal is to always return false on Android O MR1 and higher.
  private static final boolean blacklistDeviceForAAudioUsage = true;

  // Use mono as default for both audio directions.
  private static boolean useStereoOutput;
  private static boolean useStereoInput;

  private static boolean blacklistDeviceForOpenSLESUsage;
  private static boolean blacklistDeviceForOpenSLESUsageIsOverridden;

  // Call this method to override the default list of blacklisted devices
  // specified in WebRtcAudioUtils.BLACKLISTED_OPEN_SL_ES_MODELS.
  // Allows an app to take control over which devices to exclude from using
  // the OpenSL ES audio output path
  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized void setBlacklistDeviceForOpenSLESUsage(boolean enable) {
    blacklistDeviceForOpenSLESUsageIsOverridden = true;
    blacklistDeviceForOpenSLESUsage = enable;
  }

  // Call these methods to override the default mono audio modes for the specified direction(s)
  // (input and/or output).
  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized void setStereoOutput(boolean enable) {
    Logging.w(TAG, "Overriding default output behavior: setStereoOutput(" + enable + ')');
    useStereoOutput = enable;
  }

  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized void setStereoInput(boolean enable) {
    Logging.w(TAG, "Overriding default input behavior: setStereoInput(" + enable + ')');
    useStereoInput = enable;
  }

  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized boolean getStereoOutput() {
    return useStereoOutput;
  }

  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized boolean getStereoInput() {
    return useStereoInput;
  }

  // Default audio data format is PCM 16 bit per sample.
  // Guaranteed to be supported by all devices.
  private static final int BITS_PER_SAMPLE = 16;

  private static final int DEFAULT_FRAME_PER_BUFFER = 256;

  // Private utility class that periodically checks and logs the volume level
  // of the audio stream that is currently controlled by the volume control.
  // A timer triggers logs once every 30 seconds and the timer's associated
  // thread is named "WebRtcVolumeLevelLoggerThread".
  private static class VolumeLogger {
    private static final String THREAD_NAME = "WebRtcVolumeLevelLoggerThread";
    private static final int TIMER_PERIOD_IN_SECONDS = 30;

    private final AudioManager audioManager;
    private @Nullable Timer timer;

    public VolumeLogger(AudioManager audioManager) {
      this.audioManager = audioManager;
    }

    public void start() {
      timer = new Timer(THREAD_NAME);
      timer.schedule(new LogVolumeTask(audioManager.getStreamMaxVolume(AudioManager.STREAM_RING),
                         audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)),
          0, TIMER_PERIOD_IN_SECONDS * 1000);
    }

    private class LogVolumeTask extends TimerTask {
      private final int maxRingVolume;
      private final int maxVoiceCallVolume;

      LogVolumeTask(int maxRingVolume, int maxVoiceCallVolume) {
        this.maxRingVolume = maxRingVolume;
        this.maxVoiceCallVolume = maxVoiceCallVolume;
      }

      @Override
      public void run() {
        final int mode = audioManager.getMode();
        if (mode == AudioManager.MODE_RINGTONE) {
          Logging.d(TAG, "STREAM_RING stream volume: "
                  + audioManager.getStreamVolume(AudioManager.STREAM_RING) + " (max="
                  + maxRingVolume + ")");
        } else if (mode == AudioManager.MODE_IN_COMMUNICATION) {
          Logging.d(TAG, "VOICE_CALL stream volume: "
                  + audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) + " (max="
                  + maxVoiceCallVolume + ")");
        }
      }
    }

    private void stop() {
      if (timer != null) {
        timer.cancel();
        timer = null;
      }
    }
  }

  private final long nativeAudioManager;
  private final AudioManager audioManager;

  private boolean initialized;
  private int nativeSampleRate;
  private int nativeChannels;

  private boolean hardwareAEC;
  private boolean hardwareAGC;
  private boolean hardwareNS;
  private boolean lowLatencyOutput;
  private boolean lowLatencyInput;
  private boolean proAudio;
  private boolean aAudio;
  private int sampleRate;
  private int outputChannels;
  private int inputChannels;
  private int outputBufferSize;
  private int inputBufferSize;

  private final VolumeLogger volumeLogger;

  WebRtcAudioManager(long nativeAudioManager) {
    Logging.d(TAG, "ctor" + WebRtcAudioUtils.getThreadInfo());
    this.nativeAudioManager = nativeAudioManager;
    audioManager =
        (AudioManager) ContextUtils.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    if (DEBUG) {
      WebRtcAudioUtils.logDeviceInfo(TAG);
    }
    volumeLogger = new VolumeLogger(audioManager);
    storeAudioParameters();
    nativeCacheAudioParameters(sampleRate, outputChannels, inputChannels, hardwareAEC, hardwareAGC,
        hardwareNS, lowLatencyOutput, lowLatencyInput, proAudio, aAudio, outputBufferSize,
        inputBufferSize, nativeAudioManager);
    WebRtcAudioUtils.logAudioState(TAG);
  }

  private boolean init() {
    Logging.d(TAG, "init" + WebRtcAudioUtils.getThreadInfo());
    if (initialized) {
      return true;
    }
    Logging.d(TAG, "audio mode is: "
        + WebRtcAudioUtils.modeToString(audioManager.getMode()));
    initialized = true;
    volumeLogger.start();
    return true;
  }

  private void dispose() {
    Logging.d(TAG, "dispose" + WebRtcAudioUtils.getThreadInfo());
    if (!initialized) {
      return;
    }
    volumeLogger.stop();
  }

  private boolean isCommunicationModeEnabled() {
    return (audioManager.getMode() == AudioManager.MODE_IN_COMMUNICATION);
  }

  private boolean isDeviceBlacklistedForOpenSLESUsage() {
    boolean blacklisted = blacklistDeviceForOpenSLESUsageIsOverridden
        ? blacklistDeviceForOpenSLESUsage
        : WebRtcAudioUtils.deviceIsBlacklistedForOpenSLESUsage();
    if (blacklisted) {
      Logging.d(TAG, Build.MODEL + " is blacklisted for OpenSL ES usage!");
    }
    return true;//blacklisted;
  }

  private void storeAudioParameters() {
    outputChannels = getStereoOutput() ? 2 : 1;
    inputChannels = getStereoInput() ? 2 : 1;
    sampleRate = getNativeOutputSampleRate();
    hardwareAEC = isAcousticEchoCancelerSupported();
    // TODO(henrika): use of hardware AGC is no longer supported. Currently
    // hardcoded to false. To be removed.
    hardwareAGC = false;
    hardwareNS = isNoiseSuppressorSupported();
    lowLatencyOutput = isLowLatencyOutputSupported();
    lowLatencyInput = isLowLatencyInputSupported();
    proAudio = isProAudioSupported();
    aAudio = isAAudioSupported();
    outputBufferSize = lowLatencyOutput ? getLowLatencyOutputFramesPerBuffer()
                                        : getMinOutputFrameSize(sampleRate, outputChannels);
    inputBufferSize = lowLatencyInput ? getLowLatencyInputFramesPerBuffer()
                                      : getMinInputFrameSize(sampleRate, inputChannels);
  }

  // Gets the current earpiece state.
  private boolean hasEarpiece() {
    return ContextUtils.getApplicationContext().getPackageManager().hasSystemFeature(
        PackageManager.FEATURE_TELEPHONY);
  }

  // Returns true if low-latency audio output is supported.
  private boolean isLowLatencyOutputSupported() {
    return ContextUtils.getApplicationContext().getPackageManager().hasSystemFeature(
        PackageManager.FEATURE_AUDIO_LOW_LATENCY);
  }

  // Returns true if low-latency audio input is supported.
  // TODO(henrika): remove the hardcoded false return value when OpenSL ES
  // input performance has been evaluated and tested more.
  public boolean isLowLatencyInputSupported() {
    // TODO(henrika): investigate if some sort of device list is needed here
    // as well. The NDK doc states that: "As of API level 21, lower latency
    // audio input is supported on select devices. To take advantage of this
    // feature, first confirm that lower latency output is available".
    return Build.VERSION.SDK_INT >= 21 && isLowLatencyOutputSupported();
  }

  // Returns true if the device has professional audio level of functionality
  // and therefore supports the lowest possible round-trip latency.
  private boolean isProAudioSupported() {
    return Build.VERSION.SDK_INT >= 23
        && ContextUtils.getApplicationContext().getPackageManager().hasSystemFeature(
               PackageManager.FEATURE_AUDIO_PRO);
  }

  // AAudio is supported on Androio Oreo MR1 (API 27) and higher.
  // TODO(bugs.webrtc.org/8914): currently disabled by default.
  private boolean isAAudioSupported() {
    if (blacklistDeviceForAAudioUsage) {
      Logging.w(TAG, "AAudio support is currently disabled on all devices!");
    }
    return !blacklistDeviceForAAudioUsage && Build.VERSION.SDK_INT >= 27;
  }

  // Returns the native output sample rate for this device's output stream.
  private int getNativeOutputSampleRate() {
    // Override this if we're running on an old emulator image which only
    // supports 8 kHz and doesn't support PROPERTY_OUTPUT_SAMPLE_RATE.
    if (WebRtcAudioUtils.runningOnEmulator()) {
      Logging.d(TAG, "Running emulator, overriding sample rate to 8 kHz.");
      return 8000;
    }
    // Default can be overriden by WebRtcAudioUtils.setDefaultSampleRateHz().
    // If so, use that value and return here.
    if (WebRtcAudioUtils.isDefaultSampleRateOverridden()) {
      Logging.d(TAG, "Default sample rate is overriden to "
              + WebRtcAudioUtils.getDefaultSampleRateHz() + " Hz");
      return WebRtcAudioUtils.getDefaultSampleRateHz();
    }
    // No overrides available. Deliver best possible estimate based on default
    // Android AudioManager APIs.
    final int sampleRateHz = getSampleRateForApiLevel();
    Logging.d(TAG, "Sample rate is set to " + sampleRateHz + " Hz");
    return sampleRateHz;
  }

  private int getSampleRateForApiLevel() {
    if (Build.VERSION.SDK_INT < 17) {
      return WebRtcAudioUtils.getDefaultSampleRateHz();
    }
    String sampleRateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
    return (sampleRateString == null) ? WebRtcAudioUtils.getDefaultSampleRateHz()
                                      : Integer.parseInt(sampleRateString);
  }

  // Returns the native output buffer size for low-latency output streams.
  private int getLowLatencyOutputFramesPerBuffer() {
    assertTrue(isLowLatencyOutputSupported());
    if (Build.VERSION.SDK_INT < 17) {
      return DEFAULT_FRAME_PER_BUFFER;
    }
    String framesPerBuffer =
        audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
    return framesPerBuffer == null ? DEFAULT_FRAME_PER_BUFFER : Integer.parseInt(framesPerBuffer);
  }

  // Returns true if the device supports an audio effect (AEC or NS).
  // Four conditions must be fulfilled if functions are to return true:
  // 1) the platform must support the built-in (HW) effect,
  // 2) explicit use (override) of a WebRTC based version must not be set,
  // 3) the device must not be blacklisted for use of the effect, and
  // 4) the UUID of the effect must be approved (some UUIDs can be excluded).
  private static boolean isAcousticEchoCancelerSupported() {
    return WebRtcAudioEffects.canUseAcousticEchoCanceler();
  }
  private static boolean isNoiseSuppressorSupported() {
    return WebRtcAudioEffects.canUseNoiseSuppressor();
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

  // Returns the native input buffer size for input streams.
  private int getLowLatencyInputFramesPerBuffer() {
    assertTrue(isLowLatencyInputSupported());
    return getLowLatencyOutputFramesPerBuffer();
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

  // Helper method which throws an exception  when an assertion has failed.
  private static void assertTrue(boolean condition) {
    if (!condition) {
      throw new AssertionError("Expected condition to be true");
    }
  }

  private native void nativeCacheAudioParameters(int sampleRate, int outputChannels,
      int inputChannels, boolean hardwareAEC, boolean hardwareAGC, boolean hardwareNS,
      boolean lowLatencyOutput, boolean lowLatencyInput, boolean proAudio, boolean aAudio,
      int outputBufferSize, int inputBufferSize, long nativeAudioManager);
}
