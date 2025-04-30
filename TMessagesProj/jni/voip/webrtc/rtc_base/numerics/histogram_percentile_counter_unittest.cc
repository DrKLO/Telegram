/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/histogram_percentile_counter.h"

#include <cstdint>
#include <utility>
#include <vector>

#include "test/gtest.h"

TEST(HistogramPercentileCounterTest, ReturnsCorrectPercentiles) {
  rtc::HistogramPercentileCounter counter(10);
  const std::vector<int> kTestValues = {1,  2,  3,  4,  5,  6,  7,  8,  9,  10,
                                        11, 12, 13, 14, 15, 16, 17, 18, 19, 20};

  EXPECT_FALSE(counter.GetPercentile(0.5f));
  // Pairs of {fraction, percentile value} computed by hand
  // for `kTestValues`.
  const std::vector<std::pair<float, uint32_t>> kTestPercentiles = {
      {0.0f, 1},   {0.01f, 1},  {0.5f, 10}, {0.9f, 18},
      {0.95f, 19}, {0.99f, 20}, {1.0f, 20}};
  for (int value : kTestValues) {
    counter.Add(value);
  }
  for (const auto& test_percentile : kTestPercentiles) {
    EXPECT_EQ(test_percentile.second,
              counter.GetPercentile(test_percentile.first).value_or(0));
  }
}

TEST(HistogramPercentileCounterTest, HandlesEmptySequence) {
  rtc::HistogramPercentileCounter counter(10);
  EXPECT_FALSE(counter.GetPercentile(0.5f));
  counter.Add(1u);
  EXPECT_TRUE(counter.GetPercentile(0.5f));
}
