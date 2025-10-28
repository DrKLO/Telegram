/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_device/include/test_audio_device.h"

#include <algorithm>
#include <array>
#include <memory>
#include <utility>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/task_queue/task_queue_factory.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "common_audio/wav_file.h"
#include "common_audio/wav_header.h"
#include "modules/audio_device/include/audio_device_defines.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/synchronization/mutex.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"
#include "test/time_controller/simulated_time_controller.h"

namespace webrtc {
namespace {

void RunWavTest(const std::vector<int16_t>& input_samples,
                const std::vector<int16_t>& expected_samples) {
  const ::testing::TestInfo* const test_info =
      ::testing::UnitTest::GetInstance()->current_test_info();

  const std::string output_filename = test::OutputPathWithRandomDirectory() +
                                      "BoundedWavFileWriterTest_" +
                                      test_info->name() + ".wav";

  static const size_t kSamplesPerFrame = 8;
  static const int kSampleRate = kSamplesPerFrame * 100;
  EXPECT_EQ(TestAudioDeviceModule::SamplesPerFrame(kSampleRate),
            kSamplesPerFrame);

  // Test through file name API.
  {
    std::unique_ptr<TestAudioDeviceModule::Renderer> writer =
        TestAudioDeviceModule::CreateBoundedWavFileWriter(output_filename, 800);

    for (size_t i = 0; i < input_samples.size(); i += kSamplesPerFrame) {
      EXPECT_TRUE(writer->Render(rtc::ArrayView<const int16_t>(
          &input_samples[i],
          std::min(kSamplesPerFrame, input_samples.size() - i))));
    }
  }

  {
    WavReader reader(output_filename);
    std::vector<int16_t> read_samples(expected_samples.size());
    EXPECT_EQ(expected_samples.size(),
              reader.ReadSamples(read_samples.size(), read_samples.data()));
    EXPECT_EQ(expected_samples, read_samples);

    EXPECT_EQ(0u, reader.ReadSamples(read_samples.size(), read_samples.data()));
  }

  remove(output_filename.c_str());
}

TEST(BoundedWavFileWriterTest, NoSilence) {
  static const std::vector<int16_t> kInputSamples = {
      75,   1234,  243,    -1231, -22222, 0,    3,      88,
      1222, -1213, -13222, -7,    -3525,  5787, -25247, 8};
  static const std::vector<int16_t> kExpectedSamples = kInputSamples;
  RunWavTest(kInputSamples, kExpectedSamples);
}

TEST(BoundedWavFileWriterTest, SomeStartSilence) {
  static const std::vector<int16_t> kInputSamples = {
      0, 0, 0, 0, 3, 0, 0, 0, 0, 3, -13222, -7, -3525, 5787, -25247, 8};
  static const std::vector<int16_t> kExpectedSamples(kInputSamples.begin() + 10,
                                                     kInputSamples.end());
  RunWavTest(kInputSamples, kExpectedSamples);
}

TEST(BoundedWavFileWriterTest, NegativeStartSilence) {
  static const std::vector<int16_t> kInputSamples = {
      0, -4, -6, 0, 3, 0, 0, 0, 0, 3, -13222, -7, -3525, 5787, -25247, 8};
  static const std::vector<int16_t> kExpectedSamples(kInputSamples.begin() + 2,
                                                     kInputSamples.end());
  RunWavTest(kInputSamples, kExpectedSamples);
}

TEST(BoundedWavFileWriterTest, SomeEndSilence) {
  static const std::vector<int16_t> kInputSamples = {
      75, 1234, 243, -1231, -22222, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0};
  static const std::vector<int16_t> kExpectedSamples(kInputSamples.begin(),
                                                     kInputSamples.end() - 9);
  RunWavTest(kInputSamples, kExpectedSamples);
}

TEST(BoundedWavFileWriterTest, DoubleEndSilence) {
  static const std::vector<int16_t> kInputSamples = {
      75, 1234,  243,    -1231, -22222, 0,    0, 0,
      0,  -1213, -13222, -7,    -3525,  5787, 0, 0};
  static const std::vector<int16_t> kExpectedSamples(kInputSamples.begin(),
                                                     kInputSamples.end() - 2);
  RunWavTest(kInputSamples, kExpectedSamples);
}

TEST(BoundedWavFileWriterTest, DoubleSilence) {
  static const std::vector<int16_t> kInputSamples = {0,     -1213, -13222, -7,
                                                     -3525, 5787,  0,      0};
  static const std::vector<int16_t> kExpectedSamples(kInputSamples.begin() + 1,
                                                     kInputSamples.end() - 2);
  RunWavTest(kInputSamples, kExpectedSamples);
}

TEST(BoundedWavFileWriterTest, EndSilenceCutoff) {
  static const std::vector<int16_t> kInputSamples = {
      75, 1234, 243, -1231, -22222, 0, 1, 0, 0, 0, 0};
  static const std::vector<int16_t> kExpectedSamples(kInputSamples.begin(),
                                                     kInputSamples.end() - 4);
  RunWavTest(kInputSamples, kExpectedSamples);
}

TEST(WavFileReaderTest, RepeatedTrueWithSingleFrameFileReadTwice) {
  static const std::vector<int16_t> kInputSamples = {75,     1234, 243, -1231,
                                                     -22222, 0,    3,   88};
  static const rtc::BufferT<int16_t> kExpectedSamples(kInputSamples.data(),
                                                      kInputSamples.size());

  const std::string output_filename = test::OutputPathWithRandomDirectory() +
                                      "WavFileReaderTest_RepeatedTrue_" +
                                      ".wav";

  static const size_t kSamplesPerFrame = 8;
  static const int kSampleRate = kSamplesPerFrame * 100;
  EXPECT_EQ(TestAudioDeviceModule::SamplesPerFrame(kSampleRate),
            kSamplesPerFrame);

  // Create raw file to read.
  {
    std::unique_ptr<TestAudioDeviceModule::Renderer> writer =
        TestAudioDeviceModule::CreateWavFileWriter(output_filename, 800);

    for (size_t i = 0; i < kInputSamples.size(); i += kSamplesPerFrame) {
      EXPECT_TRUE(writer->Render(rtc::ArrayView<const int16_t>(
          &kInputSamples[i],
          std::min(kSamplesPerFrame, kInputSamples.size() - i))));
    }
  }

  {
    std::unique_ptr<TestAudioDeviceModule::Capturer> reader =
        TestAudioDeviceModule::CreateWavFileReader(output_filename, true);
    rtc::BufferT<int16_t> buffer(kExpectedSamples.size());
    EXPECT_TRUE(reader->Capture(&buffer));
    EXPECT_EQ(kExpectedSamples, buffer);
    EXPECT_TRUE(reader->Capture(&buffer));
    EXPECT_EQ(kExpectedSamples, buffer);
  }

  remove(output_filename.c_str());
}

void RunRawTestNoRepeat(const std::vector<int16_t>& input_samples,
                        const std::vector<int16_t>& expected_samples) {
  const ::testing::TestInfo* const test_info =
      ::testing::UnitTest::GetInstance()->current_test_info();

  const std::string output_filename = test::OutputPathWithRandomDirectory() +
                                      "RawFileTest_" + test_info->name() +
                                      ".raw";

  static const size_t kSamplesPerFrame = 8;
  static const int kSampleRate = kSamplesPerFrame * 100;
  EXPECT_EQ(TestAudioDeviceModule::SamplesPerFrame(kSampleRate),
            kSamplesPerFrame);

  // Test through file name API.
  {
    std::unique_ptr<TestAudioDeviceModule::Renderer> writer =
        TestAudioDeviceModule::CreateRawFileWriter(
            output_filename, /*sampling_frequency_in_hz=*/800);

    for (size_t i = 0; i < input_samples.size(); i += kSamplesPerFrame) {
      EXPECT_TRUE(writer->Render(rtc::ArrayView<const int16_t>(
          &input_samples[i],
          std::min(kSamplesPerFrame, input_samples.size() - i))));
    }
  }

  {
    std::unique_ptr<TestAudioDeviceModule::Capturer> reader =
        TestAudioDeviceModule::CreateRawFileReader(
            output_filename, /*sampling_frequency_in_hz=*/800,
            /*num_channels=*/2, /*repeat=*/false);
    rtc::BufferT<int16_t> buffer(expected_samples.size());
    rtc::BufferT<int16_t> expected_buffer(expected_samples.size());
    expected_buffer.SetData(expected_samples);
    EXPECT_TRUE(reader->Capture(&buffer));
    EXPECT_EQ(expected_buffer, buffer);
    EXPECT_FALSE(reader->Capture(&buffer));
    EXPECT_TRUE(buffer.empty());
  }

  remove(output_filename.c_str());
}

TEST(RawFileWriterTest, NoSilence) {
  static const std::vector<int16_t> kInputSamples = {
      75,   1234,  243,    -1231, -22222, 0,    3,      88,
      1222, -1213, -13222, -7,    -3525,  5787, -25247, 8};
  static const std::vector<int16_t> kExpectedSamples = kInputSamples;
  RunRawTestNoRepeat(kInputSamples, kExpectedSamples);
}

TEST(RawFileWriterTest, SomeStartSilence) {
  static const std::vector<int16_t> kInputSamples = {
      0, 0, 0, 0, 3, 0, 0, 0, 0, 3, -13222, -7, -3525, 5787, -25247, 8};
  static const std::vector<int16_t> kExpectedSamples(kInputSamples.begin() + 10,
                                                     kInputSamples.end());
  RunRawTestNoRepeat(kInputSamples, kExpectedSamples);
}

TEST(RawFileWriterTest, NegativeStartSilence) {
  static const std::vector<int16_t> kInputSamples = {
      0, -4, -6, 0, 3, 0, 0, 0, 0, 3, -13222, -7, -3525, 5787, -25247, 8};
  static const std::vector<int16_t> kExpectedSamples(kInputSamples.begin() + 2,
                                                     kInputSamples.end());
  RunRawTestNoRepeat(kInputSamples, kExpectedSamples);
}

TEST(RawFileWriterTest, SomeEndSilence) {
  static const std::vector<int16_t> kInputSamples = {
      75, 1234, 243, -1231, -22222, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0};
  static const std::vector<int16_t> kExpectedSamples(kInputSamples.begin(),
                                                     kInputSamples.end() - 9);
  RunRawTestNoRepeat(kInputSamples, kExpectedSamples);
}

TEST(RawFileWriterTest, DoubleEndSilence) {
  static const std::vector<int16_t> kInputSamples = {
      75, 1234,  243,    -1231, -22222, 0,    0, 0,
      0,  -1213, -13222, -7,    -3525,  5787, 0, 0};
  static const std::vector<int16_t> kExpectedSamples(kInputSamples.begin(),
                                                     kInputSamples.end() - 2);
  RunRawTestNoRepeat(kInputSamples, kExpectedSamples);
}

TEST(RawFileWriterTest, DoubleSilence) {
  static const std::vector<int16_t> kInputSamples = {0,     -1213, -13222, -7,
                                                     -3525, 5787,  0,      0};
  static const std::vector<int16_t> kExpectedSamples(kInputSamples.begin() + 1,
                                                     kInputSamples.end() - 2);
  RunRawTestNoRepeat(kInputSamples, kExpectedSamples);
}

TEST(RawFileWriterTest, EndSilenceCutoff) {
  static const std::vector<int16_t> kInputSamples = {
      75, 1234, 243, -1231, -22222, 0, 1, 0, 0, 0, 0};
  static const std::vector<int16_t> kExpectedSamples(kInputSamples.begin(),
                                                     kInputSamples.end() - 4);
  RunRawTestNoRepeat(kInputSamples, kExpectedSamples);
}

TEST(RawFileWriterTest, Repeat) {
  static const std::vector<int16_t> kInputSamples = {
      75,   1234,  243,    -1231, -22222, 0,    3,      88,
      1222, -1213, -13222, -7,    -3525,  5787, -25247, 8};
  static const rtc::BufferT<int16_t> kExpectedSamples(kInputSamples.data(),
                                                      kInputSamples.size());

  const ::testing::TestInfo* const test_info =
      ::testing::UnitTest::GetInstance()->current_test_info();

  const std::string output_filename = test::OutputPathWithRandomDirectory() +
                                      "RawFileTest_" + test_info->name() + "_" +
                                      std::to_string(std::rand()) + ".raw";

  static const size_t kSamplesPerFrame = 8;
  static const int kSampleRate = kSamplesPerFrame * 100;
  EXPECT_EQ(TestAudioDeviceModule::SamplesPerFrame(kSampleRate),
            kSamplesPerFrame);

  // Test through file name API.
  {
    std::unique_ptr<TestAudioDeviceModule::Renderer> writer =
        TestAudioDeviceModule::CreateRawFileWriter(
            output_filename, /*sampling_frequency_in_hz=*/800);

    for (size_t i = 0; i < kInputSamples.size(); i += kSamplesPerFrame) {
      EXPECT_TRUE(writer->Render(rtc::ArrayView<const int16_t>(
          &kInputSamples[i],
          std::min(kSamplesPerFrame, kInputSamples.size() - i))));
    }
  }

  {
    std::unique_ptr<TestAudioDeviceModule::Capturer> reader =
        TestAudioDeviceModule::CreateRawFileReader(
            output_filename, /*sampling_frequency_in_hz=*/800,
            /*num_channels=*/2, /*repeat=*/true);
    rtc::BufferT<int16_t> buffer(kExpectedSamples.size());
    EXPECT_TRUE(reader->Capture(&buffer));
    EXPECT_EQ(kExpectedSamples, buffer);
    EXPECT_TRUE(reader->Capture(&buffer));
    EXPECT_EQ(kExpectedSamples, buffer);
  }

  remove(output_filename.c_str());
}

TEST(PulsedNoiseCapturerTest, SetMaxAmplitude) {
  const int16_t kAmplitude = 50;
  std::unique_ptr<TestAudioDeviceModule::PulsedNoiseCapturer> capturer =
      TestAudioDeviceModule::CreatePulsedNoiseCapturer(
          kAmplitude, /*sampling_frequency_in_hz=*/8000);
  rtc::BufferT<int16_t> recording_buffer;

  // Verify that the capturer doesn't create entries louder than than
  // kAmplitude. Since the pulse generator alternates between writing
  // zeroes and actual entries, we need to do the capturing twice.
  capturer->Capture(&recording_buffer);
  capturer->Capture(&recording_buffer);
  int16_t max_sample =
      *std::max_element(recording_buffer.begin(), recording_buffer.end());
  EXPECT_LE(max_sample, kAmplitude);

  // Increase the amplitude and verify that the samples can now be louder
  // than the previous max.
  capturer->SetMaxAmplitude(kAmplitude * 2);
  capturer->Capture(&recording_buffer);
  capturer->Capture(&recording_buffer);
  max_sample =
      *std::max_element(recording_buffer.begin(), recording_buffer.end());
  EXPECT_GT(max_sample, kAmplitude);
}

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
      EXPECT_TRUE(false)
          << "NeedMorePlayData mustn't be called when mode isn't kRecording";
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

TEST(TestAudioDeviceModuleTest, CreatedADMCanRecord) {
  GlobalSimulatedTimeController time_controller(kStartTime);
  TestAudioTransport audio_transport(TestAudioTransport::Mode::kRecording);
  std::unique_ptr<TestAudioDeviceModule::PulsedNoiseCapturer> capturer =
      TestAudioDeviceModule::CreatePulsedNoiseCapturer(
          /*max_amplitude=*/1000,
          /*sampling_frequency_in_hz=*/48000, /*num_channels=*/2);

  rtc::scoped_refptr<AudioDeviceModule> adm = TestAudioDeviceModule::Create(
      time_controller.GetTaskQueueFactory(), std::move(capturer),
      /*renderer=*/nullptr);

  ASSERT_EQ(adm->RegisterAudioCallback(&audio_transport), 0);
  ASSERT_EQ(adm->Init(), 0);

  EXPECT_FALSE(adm->RecordingIsInitialized());
  ASSERT_EQ(adm->InitRecording(), 0);
  EXPECT_TRUE(adm->RecordingIsInitialized());
  ASSERT_EQ(adm->StartRecording(), 0);
  time_controller.AdvanceTime(TimeDelta::Millis(10));
  ASSERT_TRUE(adm->Recording());
  time_controller.AdvanceTime(TimeDelta::Millis(10));
  ASSERT_EQ(adm->StopRecording(), 0);

  EXPECT_THAT(audio_transport.samples_per_channel(),
              ElementsAre(480, 480, 480));
  EXPECT_THAT(audio_transport.number_of_channels(), ElementsAre(2, 2, 2));
  EXPECT_THAT(audio_transport.bytes_per_sample(), ElementsAre(4, 4, 4));
  EXPECT_THAT(audio_transport.samples_per_second(),
              ElementsAre(48000, 48000, 48000));
}

TEST(TestAudioDeviceModuleTest, CreatedADMCanPlay) {
  GlobalSimulatedTimeController time_controller(kStartTime);
  TestAudioTransport audio_transport(TestAudioTransport::Mode::kPlaying);
  std::unique_ptr<TestAudioDeviceModule::Renderer> renderer =
      TestAudioDeviceModule::CreateDiscardRenderer(
          /*sampling_frequency_in_hz=*/48000, /*num_channels=*/2);

  rtc::scoped_refptr<AudioDeviceModule> adm =
      TestAudioDeviceModule::Create(time_controller.GetTaskQueueFactory(),
                                    /*capturer=*/nullptr, std::move(renderer));

  ASSERT_EQ(adm->RegisterAudioCallback(&audio_transport), 0);
  ASSERT_EQ(adm->Init(), 0);

  EXPECT_FALSE(adm->PlayoutIsInitialized());
  ASSERT_EQ(adm->InitPlayout(), 0);
  EXPECT_TRUE(adm->PlayoutIsInitialized());
  ASSERT_EQ(adm->StartPlayout(), 0);
  time_controller.AdvanceTime(TimeDelta::Millis(10));
  ASSERT_TRUE(adm->Playing());
  time_controller.AdvanceTime(TimeDelta::Millis(10));
  ASSERT_EQ(adm->StopPlayout(), 0);

  EXPECT_THAT(audio_transport.samples_per_channel(),
              ElementsAre(480, 480, 480));
  EXPECT_THAT(audio_transport.number_of_channels(), ElementsAre(2, 2, 2));
  EXPECT_THAT(audio_transport.bytes_per_sample(), ElementsAre(4, 4, 4));
  EXPECT_THAT(audio_transport.samples_per_second(),
              ElementsAre(48000, 48000, 48000));
}

}  // namespace
}  // namespace webrtc
