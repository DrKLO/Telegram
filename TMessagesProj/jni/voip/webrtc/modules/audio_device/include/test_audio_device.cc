/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_device/include/test_audio_device.h"

#include <algorithm>
#include <cstdint>
#include <cstdlib>
#include <memory>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/array_view.h"
#include "api/make_ref_counted.h"
#include "common_audio/wav_file.h"
#include "modules/audio_device/include/audio_device_default.h"
#include "rtc_base/buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/random.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/task_queue.h"
#include "rtc_base/task_utils/repeating_task.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

namespace {

constexpr int kFrameLengthUs = 10000;
constexpr int kFramesPerSecond = rtc::kNumMicrosecsPerSec / kFrameLengthUs;

// TestAudioDeviceModule implements an AudioDevice module that can act both as a
// capturer and a renderer. It will use 10ms audio frames.
class TestAudioDeviceModuleImpl
    : public webrtc_impl::AudioDeviceModuleDefault<TestAudioDeviceModule> {
 public:
  // Creates a new TestAudioDeviceModule. When capturing or playing, 10 ms audio
  // frames will be processed every 10ms / `speed`.
  // `capturer` is an object that produces audio data. Can be nullptr if this
  // device is never used for recording.
  // `renderer` is an object that receives audio data that would have been
  // played out. Can be nullptr if this device is never used for playing.
  // Use one of the Create... functions to get these instances.
  TestAudioDeviceModuleImpl(TaskQueueFactory* task_queue_factory,
                            std::unique_ptr<Capturer> capturer,
                            std::unique_ptr<Renderer> renderer,
                            float speed = 1)
      : task_queue_factory_(task_queue_factory),
        capturer_(std::move(capturer)),
        renderer_(std::move(renderer)),
        process_interval_us_(kFrameLengthUs / speed),
        audio_callback_(nullptr),
        rendering_(false),
        capturing_(false) {
    auto good_sample_rate = [](int sr) {
      return sr == 8000 || sr == 16000 || sr == 32000 || sr == 44100 ||
             sr == 48000;
    };

    if (renderer_) {
      const int sample_rate = renderer_->SamplingFrequency();
      playout_buffer_.resize(
          SamplesPerFrame(sample_rate) * renderer_->NumChannels(), 0);
      RTC_CHECK(good_sample_rate(sample_rate));
    }
    if (capturer_) {
      RTC_CHECK(good_sample_rate(capturer_->SamplingFrequency()));
    }
  }

  ~TestAudioDeviceModuleImpl() override {
    StopPlayout();
    StopRecording();
  }

  int32_t Init() override {
    task_queue_ =
        std::make_unique<rtc::TaskQueue>(task_queue_factory_->CreateTaskQueue(
            "TestAudioDeviceModuleImpl", TaskQueueFactory::Priority::NORMAL));

    RepeatingTaskHandle::Start(task_queue_->Get(), [this]() {
      ProcessAudio();
      return TimeDelta::Micros(process_interval_us_);
    });
    return 0;
  }

  int32_t RegisterAudioCallback(AudioTransport* callback) override {
    MutexLock lock(&lock_);
    RTC_DCHECK(callback || audio_callback_);
    audio_callback_ = callback;
    return 0;
  }

  int32_t StartPlayout() override {
    MutexLock lock(&lock_);
    RTC_CHECK(renderer_);
    rendering_ = true;
    return 0;
  }

  int32_t StopPlayout() override {
    MutexLock lock(&lock_);
    rendering_ = false;
    return 0;
  }

  int32_t StartRecording() override {
    MutexLock lock(&lock_);
    RTC_CHECK(capturer_);
    capturing_ = true;
    return 0;
  }

  int32_t StopRecording() override {
    MutexLock lock(&lock_);
    capturing_ = false;
    return 0;
  }

  bool Playing() const override {
    MutexLock lock(&lock_);
    return rendering_;
  }

  bool Recording() const override {
    MutexLock lock(&lock_);
    return capturing_;
  }

  // Blocks forever until the Recorder stops producing data.
  void WaitForRecordingEnd() override {
    done_capturing_.Wait(rtc::Event::kForever);
  }

 private:
  void ProcessAudio() {
    MutexLock lock(&lock_);
    if (capturing_) {
      // Capture 10ms of audio. 2 bytes per sample.
      const bool keep_capturing = capturer_->Capture(&recording_buffer_);
      uint32_t new_mic_level = 0;
      if (recording_buffer_.size() > 0) {
        audio_callback_->RecordedDataIsAvailable(
            recording_buffer_.data(),
            recording_buffer_.size() / capturer_->NumChannels(),
            2 * capturer_->NumChannels(), capturer_->NumChannels(),
            capturer_->SamplingFrequency(), 0, 0, 0, false, new_mic_level);
      }
      if (!keep_capturing) {
        capturing_ = false;
        done_capturing_.Set();
      }
    }
    if (rendering_) {
      size_t samples_out = 0;
      int64_t elapsed_time_ms = -1;
      int64_t ntp_time_ms = -1;
      const int sampling_frequency = renderer_->SamplingFrequency();
      audio_callback_->NeedMorePlayData(
          SamplesPerFrame(sampling_frequency), 2 * renderer_->NumChannels(),
          renderer_->NumChannels(), sampling_frequency, playout_buffer_.data(),
          samples_out, &elapsed_time_ms, &ntp_time_ms);
      const bool keep_rendering = renderer_->Render(
          rtc::ArrayView<const int16_t>(playout_buffer_.data(), samples_out));
      if (!keep_rendering) {
        rendering_ = false;
        done_rendering_.Set();
      }
    }
  }
  TaskQueueFactory* const task_queue_factory_;
  const std::unique_ptr<Capturer> capturer_ RTC_GUARDED_BY(lock_);
  const std::unique_ptr<Renderer> renderer_ RTC_GUARDED_BY(lock_);
  const int64_t process_interval_us_;

  mutable Mutex lock_;
  AudioTransport* audio_callback_ RTC_GUARDED_BY(lock_);
  bool rendering_ RTC_GUARDED_BY(lock_);
  bool capturing_ RTC_GUARDED_BY(lock_);
  rtc::Event done_rendering_;
  rtc::Event done_capturing_;

  std::vector<int16_t> playout_buffer_ RTC_GUARDED_BY(lock_);
  rtc::BufferT<int16_t> recording_buffer_ RTC_GUARDED_BY(lock_);
  std::unique_ptr<rtc::TaskQueue> task_queue_;
};

// A fake capturer that generates pulses with random samples between
// -max_amplitude and +max_amplitude.
class PulsedNoiseCapturerImpl final
    : public TestAudioDeviceModule::PulsedNoiseCapturer {
 public:
  // Assuming 10ms audio packets.
  PulsedNoiseCapturerImpl(int16_t max_amplitude,
                          int sampling_frequency_in_hz,
                          int num_channels)
      : sampling_frequency_in_hz_(sampling_frequency_in_hz),
        fill_with_zero_(false),
        random_generator_(1),
        max_amplitude_(max_amplitude),
        num_channels_(num_channels) {
    RTC_DCHECK_GT(max_amplitude, 0);
  }

  int SamplingFrequency() const override { return sampling_frequency_in_hz_; }

  int NumChannels() const override { return num_channels_; }

  bool Capture(rtc::BufferT<int16_t>* buffer) override {
    fill_with_zero_ = !fill_with_zero_;
    int16_t max_amplitude;
    {
      MutexLock lock(&lock_);
      max_amplitude = max_amplitude_;
    }
    buffer->SetData(
        TestAudioDeviceModule::SamplesPerFrame(sampling_frequency_in_hz_) *
            num_channels_,
        [&](rtc::ArrayView<int16_t> data) {
          if (fill_with_zero_) {
            std::fill(data.begin(), data.end(), 0);
          } else {
            std::generate(data.begin(), data.end(), [&]() {
              return random_generator_.Rand(-max_amplitude, max_amplitude);
            });
          }
          return data.size();
        });
    return true;
  }

  void SetMaxAmplitude(int16_t amplitude) override {
    MutexLock lock(&lock_);
    max_amplitude_ = amplitude;
  }

 private:
  int sampling_frequency_in_hz_;
  bool fill_with_zero_;
  Random random_generator_;
  Mutex lock_;
  int16_t max_amplitude_ RTC_GUARDED_BY(lock_);
  const int num_channels_;
};

class WavFileReader final : public TestAudioDeviceModule::Capturer {
 public:
  WavFileReader(absl::string_view filename,
                int sampling_frequency_in_hz,
                int num_channels,
                bool repeat)
      : WavFileReader(std::make_unique<WavReader>(filename),
                      sampling_frequency_in_hz,
                      num_channels,
                      repeat) {}

  int SamplingFrequency() const override { return sampling_frequency_in_hz_; }

  int NumChannels() const override { return num_channels_; }

  bool Capture(rtc::BufferT<int16_t>* buffer) override {
    buffer->SetData(
        TestAudioDeviceModule::SamplesPerFrame(sampling_frequency_in_hz_) *
            num_channels_,
        [&](rtc::ArrayView<int16_t> data) {
          size_t read = wav_reader_->ReadSamples(data.size(), data.data());
          if (read < data.size() && repeat_) {
            do {
              wav_reader_->Reset();
              size_t delta = wav_reader_->ReadSamples(
                  data.size() - read, data.subview(read).data());
              RTC_CHECK_GT(delta, 0) << "No new data read from file";
              read += delta;
            } while (read < data.size());
          }
          return read;
        });
    return buffer->size() > 0;
  }

 private:
  WavFileReader(std::unique_ptr<WavReader> wav_reader,
                int sampling_frequency_in_hz,
                int num_channels,
                bool repeat)
      : sampling_frequency_in_hz_(sampling_frequency_in_hz),
        num_channels_(num_channels),
        wav_reader_(std::move(wav_reader)),
        repeat_(repeat) {
    RTC_CHECK_EQ(wav_reader_->sample_rate(), sampling_frequency_in_hz);
    RTC_CHECK_EQ(wav_reader_->num_channels(), num_channels);
  }

  const int sampling_frequency_in_hz_;
  const int num_channels_;
  std::unique_ptr<WavReader> wav_reader_;
  const bool repeat_;
};

class WavFileWriter final : public TestAudioDeviceModule::Renderer {
 public:
  WavFileWriter(absl::string_view filename,
                int sampling_frequency_in_hz,
                int num_channels)
      : WavFileWriter(std::make_unique<WavWriter>(filename,
                                                  sampling_frequency_in_hz,
                                                  num_channels),
                      sampling_frequency_in_hz,
                      num_channels) {}

  int SamplingFrequency() const override { return sampling_frequency_in_hz_; }

  int NumChannels() const override { return num_channels_; }

  bool Render(rtc::ArrayView<const int16_t> data) override {
    wav_writer_->WriteSamples(data.data(), data.size());
    return true;
  }

 private:
  WavFileWriter(std::unique_ptr<WavWriter> wav_writer,
                int sampling_frequency_in_hz,
                int num_channels)
      : sampling_frequency_in_hz_(sampling_frequency_in_hz),
        wav_writer_(std::move(wav_writer)),
        num_channels_(num_channels) {}

  int sampling_frequency_in_hz_;
  std::unique_ptr<WavWriter> wav_writer_;
  const int num_channels_;
};

class BoundedWavFileWriter : public TestAudioDeviceModule::Renderer {
 public:
  BoundedWavFileWriter(absl::string_view filename,
                       int sampling_frequency_in_hz,
                       int num_channels)
      : sampling_frequency_in_hz_(sampling_frequency_in_hz),
        wav_writer_(filename, sampling_frequency_in_hz, num_channels),
        num_channels_(num_channels),
        silent_audio_(
            TestAudioDeviceModule::SamplesPerFrame(sampling_frequency_in_hz) *
                num_channels,
            0),
        started_writing_(false),
        trailing_zeros_(0) {}

  int SamplingFrequency() const override { return sampling_frequency_in_hz_; }

  int NumChannels() const override { return num_channels_; }

  bool Render(rtc::ArrayView<const int16_t> data) override {
    const int16_t kAmplitudeThreshold = 5;

    const int16_t* begin = data.begin();
    const int16_t* end = data.end();
    if (!started_writing_) {
      // Cut off silence at the beginning.
      while (begin < end) {
        if (std::abs(*begin) > kAmplitudeThreshold) {
          started_writing_ = true;
          break;
        }
        ++begin;
      }
    }
    if (started_writing_) {
      // Cut off silence at the end.
      while (begin < end) {
        if (*(end - 1) != 0) {
          break;
        }
        --end;
      }
      if (begin < end) {
        // If it turns out that the silence was not final, need to write all the
        // skipped zeros and continue writing audio.
        while (trailing_zeros_ > 0) {
          const size_t zeros_to_write =
              std::min(trailing_zeros_, silent_audio_.size());
          wav_writer_.WriteSamples(silent_audio_.data(), zeros_to_write);
          trailing_zeros_ -= zeros_to_write;
        }
        wav_writer_.WriteSamples(begin, end - begin);
      }
      // Save the number of zeros we skipped in case this needs to be restored.
      trailing_zeros_ += data.end() - end;
    }
    return true;
  }

 private:
  int sampling_frequency_in_hz_;
  WavWriter wav_writer_;
  const int num_channels_;
  std::vector<int16_t> silent_audio_;
  bool started_writing_;
  size_t trailing_zeros_;
};

class DiscardRenderer final : public TestAudioDeviceModule::Renderer {
 public:
  explicit DiscardRenderer(int sampling_frequency_in_hz, int num_channels)
      : sampling_frequency_in_hz_(sampling_frequency_in_hz),
        num_channels_(num_channels) {}

  int SamplingFrequency() const override { return sampling_frequency_in_hz_; }

  int NumChannels() const override { return num_channels_; }

  bool Render(rtc::ArrayView<const int16_t> data) override { return true; }

 private:
  int sampling_frequency_in_hz_;
  const int num_channels_;
};

}  // namespace

size_t TestAudioDeviceModule::SamplesPerFrame(int sampling_frequency_in_hz) {
  return rtc::CheckedDivExact(sampling_frequency_in_hz, kFramesPerSecond);
}

rtc::scoped_refptr<TestAudioDeviceModule> TestAudioDeviceModule::Create(
    TaskQueueFactory* task_queue_factory,
    std::unique_ptr<TestAudioDeviceModule::Capturer> capturer,
    std::unique_ptr<TestAudioDeviceModule::Renderer> renderer,
    float speed) {
  return rtc::make_ref_counted<TestAudioDeviceModuleImpl>(
      task_queue_factory, std::move(capturer), std::move(renderer), speed);
}

std::unique_ptr<TestAudioDeviceModule::PulsedNoiseCapturer>
TestAudioDeviceModule::CreatePulsedNoiseCapturer(int16_t max_amplitude,
                                                 int sampling_frequency_in_hz,
                                                 int num_channels) {
  return std::make_unique<PulsedNoiseCapturerImpl>(
      max_amplitude, sampling_frequency_in_hz, num_channels);
}

std::unique_ptr<TestAudioDeviceModule::Renderer>
TestAudioDeviceModule::CreateDiscardRenderer(int sampling_frequency_in_hz,
                                             int num_channels) {
  return std::make_unique<DiscardRenderer>(sampling_frequency_in_hz,
                                           num_channels);
}

std::unique_ptr<TestAudioDeviceModule::Capturer>
TestAudioDeviceModule::CreateWavFileReader(absl::string_view filename,
                                           int sampling_frequency_in_hz,
                                           int num_channels) {
  return std::make_unique<WavFileReader>(filename, sampling_frequency_in_hz,
                                         num_channels, false);
}

std::unique_ptr<TestAudioDeviceModule::Capturer>
TestAudioDeviceModule::CreateWavFileReader(absl::string_view filename,
                                           bool repeat) {
  WavReader reader(filename);
  int sampling_frequency_in_hz = reader.sample_rate();
  int num_channels = rtc::checked_cast<int>(reader.num_channels());
  return std::make_unique<WavFileReader>(filename, sampling_frequency_in_hz,
                                         num_channels, repeat);
}

std::unique_ptr<TestAudioDeviceModule::Renderer>
TestAudioDeviceModule::CreateWavFileWriter(absl::string_view filename,
                                           int sampling_frequency_in_hz,
                                           int num_channels) {
  return std::make_unique<WavFileWriter>(filename, sampling_frequency_in_hz,
                                         num_channels);
}

std::unique_ptr<TestAudioDeviceModule::Renderer>
TestAudioDeviceModule::CreateBoundedWavFileWriter(absl::string_view filename,
                                                  int sampling_frequency_in_hz,
                                                  int num_channels) {
  return std::make_unique<BoundedWavFileWriter>(
      filename, sampling_frequency_in_hz, num_channels);
}

}  // namespace webrtc
