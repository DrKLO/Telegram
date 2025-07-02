/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/common/math.h"

#include "test/gmock.h"

namespace dcsctp {
namespace {

TEST(MathUtilTest, CanRoundUpTo4) {
  // Signed numbers
  EXPECT_EQ(RoundUpTo4(static_cast<int>(-5)), -4);
  EXPECT_EQ(RoundUpTo4(static_cast<int>(-4)), -4);
  EXPECT_EQ(RoundUpTo4(static_cast<int>(-3)), 0);
  EXPECT_EQ(RoundUpTo4(static_cast<int>(-2)), 0);
  EXPECT_EQ(RoundUpTo4(static_cast<int>(-1)), 0);
  EXPECT_EQ(RoundUpTo4(static_cast<int>(0)), 0);
  EXPECT_EQ(RoundUpTo4(static_cast<int>(1)), 4);
  EXPECT_EQ(RoundUpTo4(static_cast<int>(2)), 4);
  EXPECT_EQ(RoundUpTo4(static_cast<int>(3)), 4);
  EXPECT_EQ(RoundUpTo4(static_cast<int>(4)), 4);
  EXPECT_EQ(RoundUpTo4(static_cast<int>(5)), 8);
  EXPECT_EQ(RoundUpTo4(static_cast<int>(6)), 8);
  EXPECT_EQ(RoundUpTo4(static_cast<int>(7)), 8);
  EXPECT_EQ(RoundUpTo4(static_cast<int>(8)), 8);
  EXPECT_EQ(RoundUpTo4(static_cast<int64_t>(10000000000)), 10000000000);
  EXPECT_EQ(RoundUpTo4(static_cast<int64_t>(10000000001)), 10000000004);

  // Unsigned numbers
  EXPECT_EQ(RoundUpTo4(static_cast<unsigned int>(0)), 0u);
  EXPECT_EQ(RoundUpTo4(static_cast<unsigned int>(1)), 4u);
  EXPECT_EQ(RoundUpTo4(static_cast<unsigned int>(2)), 4u);
  EXPECT_EQ(RoundUpTo4(static_cast<unsigned int>(3)), 4u);
  EXPECT_EQ(RoundUpTo4(static_cast<unsigned int>(4)), 4u);
  EXPECT_EQ(RoundUpTo4(static_cast<unsigned int>(5)), 8u);
  EXPECT_EQ(RoundUpTo4(static_cast<unsigned int>(6)), 8u);
  EXPECT_EQ(RoundUpTo4(static_cast<unsigned int>(7)), 8u);
  EXPECT_EQ(RoundUpTo4(static_cast<unsigned int>(8)), 8u);
  EXPECT_EQ(RoundUpTo4(static_cast<uint64_t>(10000000000)), 10000000000u);
  EXPECT_EQ(RoundUpTo4(static_cast<uint64_t>(10000000001)), 10000000004u);
}

TEST(MathUtilTest, CanRoundDownTo4) {
  // Signed numbers
  EXPECT_EQ(RoundDownTo4(static_cast<int>(-5)), -8);
  EXPECT_EQ(RoundDownTo4(static_cast<int>(-4)), -4);
  EXPECT_EQ(RoundDownTo4(static_cast<int>(-3)), -4);
  EXPECT_EQ(RoundDownTo4(static_cast<int>(-2)), -4);
  EXPECT_EQ(RoundDownTo4(static_cast<int>(-1)), -4);
  EXPECT_EQ(RoundDownTo4(static_cast<int>(0)), 0);
  EXPECT_EQ(RoundDownTo4(static_cast<int>(1)), 0);
  EXPECT_EQ(RoundDownTo4(static_cast<int>(2)), 0);
  EXPECT_EQ(RoundDownTo4(static_cast<int>(3)), 0);
  EXPECT_EQ(RoundDownTo4(static_cast<int>(4)), 4);
  EXPECT_EQ(RoundDownTo4(static_cast<int>(5)), 4);
  EXPECT_EQ(RoundDownTo4(static_cast<int>(6)), 4);
  EXPECT_EQ(RoundDownTo4(static_cast<int>(7)), 4);
  EXPECT_EQ(RoundDownTo4(static_cast<int>(8)), 8);
  EXPECT_EQ(RoundDownTo4(static_cast<int64_t>(10000000000)), 10000000000);
  EXPECT_EQ(RoundDownTo4(static_cast<int64_t>(10000000001)), 10000000000);

  // Unsigned numbers
  EXPECT_EQ(RoundDownTo4(static_cast<unsigned int>(0)), 0u);
  EXPECT_EQ(RoundDownTo4(static_cast<unsigned int>(1)), 0u);
  EXPECT_EQ(RoundDownTo4(static_cast<unsigned int>(2)), 0u);
  EXPECT_EQ(RoundDownTo4(static_cast<unsigned int>(3)), 0u);
  EXPECT_EQ(RoundDownTo4(static_cast<unsigned int>(4)), 4u);
  EXPECT_EQ(RoundDownTo4(static_cast<unsigned int>(5)), 4u);
  EXPECT_EQ(RoundDownTo4(static_cast<unsigned int>(6)), 4u);
  EXPECT_EQ(RoundDownTo4(static_cast<unsigned int>(7)), 4u);
  EXPECT_EQ(RoundDownTo4(static_cast<unsigned int>(8)), 8u);
  EXPECT_EQ(RoundDownTo4(static_cast<uint64_t>(10000000000)), 10000000000u);
  EXPECT_EQ(RoundDownTo4(static_cast<uint64_t>(10000000001)), 10000000000u);
}

TEST(MathUtilTest, IsDivisibleBy4) {
  // Signed numbers
  EXPECT_EQ(IsDivisibleBy4(static_cast<int>(-4)), true);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int>(-3)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int>(-2)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int>(-1)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int>(0)), true);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int>(1)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int>(2)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int>(3)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int>(4)), true);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int>(5)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int>(6)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int>(7)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int>(8)), true);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int64_t>(10000000000)), true);
  EXPECT_EQ(IsDivisibleBy4(static_cast<int64_t>(10000000001)), false);

  // Unsigned numbers
  EXPECT_EQ(IsDivisibleBy4(static_cast<unsigned int>(0)), true);
  EXPECT_EQ(IsDivisibleBy4(static_cast<unsigned int>(1)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<unsigned int>(2)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<unsigned int>(3)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<unsigned int>(4)), true);
  EXPECT_EQ(IsDivisibleBy4(static_cast<unsigned int>(5)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<unsigned int>(6)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<unsigned int>(7)), false);
  EXPECT_EQ(IsDivisibleBy4(static_cast<unsigned int>(8)), true);
  EXPECT_EQ(IsDivisibleBy4(static_cast<uint64_t>(10000000000)), true);
  EXPECT_EQ(IsDivisibleBy4(static_cast<uint64_t>(10000000001)), false);
}

}  // namespace
}  // namespace dcsctp
