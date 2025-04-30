/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/test/videocodec_test_stats_impl.h"

#include <vector>

#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {
namespace test {

using FrameStatistics = VideoCodecTestStatsImpl::FrameStatistics;

namespace {

const size_t kTimestamp = 12345;

using ::testing::AllOf;
using ::testing::Contains;
using ::testing::Field;

}  // namespace

TEST(StatsTest, AddAndGetFrame) {
  VideoCodecTestStatsImpl stats;
  stats.AddFrame(FrameStatistics(0, kTimestamp, 0));
  FrameStatistics* frame_stat = stats.GetFrame(0u, 0);
  EXPECT_EQ(0u, frame_stat->frame_number);
  EXPECT_EQ(kTimestamp, frame_stat->rtp_timestamp);
}

TEST(StatsTest, GetOrAddFrame_noFrame_createsNewFrameStat) {
  VideoCodecTestStatsImpl stats;
  stats.GetOrAddFrame(kTimestamp, 0);
  FrameStatistics* frame_stat = stats.GetFrameWithTimestamp(kTimestamp, 0);
  EXPECT_EQ(kTimestamp, frame_stat->rtp_timestamp);
}

TEST(StatsTest, GetOrAddFrame_frameExists_returnsExistingFrameStat) {
  VideoCodecTestStatsImpl stats;
  stats.AddFrame(FrameStatistics(0, kTimestamp, 0));
  FrameStatistics* frame_stat1 = stats.GetFrameWithTimestamp(kTimestamp, 0);
  FrameStatistics* frame_stat2 = stats.GetOrAddFrame(kTimestamp, 0);
  EXPECT_EQ(frame_stat1, frame_stat2);
}

TEST(StatsTest, AddAndGetFrames) {
  VideoCodecTestStatsImpl stats;
  const size_t kNumFrames = 1000;
  for (size_t i = 0; i < kNumFrames; ++i) {
    stats.AddFrame(FrameStatistics(i, kTimestamp + i, 0));
    FrameStatistics* frame_stat = stats.GetFrame(i, 0);
    EXPECT_EQ(i, frame_stat->frame_number);
    EXPECT_EQ(kTimestamp + i, frame_stat->rtp_timestamp);
  }
  EXPECT_EQ(kNumFrames, stats.Size(0));
  // Get frame.
  size_t i = 22;
  FrameStatistics* frame_stat = stats.GetFrameWithTimestamp(kTimestamp + i, 0);
  EXPECT_EQ(i, frame_stat->frame_number);
  EXPECT_EQ(kTimestamp + i, frame_stat->rtp_timestamp);
}

TEST(StatsTest, AddFrameLayering) {
  VideoCodecTestStatsImpl stats;
  for (size_t i = 0; i < 3; ++i) {
    stats.AddFrame(FrameStatistics(0, kTimestamp + i, i));
    FrameStatistics* frame_stat = stats.GetFrame(0u, i);
    EXPECT_EQ(0u, frame_stat->frame_number);
    EXPECT_EQ(kTimestamp, frame_stat->rtp_timestamp - i);
    EXPECT_EQ(1u, stats.Size(i));
  }
}

TEST(StatsTest, GetFrameStatistics) {
  VideoCodecTestStatsImpl stats;

  stats.AddFrame(FrameStatistics(0, kTimestamp, 0));
  stats.AddFrame(FrameStatistics(0, kTimestamp, 1));
  stats.AddFrame(FrameStatistics(1, kTimestamp + 3000, 0));
  stats.AddFrame(FrameStatistics(1, kTimestamp + 3000, 1));

  const std::vector<FrameStatistics> frame_stats = stats.GetFrameStatistics();

  auto field_matcher = [](size_t frame_number, size_t spatial_idx) {
    return AllOf(Field(&FrameStatistics::frame_number, frame_number),
                 Field(&FrameStatistics::spatial_idx, spatial_idx));
  };
  EXPECT_THAT(frame_stats, Contains(field_matcher(0, 0)));
  EXPECT_THAT(frame_stats, Contains(field_matcher(0, 1)));
  EXPECT_THAT(frame_stats, Contains(field_matcher(1, 0)));
  EXPECT_THAT(frame_stats, Contains(field_matcher(1, 1)));
}

}  // namespace test
}  // namespace webrtc
