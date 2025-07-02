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
#include "api/task_queue/task_queue_factory.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/audio_device/audio_device_buffer.h"
#include "modules/audio_device/audio_device_generic.h"
#include "modules/audio_device/include/audio_device.h"
#include "modules/audio_device/include/audio_device_defines.h"
#include "modules/audio_device/include/test_audio_device.h"
#include "rtc_base/checks.h"
#include "rtc_base/synchronization/mutex.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/time_controller/simulated_time_controller.h"

namespace webrtc {
namespace {

using ::testing::ElementsAre;

constexpr Timestamp kStartTime = Timestamp::Millis(10000);

class TestAudioTransport : public AudioTransport {
 public:
  enum class Mode { kPlaying, kRecording };

  explicit TestAudioTransport(Mode mode) : mode_(mode) {}
  ~TestAudioTransport() override = default;

  int32_t RecordedDataIsAvailable(
      const void* audioSamples,
      size_t samples_per_channel,
      size_t bytes_per_sample,
      size_t number_of_channels,
      uint32_t samples_per_second,
      uint32_t total_delay_ms,
      int32_t clock_drift,
      uint32_t current_mic_level,
      bool key_pressed,
      uint32_t& new_mic_level,
      absl::optional<int64_t> estimated_capture_time_ns) override {
    new_mic_level = 1;

    if (mode_ != Mode::kRecording) {
      EXPECT_TRUE(false) << "RecordedDataIsAvailable mustn't be called when "
                            "mode isn't kRecording";
      return -1;
    }

    MutexLock lock(&mutex_);
    samples_per_channel_.push_back(samples_per_channel);
    number_of_channels_.push_back(number_of_channels);
    bytes_per_sample_.push_back(bytes_per_sample);
    samples_per_second_.push_back(samples_per_second);
    return 0;
  }

  int32_t NeedMorePlayData(size_t samples_per_channel,
                           size_t bytes_per_sample,
                           size_t number_of_channels,
                           uint32_t samples_per_second,
                           void* audio_samples,
                           size_t& samples_out,
                           int64_t* elapsed_time_ms,
                           int64_t* ntp_time_ms) override {
    const size_t num_bytes = samples_per_channel * number_of_channels;
    std::memset(audio_samples, 1, num_bytes);
    samples_out = samples_per_channel * number_of_channels;
    *elapsed_time_ms = 0;
    *ntp_time_ms = 0;

    if (mode_ != Mode::kPlaying) {
      EXPECT_TRUE(false)
          << "NeedMorePlayData mustn't be called when mode isn't kPlaying";
      return -1;
    }

    MutexLock lock(&mutex_);
    samples_per_channel_.push_back(samples_per_channel);
    number_of_channels_.push_back(number_of_channels);
    bytes_per_sample_.push_back(bytes_per_sample);
    samples_per_second_.push_back(samples_per_second);
    return 0;
  }

  int32_t RecordedDataIsAvailable(const void* audio_samples,
                                  size_t samples_per_channel,
                                  size_t bytes_per_sample,
                                  size_t number_of_channels,
                                  uint32_t samples_per_second,
                                  uint32_t total_delay_ms,
                                  int32_t clockDrift,
                                  uint32_t current_mic_level,
                                  bool key_pressed,
                                  uint32_t& new_mic_level) override {
    RTC_CHECK(false) << "This methods should be never executed";
  }

  void PullRenderData(int bits_per_sample,
                      int sample_rate,
                      size_t number_of_channels,
                      size_t number_of_frames,
                      void* audio_data,
                      int64_t* elapsed_time_ms,
                      int64_t* ntp_time_ms) override {
    RTC_CHECK(false) << "This methods should be never executed";
  }

  std::vector<size_t> samples_per_channel() const {
    MutexLock lock(&mutex_);
    return samples_per_channel_;
  }
  std::vector<size_t> number_of_channels() const {
    MutexLock lock(&mutex_);
    return number_of_channels_;
  }
  std::vector<size_t> bytes_per_sample() const {
    MutexLock lock(&mutex_);
    return bytes_per_sample_;
  }
  std::vector<size_t> samples_per_second() const {
    MutexLock lock(&mutex_);
    return samples_per_second_;
  }

 private:
  const Mode mode_;

  mutable Mutex mutex_;
  std::vector<size_t> samples_per_channel_ RTC_GUARDED_BY(mutex_);
  std::vector<size_t> number_of_channels_ RTC_GUARDED_BY(mutex_);
  std::vector<size_t> bytes_per_sample_ RTC_GUARDED_BY(mutex_);
  std::vector<size_t> samples_per_second_ RTC_GUARDED_BY(mutex_);
};

TEST(TestAudioDeviceTest, EnablingRecordingProducesAudio) {
  GlobalSimulatedTimeController time_controller(kStartTime);
  TestAudioTransport audio_transport(TestAudioTransport::Mode::kRecording);
  AudioDeviceBuffer audio_buffer(time_controller.GetTaskQueueFactory());
  ASSERT_EQ(audio_buffer.RegisterAudioCallback(&audio_transport), 0);
  std::unique_ptr<TestAudioDeviceModule::PulsedNoiseCapturer> capturer =
      TestAudioDeviceModule::CreatePulsedNoiseCapturer(
          /*max_amplitude=*/1000,
          /*sampling_frequency_in_hz=*/48000, /*num_channels=*/2);

  TestAudioDevice audio_device(time_controller.GetTaskQueueFactory(),
                               std::move(capturer),
                               /*renderer=*/nullptr);
  ASSERT_EQ(audio_device.Init(), AudioDeviceGeneric::InitStatus::OK);
  audio_device.AttachAudioBuffer(&audio_buffer);

  EXPECT_FALSE(audio_device.RecordingIsInitialized());
  ASSERT_EQ(audio_device.InitRecording(), 0);
  EXPECT_TRUE(audio_device.RecordingIsInitialized());
  audio_buffer.StartRecording();
  ASSERT_EQ(audio_device.StartRecording(), 0);
  time_controller.AdvanceTime(TimeDelta::Millis(10));
  ASSERT_TRUE(audio_device.Recording());
  time_controller.AdvanceTime(TimeDelta::Millis(10));
  ASSERT_EQ(audio_device.StopRecording(), 0);
  audio_buffer.StopRecording();

  EXPECT_THAT(audio_transport.samples_per_channel(),
              ElementsAre(480, 480, 480));
  EXPECT_THAT(audio_transport.number_of_channels(), ElementsAre(2, 2, 2));
  EXPECT_THAT(audio_transport.bytes_per_sample(), ElementsAre(4, 4, 4));
  EXPECT_THAT(audio_transport.samples_per_second(),
              ElementsAre(48000, 48000, 48000));
}

TEST(TestAudioDeviceTest, RecordingIsAvailableWhenCapturerIsSet) {
  GlobalSimulatedTimeController time_controller(kStartTime);
  std::unique_ptr<TestAudioDeviceModule::PulsedNoiseCapturer> capturer =
      TestAudioDeviceModule::CreatePulsedNoiseCapturer(
          /*max_amplitude=*/1000,
          /*sampling_frequency_in_hz=*/48000, /*num_channels=*/2);

  TestAudioDevice audio_device(time_controller.GetTaskQueueFactory(),
                               std::move(capturer),
                               /*renderer=*/nullptr);
  ASSERT_EQ(audio_device.Init(), AudioDeviceGeneric::InitStatus::OK);

  bool available;
  EXPECT_EQ(audio_device.RecordingIsAvailable(available), 0);
  EXPECT_TRUE(available);
}

TEST(TestAudioDeviceTest, RecordingIsNotAvailableWhenCapturerIsNotSet) {
  GlobalSimulatedTimeController time_controller(kStartTime);
  TestAudioDevice audio_device(time_controller.GetTaskQueueFactory(),
                               /*capturer=*/nullptr,
                               /*renderer=*/nullptr);
  ASSERT_EQ(audio_device.Init(), AudioDeviceGeneric::InitStatus::OK);

  bool available;
  EXPECT_EQ(audio_device.RecordingIsAvailable(available), 0);
  EXPECT_FALSE(available);
}

TEST(TestAudioDeviceTest, EnablingPlayoutProducesAudio) {
  GlobalSimulatedTimeController time_controller(kStartTime);
  TestAudioTransport audio_transport(TestAudioTransport::Mode::kPlaying);
  AudioDeviceBuffer audio_buffer(time_controller.GetTaskQueueFactory());
  ASSERT_EQ(audio_buffer.RegisterAudioCallback(&audio_transport), 0);
  std::unique_ptr<TestAudioDeviceModule::Renderer> renderer =
      TestAudioDeviceModule::CreateDiscardRenderer(
          /*sampling_frequency_in_hz=*/48000, /*num_channels=*/2);

  TestAudioDevice audio_device(time_controller.GetTaskQueueFactory(),
                               /*capturer=*/nullptr, std::move(renderer));
  ASSERT_EQ(audio_device.Init(), AudioDeviceGeneric::InitStatus::OK);
  audio_device.AttachAudioBuffer(&audio_buffer);

  EXPECT_FALSE(audio_device.PlayoutIsInitialized());
  ASSERT_EQ(audio_device.InitPlayout(), 0);
  EXPECT_TRUE(audio_device.PlayoutIsInitialized());
  audio_buffer.StartPlayout();
  ASSERT_EQ(audio_device.StartPlayout(), 0);
  time_controller.AdvanceTime(TimeDelta::Millis(10));
  ASSERT_TRUE(audio_device.Playing());
  time_controller.AdvanceTime(TimeDelta::Millis(10));
  ASSERT_EQ(audio_device.StopPlayout(), 0);
  audio_buffer.StopPlayout();

  EXPECT_THAT(audio_transport.samples_per_channel(),
              ElementsAre(480, 480, 480));
  EXPECT_THAT(audio_transport.number_of_channels(), ElementsAre(2, 2, 2));
  EXPECT_THAT(audio_transport.bytes_per_sample(), ElementsAre(4, 4, 4));
  EXPECT_THAT(audio_transport.samples_per_second(),
              ElementsAre(48000, 48000, 48000));
}

TEST(TestAudioDeviceTest, PlayoutIsAvailableWhenRendererIsSet) {
  GlobalSimulatedTimeController time_controller(kStartTime);
  std::unique_ptr<TestAudioDeviceModule::Renderer> renderer =
      TestAudioDeviceModule::CreateDiscardRenderer(
          /*sampling_frequency_in_hz=*/48000, /*num_channels=*/2);

  TestAudioDevice audio_device(time_controller.GetTaskQueueFactory(),
                               /*capturer=*/nullptr, std::move(renderer));
  ASSERT_EQ(audio_device.Init(), AudioDeviceGeneric::InitStatus::OK);

  bool available;
  EXPECT_EQ(audio_device.PlayoutIsAvailable(available), 0);
  EXPECT_TRUE(available);
}

TEST(TestAudioDeviceTest, PlayoutIsNotAvailableWhenRendererIsNotSet) {
  GlobalSimulatedTimeController time_controller(kStartTime);
  TestAudioDevice audio_device(time_controller.GetTaskQueueFactory(),
                               /*capturer=*/nullptr,
                               /*renderer=*/nullptr);
  ASSERT_EQ(audio_device.Init(), AudioDeviceGeneric::InitStatus::OK);

  bool available;
  EXPECT_EQ(audio_device.PlayoutIsAvailable(available), 0);
  EXPECT_FALSE(available);
}

}  // namespace
}  // namespace webrtc
