/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/exp_filter.h"

#include <cmath>

#include "test/gtest.h"

namespace rtc {

TEST(ExpFilterTest, FirstTimeOutputEqualInput) {
  // No max value defined.
  ExpFilter filter = ExpFilter(0.9f);
  filter.Apply(100.0f, 10.0f);

  // First time, first argument no effect.
  double value = 10.0f;
  EXPECT_FLOAT_EQ(value, filter.filtered());
}

TEST(ExpFilterTest, SecondTime) {
  float value;

  ExpFilter filter = ExpFilter(0.9f);
  filter.Apply(100.0f, 10.0f);

  // First time, first argument no effect.
  value = 10.0f;

  filter.Apply(10.0f, 20.0f);
  float alpha = std::pow(0.9f, 10.0f);
  value = alpha * value + (1.0f - alpha) * 20.0f;
  EXPECT_FLOAT_EQ(value, filter.filtered());
}

TEST(ExpFilterTest, Reset) {
  ExpFilter filter = ExpFilter(0.9f);
  filter.Apply(100.0f, 10.0f);

  filter.Reset(0.8f);
  filter.Apply(100.0f, 1.0f);

  // Become first time after a reset.
  double value = 1.0f;
  EXPECT_FLOAT_EQ(value, filter.filtered());
}

TEST(ExpfilterTest, OutputLimitedByMax) {
  double value;

  // Max value defined.
  ExpFilter filter = ExpFilter(0.9f, 1.0f);
  filter.Apply(100.0f, 10.0f);

  // Limited to max value.
  value = 1.0f;
  EXPECT_EQ(value, filter.filtered());

  filter.Apply(1.0f, 0.0f);
  value = 0.9f * value;
  EXPECT_FLOAT_EQ(value, filter.filtered());
}

}  // namespace rtc
