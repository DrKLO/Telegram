/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for Accelerate and PreemptiveExpand classes.

#include <map>
#include <memory>

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "modules/audio_coding/neteq/accelerate.h"
#include "modules/audio_coding/neteq/background_noise.h"
#include "modules/audio_coding/neteq/preemptive_expand.h"
#include "modules/audio_coding/neteq/tools/input_audio_file.h"
#include "rtc_base/checks.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {

namespace {
const size_t kNumChannels = 1;
}

TEST(TimeStretch, CreateAndDestroy) {
  const int kSampleRate = 8000;
  const int kOverlapSamples = 5 * kSampleRate / 8000;
  BackgroundNoise bgn(kNumChannels);
  Accelerate accelerate(kSampleRate, kNumChannels, bgn);
  PreemptiveExpand preemptive_expand(kSampleRate, kNumChannels, bgn,
                                     kOverlapSamples);
}

TEST(TimeStretch, CreateUsingFactory) {
  const int kSampleRate = 8000;
  const int kOverlapSamples = 5 * kSampleRate / 8000;
  BackgroundNoise bgn(kNumChannels);

  AccelerateFactory accelerate_factory;
  Accelerate* accelerate =
      accelerate_factory.Create(kSampleRate, kNumChannels, bgn);
  EXPECT_TRUE(accelerate != NULL);
  delete accelerate;

  PreemptiveExpandFactory preemptive_expand_factory;
  PreemptiveExpand* preemptive_expand = preemptive_expand_factory.Create(
      kSampleRate, kNumChannels, bgn, kOverlapSamples);
  EXPECT_TRUE(preemptive_expand != NULL);
  delete preemptive_expand;
}

class TimeStretchTest : public ::testing::Test {
 protected:
  TimeStretchTest()
      : input_file_(new test::InputAudioFile(
            test::ResourcePath("audio_coding/testfile32kHz", "pcm"))),
        sample_rate_hz_(32000),
        block_size_(30 * sample_rate_hz_ / 1000),  // 30 ms
        audio_(new int16_t[block_size_]),
        background_noise_(kNumChannels) {}

  const int16_t* Next30Ms() {
    RTC_CHECK(input_file_->Read(block_size_, audio_.get()));
    return audio_.get();
  }

  // Returns the total length change (in samples) that the accelerate operation
  // resulted in during the run.
  size_t TestAccelerate(size_t loops, bool fast_mode) {
    Accelerate accelerate(sample_rate_hz_, kNumChannels, background_noise_);
    size_t total_length_change = 0;
    for (size_t i = 0; i < loops; ++i) {
      AudioMultiVector output(kNumChannels);
      size_t length_change;
      UpdateReturnStats(accelerate.Process(Next30Ms(), block_size_, fast_mode,
                                           &output, &length_change));
      total_length_change += length_change;
    }
    return total_length_change;
  }

  void UpdateReturnStats(TimeStretch::ReturnCodes ret) {
    switch (ret) {
      case TimeStretch::kSuccess:
      case TimeStretch::kSuccessLowEnergy:
      case TimeStretch::kNoStretch:
        ++return_stats_[ret];
        break;
      case TimeStretch::kError:
        FAIL() << "Process returned an error";
    }
  }

  std::unique_ptr<test::InputAudioFile> input_file_;
  const int sample_rate_hz_;
  const size_t block_size_;
  std::unique_ptr<int16_t[]> audio_;
  std::map<TimeStretch::ReturnCodes, int> return_stats_;
  BackgroundNoise background_noise_;
};

TEST_F(TimeStretchTest, Accelerate) {
  // TestAccelerate returns the total length change in samples.
  EXPECT_EQ(15268U, TestAccelerate(100, false));
  EXPECT_EQ(9, return_stats_[TimeStretch::kSuccess]);
  EXPECT_EQ(58, return_stats_[TimeStretch::kSuccessLowEnergy]);
  EXPECT_EQ(33, return_stats_[TimeStretch::kNoStretch]);
}

TEST_F(TimeStretchTest, AccelerateFastMode) {
  // TestAccelerate returns the total length change in samples.
  EXPECT_EQ(21400U, TestAccelerate(100, true));
  EXPECT_EQ(31, return_stats_[TimeStretch::kSuccess]);
  EXPECT_EQ(58, return_stats_[TimeStretch::kSuccessLowEnergy]);
  EXPECT_EQ(11, return_stats_[TimeStretch::kNoStretch]);
}

}  // namespace webrtc
