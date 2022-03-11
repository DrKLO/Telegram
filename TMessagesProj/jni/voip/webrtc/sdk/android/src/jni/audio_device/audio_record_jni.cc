/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/audio_device/audio_record_jni.h"

#include <string>
#include <utility>

#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/format_macros.h"
#include "rtc_base/logging.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/time_utils.h"
#include "sdk/android/generated_java_audio_device_module_native_jni/WebRtcAudioRecord_jni.h"
#include "sdk/android/src/jni/audio_device/audio_common.h"
#include "sdk/android/src/jni/jni_helpers.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace jni {

namespace {
// Scoped class which logs its time of life as a UMA statistic. It generates
// a histogram which measures the time it takes for a method/scope to execute.
class ScopedHistogramTimer {
 public:
  explicit ScopedHistogramTimer(const std::string& name)
      : histogram_name_(name), start_time_ms_(rtc::TimeMillis()) {}
  ~ScopedHistogramTimer() {
    const int64_t life_time_ms = rtc::TimeSince(start_time_ms_);
    RTC_HISTOGRAM_COUNTS_1000(histogram_name_, life_time_ms);
    RTC_LOG(LS_INFO) << histogram_name_ << ": " << life_time_ms;
  }

 private:
  const std::string histogram_name_;
  int64_t start_time_ms_;
};

}  // namespace

ScopedJavaLocalRef<jobject> AudioRecordJni::CreateJavaWebRtcAudioRecord(
    JNIEnv* env,
    const JavaRef<jobject>& j_context,
    const JavaRef<jobject>& j_audio_manager) {
  return Java_WebRtcAudioRecord_Constructor(env, j_context, j_audio_manager);
}

AudioRecordJni::AudioRecordJni(JNIEnv* env,
                               const AudioParameters& audio_parameters,
                               int total_delay_ms,
                               const JavaRef<jobject>& j_audio_record)
    : j_audio_record_(env, j_audio_record),
      audio_parameters_(audio_parameters),
      total_delay_ms_(total_delay_ms),
      direct_buffer_address_(nullptr),
      direct_buffer_capacity_in_bytes_(0),
      frames_per_buffer_(0),
      initialized_(false),
      recording_(false),
      audio_device_buffer_(nullptr) {
  RTC_LOG(LS_INFO) << "ctor";
  RTC_DCHECK(audio_parameters_.is_valid());
  Java_WebRtcAudioRecord_setNativeAudioRecord(env, j_audio_record_,
                                              jni::jlongFromPointer(this));
  // Detach from this thread since construction is allowed to happen on a
  // different thread.
  thread_checker_.Detach();
  thread_checker_java_.Detach();
}

AudioRecordJni::~AudioRecordJni() {
  RTC_LOG(LS_INFO) << "dtor";
  RTC_DCHECK(thread_checker_.IsCurrent());
  Terminate();
}

int32_t AudioRecordJni::Init() {
  RTC_LOG(LS_INFO) << "Init";
  env_ = AttachCurrentThreadIfNeeded();
  RTC_DCHECK(thread_checker_.IsCurrent());
  return 0;
}

int32_t AudioRecordJni::Terminate() {
  RTC_LOG(LS_INFO) << "Terminate";
  RTC_DCHECK(thread_checker_.IsCurrent());
  StopRecording();
  thread_checker_.Detach();
  return 0;
}

int32_t AudioRecordJni::InitRecording() {
  RTC_LOG(LS_INFO) << "InitRecording";
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (initialized_) {
    // Already initialized.
    return 0;
  }
  RTC_DCHECK(!recording_);
  ScopedHistogramTimer timer("WebRTC.Audio.InitRecordingDurationMs");

  int frames_per_buffer = Java_WebRtcAudioRecord_initRecording(
      env_, j_audio_record_, audio_parameters_.sample_rate(),
      static_cast<int>(audio_parameters_.channels()));
  if (frames_per_buffer < 0) {
    direct_buffer_address_ = nullptr;
    RTC_LOG(LS_ERROR) << "InitRecording failed";
    return -1;
  }
  frames_per_buffer_ = static_cast<size_t>(frames_per_buffer);
  RTC_LOG(LS_INFO) << "frames_per_buffer: " << frames_per_buffer_;
  const size_t bytes_per_frame = audio_parameters_.channels() * sizeof(int16_t);
  RTC_CHECK_EQ(direct_buffer_capacity_in_bytes_,
               frames_per_buffer_ * bytes_per_frame);
  RTC_CHECK_EQ(frames_per_buffer_, audio_parameters_.frames_per_10ms_buffer());
  initialized_ = true;
  return 0;
}

bool AudioRecordJni::RecordingIsInitialized() const {
  return initialized_;
}

int32_t AudioRecordJni::StartRecording() {
  RTC_LOG(LS_INFO) << "StartRecording";
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (recording_) {
    // Already recording.
    return 0;
  }
  if (!initialized_) {
    RTC_DLOG(LS_WARNING)
        << "Recording can not start since InitRecording must succeed first";
    return 0;
  }
  ScopedHistogramTimer timer("WebRTC.Audio.StartRecordingDurationMs");
  if (!Java_WebRtcAudioRecord_startRecording(env_, j_audio_record_)) {
    RTC_LOG(LS_ERROR) << "StartRecording failed";
    return -1;
  }
  recording_ = true;
  return 0;
}

int32_t AudioRecordJni::StopRecording() {
  RTC_LOG(LS_INFO) << "StopRecording";
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!initialized_ || !recording_) {
    return 0;
  }
  // Check if the audio source matched the activated recording session but only
  // if a valid results exists to avoid invalid statistics.
  if (Java_WebRtcAudioRecord_isAudioConfigVerified(env_, j_audio_record_)) {
    const bool session_was_ok =
        Java_WebRtcAudioRecord_isAudioSourceMatchingRecordingSession(
            env_, j_audio_record_);
    RTC_HISTOGRAM_BOOLEAN("WebRTC.Audio.SourceMatchesRecordingSession",
                          session_was_ok);
    RTC_LOG(LS_INFO)
        << "HISTOGRAM(WebRTC.Audio.SourceMatchesRecordingSession): "
        << session_was_ok;
  }
  if (!Java_WebRtcAudioRecord_stopRecording(env_, j_audio_record_)) {
    RTC_LOG(LS_ERROR) << "StopRecording failed";
    return -1;
  }
  // If we don't detach here, we will hit a RTC_DCHECK in OnDataIsRecorded()
  // next time StartRecording() is called since it will create a new Java
  // thread.
  thread_checker_java_.Detach();
  initialized_ = false;
  recording_ = false;
  direct_buffer_address_ = nullptr;
  return 0;
}

bool AudioRecordJni::Recording() const {
  return recording_;
}

void AudioRecordJni::AttachAudioBuffer(AudioDeviceBuffer* audioBuffer) {
  RTC_LOG(LS_INFO) << "AttachAudioBuffer";
  RTC_DCHECK(thread_checker_.IsCurrent());
  audio_device_buffer_ = audioBuffer;
  const int sample_rate_hz = audio_parameters_.sample_rate();
  RTC_LOG(LS_INFO) << "SetRecordingSampleRate(" << sample_rate_hz << ")";
  audio_device_buffer_->SetRecordingSampleRate(sample_rate_hz);
  const size_t channels = audio_parameters_.channels();
  RTC_LOG(LS_INFO) << "SetRecordingChannels(" << channels << ")";
  audio_device_buffer_->SetRecordingChannels(channels);
}

bool AudioRecordJni::IsAcousticEchoCancelerSupported() const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  return Java_WebRtcAudioRecord_isAcousticEchoCancelerSupported(
      env_, j_audio_record_);
}

bool AudioRecordJni::IsNoiseSuppressorSupported() const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  return Java_WebRtcAudioRecord_isNoiseSuppressorSupported(env_,
                                                           j_audio_record_);
}

int32_t AudioRecordJni::EnableBuiltInAEC(bool enable) {
  RTC_LOG(LS_INFO) << "EnableBuiltInAEC(" << enable << ")";
  RTC_DCHECK(thread_checker_.IsCurrent());
  return Java_WebRtcAudioRecord_enableBuiltInAEC(env_, j_audio_record_, enable)
             ? 0
             : -1;
}

int32_t AudioRecordJni::EnableBuiltInNS(bool enable) {
  RTC_LOG(LS_INFO) << "EnableBuiltInNS(" << enable << ")";
  RTC_DCHECK(thread_checker_.IsCurrent());
  return Java_WebRtcAudioRecord_enableBuiltInNS(env_, j_audio_record_, enable)
             ? 0
             : -1;
}

void AudioRecordJni::CacheDirectBufferAddress(
    JNIEnv* env,
    const JavaParamRef<jobject>& j_caller,
    const JavaParamRef<jobject>& byte_buffer) {
  RTC_LOG(LS_INFO) << "OnCacheDirectBufferAddress";
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!direct_buffer_address_);
  direct_buffer_address_ = env->GetDirectBufferAddress(byte_buffer.obj());
  jlong capacity = env->GetDirectBufferCapacity(byte_buffer.obj());
  RTC_LOG(LS_INFO) << "direct buffer capacity: " << capacity;
  direct_buffer_capacity_in_bytes_ = static_cast<size_t>(capacity);
}

// This method is called on a high-priority thread from Java. The name of
// the thread is 'AudioRecordThread'.
void AudioRecordJni::DataIsRecorded(JNIEnv* env,
                                    const JavaParamRef<jobject>& j_caller,
                                    int length,
                                    int64_t capture_timestamp_ns) {
  RTC_DCHECK(thread_checker_java_.IsCurrent());
  if (!audio_device_buffer_) {
    RTC_LOG(LS_ERROR) << "AttachAudioBuffer has not been called";
    return;
  }
  audio_device_buffer_->SetRecordedBuffer(
      direct_buffer_address_, frames_per_buffer_, capture_timestamp_ns);
  // We provide one (combined) fixed delay estimate for the APM and use the
  // `playDelayMs` parameter only. Components like the AEC only sees the sum
  // of `playDelayMs` and `recDelayMs`, hence the distributions does not matter.
  audio_device_buffer_->SetVQEData(total_delay_ms_, 0);
  if (audio_device_buffer_->DeliverRecordedData() == -1) {
    RTC_LOG(LS_INFO) << "AudioDeviceBuffer::DeliverRecordedData failed";
  }
}

}  // namespace jni

}  // namespace webrtc
