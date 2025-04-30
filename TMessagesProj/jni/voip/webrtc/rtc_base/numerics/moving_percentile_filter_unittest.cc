/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/moving_percentile_filter.h"

#include <stdint.h>

#include <algorithm>

#include "test/gtest.h"

namespace webrtc {

// 25th percentile can be exactly found with a window of length 4.
TEST(MovingPercentileFilter, Percentile25ReturnsMovingPercentile25WithWindow4) {
  MovingPercentileFilter<int> perc25(0.25f, 4);
  const int64_t kSamples[10] = {1, 2, 3, 4, 4, 4, 5, 6, 7, 8};
  const int64_t kExpectedFilteredValues[10] = {1, 1, 1, 1, 2, 3, 4, 4, 4, 5};
  for (size_t i = 0; i < 10; ++i) {
    perc25.Insert(kSamples[i]);
    EXPECT_EQ(kExpectedFilteredValues[i], perc25.GetFilteredValue());
    EXPECT_EQ(std::min<size_t>(i + 1, 4), perc25.GetNumberOfSamplesStored());
  }
}

// 90th percentile becomes the 67th percentile with a window of length 4.
TEST(MovingPercentileFilter, Percentile90ReturnsMovingPercentile67WithWindow4) {
  MovingPercentileFilter<int> perc67(0.67f, 4);
  MovingPercentileFilter<int> perc90(0.9f, 4);
  const int64_t kSamples[8] = {1, 10, 1, 9, 1, 10, 1, 8};
  const int64_t kExpectedFilteredValues[9] = {1, 1, 1, 9, 9, 9, 9, 8};
  for (size_t i = 0; i < 8; ++i) {
    perc67.Insert(kSamples[i]);
    perc90.Insert(kSamples[i]);
    EXPECT_EQ(kExpectedFilteredValues[i], perc67.GetFilteredValue());
    EXPECT_EQ(kExpectedFilteredValues[i], perc90.GetFilteredValue());
  }
}

TEST(MovingMedianFilterTest, ProcessesNoSamples) {
  MovingMedianFilter<int> filter(2);
  EXPECT_EQ(0, filter.GetFilteredValue());
  EXPECT_EQ(0u, filter.GetNumberOfSamplesStored());
}

TEST(MovingMedianFilterTest, ReturnsMovingMedianWindow5) {
  MovingMedianFilter<int> filter(5);
  const int64_t kSamples[5] = {1, 5, 2, 3, 4};
  const int64_t kExpectedFilteredValues[5] = {1, 1, 2, 2, 3};
  for (size_t i = 0; i < 5; ++i) {
    filter.Insert(kSamples[i]);
    EXPECT_EQ(kExpectedFilteredValues[i], filter.GetFilteredValue());
    EXPECT_EQ(i + 1, filter.GetNumberOfSamplesStored());
  }
}

TEST(MovingMedianFilterTest, ReturnsMovingMedianWindow3) {
  MovingMedianFilter<int> filter(3);
  const int64_t kSamples[5] = {1, 5, 2, 3, 4};
  const int64_t kExpectedFilteredValues[5] = {1, 1, 2, 3, 3};
  for (int i = 0; i < 5; ++i) {
    filter.Insert(kSamples[i]);
    EXPECT_EQ(kExpectedFilteredValues[i], filter.GetFilteredValue());
    EXPECT_EQ(std::min<size_t>(i + 1, 3), filter.GetNumberOfSamplesStored());
  }
}

TEST(MovingMedianFilterTest, ReturnsMovingMedianWindow1) {
  MovingMedianFilter<int> filter(1);
  const int64_t kSamples[5] = {1, 5, 2, 3, 4};
  const int64_t kExpectedFilteredValues[5] = {1, 5, 2, 3, 4};
  for (int i = 0; i < 5; ++i) {
    filter.Insert(kSamples[i]);
    EXPECT_EQ(kExpectedFilteredValues[i], filter.GetFilteredValue());
    EXPECT_EQ(1u, filter.GetNumberOfSamplesStored());
  }
}

}  // namespace webrtc
