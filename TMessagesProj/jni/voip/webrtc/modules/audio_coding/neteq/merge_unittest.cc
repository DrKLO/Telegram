/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for Merge class.

#include "modules/audio_coding/neteq/merge.h"

#include <algorithm>
#include <vector>

#include "modules/audio_coding/neteq/background_noise.h"
#include "modules/audio_coding/neteq/expand.h"
#include "modules/audio_coding/neteq/random_vector.h"
#include "modules/audio_coding/neteq/statistics_calculator.h"
#include "modules/audio_coding/neteq/sync_buffer.h"
#include "modules/audio_coding/neteq/tools/resample_input_audio_file.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {

TEST(Merge, CreateAndDestroy) {
  int fs = 8000;
  size_t channels = 1;
  BackgroundNoise bgn(channels);
  SyncBuffer sync_buffer(1, 1000);
  RandomVector random_vector;
  StatisticsCalculator statistics;
  Expand expand(&bgn, &sync_buffer, &random_vector, &statistics, fs, channels);
  Merge merge(fs, channels, &expand, &sync_buffer);
}

namespace {
// This is the same size that is given to the SyncBuffer object in NetEq.
const size_t kNetEqSyncBufferLengthMs = 720;
}  // namespace

class MergeTest : public testing::TestWithParam<size_t> {
 protected:
  MergeTest()
      : input_file_(test::ResourcePath("audio_coding/testfile32kHz", "pcm"),
                    32000),
        test_sample_rate_hz_(8000),
        num_channels_(1),
        background_noise_(num_channels_),
        sync_buffer_(num_channels_,
                     kNetEqSyncBufferLengthMs * test_sample_rate_hz_ / 1000),
        expand_(&background_noise_,
                &sync_buffer_,
                &random_vector_,
                &statistics_,
                test_sample_rate_hz_,
                num_channels_),
        merge_(test_sample_rate_hz_, num_channels_, &expand_, &sync_buffer_) {
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
    // Move index such that the sync buffer appears to have 5 ms left to play.
    sync_buffer_.set_next_index(sync_buffer_.next_index() -
                                test_sample_rate_hz_ * 5 / 1000);
    ASSERT_EQ(1u, num_channels_) << "Fix: Must populate all channels.";
    ASSERT_GT(sync_buffer_.FutureLength(), 0u);
  }

  test::ResampleInputAudioFile input_file_;
  int test_sample_rate_hz_;
  size_t num_channels_;
  BackgroundNoise background_noise_;
  SyncBuffer sync_buffer_;
  RandomVector random_vector_;
  StatisticsCalculator statistics_;
  Expand expand_;
  Merge merge_;
};

TEST_P(MergeTest, Process) {
  AudioMultiVector output(num_channels_);
  // Start by calling Expand once, to prime the state.
  EXPECT_EQ(0, expand_.Process(&output));
  EXPECT_GT(output.Size(), 0u);
  output.Clear();
  // Now call Merge, but with a very short decoded input. Try different length
  // if the input.
  const size_t input_len = GetParam();
  std::vector<int16_t> input(input_len, 17);
  merge_.Process(input.data(), input_len, &output);
  EXPECT_GT(output.Size(), 0u);
}

// Instantiate with values for the input length that are interesting in
// Merge::Downsample. Why are these values interesting?
// - In 8000 Hz sample rate, signal_offset in Merge::Downsample will be 2, so
//   the values 1, 2, 3 are just around that value.
// - Also in 8000 Hz, the variable length_limit in the same method will be 80,
//   so values 80 and 81 will be on either side of the branch point
//   "input_length <= length_limit".
// - Finally, 160 is simply 20 ms in 8000 Hz, which is a common packet size.
INSTANTIATE_TEST_SUITE_P(DifferentInputLengths,
                         MergeTest,
                         testing::Values(1, 2, 3, 80, 81, 160));
// TODO(hlundin): Write more tests.

}  // namespace webrtc
