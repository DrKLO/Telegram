/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/mod_ops.h"

#include <stdint.h>

#include "test/gtest.h"

namespace webrtc {
class TestModOps : public ::testing::Test {
 protected:
  // Can't use std::numeric_limits<unsigned long>::max() since
  // MSVC doesn't support constexpr.
  static const unsigned long ulmax = ~0ul;  // NOLINT
};

TEST_F(TestModOps, Add) {
  const int D = 100;
  ASSERT_EQ(1u, Add<D>(0, 1));
  ASSERT_EQ(0u, Add<D>(0, D));
  for (int i = 0; i < D; ++i)
    ASSERT_EQ(0u, Add<D>(i, D - i));

  int t = 37;
  uint8_t a = t;
  for (int i = 0; i < 256; ++i) {
    ASSERT_EQ(a, static_cast<uint8_t>(t));
    t = Add<256>(t, 1);
    ++a;
  }
}

TEST_F(TestModOps, AddLarge) {
  const unsigned long D = ulmax - 10ul;  // NOLINT
  unsigned long l = D - 1ul;             // NOLINT
  ASSERT_EQ(D - 2ul, Add<D>(l, l));
  ASSERT_EQ(9ul, Add<D>(l, ulmax));
  ASSERT_EQ(10ul, Add<D>(0ul, ulmax));
}

TEST_F(TestModOps, Subtract) {
  const int D = 100;
  ASSERT_EQ(99u, Subtract<D>(0, 1));
  ASSERT_EQ(0u, Subtract<D>(0, D));
  for (int i = 0; i < D; ++i)
    ASSERT_EQ(0u, Subtract<D>(i, D + i));

  int t = 37;
  uint8_t a = t;
  for (int i = 0; i < 256; ++i) {
    ASSERT_EQ(a, static_cast<uint8_t>(t));
    t = Subtract<256>(t, 1);
    --a;
  }
}

TEST_F(TestModOps, SubtractLarge) {
  // NOLINTNEXTLINE
  const unsigned long D = ulmax - 10ul;  // NOLINT
  unsigned long l = D - 1ul;             // NOLINT
  ASSERT_EQ(0ul, Subtract<D>(l, l));
  ASSERT_EQ(D - 11ul, Subtract<D>(l, ulmax));
  ASSERT_EQ(D - 10ul, Subtract<D>(0ul, ulmax));
}

TEST_F(TestModOps, ForwardDiff) {
  ASSERT_EQ(0u, ForwardDiff(4711u, 4711u));

  uint8_t x = 0;
  uint8_t y = 255;
  for (int i = 0; i < 256; ++i) {
    ASSERT_EQ(255u, ForwardDiff(x, y));
    ++x;
    ++y;
  }

  int yi = 255;
  for (int i = 0; i < 256; ++i) {
    ASSERT_EQ(255u, ForwardDiff<uint8_t>(x, yi));
    ++x;
    ++yi;
  }
}

TEST_F(TestModOps, ForwardDiffWithDivisor) {
  ASSERT_EQ(122, (ForwardDiff<uint8_t, 123>(0, 122)));
  ASSERT_EQ(0, (ForwardDiff<uint8_t, 123>(122, 122)));
  ASSERT_EQ(122, (ForwardDiff<uint8_t, 123>(1, 0)));
  ASSERT_EQ(0, (ForwardDiff<uint8_t, 123>(0, 0)));
  ASSERT_EQ(1, (ForwardDiff<uint8_t, 123>(122, 0)));
}

TEST_F(TestModOps, ReverseDiff) {
  ASSERT_EQ(0u, ReverseDiff(4711u, 4711u));

  uint8_t x = 0;
  uint8_t y = 255;
  for (int i = 0; i < 256; ++i) {
    ASSERT_EQ(1u, ReverseDiff(x, y));
    ++x;
    ++y;
  }

  int yi = 255;
  for (int i = 0; i < 256; ++i) {
    ASSERT_EQ(1u, ReverseDiff<uint8_t>(x, yi));
    ++x;
    ++yi;
  }
}

TEST_F(TestModOps, ReverseDiffWithDivisor) {
  ASSERT_EQ(1, (ReverseDiff<uint8_t, 123>(0, 122)));
  ASSERT_EQ(0, (ReverseDiff<uint8_t, 123>(122, 122)));
  ASSERT_EQ(1, (ReverseDiff<uint8_t, 123>(1, 0)));
  ASSERT_EQ(0, (ReverseDiff<uint8_t, 123>(0, 0)));
  ASSERT_EQ(122, (ReverseDiff<uint8_t, 123>(122, 0)));
}

TEST_F(TestModOps, MinDiff) {
  for (uint16_t i = 0; i < 256; ++i) {
    ASSERT_EQ(0, MinDiff<uint8_t>(i, i));
    ASSERT_EQ(1, MinDiff<uint8_t>(i - 1, i));
    ASSERT_EQ(1, MinDiff<uint8_t>(i + 1, i));
  }

  for (uint8_t i = 0; i < 128; ++i)
    ASSERT_EQ(i, MinDiff<uint8_t>(0, i));

  for (uint8_t i = 0; i < 128; ++i)
    ASSERT_EQ(128 - i, MinDiff<uint8_t>(0, 128 + i));
}

TEST_F(TestModOps, MinDiffWitDivisor) {
  ASSERT_EQ(5u, (MinDiff<uint8_t, 11>(0, 5)));
  ASSERT_EQ(5u, (MinDiff<uint8_t, 11>(0, 6)));
  ASSERT_EQ(5u, (MinDiff<uint8_t, 11>(5, 0)));
  ASSERT_EQ(5u, (MinDiff<uint8_t, 11>(6, 0)));

  const uint16_t D = 4711;

  for (uint16_t i = 0; i < D / 2; ++i)
    ASSERT_EQ(i, (MinDiff<uint16_t, D>(0, i)));

  ASSERT_EQ(D / 2, (MinDiff<uint16_t, D>(0, D / 2)));

  for (uint16_t i = 0; i < D / 2; ++i)
    ASSERT_EQ(D / 2 - i, (MinDiff<uint16_t, D>(0, D / 2 - i)));
}

}  // namespace webrtc
