/*
 *  Copyright (c) 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_device/test_audio_device_impl.h"

#include <memory>
#include <utility>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/task_queue/task_queue_factory.h"
#include "api/units/time_delta.h"
#include "modules/audio_device/include/test_audio_device.h"
#include "rtc_base/checks.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/task_utils/repeating_task.h"

namespace webrtc {
namespace {

constexpr int kFrameLengthUs = 10000;

}

TestAudioDevice::TestAudioDevice(
    TaskQueueFactory* task_queue_factory,
    std::unique_ptr<TestAudioDeviceModule::Capturer> capturer,
    std::unique_ptr<TestAudioDeviceModule::Renderer> renderer,
    float speed)
    : task_queue_factory_(task_queue_factory),
      capturer_(std::move(capturer)),
      renderer_(std::move(renderer)),
      process_interval_us_(kFrameLengthUs / speed),
      audio_buffer_(nullptr),
      rendering_(false),
      capturing_(false) {
  auto good_sample_rate = [](int sr) {
    return sr == 8000 || sr == 16000 || sr == 32000 || sr == 44100 ||
           sr == 48000;
  };

  if (renderer_) {
    const int sample_rate = renderer_->SamplingFrequency();
    playout_buffer_.resize(TestAudioDeviceModule::SamplesPerFrame(sample_rate) *
                               renderer_->NumChannels(),
                           0);
    RTC_CHECK(good_sample_rate(sample_rate));
  }
  if (capturer_) {
    RTC_CHECK(good_sample_rate(capturer_->SamplingFrequency()));
  }
}

AudioDeviceGeneric::InitStatus TestAudioDevice::Init() {
  task_queue_ = task_queue_factory_->CreateTaskQueue(
      "TestAudioDeviceModuleImpl", TaskQueueFactory::Priority::NORMAL);

  RepeatingTaskHandle::Start(task_queue_.get(), [this]() {
    ProcessAudio();
    return TimeDelta::Micros(process_interval_us_);
  });
  return InitStatus::OK;
}

int32_t TestAudioDevice::PlayoutIsAvailable(bool& available) {
  MutexLock lock(&lock_);
  available = renderer_ != nullptr;
  return 0;
}

int32_t TestAudioDevice::InitPlayout() {
  MutexLock lock(&lock_);

  if (rendering_) {
    return -1;
  }

  if (audio_buffer_ != nullptr && renderer_ != nullptr) {
    // Update webrtc audio buffer with the selected parameters
    audio_buffer_->SetPlayoutSampleRate(renderer_->SamplingFrequency());
    audio_buffer_->SetPlayoutChannels(renderer_->NumChannels());
  }
  rendering_initialized_ = true;
  return 0;
}

bool TestAudioDevice::PlayoutIsInitialized() const {
  MutexLock lock(&lock_);
  return rendering_initialized_;
}

int32_t TestAudioDevice::StartPlayout() {
  MutexLock lock(&lock_);
  RTC_CHECK(renderer_);
  rendering_ = true;
  return 0;
}

int32_t TestAudioDevice::StopPlayout() {
  MutexLock lock(&lock_);
  rendering_ = false;
  return 0;
}

int32_t TestAudioDevice::RecordingIsAvailable(bool& available) {
  MutexLock lock(&lock_);
  available = capturer_ != nullptr;
  return 0;
}

int32_t TestAudioDevice::InitRecording() {
  MutexLock lock(&lock_);

  if (capturing_) {
    return -1;
  }

  if (audio_buffer_ != nullptr && capturer_ != nullptr) {
    // Update webrtc audio buffer with the selected parameters
    audio_buffer_->SetRecordingSampleRate(capturer_->SamplingFrequency());
    audio_buffer_->SetRecordingChannels(capturer_->NumChannels());
  }
  capturing_initialized_ = true;
  return 0;
}

bool TestAudioDevice::RecordingIsInitialized() const {
  MutexLock lock(&lock_);
  return capturing_initialized_;
}

int32_t TestAudioDevice::StartRecording() {
  MutexLock lock(&lock_);
  capturing_ = true;
  return 0;
}

int32_t TestAudioDevice::StopRecording() {
  MutexLock lock(&lock_);
  capturing_ = false;
  return 0;
}

bool TestAudioDevice::Playing() const {
  MutexLock lock(&lock_);
  return rendering_;
}

bool TestAudioDevice::Recording() const {
  MutexLock lock(&lock_);
  return capturing_;
}

void TestAudioDevice::ProcessAudio() {
  MutexLock lock(&lock_);
  if (audio_buffer_ == nullptr) {
    return;
  }
  if (capturing_ && capturer_ != nullptr) {
    // Capture 10ms of audio. 2 bytes per sample.
    const bool keep_capturing = capturer_->Capture(&recording_buffer_);
    if (recording_buffer_.size() > 0) {
      audio_buffer_->SetRecordedBuffer(
          recording_buffer_.data(),
          recording_buffer_.size() / capturer_->NumChannels(),
          absl::make_optional(rtc::TimeNanos()));
      audio_buffer_->DeliverRecordedData();
    }
    if (!keep_capturing) {
      capturing_ = false;
    }
  }
  if (rendering_) {
    const int sampling_frequency = renderer_->SamplingFrequency();
    int32_t samples_per_channel = audio_buffer_->RequestPlayoutData(
        TestAudioDeviceModule::SamplesPerFrame(sampling_frequency));
    audio_buffer_->GetPlayoutData(playout_buffer_.data());
    size_t samples_out = samples_per_channel * renderer_->NumChannels();
    RTC_CHECK_LE(samples_out, playout_buffer_.size());
    const bool keep_rendering = renderer_->Render(
        rtc::ArrayView<const int16_t>(playout_buffer_.data(), samples_out));
    if (!keep_rendering) {
      rendering_ = false;
    }
  }
}

void TestAudioDevice::AttachAudioBuffer(AudioDeviceBuffer* audio_buffer) {
  MutexLock lock(&lock_);
  RTC_DCHECK(audio_buffer || audio_buffer_);
  audio_buffer_ = audio_buffer;

  if (renderer_ != nullptr) {
    audio_buffer_->SetPlayoutSampleRate(renderer_->SamplingFrequency());
    audio_buffer_->SetPlayoutChannels(renderer_->NumChannels());
  }
  if (capturer_ != nullptr) {
    audio_buffer_->SetRecordingSampleRate(capturer_->SamplingFrequency());
    audio_buffer_->SetRecordingChannels(capturer_->NumChannels());
  }
}

}  // namespace webrtc
