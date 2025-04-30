/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/safe_compare.h"

#include <limits>

#include "test/gtest.h"

namespace rtc {

namespace {

constexpr std::uintmax_t umax = std::numeric_limits<std::uintmax_t>::max();
constexpr std::intmax_t imin = std::numeric_limits<std::intmax_t>::min();
constexpr std::intmax_t m1 = -1;

// m1 and umax have the same representation because we use 2's complement
// arithmetic, so naive casting will confuse them.
static_assert(static_cast<std::uintmax_t>(m1) == umax, "");
static_assert(m1 == static_cast<std::intmax_t>(umax), "");

static const std::pair<int, int> p1(1, 1);
static const std::pair<int, int> p2(1, 2);

}  // namespace

// clang-format off

// These functions aren't used in the tests, but it's useful to look at the
// compiler output for them, and verify that (1) the same-signedness *Safe
// functions result in exactly the same code as their *Ref counterparts, and
// that (2) the mixed-signedness *Safe functions have just a few extra
// arithmetic and logic instructions (but no extra control flow instructions).
bool TestLessThanRef(      int a,      int b) { return a < b; }
bool TestLessThanRef( unsigned a, unsigned b) { return a < b; }
bool TestLessThanSafe(     int a,      int b) { return SafeLt(a, b); }
bool TestLessThanSafe(unsigned a, unsigned b) { return SafeLt(a, b); }
bool TestLessThanSafe(unsigned a,      int b) { return SafeLt(a, b); }
bool TestLessThanSafe(     int a, unsigned b) { return SafeLt(a, b); }

// For these, we expect the *Ref and *Safe functions to result in identical
// code, except for the ones that compare a signed variable with an unsigned
// constant; in that case, the *Ref function does an unsigned comparison (fast
// but incorrect) and the *Safe function spends a few extra instructions on
// doing it right.
bool TestLessThan17Ref(       int a) { return a < 17; }
bool TestLessThan17Ref(  unsigned a) { return a < 17; }
bool TestLessThan17uRef(      int a) { return static_cast<unsigned>(a) < 17u; }
bool TestLessThan17uRef( unsigned a) { return a < 17u; }
bool TestLessThan17Safe(      int a) { return SafeLt(a, 17); }
bool TestLessThan17Safe( unsigned a) { return SafeLt(a, 17); }
bool TestLessThan17uSafe(     int a) { return SafeLt(a, 17u); }
bool TestLessThan17uSafe(unsigned a) { return SafeLt(a, 17u); }

// Cases where we can't convert to a larger signed type.
bool TestLessThanMax( intmax_t a, uintmax_t b) { return SafeLt(a, b); }
bool TestLessThanMax(uintmax_t a,  intmax_t b) { return SafeLt(a, b); }
bool TestLessThanMax17u( intmax_t a) { return SafeLt(a, uintmax_t{17}); }
bool TestLessThanMax17( uintmax_t a) { return SafeLt(a,  intmax_t{17}); }

// Cases where the compiler should be able to compute the result at compile
// time.
bool TestLessThanConst1() { return SafeLt(  -1,    1); }
bool TestLessThanConst2() { return SafeLt(  m1, umax); }
bool TestLessThanConst3() { return SafeLt(umax, imin); }
bool TestLessThanConst4(unsigned a) { return SafeLt( a, -1); }
bool TestLessThanConst5(unsigned a) { return SafeLt(-1,  a); }
bool TestLessThanConst6(unsigned a) { return SafeLt( a,  a); }

// clang-format on

TEST(SafeCmpTest, Eq) {
  static_assert(!SafeEq(-1, 2), "");
  static_assert(!SafeEq(-1, 2u), "");
  static_assert(!SafeEq(2, -1), "");
  static_assert(!SafeEq(2u, -1), "");

  static_assert(!SafeEq(1, 2), "");
  static_assert(!SafeEq(1, 2u), "");
  static_assert(!SafeEq(1u, 2), "");
  static_assert(!SafeEq(1u, 2u), "");
  static_assert(!SafeEq(2, 1), "");
  static_assert(!SafeEq(2, 1u), "");
  static_assert(!SafeEq(2u, 1), "");
  static_assert(!SafeEq(2u, 1u), "");

  static_assert(SafeEq(2, 2), "");
  static_assert(SafeEq(2, 2u), "");
  static_assert(SafeEq(2u, 2), "");
  static_assert(SafeEq(2u, 2u), "");

  static_assert(SafeEq(imin, imin), "");
  static_assert(!SafeEq(imin, umax), "");
  static_assert(!SafeEq(umax, imin), "");
  static_assert(SafeEq(umax, umax), "");

  static_assert(SafeEq(m1, m1), "");
  static_assert(!SafeEq(m1, umax), "");
  static_assert(!SafeEq(umax, m1), "");
  static_assert(SafeEq(umax, umax), "");

  static_assert(!SafeEq(1, 2), "");
  static_assert(!SafeEq(1, 2.0), "");
  static_assert(!SafeEq(1.0, 2), "");
  static_assert(!SafeEq(1.0, 2.0), "");
  static_assert(!SafeEq(2, 1), "");
  static_assert(!SafeEq(2, 1.0), "");
  static_assert(!SafeEq(2.0, 1), "");
  static_assert(!SafeEq(2.0, 1.0), "");

  static_assert(SafeEq(2, 2), "");
  static_assert(SafeEq(2, 2.0), "");
  static_assert(SafeEq(2.0, 2), "");
  static_assert(SafeEq(2.0, 2.0), "");

  EXPECT_TRUE(SafeEq(p1, p1));
  EXPECT_FALSE(SafeEq(p1, p2));
  EXPECT_FALSE(SafeEq(p2, p1));
  EXPECT_TRUE(SafeEq(p2, p2));
}

TEST(SafeCmpTest, Ne) {
  static_assert(SafeNe(-1, 2), "");
  static_assert(SafeNe(-1, 2u), "");
  static_assert(SafeNe(2, -1), "");
  static_assert(SafeNe(2u, -1), "");

  static_assert(SafeNe(1, 2), "");
  static_assert(SafeNe(1, 2u), "");
  static_assert(SafeNe(1u, 2), "");
  static_assert(SafeNe(1u, 2u), "");
  static_assert(SafeNe(2, 1), "");
  static_assert(SafeNe(2, 1u), "");
  static_assert(SafeNe(2u, 1), "");
  static_assert(SafeNe(2u, 1u), "");

  static_assert(!SafeNe(2, 2), "");
  static_assert(!SafeNe(2, 2u), "");
  static_assert(!SafeNe(2u, 2), "");
  static_assert(!SafeNe(2u, 2u), "");

  static_assert(!SafeNe(imin, imin), "");
  static_assert(SafeNe(imin, umax), "");
  static_assert(SafeNe(umax, imin), "");
  static_assert(!SafeNe(umax, umax), "");

  static_assert(!SafeNe(m1, m1), "");
  static_assert(SafeNe(m1, umax), "");
  static_assert(SafeNe(umax, m1), "");
  static_assert(!SafeNe(umax, umax), "");

  static_assert(SafeNe(1, 2), "");
  static_assert(SafeNe(1, 2.0), "");
  static_assert(SafeNe(1.0, 2), "");
  static_assert(SafeNe(1.0, 2.0), "");
  static_assert(SafeNe(2, 1), "");
  static_assert(SafeNe(2, 1.0), "");
  static_assert(SafeNe(2.0, 1), "");
  static_assert(SafeNe(2.0, 1.0), "");

  static_assert(!SafeNe(2, 2), "");
  static_assert(!SafeNe(2, 2.0), "");
  static_assert(!SafeNe(2.0, 2), "");
  static_assert(!SafeNe(2.0, 2.0), "");

  EXPECT_FALSE(SafeNe(p1, p1));
  EXPECT_TRUE(SafeNe(p1, p2));
  EXPECT_TRUE(SafeNe(p2, p1));
  EXPECT_FALSE(SafeNe(p2, p2));
}

TEST(SafeCmpTest, Lt) {
  static_assert(SafeLt(-1, 2), "");
  static_assert(SafeLt(-1, 2u), "");
  static_assert(!SafeLt(2, -1), "");
  static_assert(!SafeLt(2u, -1), "");

  static_assert(SafeLt(1, 2), "");
  static_assert(SafeLt(1, 2u), "");
  static_assert(SafeLt(1u, 2), "");
  static_assert(SafeLt(1u, 2u), "");
  static_assert(!SafeLt(2, 1), "");
  static_assert(!SafeLt(2, 1u), "");
  static_assert(!SafeLt(2u, 1), "");
  static_assert(!SafeLt(2u, 1u), "");

  static_assert(!SafeLt(2, 2), "");
  static_assert(!SafeLt(2, 2u), "");
  static_assert(!SafeLt(2u, 2), "");
  static_assert(!SafeLt(2u, 2u), "");

  static_assert(!SafeLt(imin, imin), "");
  static_assert(SafeLt(imin, umax), "");
  static_assert(!SafeLt(umax, imin), "");
  static_assert(!SafeLt(umax, umax), "");

  static_assert(!SafeLt(m1, m1), "");
  static_assert(SafeLt(m1, umax), "");
  static_assert(!SafeLt(umax, m1), "");
  static_assert(!SafeLt(umax, umax), "");

  static_assert(SafeLt(1, 2), "");
  static_assert(SafeLt(1, 2.0), "");
  static_assert(SafeLt(1.0, 2), "");
  static_assert(SafeLt(1.0, 2.0), "");
  static_assert(!SafeLt(2, 1), "");
  static_assert(!SafeLt(2, 1.0), "");
  static_assert(!SafeLt(2.0, 1), "");
  static_assert(!SafeLt(2.0, 1.0), "");

  static_assert(!SafeLt(2, 2), "");
  static_assert(!SafeLt(2, 2.0), "");
  static_assert(!SafeLt(2.0, 2), "");
  static_assert(!SafeLt(2.0, 2.0), "");

  EXPECT_FALSE(SafeLt(p1, p1));
  EXPECT_TRUE(SafeLt(p1, p2));
  EXPECT_FALSE(SafeLt(p2, p1));
  EXPECT_FALSE(SafeLt(p2, p2));
}

TEST(SafeCmpTest, Le) {
  static_assert(SafeLe(-1, 2), "");
  static_assert(SafeLe(-1, 2u), "");
  static_assert(!SafeLe(2, -1), "");
  static_assert(!SafeLe(2u, -1), "");

  static_assert(SafeLe(1, 2), "");
  static_assert(SafeLe(1, 2u), "");
  static_assert(SafeLe(1u, 2), "");
  static_assert(SafeLe(1u, 2u), "");
  static_assert(!SafeLe(2, 1), "");
  static_assert(!SafeLe(2, 1u), "");
  static_assert(!SafeLe(2u, 1), "");
  static_assert(!SafeLe(2u, 1u), "");

  static_assert(SafeLe(2, 2), "");
  static_assert(SafeLe(2, 2u), "");
  static_assert(SafeLe(2u, 2), "");
  static_assert(SafeLe(2u, 2u), "");

  static_assert(SafeLe(imin, imin), "");
  static_assert(SafeLe(imin, umax), "");
  static_assert(!SafeLe(umax, imin), "");
  static_assert(SafeLe(umax, umax), "");

  static_assert(SafeLe(m1, m1), "");
  static_assert(SafeLe(m1, umax), "");
  static_assert(!SafeLe(umax, m1), "");
  static_assert(SafeLe(umax, umax), "");

  static_assert(SafeLe(1, 2), "");
  static_assert(SafeLe(1, 2.0), "");
  static_assert(SafeLe(1.0, 2), "");
  static_assert(SafeLe(1.0, 2.0), "");
  static_assert(!SafeLe(2, 1), "");
  static_assert(!SafeLe(2, 1.0), "");
  static_assert(!SafeLe(2.0, 1), "");
  static_assert(!SafeLe(2.0, 1.0), "");

  static_assert(SafeLe(2, 2), "");
  static_assert(SafeLe(2, 2.0), "");
  static_assert(SafeLe(2.0, 2), "");
  static_assert(SafeLe(2.0, 2.0), "");

  EXPECT_TRUE(SafeLe(p1, p1));
  EXPECT_TRUE(SafeLe(p1, p2));
  EXPECT_FALSE(SafeLe(p2, p1));
  EXPECT_TRUE(SafeLe(p2, p2));
}

TEST(SafeCmpTest, Gt) {
  static_assert(!SafeGt(-1, 2), "");
  static_assert(!SafeGt(-1, 2u), "");
  static_assert(SafeGt(2, -1), "");
  static_assert(SafeGt(2u, -1), "");

  static_assert(!SafeGt(1, 2), "");
  static_assert(!SafeGt(1, 2u), "");
  static_assert(!SafeGt(1u, 2), "");
  static_assert(!SafeGt(1u, 2u), "");
  static_assert(SafeGt(2, 1), "");
  static_assert(SafeGt(2, 1u), "");
  static_assert(SafeGt(2u, 1), "");
  static_assert(SafeGt(2u, 1u), "");

  static_assert(!SafeGt(2, 2), "");
  static_assert(!SafeGt(2, 2u), "");
  static_assert(!SafeGt(2u, 2), "");
  static_assert(!SafeGt(2u, 2u), "");

  static_assert(!SafeGt(imin, imin), "");
  static_assert(!SafeGt(imin, umax), "");
  static_assert(SafeGt(umax, imin), "");
  static_assert(!SafeGt(umax, umax), "");

  static_assert(!SafeGt(m1, m1), "");
  static_assert(!SafeGt(m1, umax), "");
  static_assert(SafeGt(umax, m1), "");
  static_assert(!SafeGt(umax, umax), "");

  static_assert(!SafeGt(1, 2), "");
  static_assert(!SafeGt(1, 2.0), "");
  static_assert(!SafeGt(1.0, 2), "");
  static_assert(!SafeGt(1.0, 2.0), "");
  static_assert(SafeGt(2, 1), "");
  static_assert(SafeGt(2, 1.0), "");
  static_assert(SafeGt(2.0, 1), "");
  static_assert(SafeGt(2.0, 1.0), "");

  static_assert(!SafeGt(2, 2), "");
  static_assert(!SafeGt(2, 2.0), "");
  static_assert(!SafeGt(2.0, 2), "");
  static_assert(!SafeGt(2.0, 2.0), "");

  EXPECT_FALSE(SafeGt(p1, p1));
  EXPECT_FALSE(SafeGt(p1, p2));
  EXPECT_TRUE(SafeGt(p2, p1));
  EXPECT_FALSE(SafeGt(p2, p2));
}

TEST(SafeCmpTest, Ge) {
  static_assert(!SafeGe(-1, 2), "");
  static_assert(!SafeGe(-1, 2u), "");
  static_assert(SafeGe(2, -1), "");
  static_assert(SafeGe(2u, -1), "");

  static_assert(!SafeGe(1, 2), "");
  static_assert(!SafeGe(1, 2u), "");
  static_assert(!SafeGe(1u, 2), "");
  static_assert(!SafeGe(1u, 2u), "");
  static_assert(SafeGe(2, 1), "");
  static_assert(SafeGe(2, 1u), "");
  static_assert(SafeGe(2u, 1), "");
  static_assert(SafeGe(2u, 1u), "");

  static_assert(SafeGe(2, 2), "");
  static_assert(SafeGe(2, 2u), "");
  static_assert(SafeGe(2u, 2), "");
  static_assert(SafeGe(2u, 2u), "");

  static_assert(SafeGe(imin, imin), "");
  static_assert(!SafeGe(imin, umax), "");
  static_assert(SafeGe(umax, imin), "");
  static_assert(SafeGe(umax, umax), "");

  static_assert(SafeGe(m1, m1), "");
  static_assert(!SafeGe(m1, umax), "");
  static_assert(SafeGe(umax, m1), "");
  static_assert(SafeGe(umax, umax), "");

  static_assert(!SafeGe(1, 2), "");
  static_assert(!SafeGe(1, 2.0), "");
  static_assert(!SafeGe(1.0, 2), "");
  static_assert(!SafeGe(1.0, 2.0), "");
  static_assert(SafeGe(2, 1), "");
  static_assert(SafeGe(2, 1.0), "");
  static_assert(SafeGe(2.0, 1), "");
  static_assert(SafeGe(2.0, 1.0), "");

  static_assert(SafeGe(2, 2), "");
  static_assert(SafeGe(2, 2.0), "");
  static_assert(SafeGe(2.0, 2), "");
  static_assert(SafeGe(2.0, 2.0), "");

  EXPECT_TRUE(SafeGe(p1, p1));
  EXPECT_FALSE(SafeGe(p1, p2));
  EXPECT_TRUE(SafeGe(p2, p1));
  EXPECT_TRUE(SafeGe(p2, p2));
}

TEST(SafeCmpTest, Enum) {
  enum E1 { e1 = 13 };
  enum { e2 = 13 };
  enum E3 : unsigned { e3 = 13 };
  enum : unsigned { e4 = 13 };
  static_assert(SafeEq(13, e1), "");
  static_assert(SafeEq(13u, e1), "");
  static_assert(SafeEq(13, e2), "");
  static_assert(SafeEq(13u, e2), "");
  static_assert(SafeEq(13, e3), "");
  static_assert(SafeEq(13u, e3), "");
  static_assert(SafeEq(13, e4), "");
  static_assert(SafeEq(13u, e4), "");
}

}  // namespace rtc
