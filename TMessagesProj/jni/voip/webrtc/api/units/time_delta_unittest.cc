/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/units/time_delta.h"

#include <limits>

#include "test/gtest.h"

namespace webrtc {
namespace test {
TEST(TimeDeltaTest, ConstExpr) {
  constexpr int64_t kValue = -12345;
  constexpr TimeDelta kTimeDeltaZero = TimeDelta::Zero();
  constexpr TimeDelta kTimeDeltaPlusInf = TimeDelta::PlusInfinity();
  constexpr TimeDelta kTimeDeltaMinusInf = TimeDelta::MinusInfinity();
  static_assert(kTimeDeltaZero.IsZero(), "");
  static_assert(kTimeDeltaPlusInf.IsPlusInfinity(), "");
  static_assert(kTimeDeltaMinusInf.IsMinusInfinity(), "");
  static_assert(kTimeDeltaPlusInf.ms_or(-1) == -1, "");

  static_assert(kTimeDeltaPlusInf > kTimeDeltaZero, "");

  constexpr TimeDelta kTimeDeltaMinutes = TimeDelta::Minutes(kValue);
  constexpr TimeDelta kTimeDeltaSeconds = TimeDelta::Seconds(kValue);
  constexpr TimeDelta kTimeDeltaMs = TimeDelta::Millis(kValue);
  constexpr TimeDelta kTimeDeltaUs = TimeDelta::Micros(kValue);

  static_assert(kTimeDeltaMinutes.seconds_or(0) == kValue * 60, "");
  static_assert(kTimeDeltaSeconds.seconds_or(0) == kValue, "");
  static_assert(kTimeDeltaMs.ms_or(0) == kValue, "");
  static_assert(kTimeDeltaUs.us_or(0) == kValue, "");
}

TEST(TimeDeltaTest, GetBackSameValues) {
  const int64_t kValue = 499;
  for (int sign = -1; sign <= 1; ++sign) {
    int64_t value = kValue * sign;
    EXPECT_EQ(TimeDelta::Millis(value).ms(), value);
    EXPECT_EQ(TimeDelta::Micros(value).us(), value);
    EXPECT_EQ(TimeDelta::Seconds(value).seconds(), value);
    EXPECT_EQ(TimeDelta::Seconds(value).seconds(), value);
  }
  EXPECT_EQ(TimeDelta::Zero().us(), 0);
}

TEST(TimeDeltaTest, GetDifferentPrefix) {
  const int64_t kValue = 3000000;
  EXPECT_EQ(TimeDelta::Micros(kValue).seconds(), kValue / 1000000);
  EXPECT_EQ(TimeDelta::Millis(kValue).seconds(), kValue / 1000);
  EXPECT_EQ(TimeDelta::Micros(kValue).ms(), kValue / 1000);
  EXPECT_EQ(TimeDelta::Minutes(kValue / 60).seconds(), kValue);

  EXPECT_EQ(TimeDelta::Millis(kValue).us(), kValue * 1000);
  EXPECT_EQ(TimeDelta::Seconds(kValue).ms(), kValue * 1000);
  EXPECT_EQ(TimeDelta::Seconds(kValue).us(), kValue * 1000000);
  EXPECT_EQ(TimeDelta::Minutes(kValue / 60).seconds(), kValue);
}

TEST(TimeDeltaTest, IdentityChecks) {
  const int64_t kValue = 3000;
  EXPECT_TRUE(TimeDelta::Zero().IsZero());
  EXPECT_FALSE(TimeDelta::Millis(kValue).IsZero());

  EXPECT_TRUE(TimeDelta::PlusInfinity().IsInfinite());
  EXPECT_TRUE(TimeDelta::MinusInfinity().IsInfinite());
  EXPECT_FALSE(TimeDelta::Zero().IsInfinite());
  EXPECT_FALSE(TimeDelta::Millis(-kValue).IsInfinite());
  EXPECT_FALSE(TimeDelta::Millis(kValue).IsInfinite());

  EXPECT_FALSE(TimeDelta::PlusInfinity().IsFinite());
  EXPECT_FALSE(TimeDelta::MinusInfinity().IsFinite());
  EXPECT_TRUE(TimeDelta::Millis(-kValue).IsFinite());
  EXPECT_TRUE(TimeDelta::Millis(kValue).IsFinite());
  EXPECT_TRUE(TimeDelta::Zero().IsFinite());

  EXPECT_TRUE(TimeDelta::PlusInfinity().IsPlusInfinity());
  EXPECT_FALSE(TimeDelta::MinusInfinity().IsPlusInfinity());

  EXPECT_TRUE(TimeDelta::MinusInfinity().IsMinusInfinity());
  EXPECT_FALSE(TimeDelta::PlusInfinity().IsMinusInfinity());
}

TEST(TimeDeltaTest, ComparisonOperators) {
  const int64_t kSmall = 450;
  const int64_t kLarge = 451;
  const TimeDelta small = TimeDelta::Millis(kSmall);
  const TimeDelta large = TimeDelta::Millis(kLarge);

  EXPECT_EQ(TimeDelta::Zero(), TimeDelta::Millis(0));
  EXPECT_EQ(TimeDelta::PlusInfinity(), TimeDelta::PlusInfinity());
  EXPECT_EQ(small, TimeDelta::Millis(kSmall));
  EXPECT_LE(small, TimeDelta::Millis(kSmall));
  EXPECT_GE(small, TimeDelta::Millis(kSmall));
  EXPECT_NE(small, TimeDelta::Millis(kLarge));
  EXPECT_LE(small, TimeDelta::Millis(kLarge));
  EXPECT_LT(small, TimeDelta::Millis(kLarge));
  EXPECT_GE(large, TimeDelta::Millis(kSmall));
  EXPECT_GT(large, TimeDelta::Millis(kSmall));
  EXPECT_LT(TimeDelta::Zero(), small);
  EXPECT_GT(TimeDelta::Zero(), TimeDelta::Millis(-kSmall));
  EXPECT_GT(TimeDelta::Zero(), TimeDelta::Millis(-kSmall));

  EXPECT_GT(TimeDelta::PlusInfinity(), large);
  EXPECT_LT(TimeDelta::MinusInfinity(), TimeDelta::Zero());
}

TEST(TimeDeltaTest, Clamping) {
  const TimeDelta upper = TimeDelta::Millis(800);
  const TimeDelta lower = TimeDelta::Millis(100);
  const TimeDelta under = TimeDelta::Millis(100);
  const TimeDelta inside = TimeDelta::Millis(500);
  const TimeDelta over = TimeDelta::Millis(1000);
  EXPECT_EQ(under.Clamped(lower, upper), lower);
  EXPECT_EQ(inside.Clamped(lower, upper), inside);
  EXPECT_EQ(over.Clamped(lower, upper), upper);

  TimeDelta mutable_delta = lower;
  mutable_delta.Clamp(lower, upper);
  EXPECT_EQ(mutable_delta, lower);
  mutable_delta = inside;
  mutable_delta.Clamp(lower, upper);
  EXPECT_EQ(mutable_delta, inside);
  mutable_delta = over;
  mutable_delta.Clamp(lower, upper);
  EXPECT_EQ(mutable_delta, upper);
}

TEST(TimeDeltaTest, CanBeInititializedFromLargeInt) {
  const int kMaxInt = std::numeric_limits<int>::max();
  EXPECT_EQ(TimeDelta::Seconds(kMaxInt).us(),
            static_cast<int64_t>(kMaxInt) * 1000000);
  EXPECT_EQ(TimeDelta::Millis(kMaxInt).us(),
            static_cast<int64_t>(kMaxInt) * 1000);
}

TEST(TimeDeltaTest, ConvertsToAndFromDouble) {
  const int64_t kMicros = 17017;
  const double kNanosDouble = kMicros * 1e3;
  const double kMicrosDouble = kMicros;
  const double kMillisDouble = kMicros * 1e-3;
  const double kSecondsDouble = kMillisDouble * 1e-3;

  EXPECT_EQ(TimeDelta::Micros(kMicros).seconds<double>(), kSecondsDouble);
  EXPECT_EQ(TimeDelta::Seconds(kSecondsDouble).us(), kMicros);

  EXPECT_EQ(TimeDelta::Micros(kMicros).ms<double>(), kMillisDouble);
  EXPECT_EQ(TimeDelta::Millis(kMillisDouble).us(), kMicros);

  EXPECT_EQ(TimeDelta::Micros(kMicros).us<double>(), kMicrosDouble);
  EXPECT_EQ(TimeDelta::Micros(kMicrosDouble).us(), kMicros);

  EXPECT_NEAR(TimeDelta::Micros(kMicros).ns<double>(), kNanosDouble, 1);

  const double kPlusInfinity = std::numeric_limits<double>::infinity();
  const double kMinusInfinity = -kPlusInfinity;

  EXPECT_EQ(TimeDelta::PlusInfinity().seconds<double>(), kPlusInfinity);
  EXPECT_EQ(TimeDelta::MinusInfinity().seconds<double>(), kMinusInfinity);
  EXPECT_EQ(TimeDelta::PlusInfinity().ms<double>(), kPlusInfinity);
  EXPECT_EQ(TimeDelta::MinusInfinity().ms<double>(), kMinusInfinity);
  EXPECT_EQ(TimeDelta::PlusInfinity().us<double>(), kPlusInfinity);
  EXPECT_EQ(TimeDelta::MinusInfinity().us<double>(), kMinusInfinity);
  EXPECT_EQ(TimeDelta::PlusInfinity().ns<double>(), kPlusInfinity);
  EXPECT_EQ(TimeDelta::MinusInfinity().ns<double>(), kMinusInfinity);

  EXPECT_TRUE(TimeDelta::Seconds(kPlusInfinity).IsPlusInfinity());
  EXPECT_TRUE(TimeDelta::Seconds(kMinusInfinity).IsMinusInfinity());
  EXPECT_TRUE(TimeDelta::Millis(kPlusInfinity).IsPlusInfinity());
  EXPECT_TRUE(TimeDelta::Millis(kMinusInfinity).IsMinusInfinity());
  EXPECT_TRUE(TimeDelta::Micros(kPlusInfinity).IsPlusInfinity());
  EXPECT_TRUE(TimeDelta::Micros(kMinusInfinity).IsMinusInfinity());
}

TEST(TimeDeltaTest, MathOperations) {
  const int64_t kValueA = 267;
  const int64_t kValueB = 450;
  const TimeDelta delta_a = TimeDelta::Millis(kValueA);
  const TimeDelta delta_b = TimeDelta::Millis(kValueB);
  EXPECT_EQ((delta_a + delta_b).ms(), kValueA + kValueB);
  EXPECT_EQ((delta_a - delta_b).ms(), kValueA - kValueB);

  EXPECT_EQ((delta_b / 10).ms(), kValueB / 10);
  EXPECT_EQ(delta_b / delta_a, static_cast<double>(kValueB) / kValueA);

  EXPECT_EQ(TimeDelta::Micros(-kValueA).Abs().us(), kValueA);
  EXPECT_EQ(TimeDelta::Micros(kValueA).Abs().us(), kValueA);

  TimeDelta mutable_delta = TimeDelta::Millis(kValueA);
  mutable_delta += TimeDelta::Millis(kValueB);
  EXPECT_EQ(mutable_delta, TimeDelta::Millis(kValueA + kValueB));
  mutable_delta -= TimeDelta::Millis(kValueB);
  EXPECT_EQ(mutable_delta, TimeDelta::Millis(kValueA));
}

TEST(TimeDeltaTest, MultiplyByScalar) {
  const TimeDelta kValue = TimeDelta::Micros(267);
  const int64_t kInt64 = 450;
  const int32_t kInt32 = 123;
  const size_t kUnsignedInt = 125;
  const double kFloat = 123.0;

  EXPECT_EQ((kValue * kInt64).us(), kValue.us() * kInt64);
  EXPECT_EQ(kValue * kInt64, kInt64 * kValue);

  EXPECT_EQ((kValue * kInt32).us(), kValue.us() * kInt32);
  EXPECT_EQ(kValue * kInt32, kInt32 * kValue);

  EXPECT_EQ((kValue * kUnsignedInt).us(), kValue.us() * int64_t{kUnsignedInt});
  EXPECT_EQ(kValue * kUnsignedInt, kUnsignedInt * kValue);

  EXPECT_DOUBLE_EQ((kValue * kFloat).us(), kValue.us() * kFloat);
  EXPECT_EQ(kValue * kFloat, kFloat * kValue);
}

TEST(TimeDeltaTest, InfinityOperations) {
  const int64_t kValue = 267;
  const TimeDelta finite = TimeDelta::Millis(kValue);
  EXPECT_TRUE((TimeDelta::PlusInfinity() + finite).IsPlusInfinity());
  EXPECT_TRUE((TimeDelta::PlusInfinity() - finite).IsPlusInfinity());
  EXPECT_TRUE((finite + TimeDelta::PlusInfinity()).IsPlusInfinity());
  EXPECT_TRUE((finite - TimeDelta::MinusInfinity()).IsPlusInfinity());

  EXPECT_TRUE((TimeDelta::MinusInfinity() + finite).IsMinusInfinity());
  EXPECT_TRUE((TimeDelta::MinusInfinity() - finite).IsMinusInfinity());
  EXPECT_TRUE((finite + TimeDelta::MinusInfinity()).IsMinusInfinity());
  EXPECT_TRUE((finite - TimeDelta::PlusInfinity()).IsMinusInfinity());
}
}  // namespace test
}  // namespace webrtc
