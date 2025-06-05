/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/percentile_filter.h"

#include <stdlib.h>

#include <array>
#include <climits>
#include <cstdint>
#include <random>

#include "absl/algorithm/container.h"
#include "test/gtest.h"

namespace webrtc {

class PercentileFilterTest : public ::testing::TestWithParam<float> {
 public:
  PercentileFilterTest() : filter_(GetParam()) {
    // Make sure the tests are deterministic by seeding with a constant.
    srand(42);
  }

  PercentileFilterTest(const PercentileFilterTest&) = delete;
  PercentileFilterTest& operator=(const PercentileFilterTest&) = delete;

 protected:
  PercentileFilter<int64_t> filter_;
};

INSTANTIATE_TEST_SUITE_P(PercentileFilterTests,
                         PercentileFilterTest,
                         ::testing::Values(0.0f, 0.1f, 0.5f, 0.9f, 1.0f));

TEST(PercentileFilterTest, MinFilter) {
  PercentileFilter<int64_t> filter(0.0f);
  filter.Insert(4);
  EXPECT_EQ(4, filter.GetPercentileValue());
  filter.Insert(3);
  EXPECT_EQ(3, filter.GetPercentileValue());
}

TEST(PercentileFilterTest, MaxFilter) {
  PercentileFilter<int64_t> filter(1.0f);
  filter.Insert(3);
  EXPECT_EQ(3, filter.GetPercentileValue());
  filter.Insert(4);
  EXPECT_EQ(4, filter.GetPercentileValue());
}

TEST(PercentileFilterTest, MedianFilterDouble) {
  PercentileFilter<double> filter(0.5f);
  filter.Insert(2.71828);
  filter.Insert(3.14159);
  filter.Insert(1.41421);
  EXPECT_EQ(2.71828, filter.GetPercentileValue());
}

TEST(PercentileFilterTest, MedianFilterInt) {
  PercentileFilter<int> filter(0.5f);
  filter.Insert(INT_MIN);
  filter.Insert(1);
  filter.Insert(2);
  EXPECT_EQ(1, filter.GetPercentileValue());
  filter.Insert(INT_MAX);
  filter.Erase(INT_MIN);
  EXPECT_EQ(2, filter.GetPercentileValue());
}

TEST(PercentileFilterTest, MedianFilterUnsigned) {
  PercentileFilter<unsigned> filter(0.5f);
  filter.Insert(UINT_MAX);
  filter.Insert(2u);
  filter.Insert(1u);
  EXPECT_EQ(2u, filter.GetPercentileValue());
  filter.Insert(0u);
  filter.Erase(UINT_MAX);
  EXPECT_EQ(1u, filter.GetPercentileValue());
}

TEST_P(PercentileFilterTest, EmptyFilter) {
  EXPECT_EQ(0, filter_.GetPercentileValue());
  filter_.Insert(3);
  bool success = filter_.Erase(3);
  EXPECT_TRUE(success);
  EXPECT_EQ(0, filter_.GetPercentileValue());
}

TEST_P(PercentileFilterTest, EraseNonExistingElement) {
  bool success = filter_.Erase(3);
  EXPECT_FALSE(success);
  EXPECT_EQ(0, filter_.GetPercentileValue());
  filter_.Insert(4);
  success = filter_.Erase(3);
  EXPECT_FALSE(success);
  EXPECT_EQ(4, filter_.GetPercentileValue());
}

TEST_P(PercentileFilterTest, DuplicateElements) {
  filter_.Insert(3);
  filter_.Insert(3);
  filter_.Erase(3);
  EXPECT_EQ(3, filter_.GetPercentileValue());
}

TEST_P(PercentileFilterTest, InsertAndEraseTenValuesInRandomOrder) {
  std::array<int64_t, 10> zero_to_nine = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
  // The percentile value of the ten values above.
  const int64_t expected_value = static_cast<int64_t>(GetParam() * 9);

  // Insert two sets of `zero_to_nine` in random order.
  for (int i = 0; i < 2; ++i) {
    absl::c_shuffle(zero_to_nine, std::mt19937(std::random_device()()));
    for (int64_t value : zero_to_nine)
      filter_.Insert(value);
    // After inserting a full set of `zero_to_nine`, the percentile should
    // stay constant.
    EXPECT_EQ(expected_value, filter_.GetPercentileValue());
  }

  // Insert and erase sets of `zero_to_nine` in random order a few times.
  for (int i = 0; i < 3; ++i) {
    absl::c_shuffle(zero_to_nine, std::mt19937(std::random_device()()));
    for (int64_t value : zero_to_nine)
      filter_.Erase(value);
    EXPECT_EQ(expected_value, filter_.GetPercentileValue());
    absl::c_shuffle(zero_to_nine, std::mt19937(std::random_device()()));
    for (int64_t value : zero_to_nine)
      filter_.Insert(value);
    EXPECT_EQ(expected_value, filter_.GetPercentileValue());
  }
}

}  // namespace webrtc
