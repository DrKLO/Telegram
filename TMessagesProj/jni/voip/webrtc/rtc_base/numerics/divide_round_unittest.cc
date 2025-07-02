/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/divide_round.h"

#include <limits>

#include "test/gtest.h"

namespace webrtc {
namespace {

TEST(DivideRoundUpTest, CanBeUsedAsConstexpr) {
  static_assert(DivideRoundUp(5, 1) == 5, "");
  static_assert(DivideRoundUp(5, 2) == 3, "");
}

TEST(DivideRoundUpTest, ReturnsZeroForZeroDividend) {
  EXPECT_EQ(DivideRoundUp(uint8_t{0}, 1), 0);
  EXPECT_EQ(DivideRoundUp(uint8_t{0}, 3), 0);
  EXPECT_EQ(DivideRoundUp(int{0}, 1), 0);
  EXPECT_EQ(DivideRoundUp(int{0}, 3), 0);
}

TEST(DivideRoundUpTest, WorksForMaxDividend) {
  EXPECT_EQ(DivideRoundUp(uint8_t{255}, 2), 128);
  EXPECT_EQ(DivideRoundUp(std::numeric_limits<int>::max(), 2),
            std::numeric_limits<int>::max() / 2 +
                (std::numeric_limits<int>::max() % 2));
}

TEST(DivideRoundToNearestTest, CanBeUsedAsConstexpr) {
  static constexpr int kOne = DivideRoundToNearest(5, 4);
  static constexpr int kTwo = DivideRoundToNearest(7, 4);
  static_assert(kOne == 1);
  static_assert(kTwo == 2);
  static_assert(DivideRoundToNearest(-5, 4) == -1);
  static_assert(DivideRoundToNearest(-7, 4) == -2);
}

TEST(DivideRoundToNearestTest, DivideByOddNumber) {
  EXPECT_EQ(DivideRoundToNearest(-5, 3), -2);
  EXPECT_EQ(DivideRoundToNearest(-4, 3), -1);
  EXPECT_EQ(DivideRoundToNearest(-3, 3), -1);
  EXPECT_EQ(DivideRoundToNearest(-2, 3), -1);
  EXPECT_EQ(DivideRoundToNearest(-1, 3), 0);
  EXPECT_EQ(DivideRoundToNearest(0, 3), 0);
  EXPECT_EQ(DivideRoundToNearest(1, 3), 0);
  EXPECT_EQ(DivideRoundToNearest(2, 3), 1);
  EXPECT_EQ(DivideRoundToNearest(3, 3), 1);
  EXPECT_EQ(DivideRoundToNearest(4, 3), 1);
  EXPECT_EQ(DivideRoundToNearest(5, 3), 2);
  EXPECT_EQ(DivideRoundToNearest(6, 3), 2);
}

TEST(DivideRoundToNearestTest, DivideByEvenNumberTieRoundsUp) {
  EXPECT_EQ(DivideRoundToNearest(-7, 4), -2);
  EXPECT_EQ(DivideRoundToNearest(-6, 4), -1);
  EXPECT_EQ(DivideRoundToNearest(-5, 4), -1);
  EXPECT_EQ(DivideRoundToNearest(-4, 4), -1);
  EXPECT_EQ(DivideRoundToNearest(-3, 4), -1);
  EXPECT_EQ(DivideRoundToNearest(-2, 4), 0);
  EXPECT_EQ(DivideRoundToNearest(-1, 4), 0);
  EXPECT_EQ(DivideRoundToNearest(0, 4), 0);
  EXPECT_EQ(DivideRoundToNearest(1, 4), 0);
  EXPECT_EQ(DivideRoundToNearest(2, 4), 1);
  EXPECT_EQ(DivideRoundToNearest(3, 4), 1);
  EXPECT_EQ(DivideRoundToNearest(4, 4), 1);
  EXPECT_EQ(DivideRoundToNearest(5, 4), 1);
  EXPECT_EQ(DivideRoundToNearest(6, 4), 2);
  EXPECT_EQ(DivideRoundToNearest(7, 4), 2);
}

TEST(DivideRoundToNearestTest, LargeDivisor) {
  EXPECT_EQ(DivideRoundToNearest(std::numeric_limits<int>::max() - 1,
                                 std::numeric_limits<int>::max()),
            1);
  EXPECT_EQ(DivideRoundToNearest(std::numeric_limits<int>::min(),
                                 std::numeric_limits<int>::max()),
            -1);
}

TEST(DivideRoundToNearestTest, DivideSmallTypeByLargeType) {
  uint8_t small = 0xff;
  uint16_t large = 0xffff;
  EXPECT_EQ(DivideRoundToNearest(small, large), 0);
}

using IntegerTypes = ::testing::Types<int8_t,
                                      int16_t,
                                      int32_t,
                                      int64_t,
                                      uint8_t,
                                      uint16_t,
                                      uint32_t,
                                      uint64_t>;
template <typename T>
class DivideRoundTypedTest : public ::testing::Test {};
TYPED_TEST_SUITE(DivideRoundTypedTest, IntegerTypes);

TYPED_TEST(DivideRoundTypedTest, RoundToNearestPreservesType) {
  static_assert(
      std::is_same<decltype(DivideRoundToNearest(TypeParam{100}, int8_t{3})),
                   decltype(TypeParam{100} / int8_t{3})>::value,
      "");
  static_assert(
      std::is_same<decltype(DivideRoundToNearest(TypeParam{100}, int16_t{3})),
                   decltype(TypeParam{100} / int16_t{3})>::value,
      "");
  static_assert(
      std::is_same<decltype(DivideRoundToNearest(TypeParam{100}, int32_t{3})),
                   decltype(TypeParam{100} / int32_t{3})>::value,
      "");
  static_assert(
      std::is_same<decltype(DivideRoundToNearest(TypeParam{100}, int64_t{3})),
                   decltype(TypeParam{100} / int64_t{3})>::value,
      "");
  static_assert(
      std::is_same<decltype(DivideRoundToNearest(TypeParam{100}, uint8_t{3})),
                   decltype(TypeParam{100} / uint8_t{3})>::value,
      "");
  static_assert(
      std::is_same<decltype(DivideRoundToNearest(TypeParam{100}, uint16_t{3})),
                   decltype(TypeParam{100} / uint16_t{3})>::value,
      "");
  static_assert(
      std::is_same<decltype(DivideRoundToNearest(TypeParam{100}, uint32_t{3})),
                   decltype(TypeParam{100} / uint32_t{3})>::value,
      "");
  static_assert(
      std::is_same<decltype(DivideRoundToNearest(TypeParam{100}, uint64_t{3})),
                   decltype(TypeParam{100} / uint64_t{3})>::value,
      "");
}

TYPED_TEST(DivideRoundTypedTest, RoundUpPreservesType) {
  static_assert(std::is_same<decltype(DivideRoundUp(TypeParam{100}, int8_t{3})),
                             decltype(TypeParam{100} / int8_t{3})>::value,
                "");
  static_assert(
      std::is_same<decltype(DivideRoundUp(TypeParam{100}, int16_t{3})),
                   decltype(TypeParam{100} / int16_t{3})>::value,
      "");
  static_assert(
      std::is_same<decltype(DivideRoundUp(TypeParam{100}, int32_t{3})),
                   decltype(TypeParam{100} / int32_t{3})>::value,
      "");
  static_assert(
      std::is_same<decltype(DivideRoundUp(TypeParam{100}, int64_t{3})),
                   decltype(TypeParam{100} / int64_t{3})>::value,
      "");
  static_assert(
      std::is_same<decltype(DivideRoundUp(TypeParam{100}, uint8_t{3})),
                   decltype(TypeParam{100} / uint8_t{3})>::value,
      "");
  static_assert(
      std::is_same<decltype(DivideRoundUp(TypeParam{100}, uint16_t{3})),
                   decltype(TypeParam{100} / uint16_t{3})>::value,
      "");
  static_assert(
      std::is_same<decltype(DivideRoundUp(TypeParam{100}, uint32_t{3})),
                   decltype(TypeParam{100} / uint32_t{3})>::value,
      "");
  static_assert(
      std::is_same<decltype(DivideRoundUp(TypeParam{100}, uint64_t{3})),
                   decltype(TypeParam{100} / uint64_t{3})>::value,
      "");
}

}  // namespace
}  // namespace webrtc
