/*
 *  Copyright 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/test/fake_audio_capture_module.h"

#include <string.h>

#include "api/make_ref_counted.h"
#include "api/units/time_delta.h"
#include "rtc_base/checks.h"
#include "rtc_base/thread.h"
#include "rtc_base/time_utils.h"

using ::webrtc::TimeDelta;

// Audio sample value that is high enough that it doesn't occur naturally when
// frames are being faked. E.g. NetEq will not generate this large sample value
// unless it has received an audio frame containing a sample of this value.
// Even simpler buffers would likely just contain audio sample values of 0.
static const int kHighSampleValue = 10000;

// Constants here are derived by running VoE using a real ADM.
// The constants correspond to 10ms of mono audio at 44kHz.
static const int kTimePerFrameMs = 10;
static const uint8_t kNumberOfChannels = 1;
static const int kSamplesPerSecond = 44000;
static const int kTotalDelayMs = 0;
static const int kClockDriftMs = 0;
static const uint32_t kMaxVolume = 14392;

FakeAudioCaptureModule::FakeAudioCaptureModule()
    : audio_callback_(nullptr),
      recording_(false),
      playing_(false),
      play_is_initialized_(false),
      rec_is_initialized_(false),
      current_mic_level_(kMaxVolume),
      started_(false),
      next_frame_time_(0),
      frames_received_(0) {}

FakeAudioCaptureModule::~FakeAudioCaptureModule() {
  if (process_thread_) {
    process_thread_->Stop();
  }
}

rtc::scoped_refptr<FakeAudioCaptureModule> FakeAudioCaptureModule::Create() {
  auto capture_module = rtc::make_ref_counted<FakeAudioCaptureModule>();
  if (!capture_module->Initialize()) {
    return nullptr;
  }
  return capture_module;
}

int FakeAudioCaptureModule::frames_received() const {
  webrtc::MutexLock lock(&mutex_);
  return frames_received_;
}

int32_t FakeAudioCaptureModule::ActiveAudioLayer(
    AudioLayer* /*audio_layer*/) const {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::RegisterAudioCallback(
    webrtc::AudioTransport* audio_callback) {
  webrtc::MutexLock lock(&mutex_);
  audio_callback_ = audio_callback;
  return 0;
}

int32_t FakeAudioCaptureModule::Init() {
  // Initialize is called by the factory method. Safe to ignore this Init call.
  return 0;
}

int32_t FakeAudioCaptureModule::Terminate() {
  // Clean up in the destructor. No action here, just success.
  return 0;
}

bool FakeAudioCaptureModule::Initialized() const {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int16_t FakeAudioCaptureModule::PlayoutDevices() {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int16_t FakeAudioCaptureModule::RecordingDevices() {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::PlayoutDeviceName(
    uint16_t /*index*/,
    char /*name*/[webrtc::kAdmMaxDeviceNameSize],
    char /*guid*/[webrtc::kAdmMaxGuidSize]) {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::RecordingDeviceName(
    uint16_t /*index*/,
    char /*name*/[webrtc::kAdmMaxDeviceNameSize],
    char /*guid*/[webrtc::kAdmMaxGuidSize]) {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::SetPlayoutDevice(uint16_t /*index*/) {
  // No playout device, just playing from file. Return success.
  return 0;
}

int32_t FakeAudioCaptureModule::SetPlayoutDevice(WindowsDeviceType /*device*/) {
  if (play_is_initialized_) {
    return -1;
  }
  return 0;
}

int32_t FakeAudioCaptureModule::SetRecordingDevice(uint16_t /*index*/) {
  // No recording device, just dropping audio. Return success.
  return 0;
}

int32_t FakeAudioCaptureModule::SetRecordingDevice(
    WindowsDeviceType /*device*/) {
  if (rec_is_initialized_) {
    return -1;
  }
  return 0;
}

int32_t FakeAudioCaptureModule::PlayoutIsAvailable(bool* /*available*/) {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::InitPlayout() {
  play_is_initialized_ = true;
  return 0;
}

bool FakeAudioCaptureModule::PlayoutIsInitialized() const {
  return play_is_initialized_;
}

int32_t FakeAudioCaptureModule::RecordingIsAvailable(bool* /*available*/) {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::InitRecording() {
  rec_is_initialized_ = true;
  return 0;
}

bool FakeAudioCaptureModule::RecordingIsInitialized() const {
  return rec_is_initialized_;
}

int32_t FakeAudioCaptureModule::StartPlayout() {
  if (!play_is_initialized_) {
    return -1;
  }
  {
    webrtc::MutexLock lock(&mutex_);
    playing_ = true;
  }
  bool start = true;
  UpdateProcessing(start);
  return 0;
}

int32_t FakeAudioCaptureModule::StopPlayout() {
  bool start = false;
  {
    webrtc::MutexLock lock(&mutex_);
    playing_ = false;
    start = ShouldStartProcessing();
  }
  UpdateProcessing(start);
  return 0;
}

bool FakeAudioCaptureModule::Playing() const {
  webrtc::MutexLock lock(&mutex_);
  return playing_;
}

int32_t FakeAudioCaptureModule::StartRecording() {
  if (!rec_is_initialized_) {
    return -1;
  }
  {
    webrtc::MutexLock lock(&mutex_);
    recording_ = true;
  }
  bool start = true;
  UpdateProcessing(start);
  return 0;
}

int32_t FakeAudioCaptureModule::StopRecording() {
  bool start = false;
  {
    webrtc::MutexLock lock(&mutex_);
    recording_ = false;
    start = ShouldStartProcessing();
  }
  UpdateProcessing(start);
  return 0;
}

bool FakeAudioCaptureModule::Recording() const {
  webrtc::MutexLock lock(&mutex_);
  return recording_;
}

int32_t FakeAudioCaptureModule::InitSpeaker() {
  // No speaker, just playing from file. Return success.
  return 0;
}

bool FakeAudioCaptureModule::SpeakerIsInitialized() const {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::InitMicrophone() {
  // No microphone, just playing from file. Return success.
  return 0;
}

bool FakeAudioCaptureModule::MicrophoneIsInitialized() const {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::SpeakerVolumeIsAvailable(bool* /*available*/) {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::SetSpeakerVolume(uint32_t /*volume*/) {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::SpeakerVolume(uint32_t* /*volume*/) const {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::MaxSpeakerVolume(
    uint32_t* /*max_volume*/) const {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::MinSpeakerVolume(
    uint32_t* /*min_volume*/) const {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::MicrophoneVolumeIsAvailable(
    bool* /*available*/) {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::SetMicrophoneVolume(uint32_t volume) {
  webrtc::MutexLock lock(&mutex_);
  current_mic_level_ = volume;
  return 0;
}

int32_t FakeAudioCaptureModule::MicrophoneVolume(uint32_t* volume) const {
  webrtc::MutexLock lock(&mutex_);
  *volume = current_mic_level_;
  return 0;
}

int32_t FakeAudioCaptureModule::MaxMicrophoneVolume(
    uint32_t* max_volume) const {
  *max_volume = kMaxVolume;
  return 0;
}

int32_t FakeAudioCaptureModule::MinMicrophoneVolume(
    uint32_t* /*min_volume*/) const {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::SpeakerMuteIsAvailable(bool* /*available*/) {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::SetSpeakerMute(bool /*enable*/) {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::SpeakerMute(bool* /*enabled*/) const {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::MicrophoneMuteIsAvailable(bool* /*available*/) {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::SetMicrophoneMute(bool /*enable*/) {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::MicrophoneMute(bool* /*enabled*/) const {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::StereoPlayoutIsAvailable(
    bool* available) const {
  // No recording device, just dropping audio. Stereo can be dropped just
  // as easily as mono.
  *available = true;
  return 0;
}

int32_t FakeAudioCaptureModule::SetStereoPlayout(bool /*enable*/) {
  // No recording device, just dropping audio. Stereo can be dropped just
  // as easily as mono.
  return 0;
}

int32_t FakeAudioCaptureModule::StereoPlayout(bool* /*enabled*/) const {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::StereoRecordingIsAvailable(
    bool* available) const {
  // Keep thing simple. No stereo recording.
  *available = false;
  return 0;
}

int32_t FakeAudioCaptureModule::SetStereoRecording(bool enable) {
  if (!enable) {
    return 0;
  }
  return -1;
}

int32_t FakeAudioCaptureModule::StereoRecording(bool* /*enabled*/) const {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

int32_t FakeAudioCaptureModule::PlayoutDelay(uint16_t* delay_ms) const {
  // No delay since audio frames are dropped.
  *delay_ms = 0;
  return 0;
}

bool FakeAudioCaptureModule::Initialize() {
  // Set the send buffer samples high enough that it would not occur on the
  // remote side unless a packet containing a sample of that magnitude has been
  // sent to it. Note that the audio processing pipeline will likely distort the
  // original signal.
  SetSendBuffer(kHighSampleValue);
  return true;
}

void FakeAudioCaptureModule::SetSendBuffer(int value) {
  Sample* buffer_ptr = reinterpret_cast<Sample*>(send_buffer_);
  const size_t buffer_size_in_samples =
      sizeof(send_buffer_) / kNumberBytesPerSample;
  for (size_t i = 0; i < buffer_size_in_samples; ++i) {
    buffer_ptr[i] = value;
  }
}

void FakeAudioCaptureModule::ResetRecBuffer() {
  memset(rec_buffer_, 0, sizeof(rec_buffer_));
}

bool FakeAudioCaptureModule::CheckRecBuffer(int value) {
  const Sample* buffer_ptr = reinterpret_cast<const Sample*>(rec_buffer_);
  const size_t buffer_size_in_samples =
      sizeof(rec_buffer_) / kNumberBytesPerSample;
  for (size_t i = 0; i < buffer_size_in_samples; ++i) {
    if (buffer_ptr[i] >= value)
      return true;
  }
  return false;
}

bool FakeAudioCaptureModule::ShouldStartProcessing() {
  return recording_ || playing_;
}

void FakeAudioCaptureModule::UpdateProcessing(bool start) {
  if (start) {
    if (!process_thread_) {
      process_thread_ = rtc::Thread::Create();
      process_thread_->Start();
    }
    process_thread_->PostTask([this] { StartProcessP(); });
  } else {
    if (process_thread_) {
      process_thread_->Stop();
      process_thread_.reset(nullptr);
      process_thread_checker_.Detach();
    }
    webrtc::MutexLock lock(&mutex_);
    started_ = false;
  }
}

void FakeAudioCaptureModule::StartProcessP() {
  RTC_DCHECK_RUN_ON(&process_thread_checker_);
  {
    webrtc::MutexLock lock(&mutex_);
    if (started_) {
      // Already started.
      return;
    }
  }
  ProcessFrameP();
}

void FakeAudioCaptureModule::ProcessFrameP() {
  RTC_DCHECK_RUN_ON(&process_thread_checker_);
  {
    webrtc::MutexLock lock(&mutex_);
    if (!started_) {
      next_frame_time_ = rtc::TimeMillis();
      started_ = true;
    }

    // Receive and send frames every kTimePerFrameMs.
    if (playing_) {
      ReceiveFrameP();
    }
    if (recording_) {
      SendFrameP();
    }
  }

  next_frame_time_ += kTimePerFrameMs;
  const int64_t current_time = rtc::TimeMillis();
  const int64_t wait_time =
      (next_frame_time_ > current_time) ? next_frame_time_ - current_time : 0;
  process_thread_->PostDelayedTask([this] { ProcessFrameP(); },
                                   TimeDelta::Millis(wait_time));
}

void FakeAudioCaptureModule::ReceiveFrameP() {
  RTC_DCHECK_RUN_ON(&process_thread_checker_);
  if (!audio_callback_) {
    return;
  }
  ResetRecBuffer();
  size_t nSamplesOut = 0;
  int64_t elapsed_time_ms = 0;
  int64_t ntp_time_ms = 0;
  if (audio_callback_->NeedMorePlayData(kNumberSamples, kNumberBytesPerSample,
                                        kNumberOfChannels, kSamplesPerSecond,
                                        rec_buffer_, nSamplesOut,
                                        &elapsed_time_ms, &ntp_time_ms) != 0) {
    RTC_DCHECK_NOTREACHED();
  }
  RTC_CHECK(nSamplesOut == kNumberSamples);

  // The SetBuffer() function ensures that after decoding, the audio buffer
  // should contain samples of similar magnitude (there is likely to be some
  // distortion due to the audio pipeline). If one sample is detected to
  // have the same or greater magnitude somewhere in the frame, an actual frame
  // has been received from the remote side (i.e. faked frames are not being
  // pulled).
  if (CheckRecBuffer(kHighSampleValue)) {
    ++frames_received_;
  }
}

void FakeAudioCaptureModule::SendFrameP() {
  RTC_DCHECK_RUN_ON(&process_thread_checker_);
  if (!audio_callback_) {
    return;
  }
  bool key_pressed = false;
  uint32_t current_mic_level = current_mic_level_;
  if (audio_callback_->RecordedDataIsAvailable(
          send_buffer_, kNumberSamples, kNumberBytesPerSample,
          kNumberOfChannels, kSamplesPerSecond, kTotalDelayMs, kClockDriftMs,
          current_mic_level, key_pressed, current_mic_level) != 0) {
    RTC_DCHECK_NOTREACHED();
  }
  current_mic_level_ = current_mic_level;
}
