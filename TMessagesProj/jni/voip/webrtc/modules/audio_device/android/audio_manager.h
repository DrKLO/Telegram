/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_DEVICE_ANDROID_AUDIO_MANAGER_H_
#define MODULES_AUDIO_DEVICE_ANDROID_AUDIO_MANAGER_H_

#include <SLES/OpenSLES.h>
#include <jni.h>

#include <memory>

#include "api/sequence_checker.h"
#include "modules/audio_device/android/audio_common.h"
#include "modules/audio_device/android/opensles_common.h"
#include "modules/audio_device/audio_device_config.h"
#include "modules/audio_device/audio_device_generic.h"
#include "modules/audio_device/include/audio_device_defines.h"
#include "modules/utility/include/helpers_android.h"
#include "modules/utility/include/jvm_android.h"

namespace webrtc {

// Implements support for functions in the WebRTC audio stack for Android that
// relies on the AudioManager in android.media. It also populates an
// AudioParameter structure with native audio parameters detected at
// construction. This class does not make any audio-related modifications
// unless Init() is called. Caching audio parameters makes no changes but only
// reads data from the Java side.
class AudioManager {
 public:
  // Wraps the Java specific parts of the AudioManager into one helper class.
  // Stores method IDs for all supported methods at construction and then
  // allows calls like JavaAudioManager::Close() while hiding the Java/JNI
  // parts that are associated with this call.
  class JavaAudioManager {
   public:
    JavaAudioManager(NativeRegistration* native_registration,
                     std::unique_ptr<GlobalRef> audio_manager);
    ~JavaAudioManager();

    bool Init();
    void Close();
    bool IsCommunicationModeEnabled();
    bool IsDeviceBlacklistedForOpenSLESUsage();

   private:
    std::unique_ptr<GlobalRef> audio_manager_;
    jmethodID init_;
    jmethodID dispose_;
    jmethodID is_communication_mode_enabled_;
    jmethodID is_device_blacklisted_for_open_sles_usage_;
  };

  AudioManager();
  ~AudioManager();

  // Sets the currently active audio layer combination. Must be called before
  // Init().
  void SetActiveAudioLayer(AudioDeviceModule::AudioLayer audio_layer);

  // Creates and realizes the main (global) Open SL engine object and returns
  // a reference to it. The engine object is only created at the first call
  // since OpenSL ES for Android only supports a single engine per application.
  // Subsequent calls returns the already created engine. The SL engine object
  // is destroyed when the AudioManager object is deleted. It means that the
  // engine object will be the first OpenSL ES object to be created and last
  // object to be destroyed.
  // Note that NULL will be returned unless the audio layer is specified as
  // AudioDeviceModule::kAndroidOpenSLESAudio or
  // AudioDeviceModule::kAndroidJavaInputAndOpenSLESOutputAudio.
  SLObjectItf GetOpenSLEngine();

  // Initializes the audio manager and stores the current audio mode.
  bool Init();
  // Revert any setting done by Init().
  bool Close();

  // Returns true if current audio mode is AudioManager.MODE_IN_COMMUNICATION.
  bool IsCommunicationModeEnabled() const;

  // Native audio parameters stored during construction.
  const AudioParameters& GetPlayoutAudioParameters();
  const AudioParameters& GetRecordAudioParameters();

  // Returns true if the device supports built-in audio effects for AEC, AGC
  // and NS. Some devices can also be blacklisted for use in combination with
  // platform effects and these devices will return false.
  // Can currently only be used in combination with a Java based audio backend
  // for the recoring side (i.e. using the android.media.AudioRecord API).
  bool IsAcousticEchoCancelerSupported() const;
  bool IsAutomaticGainControlSupported() const;
  bool IsNoiseSuppressorSupported() const;

  // Returns true if the device supports the low-latency audio paths in
  // combination with OpenSL ES.
  bool IsLowLatencyPlayoutSupported() const;
  bool IsLowLatencyRecordSupported() const;

  // Returns true if the device supports (and has been configured for) stereo.
  // Call the Java API WebRtcAudioManager.setStereoOutput/Input() with true as
  // paramter to enable stereo. Default is mono in both directions and the
  // setting is set once and for all when the audio manager object is created.
  // TODO(henrika): stereo is not supported in combination with OpenSL ES.
  bool IsStereoPlayoutSupported() const;
  bool IsStereoRecordSupported() const;

  // Returns true if the device supports pro-audio features in combination with
  // OpenSL ES.
  bool IsProAudioSupported() const;

  // Returns true if the device supports AAudio.
  bool IsAAudioSupported() const;

  // Returns the estimated total delay of this device. Unit is in milliseconds.
  // The vaule is set once at construction and never changes after that.
  // Possible values are webrtc::kLowLatencyModeDelayEstimateInMilliseconds and
  // webrtc::kHighLatencyModeDelayEstimateInMilliseconds.
  int GetDelayEstimateInMilliseconds() const;

 private:
  // Called from Java side so we can cache the native audio parameters.
  // This method will be called by the WebRtcAudioManager constructor, i.e.
  // on the same thread that this object is created on.
  static void JNICALL CacheAudioParameters(JNIEnv* env,
                                           jobject obj,
                                           jint sample_rate,
                                           jint output_channels,
                                           jint input_channels,
                                           jboolean hardware_aec,
                                           jboolean hardware_agc,
                                           jboolean hardware_ns,
                                           jboolean low_latency_output,
                                           jboolean low_latency_input,
                                           jboolean pro_audio,
                                           jboolean a_audio,
                                           jint output_buffer_size,
                                           jint input_buffer_size,
                                           jlong native_audio_manager);
  void OnCacheAudioParameters(JNIEnv* env,
                              jint sample_rate,
                              jint output_channels,
                              jint input_channels,
                              jboolean hardware_aec,
                              jboolean hardware_agc,
                              jboolean hardware_ns,
                              jboolean low_latency_output,
                              jboolean low_latency_input,
                              jboolean pro_audio,
                              jboolean a_audio,
                              jint output_buffer_size,
                              jint input_buffer_size);

  // Stores thread ID in the constructor.
  // We can then use RTC_DCHECK_RUN_ON(&thread_checker_) to ensure that
  // other methods are called from the same thread.
  SequenceChecker thread_checker_;

  // Calls JavaVM::AttachCurrentThread() if this thread is not attached at
  // construction.
  // Also ensures that DetachCurrentThread() is called at destruction.
  JvmThreadConnector attach_thread_if_needed_;

  // Wraps the JNI interface pointer and methods associated with it.
  std::unique_ptr<JNIEnvironment> j_environment_;

  // Contains factory method for creating the Java object.
  std::unique_ptr<NativeRegistration> j_native_registration_;

  // Wraps the Java specific parts of the AudioManager.
  std::unique_ptr<AudioManager::JavaAudioManager> j_audio_manager_;

  // Contains the selected audio layer specified by the AudioLayer enumerator
  // in the AudioDeviceModule class.
  AudioDeviceModule::AudioLayer audio_layer_;

  // This object is the global entry point of the OpenSL ES API.
  // After creating the engine object, the application can obtain this object‘s
  // SLEngineItf interface. This interface contains creation methods for all
  // the other object types in the API. None of these interface are realized
  // by this class. It only provides access to the global engine object.
  webrtc::ScopedSLObjectItf engine_object_;

  // Set to true by Init() and false by Close().
  bool initialized_;

  // True if device supports hardware (or built-in) AEC.
  bool hardware_aec_;
  // True if device supports hardware (or built-in) AGC.
  bool hardware_agc_;
  // True if device supports hardware (or built-in) NS.
  bool hardware_ns_;

  // True if device supports the low-latency OpenSL ES audio path for output.
  bool low_latency_playout_;

  // True if device supports the low-latency OpenSL ES audio path for input.
  bool low_latency_record_;

  // True if device supports the low-latency OpenSL ES pro-audio path.
  bool pro_audio_;

  // True if device supports the low-latency AAudio audio path.
  bool a_audio_;

  // The delay estimate can take one of two fixed values depending on if the
  // device supports low-latency output or not.
  int delay_estimate_in_milliseconds_;

  // Contains native parameters (e.g. sample rate, channel configuration).
  // Set at construction in OnCacheAudioParameters() which is called from
  // Java on the same thread as this object is created on.
  AudioParameters playout_parameters_;
  AudioParameters record_parameters_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_DEVICE_ANDROID_AUDIO_MANAGER_H_
