/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for Expand class.

#include "modules/audio_coding/neteq/expand.h"

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "modules/audio_coding/neteq/background_noise.h"
#include "modules/audio_coding/neteq/random_vector.h"
#include "modules/audio_coding/neteq/statistics_calculator.h"
#include "modules/audio_coding/neteq/sync_buffer.h"
#include "modules/audio_coding/neteq/tools/resample_input_audio_file.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {

TEST(Expand, CreateAndDestroy) {
  int fs = 8000;
  size_t channels = 1;
  BackgroundNoise bgn(channels);
  SyncBuffer sync_buffer(1, 1000);
  RandomVector random_vector;
  StatisticsCalculator statistics;
  Expand expand(&bgn, &sync_buffer, &random_vector, &statistics, fs, channels);
}

TEST(Expand, CreateUsingFactory) {
  int fs = 8000;
  size_t channels = 1;
  BackgroundNoise bgn(channels);
  SyncBuffer sync_buffer(1, 1000);
  RandomVector random_vector;
  StatisticsCalculator statistics;
  ExpandFactory expand_factory;
  Expand* expand = expand_factory.Create(&bgn, &sync_buffer, &random_vector,
                                         &statistics, fs, channels);
  EXPECT_TRUE(expand != NULL);
  delete expand;
}

namespace {
class FakeStatisticsCalculator : public StatisticsCalculator {
 public:
  void LogDelayedPacketOutageEvent(int num_samples, int fs_hz) override {
    last_outage_duration_samples_ = num_samples;
  }

  int last_outage_duration_samples() const {
    return last_outage_duration_samples_;
  }

 private:
  int last_outage_duration_samples_ = 0;
};

// This is the same size that is given to the SyncBuffer object in NetEq.
const size_t kNetEqSyncBufferLengthMs = 720;
}  // namespace

class ExpandTest : public ::testing::Test {
 protected:
  ExpandTest()
      : input_file_(test::ResourcePath("audio_coding/testfile32kHz", "pcm"),
                    32000),
        test_sample_rate_hz_(32000),
        num_channels_(1),
        background_noise_(num_channels_),
        sync_buffer_(num_channels_,
                     kNetEqSyncBufferLengthMs * test_sample_rate_hz_ / 1000),
        expand_(&background_noise_,
                &sync_buffer_,
                &random_vector_,
                &statistics_,
                test_sample_rate_hz_,
                num_channels_) {
    input_file_.set_output_rate_hz(test_sample_rate_hz_);
  }

  void SetUp() override {
    // Fast-forward the input file until there is speech (about 1.1 second into
    // the file).
    const int speech_start_samples =
        static_cast<int>(test_sample_rate_hz_ * 1.1f);
    ASSERT_TRUE(input_file_.Seek(speech_start_samples));

    // Pre-load the sync buffer with speech data.
    std::unique_ptr<int16_t[]> temp(new int16_t[sync_buffer_.Size()]);
    ASSERT_TRUE(input_file_.Read(sync_buffer_.Size(), temp.get()));
    sync_buffer_.Channel(0).OverwriteAt(temp.get(), sync_buffer_.Size(), 0);
    ASSERT_EQ(1u, num_channels_) << "Fix: Must populate all channels.";
  }

  test::ResampleInputAudioFile input_file_;
  int test_sample_rate_hz_;
  size_t num_channels_;
  BackgroundNoise background_noise_;
  SyncBuffer sync_buffer_;
  RandomVector random_vector_;
  FakeStatisticsCalculator statistics_;
  Expand expand_;
};

// This test calls the expand object to produce concealment data a few times,
// and then ends by calling SetParametersForNormalAfterExpand. This simulates
// the situation where the packet next up for decoding was just delayed, not
// lost.
TEST_F(ExpandTest, DelayedPacketOutage) {
  AudioMultiVector output(num_channels_);
  size_t sum_output_len_samples = 0;
  for (int i = 0; i < 10; ++i) {
    EXPECT_EQ(0, expand_.Process(&output));
    EXPECT_GT(output.Size(), 0u);
    sum_output_len_samples += output.Size();
    EXPECT_EQ(0, statistics_.last_outage_duration_samples());
  }
  expand_.SetParametersForNormalAfterExpand();
  // Convert `sum_output_len_samples` to milliseconds.
  EXPECT_EQ(rtc::checked_cast<int>(sum_output_len_samples),
            statistics_.last_outage_duration_samples());
}

// This test is similar to DelayedPacketOutage, but ends by calling
// SetParametersForMergeAfterExpand. This simulates the situation where the
// packet next up for decoding was actually lost (or at least a later packet
// arrived before it).
TEST_F(ExpandTest, LostPacketOutage) {
  AudioMultiVector output(num_channels_);
  for (int i = 0; i < 10; ++i) {
    EXPECT_EQ(0, expand_.Process(&output));
    EXPECT_GT(output.Size(), 0u);
    EXPECT_EQ(0, statistics_.last_outage_duration_samples());
  }
  expand_.SetParametersForMergeAfterExpand();
  EXPECT_EQ(0, statistics_.last_outage_duration_samples());
}

// This test is similar to the DelayedPacketOutage test above, but with the
// difference that Expand::Reset() is called after 5 calls to Expand::Process().
// This should reset the statistics, and will in the end lead to an outage of
// 5 periods instead of 10.
TEST_F(ExpandTest, CheckOutageStatsAfterReset) {
  AudioMultiVector output(num_channels_);
  size_t sum_output_len_samples = 0;
  for (int i = 0; i < 10; ++i) {
    EXPECT_EQ(0, expand_.Process(&output));
    EXPECT_GT(output.Size(), 0u);
    sum_output_len_samples += output.Size();
    if (i == 5) {
      expand_.Reset();
      sum_output_len_samples = 0;
    }
    EXPECT_EQ(0, statistics_.last_outage_duration_samples());
  }
  expand_.SetParametersForNormalAfterExpand();
  // Convert `sum_output_len_samples` to milliseconds.
  EXPECT_EQ(rtc::checked_cast<int>(sum_output_len_samples),
            statistics_.last_outage_duration_samples());
}

namespace {
// Runs expand until Muted() returns true. Times out after 1000 calls.
void ExpandUntilMuted(size_t num_channels, Expand* expand) {
  EXPECT_FALSE(expand->Muted()) << "Instance is muted from the start";
  AudioMultiVector output(num_channels);
  int num_calls = 0;
  while (!expand->Muted()) {
    ASSERT_LT(num_calls++, 1000) << "Test timed out";
    EXPECT_EQ(0, expand->Process(&output));
  }
}
}  // namespace

// Verifies that Muted() returns true after a long expand period. Also verifies
// that Muted() is reset to false after calling Reset(),
// SetParametersForMergeAfterExpand() and SetParametersForNormalAfterExpand().
TEST_F(ExpandTest, Muted) {
  ExpandUntilMuted(num_channels_, &expand_);
  expand_.Reset();
  EXPECT_FALSE(expand_.Muted());  // Should be back to unmuted.

  ExpandUntilMuted(num_channels_, &expand_);
  expand_.SetParametersForMergeAfterExpand();
  EXPECT_FALSE(expand_.Muted());  // Should be back to unmuted.

  expand_.Reset();  // Must reset in order to start a new expand period.
  ExpandUntilMuted(num_channels_, &expand_);
  expand_.SetParametersForNormalAfterExpand();
  EXPECT_FALSE(expand_.Muted());  // Should be back to unmuted.
}

// TODO(hlundin): Write more tests.

}  // namespace webrtc
