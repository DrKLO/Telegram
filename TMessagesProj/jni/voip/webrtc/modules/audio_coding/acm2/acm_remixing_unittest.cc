/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/acm2/acm_remixing.h"

#include <vector>

#include "api/audio/audio_frame.h"
#include "system_wrappers/include/clock.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

using ::testing::AllOf;
using ::testing::Each;
using ::testing::ElementsAreArray;
using ::testing::SizeIs;

namespace webrtc {

TEST(AcmRemixing, DownMixFrame) {
  std::vector<int16_t> out(480, 0);
  AudioFrame in;
  in.num_channels_ = 2;
  in.samples_per_channel_ = 480;

  int16_t* const in_data = in.mutable_data();
  for (size_t k = 0; k < in.samples_per_channel_; ++k) {
    in_data[2 * k] = 2;
    in_data[2 * k + 1] = 0;
  }

  DownMixFrame(in, out);

  EXPECT_THAT(out, AllOf(SizeIs(480), Each(1)));
}

TEST(AcmRemixing, DownMixMutedFrame) {
  std::vector<int16_t> out(480, 0);
  AudioFrame in;
  in.num_channels_ = 2;
  in.samples_per_channel_ = 480;

  int16_t* const in_data = in.mutable_data();
  for (size_t k = 0; k < in.samples_per_channel_; ++k) {
    in_data[2 * k] = 2;
    in_data[2 * k + 1] = 0;
  }

  in.Mute();

  DownMixFrame(in, out);

  EXPECT_THAT(out, AllOf(SizeIs(480), Each(0)));
}

TEST(AcmRemixing, RemixMutedStereoFrameTo6Channels) {
  std::vector<int16_t> out(480, 0);
  AudioFrame in;
  in.num_channels_ = 2;
  in.samples_per_channel_ = 480;

  int16_t* const in_data = in.mutable_data();
  for (size_t k = 0; k < in.samples_per_channel_; ++k) {
    in_data[2 * k] = 1;
    in_data[2 * k + 1] = 2;
  }
  in.Mute();

  ReMixFrame(in, 6, &out);
  EXPECT_EQ(6 * 480u, out.size());

  EXPECT_THAT(out, AllOf(SizeIs(in.samples_per_channel_ * 6), Each(0)));
}

TEST(AcmRemixing, RemixStereoFrameTo6Channels) {
  std::vector<int16_t> out(480, 0);
  AudioFrame in;
  in.num_channels_ = 2;
  in.samples_per_channel_ = 480;

  int16_t* const in_data = in.mutable_data();
  for (size_t k = 0; k < in.samples_per_channel_; ++k) {
    in_data[2 * k] = 1;
    in_data[2 * k + 1] = 2;
  }

  ReMixFrame(in, 6, &out);
  EXPECT_EQ(6 * 480u, out.size());

  std::vector<int16_t> expected_output(in.samples_per_channel_ * 6);
  for (size_t k = 0; k < in.samples_per_channel_; ++k) {
    expected_output[6 * k] = 1;
    expected_output[6 * k + 1] = 2;
  }

  EXPECT_THAT(out, ElementsAreArray(expected_output));
}

TEST(AcmRemixing, RemixMonoFrameTo6Channels) {
  std::vector<int16_t> out(480, 0);
  AudioFrame in;
  in.num_channels_ = 1;
  in.samples_per_channel_ = 480;

  int16_t* const in_data = in.mutable_data();
  for (size_t k = 0; k < in.samples_per_channel_; ++k) {
    in_data[k] = 1;
  }

  ReMixFrame(in, 6, &out);
  EXPECT_EQ(6 * 480u, out.size());

  std::vector<int16_t> expected_output(in.samples_per_channel_ * 6, 0);
  for (size_t k = 0; k < in.samples_per_channel_; ++k) {
    expected_output[6 * k] = 1;
    expected_output[6 * k + 1] = 1;
  }

  EXPECT_THAT(out, ElementsAreArray(expected_output));
}

TEST(AcmRemixing, RemixStereoFrameToMono) {
  std::vector<int16_t> out(480, 0);
  AudioFrame in;
  in.num_channels_ = 2;
  in.samples_per_channel_ = 480;

  int16_t* const in_data = in.mutable_data();
  for (size_t k = 0; k < in.samples_per_channel_; ++k) {
    in_data[2 * k] = 2;
    in_data[2 * k + 1] = 0;
  }

  ReMixFrame(in, 1, &out);
  EXPECT_EQ(480u, out.size());

  EXPECT_THAT(out, AllOf(SizeIs(in.samples_per_channel_), Each(1)));
}

TEST(AcmRemixing, RemixMonoFrameToStereo) {
  std::vector<int16_t> out(480, 0);
  AudioFrame in;
  in.num_channels_ = 1;
  in.samples_per_channel_ = 480;

  int16_t* const in_data = in.mutable_data();
  for (size_t k = 0; k < in.samples_per_channel_; ++k) {
    in_data[k] = 1;
  }

  ReMixFrame(in, 2, &out);
  EXPECT_EQ(960u, out.size());

  EXPECT_THAT(out, AllOf(SizeIs(2 * in.samples_per_channel_), Each(1)));
}

TEST(AcmRemixing, Remix3ChannelFrameToStereo) {
  std::vector<int16_t> out(480, 0);
  AudioFrame in;
  in.num_channels_ = 3;
  in.samples_per_channel_ = 480;

  int16_t* const in_data = in.mutable_data();
  for (size_t k = 0; k < in.samples_per_channel_; ++k) {
    for (size_t j = 0; j < 3; ++j) {
      in_data[3 * k + j] = j;
    }
  }

  ReMixFrame(in, 2, &out);
  EXPECT_EQ(2 * 480u, out.size());

  std::vector<int16_t> expected_output(in.samples_per_channel_ * 2);
  for (size_t k = 0; k < in.samples_per_channel_; ++k) {
    for (size_t j = 0; j < 2; ++j) {
      expected_output[2 * k + j] = static_cast<int>(j);
    }
  }

  EXPECT_THAT(out, ElementsAreArray(expected_output));
}

}  // namespace webrtc
