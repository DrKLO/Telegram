/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_device/android/audio_screen_record_jni.h"

#include <string>
#include <utility>

#include "modules/audio_device/android/audio_common.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/format_macros.h"
#include "rtc_base/logging.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

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
    RTC_LOG(INFO) << histogram_name_ << ": " << life_time_ms;
  }

 private:
  const std::string histogram_name_;
  int64_t start_time_ms_;
};
}  // namespace

// AudioRecordJni::JavaAudioRecord implementation.
AudioScreenRecordJni::JavaAudioRecord::JavaAudioRecord(
    NativeRegistration* native_reg,
    std::unique_ptr<GlobalRef> audio_record)
    : audio_record_(std::move(audio_record)),
      init_recording_(native_reg->GetMethodId("initRecording", "(II)I")),
      start_recording_(native_reg->GetMethodId("startRecording", "()Z")),
      stop_recording_(native_reg->GetMethodId("stopRecording", "()Z")),
      enable_built_in_aec_(native_reg->GetMethodId("enableBuiltInAEC", "(Z)Z")),
      enable_built_in_ns_(native_reg->GetMethodId("enableBuiltInNS", "(Z)Z")) {}

int AudioScreenRecordJni::JavaAudioRecord::InitRecording(int sample_rate,
                                                   size_t channels) {
  return audio_record_->CallIntMethod(init_recording_,
                                      static_cast<jint>(sample_rate),
                                      static_cast<jint>(channels));
}

bool AudioScreenRecordJni::JavaAudioRecord::StartRecording() {
  return audio_record_->CallBooleanMethod(start_recording_);
}

bool AudioScreenRecordJni::JavaAudioRecord::StopRecording() {
  return audio_record_->CallBooleanMethod(stop_recording_);
}

bool AudioScreenRecordJni::JavaAudioRecord::EnableBuiltInAEC(bool enable) {
  return audio_record_->CallBooleanMethod(enable_built_in_aec_,
                                          static_cast<jboolean>(enable));
}

bool AudioScreenRecordJni::JavaAudioRecord::EnableBuiltInNS(bool enable) {
  return audio_record_->CallBooleanMethod(enable_built_in_ns_,
                                          static_cast<jboolean>(enable));
}

// AudioRecordJni implementation.
AudioScreenRecordJni::AudioScreenRecordJni(AudioManager* audio_manager)
    : j_environment_(JVM::GetInstance()->environment()),
      audio_manager_(audio_manager),
      audio_parameters_(audio_manager->GetRecordAudioParameters()),
      total_delay_in_milliseconds_(0),
      direct_buffer_address_(nullptr),
      direct_buffer_capacity_in_bytes_(0),
      frames_per_buffer_(0),
      initialized_(false),
      recording_(false),
      audio_device_buffer_(nullptr) {
  RTC_LOG(INFO) << "ctor";
  RTC_DCHECK(audio_parameters_.is_valid());
  RTC_CHECK(j_environment_);
  JNINativeMethod native_methods[] = {
      {"nativeCacheDirectBufferAddress", "(Ljava/nio/ByteBuffer;J)V",
       reinterpret_cast<void*>(
           &webrtc::AudioScreenRecordJni::CacheDirectBufferAddress)},
      {"nativeDataIsRecorded", "(IJ)V",
       reinterpret_cast<void*>(&webrtc::AudioScreenRecordJni::DataIsRecorded)}};
  j_native_registration_ = j_environment_->RegisterNatives(
      "org/webrtc/voiceengine/WebRtcAudioRecord", native_methods,
      arraysize(native_methods));
  j_audio_record_.reset(
      new JavaAudioRecord(j_native_registration_.get(),
                          j_native_registration_->NewObject(
                              "<init>", "(JI)V", PointerTojlong(this), 1)));
  // Detach from this thread since we want to use the checker to verify calls
  // from the Java based audio thread.
  thread_checker_java_.Detach();
}

AudioScreenRecordJni::~AudioScreenRecordJni() {
  RTC_LOG(INFO) << "dtor";
  RTC_DCHECK(thread_checker_.IsCurrent());
  Terminate();
}

int32_t AudioScreenRecordJni::Init() {
  RTC_LOG(INFO) << "Init";
  RTC_DCHECK(thread_checker_.IsCurrent());
  return 0;
}

int32_t AudioScreenRecordJni::Terminate() {
  RTC_LOG(INFO) << "Terminate";
  RTC_DCHECK(thread_checker_.IsCurrent());
  StopRecording();
  return 0;
}

int32_t AudioScreenRecordJni::InitRecording() {
  RTC_LOG(INFO) << "InitRecording";
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!initialized_);
  RTC_DCHECK(!recording_);
  ScopedHistogramTimer timer("WebRTC.Audio.InitRecordingDurationMs");
  int frames_per_buffer = j_audio_record_->InitRecording(
      audio_parameters_.sample_rate(), audio_parameters_.channels());
  if (frames_per_buffer < 0) {
    direct_buffer_address_ = nullptr;
    RTC_LOG(LS_ERROR) << "InitRecording failed";
    return -1;
  }
  frames_per_buffer_ = static_cast<size_t>(frames_per_buffer);
  RTC_LOG(INFO) << "frames_per_buffer: " << frames_per_buffer_;
  const size_t bytes_per_frame = audio_parameters_.channels() * sizeof(int16_t);
  RTC_CHECK_EQ(direct_buffer_capacity_in_bytes_,
               frames_per_buffer_ * bytes_per_frame);
  RTC_CHECK_EQ(frames_per_buffer_, audio_parameters_.frames_per_10ms_buffer());
  initialized_ = true;
  return 0;
}

int32_t AudioScreenRecordJni::StartRecording() {
  RTC_LOG(INFO) << "StartRecording";
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!recording_);
  if (!initialized_) {
    RTC_DLOG(LS_WARNING)
        << "Recording can not start since InitRecording must succeed first";
    return 0;
  }
  ScopedHistogramTimer timer("WebRTC.Audio.StartRecordingDurationMs");
  if (!j_audio_record_->StartRecording()) {
    RTC_LOG(LS_ERROR) << "StartRecording failed";
    return -1;
  }
  recording_ = true;
  return 0;
}

int32_t AudioScreenRecordJni::StopRecording() {
  RTC_LOG(INFO) << "StopRecording";
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!initialized_ || !recording_) {
    return 0;
  }
  if (!j_audio_record_->StopRecording()) {
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

void AudioScreenRecordJni::AttachAudioBuffer(AudioDeviceBuffer* audioBuffer) {
  RTC_LOG(INFO) << "AttachAudioBuffer";
  RTC_DCHECK(thread_checker_.IsCurrent());
  audio_device_buffer_ = audioBuffer;
  const int sample_rate_hz = audio_parameters_.sample_rate();
  RTC_LOG(INFO) << "SetRecordingSampleRate(" << sample_rate_hz << ")";
  audio_device_buffer_->SetRecordingSampleRate(sample_rate_hz);
  const size_t channels = audio_parameters_.channels();
  RTC_LOG(INFO) << "SetRecordingChannels(" << channels << ")";
  audio_device_buffer_->SetRecordingChannels(channels);
  total_delay_in_milliseconds_ =
      audio_manager_->GetDelayEstimateInMilliseconds();
  RTC_DCHECK_GT(total_delay_in_milliseconds_, 0);
  RTC_LOG(INFO) << "total_delay_in_milliseconds: "
                << total_delay_in_milliseconds_;
}

int32_t AudioScreenRecordJni::EnableBuiltInAEC(bool enable) {
  RTC_LOG(INFO) << "EnableBuiltInAEC(" << enable << ")";
  RTC_DCHECK(thread_checker_.IsCurrent());
  return j_audio_record_->EnableBuiltInAEC(enable) ? 0 : -1;
}

int32_t AudioScreenRecordJni::EnableBuiltInAGC(bool enable) {
  // TODO(henrika): possibly remove when no longer used by any client.
  RTC_CHECK_NOTREACHED();
}

int32_t AudioScreenRecordJni::EnableBuiltInNS(bool enable) {
  RTC_LOG(INFO) << "EnableBuiltInNS(" << enable << ")";
  RTC_DCHECK(thread_checker_.IsCurrent());
  return j_audio_record_->EnableBuiltInNS(enable) ? 0 : -1;
}

JNI_FUNCTION_ALIGN
void JNICALL AudioScreenRecordJni::CacheDirectBufferAddress(JNIEnv* env,
                                                      jobject obj,
                                                      jobject byte_buffer,
                                                      jlong nativeAudioRecord) {
  webrtc::AudioScreenRecordJni* this_object =
      reinterpret_cast<webrtc::AudioScreenRecordJni*>(nativeAudioRecord);
  this_object->OnCacheDirectBufferAddress(env, byte_buffer);
}

void AudioScreenRecordJni::OnCacheDirectBufferAddress(JNIEnv* env,
                                                jobject byte_buffer) {
  RTC_LOG(INFO) << "OnCacheDirectBufferAddress";
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!direct_buffer_address_);
  direct_buffer_address_ = env->GetDirectBufferAddress(byte_buffer);
  jlong capacity = env->GetDirectBufferCapacity(byte_buffer);
  RTC_LOG(INFO) << "direct buffer capacity: " << capacity;
  direct_buffer_capacity_in_bytes_ = static_cast<size_t>(capacity);
}

JNI_FUNCTION_ALIGN
void JNICALL AudioScreenRecordJni::DataIsRecorded(JNIEnv* env,
                                            jobject obj,
                                            jint length,
                                            jlong nativeAudioRecord) {
  webrtc::AudioScreenRecordJni* this_object =
      reinterpret_cast<webrtc::AudioScreenRecordJni*>(nativeAudioRecord);
  this_object->OnDataIsRecorded(length);
}

// This method is called on a high-priority thread from Java. The name of
// the thread is 'AudioRecordThread'.
void AudioScreenRecordJni::OnDataIsRecorded(int length) {
  RTC_DCHECK(thread_checker_java_.IsCurrent());
  if (!audio_device_buffer_) {
    RTC_LOG(LS_ERROR) << "AttachAudioBuffer has not been called";
    return;
  }
  audio_device_buffer_->SetRecordedBuffer(direct_buffer_address_,
                                          frames_per_buffer_);
  // We provide one (combined) fixed delay estimate for the APM and use the
  // |playDelayMs| parameter only. Components like the AEC only sees the sum
  // of |playDelayMs| and |recDelayMs|, hence the distributions does not matter.
  audio_device_buffer_->SetVQEData(total_delay_in_milliseconds_, 0);
  if (audio_device_buffer_->DeliverRecordedData() == -1) {
    RTC_LOG(INFO) << "AudioDeviceBuffer::DeliverRecordedData failed";
  }
}

}  // namespace webrtc
