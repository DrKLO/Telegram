/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/native_api/audio_device_module/audio_device_android.h"

#include <stdlib.h>

#include <memory>
#include <utility>

#include "api/scoped_refptr.h"
#include "rtc_base/logging.h"
#include "rtc_base/ref_count.h"

#if defined(WEBRTC_AUDIO_DEVICE_INCLUDE_ANDROID_AAUDIO)
#include "sdk/android/src/jni/audio_device/aaudio_player.h"
#include "sdk/android/src/jni/audio_device/aaudio_recorder.h"
#endif

#include "sdk/android/native_api/jni/application_context_provider.h"
#include "sdk/android/src/jni/audio_device/audio_record_jni.h"
#include "sdk/android/src/jni/audio_device/audio_track_jni.h"
#include "sdk/android/src/jni/audio_device/opensles_player.h"
#include "sdk/android/src/jni/audio_device/opensles_recorder.h"
#include "sdk/android/src/jni/jvm.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace {

void GetDefaultAudioParameters(JNIEnv* env,
                               jobject application_context,
                               AudioParameters* input_parameters,
                               AudioParameters* output_parameters) {
  const JavaParamRef<jobject> j_context(application_context);
  const ScopedJavaLocalRef<jobject> j_audio_manager =
      jni::GetAudioManager(env, j_context);
  const int input_sample_rate = jni::GetDefaultSampleRate(env, j_audio_manager);
  const int output_sample_rate =
      jni::GetDefaultSampleRate(env, j_audio_manager);
  jni::GetAudioParameters(env, j_context, j_audio_manager, input_sample_rate,
                          output_sample_rate, false /* use_stereo_input */,
                          false /* use_stereo_output */, input_parameters,
                          output_parameters);
}

}  // namespace

#if defined(WEBRTC_AUDIO_DEVICE_INCLUDE_ANDROID_AAUDIO)
rtc::scoped_refptr<AudioDeviceModule> CreateAAudioAudioDeviceModule(
    JNIEnv* env,
    jobject application_context) {
  RTC_DLOG(LS_INFO) << __FUNCTION__;
  // Get default audio input/output parameters.
  AudioParameters input_parameters;
  AudioParameters output_parameters;
  GetDefaultAudioParameters(env, application_context, &input_parameters,
                            &output_parameters);
  // Create ADM from AAudioRecorder and AAudioPlayer.
  return CreateAudioDeviceModuleFromInputAndOutput(
      AudioDeviceModule::kAndroidAAudioAudio, false /* use_stereo_input */,
      false /* use_stereo_output */,
      jni::kLowLatencyModeDelayEstimateInMilliseconds,
      std::make_unique<jni::AAudioRecorder>(input_parameters),
      std::make_unique<jni::AAudioPlayer>(output_parameters));
}

rtc::scoped_refptr<AudioDeviceModule>
CreateJavaInputAndAAudioOutputAudioDeviceModule(JNIEnv* env,
                                                jobject application_context) {
  RTC_DLOG(LS_INFO) << __FUNCTION__;
  // Get default audio input/output parameters.
  const JavaParamRef<jobject> j_context(application_context);
  const ScopedJavaLocalRef<jobject> j_audio_manager =
      jni::GetAudioManager(env, j_context);
  AudioParameters input_parameters;
  AudioParameters output_parameters;
  GetDefaultAudioParameters(env, application_context, &input_parameters,
                            &output_parameters);
  // Create ADM from AudioRecord and OpenSLESPlayer.
  auto audio_input = std::make_unique<jni::AudioRecordJni>(
      env, input_parameters, jni::kLowLatencyModeDelayEstimateInMilliseconds,
      jni::AudioRecordJni::CreateJavaWebRtcAudioRecord(env, j_context,
                                                       j_audio_manager));

  return CreateAudioDeviceModuleFromInputAndOutput(
      AudioDeviceModule::kAndroidJavaInputAndOpenSLESOutputAudio,
      false /* use_stereo_input */, false /* use_stereo_output */,
      jni::kLowLatencyModeDelayEstimateInMilliseconds, std::move(audio_input),
      std::make_unique<jni::AAudioPlayer>(output_parameters));
}
#endif

rtc::scoped_refptr<AudioDeviceModule> CreateJavaAudioDeviceModule(
    JNIEnv* env,
    jobject application_context) {
  RTC_DLOG(LS_INFO) << __FUNCTION__;
  // Get default audio input/output parameters.
  const JavaParamRef<jobject> j_context(application_context);
  const ScopedJavaLocalRef<jobject> j_audio_manager =
      jni::GetAudioManager(env, j_context);
  AudioParameters input_parameters;
  AudioParameters output_parameters;
  GetDefaultAudioParameters(env, application_context, &input_parameters,
                            &output_parameters);
  // Create ADM from AudioRecord and AudioTrack.
  auto audio_input = std::make_unique<jni::AudioRecordJni>(
      env, input_parameters, jni::kHighLatencyModeDelayEstimateInMilliseconds,
      jni::AudioRecordJni::CreateJavaWebRtcAudioRecord(env, j_context,
                                                       j_audio_manager));
  auto audio_output = std::make_unique<jni::AudioTrackJni>(
      env, output_parameters,
      jni::AudioTrackJni::CreateJavaWebRtcAudioTrack(env, j_context,
                                                     j_audio_manager));
  return CreateAudioDeviceModuleFromInputAndOutput(
      AudioDeviceModule::kAndroidJavaAudio, false /* use_stereo_input */,
      false /* use_stereo_output */,
      jni::kHighLatencyModeDelayEstimateInMilliseconds, std::move(audio_input),
      std::move(audio_output));
}

rtc::scoped_refptr<AudioDeviceModule> CreateOpenSLESAudioDeviceModule(
    JNIEnv* env,
    jobject application_context) {
  RTC_DLOG(LS_INFO) << __FUNCTION__;
  // Get default audio input/output parameters.
  AudioParameters input_parameters;
  AudioParameters output_parameters;
  GetDefaultAudioParameters(env, application_context, &input_parameters,
                            &output_parameters);
  // Create ADM from OpenSLESRecorder and OpenSLESPlayer.
  rtc::scoped_refptr<jni::OpenSLEngineManager> engine_manager(
      new jni::OpenSLEngineManager());
  auto audio_input =
      std::make_unique<jni::OpenSLESRecorder>(input_parameters, engine_manager);
  auto audio_output = std::make_unique<jni::OpenSLESPlayer>(
      output_parameters, std::move(engine_manager));
  return CreateAudioDeviceModuleFromInputAndOutput(
      AudioDeviceModule::kAndroidOpenSLESAudio, false /* use_stereo_input */,
      false /* use_stereo_output */,
      jni::kLowLatencyModeDelayEstimateInMilliseconds, std::move(audio_input),
      std::move(audio_output));
}

rtc::scoped_refptr<AudioDeviceModule>
CreateJavaInputAndOpenSLESOutputAudioDeviceModule(JNIEnv* env,
                                                  jobject application_context) {
  RTC_DLOG(LS_INFO) << __FUNCTION__;
  // Get default audio input/output parameters.
  const JavaParamRef<jobject> j_context(application_context);
  const ScopedJavaLocalRef<jobject> j_audio_manager =
      jni::GetAudioManager(env, j_context);
  AudioParameters input_parameters;
  AudioParameters output_parameters;
  GetDefaultAudioParameters(env, application_context, &input_parameters,
                            &output_parameters);
  // Create ADM from AudioRecord and OpenSLESPlayer.
  auto audio_input = std::make_unique<jni::AudioRecordJni>(
      env, input_parameters, jni::kLowLatencyModeDelayEstimateInMilliseconds,
      jni::AudioRecordJni::CreateJavaWebRtcAudioRecord(env, j_context,
                                                       j_audio_manager));

  rtc::scoped_refptr<jni::OpenSLEngineManager> engine_manager(
      new jni::OpenSLEngineManager());
  auto audio_output = std::make_unique<jni::OpenSLESPlayer>(
      output_parameters, std::move(engine_manager));
  return CreateAudioDeviceModuleFromInputAndOutput(
      AudioDeviceModule::kAndroidJavaInputAndOpenSLESOutputAudio,
      false /* use_stereo_input */, false /* use_stereo_output */,
      jni::kLowLatencyModeDelayEstimateInMilliseconds, std::move(audio_input),
      std::move(audio_output));
}

rtc::scoped_refptr<AudioDeviceModule> CreateAndroidAudioDeviceModule(
    AudioDeviceModule::AudioLayer audio_layer) {
  auto env = AttachCurrentThreadIfNeeded();
  auto j_context = webrtc::GetAppContext(env);
  // Select best possible combination of audio layers.
  if (audio_layer == AudioDeviceModule::kPlatformDefaultAudio) {
#if defined(WEBRTC_AUDIO_DEVICE_INCLUDE_ANDROID_AAUDIO)
    // AAudio based audio for both input and output.
    audio_layer = AudioDeviceModule::kAndroidAAudioAudio;
#else
    if (jni::IsLowLatencyInputSupported(env, j_context) &&
        jni::IsLowLatencyOutputSupported(env, j_context)) {
      // Use OpenSL ES for both playout and recording.
      audio_layer = AudioDeviceModule::kAndroidOpenSLESAudio;
    } else if (jni::IsLowLatencyOutputSupported(env, j_context) &&
               !jni::IsLowLatencyInputSupported(env, j_context)) {
      // Use OpenSL ES for output on devices that only supports the
      // low-latency output audio path.
      audio_layer = AudioDeviceModule::kAndroidJavaInputAndOpenSLESOutputAudio;
    } else {
      // Use Java-based audio in both directions when low-latency output is
      // not supported.
      audio_layer = AudioDeviceModule::kAndroidJavaAudio;
    }
#endif
  }
  switch (audio_layer) {
    case AudioDeviceModule::kAndroidJavaAudio:
      // Java audio for both input and output audio.
      return CreateJavaAudioDeviceModule(env, j_context.obj());
    case AudioDeviceModule::kAndroidOpenSLESAudio:
      // OpenSL ES based audio for both input and output audio.
      return CreateOpenSLESAudioDeviceModule(env, j_context.obj());
    case AudioDeviceModule::kAndroidJavaInputAndOpenSLESOutputAudio:
      // Java audio for input and OpenSL ES for output audio (i.e. mixed APIs).
      // This combination provides low-latency output audio and at the same
      // time support for HW AEC using the AudioRecord Java API.
      return CreateJavaInputAndOpenSLESOutputAudioDeviceModule(
        env, j_context.obj());
#if defined(WEBRTC_AUDIO_DEVICE_INCLUDE_ANDROID_AAUDIO)
    case AudioDeviceModule::kAndroidAAudioAudio:
      // AAudio based audio for both input and output.
      return CreateAAudioAudioDeviceModule(env, j_context.obj());
    case AudioDeviceModule::kAndroidJavaInputAndAAudioOutputAudio:
      // Java audio for input and AAudio for output audio (i.e. mixed APIs).
      return CreateJavaInputAndAAudioOutputAudioDeviceModule(
        env, j_context.obj());
#endif
    default:
      return nullptr;
  }
}

}  // namespace webrtc
