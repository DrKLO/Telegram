/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_device/android/audio_track_jni.h"

#include <utility>

#include "modules/audio_device/android/audio_manager.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/format_macros.h"
#include "rtc_base/logging.h"
#include "rtc_base/platform_thread.h"
#include "system_wrappers/include/field_trial.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

// AudioTrackJni::JavaAudioTrack implementation.
AudioTrackJni::JavaAudioTrack::JavaAudioTrack(
    NativeRegistration* native_reg,
    std::unique_ptr<GlobalRef> audio_track)
    : audio_track_(std::move(audio_track)),
      init_playout_(native_reg->GetMethodId("initPlayout", "(IID)I")),
      start_playout_(native_reg->GetMethodId("startPlayout", "()Z")),
      stop_playout_(native_reg->GetMethodId("stopPlayout", "()Z")),
      set_stream_volume_(native_reg->GetMethodId("setStreamVolume", "(I)Z")),
      get_stream_max_volume_(
          native_reg->GetMethodId("getStreamMaxVolume", "()I")),
      get_stream_volume_(native_reg->GetMethodId("getStreamVolume", "()I")),
      get_buffer_size_in_frames_(
          native_reg->GetMethodId("getBufferSizeInFrames", "()I")) {}

AudioTrackJni::JavaAudioTrack::~JavaAudioTrack() {}

bool AudioTrackJni::JavaAudioTrack::InitPlayout(int sample_rate, int channels) {
  double buffer_size_factor =
      strtod(webrtc::field_trial::FindFullName(
                 "WebRTC-AudioDevicePlayoutBufferSizeFactor")
                 .c_str(),
             nullptr);
  if (buffer_size_factor == 0)
    buffer_size_factor = 1.0;
  int requested_buffer_size_bytes = audio_track_->CallIntMethod(
      init_playout_, sample_rate, channels, buffer_size_factor);
  // Update UMA histograms for both the requested and actual buffer size.
  if (requested_buffer_size_bytes >= 0) {
    // To avoid division by zero, we assume the sample rate is 48k if an invalid
    // value is found.
    sample_rate = sample_rate <= 0 ? 48000 : sample_rate;
    // This calculation assumes that audio is mono.
    const int requested_buffer_size_ms =
        (requested_buffer_size_bytes * 1000) / (2 * sample_rate);
    RTC_HISTOGRAM_COUNTS("WebRTC.Audio.AndroidNativeRequestedAudioBufferSizeMs",
                         requested_buffer_size_ms, 0, 1000, 100);
    int actual_buffer_size_frames =
        audio_track_->CallIntMethod(get_buffer_size_in_frames_);
    if (actual_buffer_size_frames >= 0) {
      const int actual_buffer_size_ms =
          actual_buffer_size_frames * 1000 / sample_rate;
      RTC_HISTOGRAM_COUNTS("WebRTC.Audio.AndroidNativeAudioBufferSizeMs",
                           actual_buffer_size_ms, 0, 1000, 100);
    }
    return true;
  }
  return false;
}

bool AudioTrackJni::JavaAudioTrack::StartPlayout() {
  return audio_track_->CallBooleanMethod(start_playout_);
}

bool AudioTrackJni::JavaAudioTrack::StopPlayout() {
  return audio_track_->CallBooleanMethod(stop_playout_);
}

bool AudioTrackJni::JavaAudioTrack::SetStreamVolume(int volume) {
  return audio_track_->CallBooleanMethod(set_stream_volume_, volume);
}

int AudioTrackJni::JavaAudioTrack::GetStreamMaxVolume() {
  return audio_track_->CallIntMethod(get_stream_max_volume_);
}

int AudioTrackJni::JavaAudioTrack::GetStreamVolume() {
  return audio_track_->CallIntMethod(get_stream_volume_);
}

// TODO(henrika): possible extend usage of AudioManager and add it as member.
AudioTrackJni::AudioTrackJni(AudioManager* audio_manager)
    : j_environment_(JVM::GetInstance()->environment()),
      audio_parameters_(audio_manager->GetPlayoutAudioParameters()),
      direct_buffer_address_(nullptr),
      direct_buffer_capacity_in_bytes_(0),
      frames_per_buffer_(0),
      initialized_(false),
      playing_(false),
      audio_device_buffer_(nullptr) {
  RTC_LOG(LS_INFO) << "ctor";
  RTC_DCHECK(audio_parameters_.is_valid());
  RTC_CHECK(j_environment_);
  JNINativeMethod native_methods[] = {
      {"nativeCacheDirectBufferAddress", "(Ljava/nio/ByteBuffer;J)V",
       reinterpret_cast<void*>(
           &webrtc::AudioTrackJni::CacheDirectBufferAddress)},
      {"nativeGetPlayoutData", "(IJ)V",
       reinterpret_cast<void*>(&webrtc::AudioTrackJni::GetPlayoutData)}};
  j_native_registration_ = j_environment_->RegisterNatives(
      "org/webrtc/voiceengine/WebRtcAudioTrack", native_methods,
      arraysize(native_methods));
  j_audio_track_.reset(
      new JavaAudioTrack(j_native_registration_.get(),
                         j_native_registration_->NewObject(
                             "<init>", "(J)V", PointerTojlong(this))));
  // Detach from this thread since we want to use the checker to verify calls
  // from the Java based audio thread.
  thread_checker_java_.Detach();
}

AudioTrackJni::~AudioTrackJni() {
  RTC_LOG(LS_INFO) << "dtor";
  RTC_DCHECK(thread_checker_.IsCurrent());
  Terminate();
}

int32_t AudioTrackJni::Init() {
  RTC_LOG(LS_INFO) << "Init";
  RTC_DCHECK(thread_checker_.IsCurrent());
  return 0;
}

int32_t AudioTrackJni::Terminate() {
  RTC_LOG(LS_INFO) << "Terminate";
  RTC_DCHECK(thread_checker_.IsCurrent());
  StopPlayout();
  return 0;
}

int32_t AudioTrackJni::InitPlayout() {
  RTC_LOG(LS_INFO) << "InitPlayout";
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!initialized_);
  RTC_DCHECK(!playing_);
  if (!j_audio_track_->InitPlayout(audio_parameters_.sample_rate(),
                                   audio_parameters_.channels())) {
    RTC_LOG(LS_ERROR) << "InitPlayout failed";
    return -1;
  }
  initialized_ = true;
  return 0;
}

int32_t AudioTrackJni::StartPlayout() {
  RTC_LOG(LS_INFO) << "StartPlayout";
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!playing_);
  if (!initialized_) {
    RTC_DLOG(LS_WARNING)
        << "Playout can not start since InitPlayout must succeed first";
    return 0;
  }
  if (!j_audio_track_->StartPlayout()) {
    RTC_LOG(LS_ERROR) << "StartPlayout failed";
    return -1;
  }
  playing_ = true;
  return 0;
}

int32_t AudioTrackJni::StopPlayout() {
  RTC_LOG(LS_INFO) << "StopPlayout";
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!initialized_ || !playing_) {
    return 0;
  }
  if (!j_audio_track_->StopPlayout()) {
    RTC_LOG(LS_ERROR) << "StopPlayout failed";
    return -1;
  }
  // If we don't detach here, we will hit a RTC_DCHECK in OnDataIsRecorded()
  // next time StartRecording() is called since it will create a new Java
  // thread.
  thread_checker_java_.Detach();
  initialized_ = false;
  playing_ = false;
  direct_buffer_address_ = nullptr;
  return 0;
}

int AudioTrackJni::SpeakerVolumeIsAvailable(bool& available) {
  available = true;
  return 0;
}

int AudioTrackJni::SetSpeakerVolume(uint32_t volume) {
  RTC_LOG(LS_INFO) << "SetSpeakerVolume(" << volume << ")";
  RTC_DCHECK(thread_checker_.IsCurrent());
  return j_audio_track_->SetStreamVolume(volume) ? 0 : -1;
}

int AudioTrackJni::MaxSpeakerVolume(uint32_t& max_volume) const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  max_volume = j_audio_track_->GetStreamMaxVolume();
  return 0;
}

int AudioTrackJni::MinSpeakerVolume(uint32_t& min_volume) const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  min_volume = 0;
  return 0;
}

int AudioTrackJni::SpeakerVolume(uint32_t& volume) const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  volume = j_audio_track_->GetStreamVolume();
  RTC_LOG(LS_INFO) << "SpeakerVolume: " << volume;
  return 0;
}

// TODO(henrika): possibly add stereo support.
void AudioTrackJni::AttachAudioBuffer(AudioDeviceBuffer* audioBuffer) {
  RTC_LOG(LS_INFO) << "AttachAudioBuffer";
  RTC_DCHECK(thread_checker_.IsCurrent());
  audio_device_buffer_ = audioBuffer;
  const int sample_rate_hz = audio_parameters_.sample_rate();
  RTC_LOG(LS_INFO) << "SetPlayoutSampleRate(" << sample_rate_hz << ")";
  audio_device_buffer_->SetPlayoutSampleRate(sample_rate_hz);
  const size_t channels = audio_parameters_.channels();
  RTC_LOG(LS_INFO) << "SetPlayoutChannels(" << channels << ")";
  audio_device_buffer_->SetPlayoutChannels(channels);
}

JNI_FUNCTION_ALIGN
void JNICALL AudioTrackJni::CacheDirectBufferAddress(JNIEnv* env,
                                                     jobject obj,
                                                     jobject byte_buffer,
                                                     jlong nativeAudioTrack) {
  webrtc::AudioTrackJni* this_object =
      reinterpret_cast<webrtc::AudioTrackJni*>(nativeAudioTrack);
  this_object->OnCacheDirectBufferAddress(env, byte_buffer);
}

void AudioTrackJni::OnCacheDirectBufferAddress(JNIEnv* env,
                                               jobject byte_buffer) {
  RTC_LOG(LS_INFO) << "OnCacheDirectBufferAddress";
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!direct_buffer_address_);
  direct_buffer_address_ = env->GetDirectBufferAddress(byte_buffer);
  jlong capacity = env->GetDirectBufferCapacity(byte_buffer);
  RTC_LOG(LS_INFO) << "direct buffer capacity: " << capacity;
  direct_buffer_capacity_in_bytes_ = static_cast<size_t>(capacity);
  const size_t bytes_per_frame = audio_parameters_.channels() * sizeof(int16_t);
  frames_per_buffer_ = direct_buffer_capacity_in_bytes_ / bytes_per_frame;
  RTC_LOG(LS_INFO) << "frames_per_buffer: " << frames_per_buffer_;
}

JNI_FUNCTION_ALIGN
void JNICALL AudioTrackJni::GetPlayoutData(JNIEnv* env,
                                           jobject obj,
                                           jint length,
                                           jlong nativeAudioTrack) {
  webrtc::AudioTrackJni* this_object =
      reinterpret_cast<webrtc::AudioTrackJni*>(nativeAudioTrack);
  this_object->OnGetPlayoutData(static_cast<size_t>(length));
}

// This method is called on a high-priority thread from Java. The name of
// the thread is 'AudioRecordTrack'.
void AudioTrackJni::OnGetPlayoutData(size_t length) {
  RTC_DCHECK(thread_checker_java_.IsCurrent());
  const size_t bytes_per_frame = audio_parameters_.channels() * sizeof(int16_t);
  RTC_DCHECK_EQ(frames_per_buffer_, length / bytes_per_frame);
  if (!audio_device_buffer_) {
    RTC_LOG(LS_ERROR) << "AttachAudioBuffer has not been called";
    return;
  }
  // Pull decoded data (in 16-bit PCM format) from jitter buffer.
  int samples = audio_device_buffer_->RequestPlayoutData(frames_per_buffer_);
  if (samples <= 0) {
    RTC_LOG(LS_ERROR) << "AudioDeviceBuffer::RequestPlayoutData failed";
    return;
  }
  RTC_DCHECK_EQ(samples, frames_per_buffer_);
  // Copy decoded data into common byte buffer to ensure that it can be
  // written to the Java based audio track.
  samples = audio_device_buffer_->GetPlayoutData(direct_buffer_address_);
  RTC_DCHECK_EQ(length, bytes_per_frame * samples);
}

}  // namespace webrtc
