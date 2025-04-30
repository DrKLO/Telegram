/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/units/data_rate.h"

#include "rtc_base/logging.h"
#include "test/gtest.h"

namespace webrtc {
namespace test {

TEST(DataRateTest, CompilesWithChecksAndLogs) {
  DataRate a = DataRate::KilobitsPerSec(300);
  DataRate b = DataRate::KilobitsPerSec(210);
  RTC_CHECK_GT(a, b);
  RTC_LOG(LS_INFO) << a;
}

TEST(DataRateTest, ConstExpr) {
  constexpr int64_t kValue = 12345;
  constexpr DataRate kDataRateZero = DataRate::Zero();
  constexpr DataRate kDataRateInf = DataRate::Infinity();
  static_assert(kDataRateZero.IsZero(), "");
  static_assert(kDataRateInf.IsInfinite(), "");
  static_assert(kDataRateInf.bps_or(-1) == -1, "");
  static_assert(kDataRateInf > kDataRateZero, "");

  constexpr DataRate kDataRateBps = DataRate::BitsPerSec(kValue);
  constexpr DataRate kDataRateKbps = DataRate::KilobitsPerSec(kValue);
  static_assert(kDataRateBps.bps<double>() == kValue, "");
  static_assert(kDataRateBps.bps_or(0) == kValue, "");
  static_assert(kDataRateKbps.kbps_or(0) == kValue, "");
}

TEST(DataRateTest, GetBackSameValues) {
  const int64_t kValue = 123 * 8;
  EXPECT_EQ(DataRate::BitsPerSec(kValue).bps(), kValue);
  EXPECT_EQ(DataRate::KilobitsPerSec(kValue).kbps(), kValue);
}

TEST(DataRateTest, GetDifferentPrefix) {
  const int64_t kValue = 123 * 8000;
  EXPECT_EQ(DataRate::BitsPerSec(kValue).kbps(), kValue / 1000);
}

TEST(DataRateTest, IdentityChecks) {
  const int64_t kValue = 3000;
  EXPECT_TRUE(DataRate::Zero().IsZero());
  EXPECT_FALSE(DataRate::BitsPerSec(kValue).IsZero());

  EXPECT_TRUE(DataRate::Infinity().IsInfinite());
  EXPECT_FALSE(DataRate::Zero().IsInfinite());
  EXPECT_FALSE(DataRate::BitsPerSec(kValue).IsInfinite());

  EXPECT_FALSE(DataRate::Infinity().IsFinite());
  EXPECT_TRUE(DataRate::BitsPerSec(kValue).IsFinite());
  EXPECT_TRUE(DataRate::Zero().IsFinite());
}

TEST(DataRateTest, ComparisonOperators) {
  const int64_t kSmall = 450;
  const int64_t kLarge = 451;
  const DataRate small = DataRate::BitsPerSec(kSmall);
  const DataRate large = DataRate::BitsPerSec(kLarge);

  EXPECT_EQ(DataRate::Zero(), DataRate::BitsPerSec(0));
  EXPECT_EQ(DataRate::Infinity(), DataRate::Infinity());
  EXPECT_EQ(small, small);
  EXPECT_LE(small, small);
  EXPECT_GE(small, small);
  EXPECT_NE(small, large);
  EXPECT_LE(small, large);
  EXPECT_LT(small, large);
  EXPECT_GE(large, small);
  EXPECT_GT(large, small);
  EXPECT_LT(DataRate::Zero(), small);
  EXPECT_GT(DataRate::Infinity(), large);
}

TEST(DataRateTest, ConvertsToAndFromDouble) {
  const int64_t kValue = 128;
  const double kDoubleValue = static_cast<double>(kValue);
  const double kDoubleKbps = kValue * 1e-3;
  const double kFloatKbps = static_cast<float>(kDoubleKbps);

  EXPECT_EQ(DataRate::BitsPerSec(kValue).bps<double>(), kDoubleValue);
  EXPECT_EQ(DataRate::BitsPerSec(kValue).kbps<double>(), kDoubleKbps);
  EXPECT_EQ(DataRate::BitsPerSec(kValue).kbps<float>(), kFloatKbps);
  EXPECT_EQ(DataRate::BitsPerSec(kDoubleValue).bps(), kValue);
  EXPECT_EQ(DataRate::KilobitsPerSec(kDoubleKbps).bps(), kValue);

  const double kInfinity = std::numeric_limits<double>::infinity();
  EXPECT_EQ(DataRate::Infinity().bps<double>(), kInfinity);
  EXPECT_TRUE(DataRate::BitsPerSec(kInfinity).IsInfinite());
  EXPECT_TRUE(DataRate::KilobitsPerSec(kInfinity).IsInfinite());
}
TEST(DataRateTest, Clamping) {
  const DataRate upper = DataRate::KilobitsPerSec(800);
  const DataRate lower = DataRate::KilobitsPerSec(100);
  const DataRate under = DataRate::KilobitsPerSec(100);
  const DataRate inside = DataRate::KilobitsPerSec(500);
  const DataRate over = DataRate::KilobitsPerSec(1000);
  EXPECT_EQ(under.Clamped(lower, upper), lower);
  EXPECT_EQ(inside.Clamped(lower, upper), inside);
  EXPECT_EQ(over.Clamped(lower, upper), upper);

  DataRate mutable_rate = lower;
  mutable_rate.Clamp(lower, upper);
  EXPECT_EQ(mutable_rate, lower);
  mutable_rate = inside;
  mutable_rate.Clamp(lower, upper);
  EXPECT_EQ(mutable_rate, inside);
  mutable_rate = over;
  mutable_rate.Clamp(lower, upper);
  EXPECT_EQ(mutable_rate, upper);
}

TEST(DataRateTest, MathOperations) {
  const int64_t kValueA = 450;
  const int64_t kValueB = 267;
  const DataRate rate_a = DataRate::BitsPerSec(kValueA);
  const DataRate rate_b = DataRate::BitsPerSec(kValueB);
  const int32_t kInt32Value = 123;
  const double kFloatValue = 123.0;

  EXPECT_EQ((rate_a + rate_b).bps(), kValueA + kValueB);
  EXPECT_EQ((rate_a - rate_b).bps(), kValueA - kValueB);

  EXPECT_EQ((rate_a * kValueB).bps(), kValueA * kValueB);
  EXPECT_EQ((rate_a * kInt32Value).bps(), kValueA * kInt32Value);
  EXPECT_EQ((rate_a * kFloatValue).bps(), kValueA * kFloatValue);

  EXPECT_EQ(rate_a / rate_b, static_cast<double>(kValueA) / kValueB);

  EXPECT_EQ((rate_a / 10).bps(), kValueA / 10);
  EXPECT_NEAR((rate_a / 0.5).bps(), kValueA * 2, 1);

  DataRate mutable_rate = DataRate::BitsPerSec(kValueA);
  mutable_rate += rate_b;
  EXPECT_EQ(mutable_rate.bps(), kValueA + kValueB);
  mutable_rate -= rate_a;
  EXPECT_EQ(mutable_rate.bps(), kValueB);
}

TEST(UnitConversionTest, DataRateAndDataSizeAndTimeDelta) {
  const int64_t kSeconds = 5;
  const int64_t kBitsPerSecond = 440;
  const int64_t kBytes = 44000;
  const TimeDelta delta_a = TimeDelta::Seconds(kSeconds);
  const DataRate rate_b = DataRate::BitsPerSec(kBitsPerSecond);
  const DataSize size_c = DataSize::Bytes(kBytes);
  EXPECT_EQ((delta_a * rate_b).bytes(), kSeconds * kBitsPerSecond / 8);
  EXPECT_EQ((rate_b * delta_a).bytes(), kSeconds * kBitsPerSecond / 8);
  EXPECT_EQ((size_c / delta_a).bps(), kBytes * 8 / kSeconds);
  EXPECT_EQ((size_c / rate_b).seconds(), kBytes * 8 / kBitsPerSecond);
}

TEST(UnitConversionTest, DataRateAndDataSizeAndFrequency) {
  const int64_t kHertz = 30;
  const int64_t kBitsPerSecond = 96000;
  const int64_t kBytes = 1200;
  const Frequency freq_a = Frequency::Hertz(kHertz);
  const DataRate rate_b = DataRate::BitsPerSec(kBitsPerSecond);
  const DataSize size_c = DataSize::Bytes(kBytes);
  EXPECT_EQ((freq_a * size_c).bps(), kHertz * kBytes * 8);
  EXPECT_EQ((size_c * freq_a).bps(), kHertz * kBytes * 8);
  EXPECT_EQ((rate_b / size_c).hertz<int64_t>(), kBitsPerSecond / kBytes / 8);
  EXPECT_EQ((rate_b / freq_a).bytes(), kBitsPerSecond / kHertz / 8);
}

TEST(UnitConversionDeathTest, DivisionFailsOnLargeSize) {
  // Note that the failure is expected since the current implementation  is
  // implementated in a way that does not support division of large sizes. If
  // the implementation is changed, this test can safely be removed.
  const int64_t kJustSmallEnoughForDivision =
      std::numeric_limits<int64_t>::max() / 8000000;
  const DataSize large_size = DataSize::Bytes(kJustSmallEnoughForDivision);
  const DataRate data_rate = DataRate::KilobitsPerSec(100);
  const TimeDelta time_delta = TimeDelta::Millis(100);
  EXPECT_TRUE((large_size / data_rate).IsFinite());
  EXPECT_TRUE((large_size / time_delta).IsFinite());
#if GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID) && RTC_DCHECK_IS_ON
  const int64_t kToolargeForDivision = kJustSmallEnoughForDivision + 1;
  const DataSize too_large_size = DataSize::Bytes(kToolargeForDivision);
  EXPECT_DEATH(too_large_size / data_rate, "");
  EXPECT_DEATH(too_large_size / time_delta, "");
#endif  // GTEST_HAS_DEATH_TEST && !!defined(WEBRTC_ANDROID) && RTC_DCHECK_IS_ON
}
}  // namespace test
}  // namespace webrtc
