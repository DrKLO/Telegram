/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/histogram.h"

#include <cmath>

#include "test/gtest.h"

namespace webrtc {

TEST(HistogramTest, Initialization) {
  Histogram histogram(65, 32440);
  histogram.Reset();
  const auto& buckets = histogram.buckets();
  double sum = 0.0;
  for (size_t i = 0; i < buckets.size(); i++) {
    EXPECT_NEAR(ldexp(std::pow(0.5, static_cast<int>(i + 1)), 30), buckets[i],
                65537);
    // Tolerance 65537 in Q30 corresponds to a delta of approximately 0.00006.
    sum += buckets[i];
  }
  EXPECT_EQ(1 << 30, static_cast<int>(sum));  // Should be 1 in Q30.
}

TEST(HistogramTest, Add) {
  Histogram histogram(10, 32440);
  histogram.Reset();
  const std::vector<int> before = histogram.buckets();
  const int index = 5;
  histogram.Add(index);
  const std::vector<int> after = histogram.buckets();
  EXPECT_GT(after[index], before[index]);
  int sum = 0;
  for (int bucket : after) {
    sum += bucket;
  }
  EXPECT_EQ(1 << 30, sum);
}

TEST(HistogramTest, ForgetFactor) {
  Histogram histogram(10, 32440);
  histogram.Reset();
  const std::vector<int> before = histogram.buckets();
  const int index = 4;
  histogram.Add(index);
  const std::vector<int> after = histogram.buckets();
  for (int i = 0; i < histogram.NumBuckets(); ++i) {
    if (i != index) {
      EXPECT_LT(after[i], before[i]);
    }
  }
}

TEST(HistogramTest, ReachSteadyStateForgetFactor) {
  static constexpr int kSteadyStateForgetFactor = (1 << 15) * 0.9993;
  Histogram histogram(100, kSteadyStateForgetFactor, 1.0);
  histogram.Reset();
  int n = (1 << 15) / ((1 << 15) - kSteadyStateForgetFactor);
  for (int i = 0; i < n; ++i) {
    histogram.Add(0);
  }
  EXPECT_EQ(histogram.forget_factor_for_testing(), kSteadyStateForgetFactor);
}

}  // namespace webrtc
