/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/event_based_exponential_moving_average.h"

#include <cmath>

#include "test/gtest.h"

namespace {

constexpr int kHalfTime = 500;
constexpr double kError = 0.1;

}  // namespace

namespace rtc {

TEST(EventBasedExponentialMovingAverageTest, NoValue) {
  EventBasedExponentialMovingAverage average(kHalfTime);

  EXPECT_TRUE(std::isnan(average.GetAverage()));
  EXPECT_EQ(std::numeric_limits<double>::infinity(), average.GetVariance());
  EXPECT_EQ(std::numeric_limits<double>::infinity(),
            average.GetConfidenceInterval());
}

TEST(EventBasedExponentialMovingAverageTest, FirstValue) {
  EventBasedExponentialMovingAverage average(kHalfTime);

  int64_t time = 23;
  constexpr int value = 1000;
  average.AddSample(time, value);
  EXPECT_NEAR(value, average.GetAverage(), kError);
  EXPECT_EQ(std::numeric_limits<double>::infinity(), average.GetVariance());
  EXPECT_EQ(std::numeric_limits<double>::infinity(),
            average.GetConfidenceInterval());
}

TEST(EventBasedExponentialMovingAverageTest, Half) {
  EventBasedExponentialMovingAverage average(kHalfTime);

  int64_t time = 23;
  constexpr int value = 1000;
  average.AddSample(time, value);
  average.AddSample(time + kHalfTime, 0);
  EXPECT_NEAR(666.7, average.GetAverage(), kError);
  EXPECT_NEAR(1000000, average.GetVariance(), kError);
  EXPECT_NEAR(1460.9, average.GetConfidenceInterval(), kError);
}

TEST(EventBasedExponentialMovingAverageTest, Same) {
  EventBasedExponentialMovingAverage average(kHalfTime);

  int64_t time = 23;
  constexpr int value = 1000;
  average.AddSample(time, value);
  average.AddSample(time + kHalfTime, value);
  EXPECT_NEAR(value, average.GetAverage(), kError);
  EXPECT_NEAR(0, average.GetVariance(), kError);
  EXPECT_NEAR(0, average.GetConfidenceInterval(), kError);
}

TEST(EventBasedExponentialMovingAverageTest, Almost100) {
  EventBasedExponentialMovingAverage average(kHalfTime);

  int64_t time = 23;
  constexpr int value = 100;
  average.AddSample(time + 0 * kHalfTime, value - 10);
  average.AddSample(time + 1 * kHalfTime, value + 10);
  average.AddSample(time + 2 * kHalfTime, value - 15);
  average.AddSample(time + 3 * kHalfTime, value + 15);
  EXPECT_NEAR(100.2, average.GetAverage(), kError);
  EXPECT_NEAR(372.6, average.GetVariance(), kError);
  EXPECT_NEAR(19.7, average.GetConfidenceInterval(), kError);  // 100 +/- 20

  average.AddSample(time + 4 * kHalfTime, value);
  average.AddSample(time + 5 * kHalfTime, value);
  average.AddSample(time + 6 * kHalfTime, value);
  average.AddSample(time + 7 * kHalfTime, value);
  EXPECT_NEAR(100.0, average.GetAverage(), kError);
  EXPECT_NEAR(73.6, average.GetVariance(), kError);
  EXPECT_NEAR(7.6, average.GetConfidenceInterval(), kError);  // 100 +/- 7
}

// Test that getting a value at X and another at X+1
// is almost the same as getting another at X and a value at X+1.
TEST(EventBasedExponentialMovingAverageTest, AlmostSameTime) {
  int64_t time = 23;
  constexpr int value = 100;

  {
    EventBasedExponentialMovingAverage average(kHalfTime);
    average.AddSample(time + 0, value);
    average.AddSample(time + 1, 0);
    EXPECT_NEAR(50, average.GetAverage(), kError);
    EXPECT_NEAR(10000, average.GetVariance(), kError);
    EXPECT_NEAR(138.6, average.GetConfidenceInterval(),
                kError);  // 50 +/- 138.6
  }

  {
    EventBasedExponentialMovingAverage average(kHalfTime);
    average.AddSample(time + 0, 0);
    average.AddSample(time + 1, 100);
    EXPECT_NEAR(50, average.GetAverage(), kError);
    EXPECT_NEAR(10000, average.GetVariance(), kError);
    EXPECT_NEAR(138.6, average.GetConfidenceInterval(),
                kError);  // 50 +/- 138.6
  }
}

// This test shows behavior of estimator with a half_time of 100.
// It is unclear if these set of observations are representative
// of any real world scenarios.
TEST(EventBasedExponentialMovingAverageTest, NonUniformSamplesHalftime100) {
  int64_t time = 23;
  constexpr int value = 100;

  {
    // The observations at 100 and 101, are significantly close in
    // time that the estimator returns approx. the average.
    EventBasedExponentialMovingAverage average(100);
    average.AddSample(time + 0, value);
    average.AddSample(time + 100, value);
    average.AddSample(time + 101, 0);
    EXPECT_NEAR(50.2, average.GetAverage(), kError);
    EXPECT_NEAR(86.2, average.GetConfidenceInterval(), kError);  // 50 +/- 86
  }

  {
    EventBasedExponentialMovingAverage average(100);
    average.AddSample(time + 0, value);
    average.AddSample(time + 1, value);
    average.AddSample(time + 100, 0);
    EXPECT_NEAR(66.5, average.GetAverage(), kError);
    EXPECT_NEAR(65.4, average.GetConfidenceInterval(), kError);  // 66 +/- 65
  }

  {
    EventBasedExponentialMovingAverage average(100);
    for (int i = 0; i < 10; i++) {
      average.AddSample(time + i, value);
    }
    average.AddSample(time + 100, 0);
    EXPECT_NEAR(65.3, average.GetAverage(), kError);
    EXPECT_NEAR(59.1, average.GetConfidenceInterval(), kError);  // 55 +/- 59
  }

  {
    EventBasedExponentialMovingAverage average(100);
    average.AddSample(time + 0, 100);
    for (int i = 90; i <= 100; i++) {
      average.AddSample(time + i, 0);
    }
    EXPECT_NEAR(0.05, average.GetAverage(), kError);
    EXPECT_NEAR(4.9, average.GetConfidenceInterval(), kError);  // 0 +/- 5
  }
}

TEST(EventBasedExponentialMovingAverageTest, Reset) {
  constexpr int64_t time = 23;
  constexpr int value = 100;

  EventBasedExponentialMovingAverage average(100);
  EXPECT_TRUE(std::isnan(average.GetAverage()));
  EXPECT_EQ(std::numeric_limits<double>::infinity(), average.GetVariance());
  EXPECT_EQ(std::numeric_limits<double>::infinity(),
            average.GetConfidenceInterval());

  average.AddSample(time + 0, value);
  average.AddSample(time + 100, value);
  average.AddSample(time + 101, 0);
  EXPECT_FALSE(std::isnan(average.GetAverage()));

  average.Reset();
  EXPECT_TRUE(std::isnan(average.GetAverage()));
  EXPECT_EQ(std::numeric_limits<double>::infinity(), average.GetVariance());
  EXPECT_EQ(std::numeric_limits<double>::infinity(),
            average.GetConfidenceInterval());
}

// Test that SetHalfTime modifies behavior and resets average.
TEST(EventBasedExponentialMovingAverageTest, SetHalfTime) {
  constexpr int64_t time = 23;
  constexpr int value = 100;

  EventBasedExponentialMovingAverage average(100);

  average.AddSample(time + 0, value);
  average.AddSample(time + 100, 0);
  EXPECT_NEAR(66.7, average.GetAverage(), kError);

  average.SetHalfTime(1000);
  EXPECT_TRUE(std::isnan(average.GetAverage()));
  EXPECT_EQ(std::numeric_limits<double>::infinity(), average.GetVariance());
  EXPECT_EQ(std::numeric_limits<double>::infinity(),
            average.GetConfidenceInterval());

  average.AddSample(time + 0, value);
  average.AddSample(time + 100, 0);
  EXPECT_NEAR(51.7, average.GetAverage(), kError);
}

TEST(EventBasedExponentialMovingAverageTest, SimultaneousSamples) {
  constexpr int64_t time = 23;
  constexpr int value = 100;

  EventBasedExponentialMovingAverage average(100);

  average.AddSample(time, value);
  // This should really NOT be supported,
  // i.e 2 samples with same timestamp.
  // But there are tests running with simulated clock
  // that produce this.
  // TODO(webrtc:11140) : Fix those tests and remove this!
  average.AddSample(time, value);
}

}  // namespace rtc
