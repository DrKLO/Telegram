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
#include "api/task_queue/task_queue_factory.h"
#include "common_audio/wav_file.h"
#include "modules/audio_device/audio_device_impl.h"
#include "modules/audio_device/include/audio_device_default.h"
#include "modules/audio_device/test_audio_device_impl.h"
#include "rtc_base/buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/random.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/task_utils/repeating_task.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

namespace {

constexpr int kFrameLengthUs = 10000;
constexpr int kFramesPerSecond = rtc::kNumMicrosecsPerSec / kFrameLengthUs;

class TestAudioDeviceModuleImpl : public AudioDeviceModuleImpl {
 public:
  TestAudioDeviceModuleImpl(
      TaskQueueFactory* task_queue_factory,
      std::unique_ptr<TestAudioDeviceModule::Capturer> capturer,
      std::unique_ptr<TestAudioDeviceModule::Renderer> renderer,
      float speed = 1)
      : AudioDeviceModuleImpl(
            AudioLayer::kDummyAudio,
            std::make_unique<TestAudioDevice>(task_queue_factory,
                                              std::move(capturer),
                                              std::move(renderer),
                                              speed),
            task_queue_factory,
            /*create_detached=*/true) {}

  ~TestAudioDeviceModuleImpl() override = default;
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

class RawFileReader final : public TestAudioDeviceModule::Capturer {
 public:
  RawFileReader(absl::string_view input_file_name,
                int sampling_frequency_in_hz,
                int num_channels,
                bool repeat)
      : input_file_name_(input_file_name),
        sampling_frequency_in_hz_(sampling_frequency_in_hz),
        num_channels_(num_channels),
        repeat_(repeat),
        read_buffer_(
            TestAudioDeviceModule::SamplesPerFrame(sampling_frequency_in_hz) *
                num_channels * 2,
            0) {
    input_file_ = FileWrapper::OpenReadOnly(input_file_name_);
    RTC_CHECK(input_file_.is_open())
        << "Failed to open audio input file: " << input_file_name_;
  }

  ~RawFileReader() override { input_file_.Close(); }

  int SamplingFrequency() const override { return sampling_frequency_in_hz_; }

  int NumChannels() const override { return num_channels_; }

  bool Capture(rtc::BufferT<int16_t>* buffer) override {
    buffer->SetData(
        TestAudioDeviceModule::SamplesPerFrame(SamplingFrequency()) *
            NumChannels(),
        [&](rtc::ArrayView<int16_t> data) {
          rtc::ArrayView<int8_t> read_buffer_view = ReadBufferView();
          size_t size = data.size() * 2;
          size_t read = input_file_.Read(read_buffer_view.data(), size);
          if (read < size && repeat_) {
            do {
              input_file_.Rewind();
              size_t delta = input_file_.Read(
                  read_buffer_view.subview(read).data(), size - read);
              RTC_CHECK_GT(delta, 0) << "No new data to read from file";
              read += delta;
            } while (read < size);
          }
          memcpy(data.data(), read_buffer_view.data(), size);
          return read / 2;
        });
    return buffer->size() > 0;
  }

 private:
  rtc::ArrayView<int8_t> ReadBufferView() { return read_buffer_; }

  const std::string input_file_name_;
  const int sampling_frequency_in_hz_;
  const int num_channels_;
  const bool repeat_;
  FileWrapper input_file_;
  std::vector<int8_t> read_buffer_;
};

class RawFileWriter : public TestAudioDeviceModule::Renderer {
 public:
  RawFileWriter(absl::string_view output_file_name,
                int sampling_frequency_in_hz,
                int num_channels)
      : output_file_name_(output_file_name),
        sampling_frequency_in_hz_(sampling_frequency_in_hz),
        num_channels_(num_channels),
        silent_audio_(
            TestAudioDeviceModule::SamplesPerFrame(sampling_frequency_in_hz) *
                num_channels * 2,
            0),
        write_buffer_(
            TestAudioDeviceModule::SamplesPerFrame(sampling_frequency_in_hz) *
                num_channels * 2,
            0),
        started_writing_(false),
        trailing_zeros_(0) {
    output_file_ = FileWrapper::OpenWriteOnly(output_file_name_);
    RTC_CHECK(output_file_.is_open())
        << "Failed to open playout file" << output_file_name_;
  }
  ~RawFileWriter() override { output_file_.Close(); }

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
          output_file_.Write(silent_audio_.data(), zeros_to_write * 2);
          trailing_zeros_ -= zeros_to_write;
        }
        WriteInt16(begin, end);
      }
      // Save the number of zeros we skipped in case this needs to be restored.
      trailing_zeros_ += data.end() - end;
    }
    return true;
  }

 private:
  void WriteInt16(const int16_t* begin, const int16_t* end) {
    int size = (end - begin) * sizeof(int16_t);
    memcpy(write_buffer_.data(), begin, size);
    output_file_.Write(write_buffer_.data(), size);
  }

  const std::string output_file_name_;
  const int sampling_frequency_in_hz_;
  const int num_channels_;
  FileWrapper output_file_;
  std::vector<int8_t> silent_audio_;
  std::vector<int8_t> write_buffer_;
  bool started_writing_;
  size_t trailing_zeros_;
};

}  // namespace

size_t TestAudioDeviceModule::SamplesPerFrame(int sampling_frequency_in_hz) {
  return rtc::CheckedDivExact(sampling_frequency_in_hz, kFramesPerSecond);
}

rtc::scoped_refptr<AudioDeviceModule> TestAudioDeviceModule::Create(
    TaskQueueFactory* task_queue_factory,
    std::unique_ptr<TestAudioDeviceModule::Capturer> capturer,
    std::unique_ptr<TestAudioDeviceModule::Renderer> renderer,
    float speed) {
  auto audio_device = rtc::make_ref_counted<TestAudioDeviceModuleImpl>(
      task_queue_factory, std::move(capturer), std::move(renderer), speed);

  // Ensure that the current platform is supported.
  if (audio_device->CheckPlatform() == -1) {
    return nullptr;
  }

  // Create the platform-dependent implementation.
  if (audio_device->CreatePlatformSpecificObjects() == -1) {
    return nullptr;
  }

  // Ensure that the generic audio buffer can communicate with the platform
  // specific parts.
  if (audio_device->AttachAudioBuffer() == -1) {
    return nullptr;
  }

  return audio_device;
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

std::unique_ptr<TestAudioDeviceModule::Capturer>
TestAudioDeviceModule::CreateRawFileReader(absl::string_view filename,
                                           int sampling_frequency_in_hz,
                                           int num_channels,
                                           bool repeat) {
  return std::make_unique<RawFileReader>(filename, sampling_frequency_in_hz,
                                         num_channels, repeat);
}

std::unique_ptr<TestAudioDeviceModule::Renderer>
TestAudioDeviceModule::CreateRawFileWriter(absl::string_view filename,
                                           int sampling_frequency_in_hz,
                                           int num_channels) {
  return std::make_unique<RawFileWriter>(filename, sampling_frequency_in_hz,
                                         num_channels);
}

}  // namespace webrtc
