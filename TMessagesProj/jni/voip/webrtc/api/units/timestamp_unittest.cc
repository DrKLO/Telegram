/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/units/timestamp.h"

#include <limits>

#include "test/gtest.h"

namespace webrtc {
namespace test {
TEST(TimestampTest, ConstExpr) {
  constexpr int64_t kValue = 12345;
  constexpr Timestamp kTimestampInf = Timestamp::PlusInfinity();
  static_assert(kTimestampInf.IsInfinite(), "");
  static_assert(kTimestampInf.ms_or(-1) == -1, "");

  constexpr Timestamp kTimestampSeconds = Timestamp::Seconds(kValue);
  constexpr Timestamp kTimestampMs = Timestamp::Millis(kValue);
  constexpr Timestamp kTimestampUs = Timestamp::Micros(kValue);

  static_assert(kTimestampSeconds.seconds_or(0) == kValue, "");
  static_assert(kTimestampMs.ms_or(0) == kValue, "");
  static_assert(kTimestampUs.us_or(0) == kValue, "");

  static_assert(kTimestampMs > kTimestampUs, "");

  EXPECT_EQ(kTimestampSeconds.seconds(), kValue);
  EXPECT_EQ(kTimestampMs.ms(), kValue);
  EXPECT_EQ(kTimestampUs.us(), kValue);
}

TEST(TimestampTest, GetBackSameValues) {
  const int64_t kValue = 499;
  EXPECT_EQ(Timestamp::Millis(kValue).ms(), kValue);
  EXPECT_EQ(Timestamp::Micros(kValue).us(), kValue);
  EXPECT_EQ(Timestamp::Seconds(kValue).seconds(), kValue);
}

TEST(TimestampTest, GetDifferentPrefix) {
  const int64_t kValue = 3000000;
  EXPECT_EQ(Timestamp::Micros(kValue).seconds(), kValue / 1000000);
  EXPECT_EQ(Timestamp::Millis(kValue).seconds(), kValue / 1000);
  EXPECT_EQ(Timestamp::Micros(kValue).ms(), kValue / 1000);

  EXPECT_EQ(Timestamp::Millis(kValue).us(), kValue * 1000);
  EXPECT_EQ(Timestamp::Seconds(kValue).ms(), kValue * 1000);
  EXPECT_EQ(Timestamp::Seconds(kValue).us(), kValue * 1000000);
}

TEST(TimestampTest, IdentityChecks) {
  const int64_t kValue = 3000;

  EXPECT_TRUE(Timestamp::PlusInfinity().IsInfinite());
  EXPECT_TRUE(Timestamp::MinusInfinity().IsInfinite());
  EXPECT_FALSE(Timestamp::Millis(kValue).IsInfinite());

  EXPECT_FALSE(Timestamp::PlusInfinity().IsFinite());
  EXPECT_FALSE(Timestamp::MinusInfinity().IsFinite());
  EXPECT_TRUE(Timestamp::Millis(kValue).IsFinite());

  EXPECT_TRUE(Timestamp::PlusInfinity().IsPlusInfinity());
  EXPECT_FALSE(Timestamp::MinusInfinity().IsPlusInfinity());

  EXPECT_TRUE(Timestamp::MinusInfinity().IsMinusInfinity());
  EXPECT_FALSE(Timestamp::PlusInfinity().IsMinusInfinity());
}

TEST(TimestampTest, ComparisonOperators) {
  const int64_t kSmall = 450;
  const int64_t kLarge = 451;

  EXPECT_EQ(Timestamp::PlusInfinity(), Timestamp::PlusInfinity());
  EXPECT_GE(Timestamp::PlusInfinity(), Timestamp::PlusInfinity());
  EXPECT_GT(Timestamp::PlusInfinity(), Timestamp::Millis(kLarge));
  EXPECT_EQ(Timestamp::Millis(kSmall), Timestamp::Millis(kSmall));
  EXPECT_LE(Timestamp::Millis(kSmall), Timestamp::Millis(kSmall));
  EXPECT_GE(Timestamp::Millis(kSmall), Timestamp::Millis(kSmall));
  EXPECT_NE(Timestamp::Millis(kSmall), Timestamp::Millis(kLarge));
  EXPECT_LE(Timestamp::Millis(kSmall), Timestamp::Millis(kLarge));
  EXPECT_LT(Timestamp::Millis(kSmall), Timestamp::Millis(kLarge));
  EXPECT_GE(Timestamp::Millis(kLarge), Timestamp::Millis(kSmall));
  EXPECT_GT(Timestamp::Millis(kLarge), Timestamp::Millis(kSmall));
}

TEST(TimestampTest, CanBeInititializedFromLargeInt) {
  const int kMaxInt = std::numeric_limits<int>::max();
  EXPECT_EQ(Timestamp::Seconds(kMaxInt).us(),
            static_cast<int64_t>(kMaxInt) * 1000000);
  EXPECT_EQ(Timestamp::Millis(kMaxInt).us(),
            static_cast<int64_t>(kMaxInt) * 1000);
}

TEST(TimestampTest, ConvertsToAndFromDouble) {
  const int64_t kMicros = 17017;
  const double kMicrosDouble = kMicros;
  const double kMillisDouble = kMicros * 1e-3;
  const double kSecondsDouble = kMillisDouble * 1e-3;

  EXPECT_EQ(Timestamp::Micros(kMicros).seconds<double>(), kSecondsDouble);
  EXPECT_EQ(Timestamp::Seconds(kSecondsDouble).us(), kMicros);

  EXPECT_EQ(Timestamp::Micros(kMicros).ms<double>(), kMillisDouble);
  EXPECT_EQ(Timestamp::Millis(kMillisDouble).us(), kMicros);

  EXPECT_EQ(Timestamp::Micros(kMicros).us<double>(), kMicrosDouble);
  EXPECT_EQ(Timestamp::Micros(kMicrosDouble).us(), kMicros);

  const double kPlusInfinity = std::numeric_limits<double>::infinity();
  const double kMinusInfinity = -kPlusInfinity;

  EXPECT_EQ(Timestamp::PlusInfinity().seconds<double>(), kPlusInfinity);
  EXPECT_EQ(Timestamp::MinusInfinity().seconds<double>(), kMinusInfinity);
  EXPECT_EQ(Timestamp::PlusInfinity().ms<double>(), kPlusInfinity);
  EXPECT_EQ(Timestamp::MinusInfinity().ms<double>(), kMinusInfinity);
  EXPECT_EQ(Timestamp::PlusInfinity().us<double>(), kPlusInfinity);
  EXPECT_EQ(Timestamp::MinusInfinity().us<double>(), kMinusInfinity);

  EXPECT_TRUE(Timestamp::Seconds(kPlusInfinity).IsPlusInfinity());
  EXPECT_TRUE(Timestamp::Seconds(kMinusInfinity).IsMinusInfinity());
  EXPECT_TRUE(Timestamp::Millis(kPlusInfinity).IsPlusInfinity());
  EXPECT_TRUE(Timestamp::Millis(kMinusInfinity).IsMinusInfinity());
  EXPECT_TRUE(Timestamp::Micros(kPlusInfinity).IsPlusInfinity());
  EXPECT_TRUE(Timestamp::Micros(kMinusInfinity).IsMinusInfinity());
}

TEST(UnitConversionTest, TimestampAndTimeDeltaMath) {
  const int64_t kValueA = 267;
  const int64_t kValueB = 450;
  const Timestamp time_a = Timestamp::Millis(kValueA);
  const Timestamp time_b = Timestamp::Millis(kValueB);
  const TimeDelta delta_a = TimeDelta::Millis(kValueA);
  const TimeDelta delta_b = TimeDelta::Millis(kValueB);

  EXPECT_EQ((time_a - time_b), TimeDelta::Millis(kValueA - kValueB));
  EXPECT_EQ((time_b - delta_a), Timestamp::Millis(kValueB - kValueA));
  EXPECT_EQ((time_b + delta_a), Timestamp::Millis(kValueB + kValueA));

  Timestamp mutable_time = time_a;
  mutable_time += delta_b;
  EXPECT_EQ(mutable_time, time_a + delta_b);
  mutable_time -= delta_b;
  EXPECT_EQ(mutable_time, time_a);
}

TEST(UnitConversionTest, InfinityOperations) {
  const int64_t kValue = 267;
  const Timestamp finite_time = Timestamp::Millis(kValue);
  const TimeDelta finite_delta = TimeDelta::Millis(kValue);
  EXPECT_TRUE((Timestamp::PlusInfinity() + finite_delta).IsInfinite());
  EXPECT_TRUE((Timestamp::PlusInfinity() - finite_delta).IsInfinite());
  EXPECT_TRUE((finite_time + TimeDelta::PlusInfinity()).IsInfinite());
  EXPECT_TRUE((finite_time - TimeDelta::MinusInfinity()).IsInfinite());
}
}  // namespace test
}  // namespace webrtc
