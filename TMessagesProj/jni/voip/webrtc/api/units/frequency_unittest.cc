/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/units/frequency.h"

#include <limits>

#include "test/gtest.h"

namespace webrtc {
namespace test {
TEST(FrequencyTest, ConstExpr) {
  constexpr Frequency kFrequencyZero = Frequency::Zero();
  constexpr Frequency kFrequencyPlusInf = Frequency::PlusInfinity();
  constexpr Frequency kFrequencyMinusInf = Frequency::MinusInfinity();
  static_assert(kFrequencyZero.IsZero(), "");
  static_assert(kFrequencyPlusInf.IsPlusInfinity(), "");
  static_assert(kFrequencyMinusInf.IsMinusInfinity(), "");

  static_assert(kFrequencyPlusInf > kFrequencyZero, "");
}

TEST(FrequencyTest, GetBackSameValues) {
  const int64_t kValue = 31;
  EXPECT_EQ(Frequency::Hertz(kValue).hertz<int64_t>(), kValue);
  EXPECT_EQ(Frequency::Zero().hertz<int64_t>(), 0);
}

TEST(FrequencyTest, GetDifferentPrefix) {
  const int64_t kValue = 30000;
  EXPECT_EQ(Frequency::MilliHertz(kValue).hertz<int64_t>(), kValue / 1000);
  EXPECT_EQ(Frequency::Hertz(kValue).millihertz(), kValue * 1000);
  EXPECT_EQ(Frequency::KiloHertz(kValue).hertz(), kValue * 1000);
}

TEST(FrequencyTest, IdentityChecks) {
  const int64_t kValue = 31;
  EXPECT_TRUE(Frequency::Zero().IsZero());
  EXPECT_FALSE(Frequency::Hertz(kValue).IsZero());

  EXPECT_TRUE(Frequency::PlusInfinity().IsInfinite());
  EXPECT_TRUE(Frequency::MinusInfinity().IsInfinite());
  EXPECT_FALSE(Frequency::Zero().IsInfinite());
  EXPECT_FALSE(Frequency::Hertz(kValue).IsInfinite());

  EXPECT_FALSE(Frequency::PlusInfinity().IsFinite());
  EXPECT_FALSE(Frequency::MinusInfinity().IsFinite());
  EXPECT_TRUE(Frequency::Hertz(kValue).IsFinite());
  EXPECT_TRUE(Frequency::Zero().IsFinite());

  EXPECT_TRUE(Frequency::PlusInfinity().IsPlusInfinity());
  EXPECT_FALSE(Frequency::MinusInfinity().IsPlusInfinity());

  EXPECT_TRUE(Frequency::MinusInfinity().IsMinusInfinity());
  EXPECT_FALSE(Frequency::PlusInfinity().IsMinusInfinity());
}

TEST(FrequencyTest, ComparisonOperators) {
  const int64_t kSmall = 42;
  const int64_t kLarge = 45;
  const Frequency small = Frequency::Hertz(kSmall);
  const Frequency large = Frequency::Hertz(kLarge);

  EXPECT_EQ(Frequency::Zero(), Frequency::Hertz(0));
  EXPECT_EQ(Frequency::PlusInfinity(), Frequency::PlusInfinity());
  EXPECT_EQ(small, Frequency::Hertz(kSmall));
  EXPECT_LE(small, Frequency::Hertz(kSmall));
  EXPECT_GE(small, Frequency::Hertz(kSmall));
  EXPECT_NE(small, Frequency::Hertz(kLarge));
  EXPECT_LE(small, Frequency::Hertz(kLarge));
  EXPECT_LT(small, Frequency::Hertz(kLarge));
  EXPECT_GE(large, Frequency::Hertz(kSmall));
  EXPECT_GT(large, Frequency::Hertz(kSmall));
  EXPECT_LT(Frequency::Zero(), small);

  EXPECT_GT(Frequency::PlusInfinity(), large);
  EXPECT_LT(Frequency::MinusInfinity(), Frequency::Zero());
}

TEST(FrequencyTest, Clamping) {
  const Frequency upper = Frequency::Hertz(800);
  const Frequency lower = Frequency::Hertz(100);
  const Frequency under = Frequency::Hertz(100);
  const Frequency inside = Frequency::Hertz(500);
  const Frequency over = Frequency::Hertz(1000);
  EXPECT_EQ(under.Clamped(lower, upper), lower);
  EXPECT_EQ(inside.Clamped(lower, upper), inside);
  EXPECT_EQ(over.Clamped(lower, upper), upper);

  Frequency mutable_frequency = lower;
  mutable_frequency.Clamp(lower, upper);
  EXPECT_EQ(mutable_frequency, lower);
  mutable_frequency = inside;
  mutable_frequency.Clamp(lower, upper);
  EXPECT_EQ(mutable_frequency, inside);
  mutable_frequency = over;
  mutable_frequency.Clamp(lower, upper);
  EXPECT_EQ(mutable_frequency, upper);
}

TEST(FrequencyTest, MathOperations) {
  const int64_t kValueA = 457;
  const int64_t kValueB = 260;
  const Frequency frequency_a = Frequency::Hertz(kValueA);
  const Frequency frequency_b = Frequency::Hertz(kValueB);
  EXPECT_EQ((frequency_a + frequency_b).hertz<int64_t>(), kValueA + kValueB);
  EXPECT_EQ((frequency_a - frequency_b).hertz<int64_t>(), kValueA - kValueB);

  EXPECT_EQ((Frequency::Hertz(kValueA) * kValueB).hertz<int64_t>(),
            kValueA * kValueB);

  EXPECT_EQ((frequency_b / 10).hertz<int64_t>(), kValueB / 10);
  EXPECT_EQ(frequency_b / frequency_a, static_cast<double>(kValueB) / kValueA);

  Frequency mutable_frequency = Frequency::Hertz(kValueA);
  mutable_frequency += Frequency::Hertz(kValueB);
  EXPECT_EQ(mutable_frequency, Frequency::Hertz(kValueA + kValueB));
  mutable_frequency -= Frequency::Hertz(kValueB);
  EXPECT_EQ(mutable_frequency, Frequency::Hertz(kValueA));
}
TEST(FrequencyTest, Rounding) {
  const Frequency freq_high = Frequency::Hertz(23.976);
  EXPECT_EQ(freq_high.hertz(), 24);
  EXPECT_EQ(freq_high.RoundDownTo(Frequency::Hertz(1)), Frequency::Hertz(23));
  EXPECT_EQ(freq_high.RoundTo(Frequency::Hertz(1)), Frequency::Hertz(24));
  EXPECT_EQ(freq_high.RoundUpTo(Frequency::Hertz(1)), Frequency::Hertz(24));

  const Frequency freq_low = Frequency::Hertz(23.4);
  EXPECT_EQ(freq_low.hertz(), 23);
  EXPECT_EQ(freq_low.RoundDownTo(Frequency::Hertz(1)), Frequency::Hertz(23));
  EXPECT_EQ(freq_low.RoundTo(Frequency::Hertz(1)), Frequency::Hertz(23));
  EXPECT_EQ(freq_low.RoundUpTo(Frequency::Hertz(1)), Frequency::Hertz(24));
}

TEST(FrequencyTest, InfinityOperations) {
  const double kValue = 267;
  const Frequency finite = Frequency::Hertz(kValue);
  EXPECT_TRUE((Frequency::PlusInfinity() + finite).IsPlusInfinity());
  EXPECT_TRUE((Frequency::PlusInfinity() - finite).IsPlusInfinity());
  EXPECT_TRUE((finite + Frequency::PlusInfinity()).IsPlusInfinity());
  EXPECT_TRUE((finite - Frequency::MinusInfinity()).IsPlusInfinity());

  EXPECT_TRUE((Frequency::MinusInfinity() + finite).IsMinusInfinity());
  EXPECT_TRUE((Frequency::MinusInfinity() - finite).IsMinusInfinity());
  EXPECT_TRUE((finite + Frequency::MinusInfinity()).IsMinusInfinity());
  EXPECT_TRUE((finite - Frequency::PlusInfinity()).IsMinusInfinity());
}

TEST(UnitConversionTest, TimeDeltaAndFrequency) {
  EXPECT_EQ(1 / Frequency::Hertz(50), TimeDelta::Millis(20));
  EXPECT_EQ(1 / TimeDelta::Millis(20), Frequency::Hertz(50));
  EXPECT_EQ(Frequency::KiloHertz(200) * TimeDelta::Millis(2), 400.0);
}
}  // namespace test
}  // namespace webrtc
