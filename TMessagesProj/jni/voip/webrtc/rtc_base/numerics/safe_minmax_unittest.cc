/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/safe_minmax.h"

#include <algorithm>
#include <limits>

#include "test/gtest.h"

namespace rtc {

namespace {

// Functions that check that SafeMin(), SafeMax(), and SafeClamp() return the
// specified type. The functions that end in "R" use an explicitly given return
// type.

template <typename T1, typename T2, typename Tmin, typename Tmax>
constexpr bool TypeCheckMinMax() {
  return std::is_same<decltype(SafeMin(std::declval<T1>(), std::declval<T2>())),
                      Tmin>::value &&
         std::is_same<decltype(SafeMax(std::declval<T1>(), std::declval<T2>())),
                      Tmax>::value;
}

template <typename T1, typename T2, typename R>
constexpr bool TypeCheckMinR() {
  return std::is_same<
      decltype(SafeMin<R>(std::declval<T1>(), std::declval<T2>())), R>::value;
}

template <typename T1, typename T2, typename R>
constexpr bool TypeCheckMaxR() {
  return std::is_same<
      decltype(SafeMax<R>(std::declval<T1>(), std::declval<T2>())), R>::value;
}

template <typename T, typename L, typename H, typename R>
constexpr bool TypeCheckClamp() {
  return std::is_same<decltype(SafeClamp(std::declval<T>(), std::declval<L>(),
                                         std::declval<H>())),
                      R>::value;
}

template <typename T, typename L, typename H, typename R>
constexpr bool TypeCheckClampR() {
  return std::is_same<decltype(SafeClamp<R>(std::declval<T>(),
                                            std::declval<L>(),
                                            std::declval<H>())),
                      R>::value;
}

// clang-format off

// SafeMin/SafeMax: Check that all combinations of signed/unsigned 8/64 bits
// give the correct default result type.
static_assert(TypeCheckMinMax<  int8_t,   int8_t,   int8_t,   int8_t>(), "");
static_assert(TypeCheckMinMax<  int8_t,  uint8_t,   int8_t,  uint8_t>(), "");
static_assert(TypeCheckMinMax<  int8_t,  int64_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckMinMax<  int8_t, uint64_t,   int8_t, uint64_t>(), "");
static_assert(TypeCheckMinMax< uint8_t,   int8_t,   int8_t,  uint8_t>(), "");
static_assert(TypeCheckMinMax< uint8_t,  uint8_t,  uint8_t,  uint8_t>(), "");
static_assert(TypeCheckMinMax< uint8_t,  int64_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckMinMax< uint8_t, uint64_t,  uint8_t, uint64_t>(), "");
static_assert(TypeCheckMinMax< int64_t,   int8_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckMinMax< int64_t,  uint8_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckMinMax< int64_t,  int64_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckMinMax< int64_t, uint64_t,  int64_t, uint64_t>(), "");
static_assert(TypeCheckMinMax<uint64_t,   int8_t,   int8_t, uint64_t>(), "");
static_assert(TypeCheckMinMax<uint64_t,  uint8_t,  uint8_t, uint64_t>(), "");
static_assert(TypeCheckMinMax<uint64_t,  int64_t,  int64_t, uint64_t>(), "");
static_assert(TypeCheckMinMax<uint64_t, uint64_t, uint64_t, uint64_t>(), "");

// SafeClamp: Check that all combinations of signed/unsigned 8/64 bits give the
// correct result type.
static_assert(TypeCheckClamp<  int8_t,   int8_t,   int8_t,   int8_t>(), "");
static_assert(TypeCheckClamp<  int8_t,   int8_t,  uint8_t,   int8_t>(), "");
static_assert(TypeCheckClamp<  int8_t,   int8_t,  int64_t,   int8_t>(), "");
static_assert(TypeCheckClamp<  int8_t,   int8_t, uint64_t,   int8_t>(), "");
static_assert(TypeCheckClamp<  int8_t,  uint8_t,   int8_t,   int8_t>(), "");
static_assert(TypeCheckClamp<  int8_t,  uint8_t,  uint8_t,  uint8_t>(), "");
static_assert(TypeCheckClamp<  int8_t,  uint8_t,  int64_t,  int16_t>(), "");
static_assert(TypeCheckClamp<  int8_t,  uint8_t, uint64_t,  int16_t>(), "");
static_assert(TypeCheckClamp<  int8_t,  int64_t,   int8_t,   int8_t>(), "");
static_assert(TypeCheckClamp<  int8_t,  int64_t,  uint8_t,  int16_t>(), "");
static_assert(TypeCheckClamp<  int8_t,  int64_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckClamp<  int8_t,  int64_t, uint64_t,  int64_t>(), "");
static_assert(TypeCheckClamp<  int8_t, uint64_t,   int8_t,   int8_t>(), "");
static_assert(TypeCheckClamp<  int8_t, uint64_t,  uint8_t,  int16_t>(), "");
static_assert(TypeCheckClamp<  int8_t, uint64_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckClamp<  int8_t, uint64_t, uint64_t, uint64_t>(), "");
static_assert(TypeCheckClamp< uint8_t,   int8_t,   int8_t,   int8_t>(), "");
static_assert(TypeCheckClamp< uint8_t,   int8_t,  uint8_t,  uint8_t>(), "");
static_assert(TypeCheckClamp< uint8_t,   int8_t,  int64_t,  int16_t>(), "");
static_assert(TypeCheckClamp< uint8_t,   int8_t, uint64_t,  uint8_t>(), "");
static_assert(TypeCheckClamp< uint8_t,  uint8_t,   int8_t,  uint8_t>(), "");
static_assert(TypeCheckClamp< uint8_t,  uint8_t,  uint8_t,  uint8_t>(), "");
static_assert(TypeCheckClamp< uint8_t,  uint8_t,  int64_t,  uint8_t>(), "");
static_assert(TypeCheckClamp< uint8_t,  uint8_t, uint64_t,  uint8_t>(), "");
static_assert(TypeCheckClamp< uint8_t,  int64_t,   int8_t,   int8_t>(), "");
static_assert(TypeCheckClamp< uint8_t,  int64_t,  uint8_t,  uint8_t>(), "");
static_assert(TypeCheckClamp< uint8_t,  int64_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckClamp< uint8_t,  int64_t, uint64_t, uint64_t>(), "");
static_assert(TypeCheckClamp< uint8_t, uint64_t,   int8_t,  uint8_t>(), "");
static_assert(TypeCheckClamp< uint8_t, uint64_t,  uint8_t,  uint8_t>(), "");
static_assert(TypeCheckClamp< uint8_t, uint64_t,  int64_t, uint64_t>(), "");
static_assert(TypeCheckClamp< uint8_t, uint64_t, uint64_t, uint64_t>(), "");
static_assert(TypeCheckClamp< int64_t,   int8_t,   int8_t,   int8_t>(), "");
static_assert(TypeCheckClamp< int64_t,   int8_t,  uint8_t,  int16_t>(), "");
static_assert(TypeCheckClamp< int64_t,   int8_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckClamp< int64_t,   int8_t, uint64_t,  int64_t>(), "");
static_assert(TypeCheckClamp< int64_t,  uint8_t,   int8_t,   int8_t>(), "");
static_assert(TypeCheckClamp< int64_t,  uint8_t,  uint8_t,  int16_t>(), "");
static_assert(TypeCheckClamp< int64_t,  uint8_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckClamp< int64_t,  uint8_t, uint64_t,  int64_t>(), "");
static_assert(TypeCheckClamp< int64_t,  int64_t,   int8_t,  int64_t>(), "");
static_assert(TypeCheckClamp< int64_t,  int64_t,  uint8_t,  int64_t>(), "");
static_assert(TypeCheckClamp< int64_t,  int64_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckClamp< int64_t,  int64_t, uint64_t,  int64_t>(), "");
static_assert(TypeCheckClamp< int64_t, uint64_t,   int8_t,   int8_t>(), "");
static_assert(TypeCheckClamp< int64_t, uint64_t,  uint8_t,  int16_t>(), "");
static_assert(TypeCheckClamp< int64_t, uint64_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckClamp< int64_t, uint64_t, uint64_t, uint64_t>(), "");
static_assert(TypeCheckClamp<uint64_t,   int8_t,   int8_t,   int8_t>(), "");
static_assert(TypeCheckClamp<uint64_t,   int8_t,  uint8_t,  uint8_t>(), "");
static_assert(TypeCheckClamp<uint64_t,   int8_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckClamp<uint64_t,   int8_t, uint64_t, uint64_t>(), "");
static_assert(TypeCheckClamp<uint64_t,  uint8_t,   int8_t,  uint8_t>(), "");
static_assert(TypeCheckClamp<uint64_t,  uint8_t,  uint8_t,  uint8_t>(), "");
static_assert(TypeCheckClamp<uint64_t,  uint8_t,  int64_t, uint64_t>(), "");
static_assert(TypeCheckClamp<uint64_t,  uint8_t, uint64_t, uint64_t>(), "");
static_assert(TypeCheckClamp<uint64_t,  int64_t,   int8_t,   int8_t>(), "");
static_assert(TypeCheckClamp<uint64_t,  int64_t,  uint8_t,  uint8_t>(), "");
static_assert(TypeCheckClamp<uint64_t,  int64_t,  int64_t,  int64_t>(), "");
static_assert(TypeCheckClamp<uint64_t,  int64_t, uint64_t, uint64_t>(), "");
static_assert(TypeCheckClamp<uint64_t, uint64_t,   int8_t,  uint8_t>(), "");
static_assert(TypeCheckClamp<uint64_t, uint64_t,  uint8_t,  uint8_t>(), "");
static_assert(TypeCheckClamp<uint64_t, uint64_t,  int64_t, uint64_t>(), "");
static_assert(TypeCheckClamp<uint64_t, uint64_t, uint64_t, uint64_t>(), "");

enum DefaultE { kFoo = -17 };
enum UInt8E : uint8_t { kBar = 17 };

// SafeMin/SafeMax: Check that we can use enum types.
static_assert(TypeCheckMinMax<unsigned, unsigned, unsigned, unsigned>(), "");
static_assert(TypeCheckMinMax<unsigned, DefaultE,      int, unsigned>(), "");
static_assert(TypeCheckMinMax<unsigned,   UInt8E,  uint8_t, unsigned>(), "");
static_assert(TypeCheckMinMax<DefaultE, unsigned,      int, unsigned>(), "");
static_assert(TypeCheckMinMax<DefaultE, DefaultE,      int,      int>(), "");
static_assert(TypeCheckMinMax<DefaultE,   UInt8E,      int,      int>(), "");
static_assert(TypeCheckMinMax<  UInt8E, unsigned,  uint8_t, unsigned>(), "");
static_assert(TypeCheckMinMax<  UInt8E, DefaultE,     int,       int>(), "");
static_assert(TypeCheckMinMax<  UInt8E,   UInt8E,  uint8_t,  uint8_t>(), "");

// SafeClamp: Check that we can use enum types.
static_assert(TypeCheckClamp<unsigned, unsigned, unsigned, unsigned>(), "");
static_assert(TypeCheckClamp<unsigned, unsigned, DefaultE, unsigned>(), "");
static_assert(TypeCheckClamp<unsigned, unsigned,   UInt8E,  uint8_t>(), "");
static_assert(TypeCheckClamp<unsigned, DefaultE, unsigned, unsigned>(), "");
static_assert(TypeCheckClamp<unsigned, DefaultE, DefaultE,      int>(), "");
static_assert(TypeCheckClamp<unsigned, DefaultE,   UInt8E,  uint8_t>(), "");
static_assert(TypeCheckClamp<unsigned,   UInt8E, unsigned, unsigned>(), "");
static_assert(TypeCheckClamp<unsigned,   UInt8E, DefaultE, unsigned>(), "");
static_assert(TypeCheckClamp<unsigned,   UInt8E,   UInt8E,  uint8_t>(), "");
static_assert(TypeCheckClamp<DefaultE, unsigned, unsigned, unsigned>(), "");
static_assert(TypeCheckClamp<DefaultE, unsigned, DefaultE,      int>(), "");
static_assert(TypeCheckClamp<DefaultE, unsigned,   UInt8E,  int16_t>(), "");
static_assert(TypeCheckClamp<DefaultE, DefaultE, unsigned,      int>(), "");
static_assert(TypeCheckClamp<DefaultE, DefaultE, DefaultE,      int>(), "");
static_assert(TypeCheckClamp<DefaultE, DefaultE,   UInt8E,      int>(), "");
static_assert(TypeCheckClamp<DefaultE,   UInt8E, unsigned,      int>(), "");
static_assert(TypeCheckClamp<DefaultE,   UInt8E, DefaultE,      int>(), "");
static_assert(TypeCheckClamp<DefaultE,   UInt8E,   UInt8E,  int16_t>(), "");
static_assert(TypeCheckClamp<  UInt8E, unsigned, unsigned, unsigned>(), "");
static_assert(TypeCheckClamp<  UInt8E, unsigned, DefaultE, unsigned>(), "");
static_assert(TypeCheckClamp<  UInt8E, unsigned,   UInt8E,  uint8_t>(), "");
static_assert(TypeCheckClamp<  UInt8E, DefaultE, unsigned, unsigned>(), "");
static_assert(TypeCheckClamp<  UInt8E, DefaultE, DefaultE,      int>(), "");
static_assert(TypeCheckClamp<  UInt8E, DefaultE,   UInt8E,  uint8_t>(), "");
static_assert(TypeCheckClamp<  UInt8E,   UInt8E, unsigned,  uint8_t>(), "");
static_assert(TypeCheckClamp<  UInt8E,   UInt8E, DefaultE,  uint8_t>(), "");
static_assert(TypeCheckClamp<  UInt8E,   UInt8E,   UInt8E,  uint8_t>(), "");

using ld = long double;

// SafeMin/SafeMax: Check that all floating-point combinations give the
// correct result type.
static_assert(TypeCheckMinMax< float,  float,  float,  float>(), "");
static_assert(TypeCheckMinMax< float, double, double, double>(), "");
static_assert(TypeCheckMinMax< float,     ld,     ld,     ld>(), "");
static_assert(TypeCheckMinMax<double,  float, double, double>(), "");
static_assert(TypeCheckMinMax<double, double, double, double>(), "");
static_assert(TypeCheckMinMax<double,     ld,     ld,     ld>(), "");
static_assert(TypeCheckMinMax<    ld,  float,     ld,     ld>(), "");
static_assert(TypeCheckMinMax<    ld, double,     ld,     ld>(), "");
static_assert(TypeCheckMinMax<    ld,     ld,     ld,     ld>(), "");

// SafeClamp: Check that all floating-point combinations give the correct
// result type.
static_assert(TypeCheckClamp< float,  float,  float,  float>(), "");
static_assert(TypeCheckClamp< float,  float, double, double>(), "");
static_assert(TypeCheckClamp< float,  float,     ld,     ld>(), "");
static_assert(TypeCheckClamp< float, double,  float, double>(), "");
static_assert(TypeCheckClamp< float, double, double, double>(), "");
static_assert(TypeCheckClamp< float, double,     ld,     ld>(), "");
static_assert(TypeCheckClamp< float,     ld,  float,     ld>(), "");
static_assert(TypeCheckClamp< float,     ld, double,     ld>(), "");
static_assert(TypeCheckClamp< float,     ld,     ld,     ld>(), "");
static_assert(TypeCheckClamp<double,  float,  float, double>(), "");
static_assert(TypeCheckClamp<double,  float, double, double>(), "");
static_assert(TypeCheckClamp<double,  float,     ld,     ld>(), "");
static_assert(TypeCheckClamp<double, double,  float, double>(), "");
static_assert(TypeCheckClamp<double, double, double, double>(), "");
static_assert(TypeCheckClamp<double, double,     ld,     ld>(), "");
static_assert(TypeCheckClamp<double,     ld,  float,     ld>(), "");
static_assert(TypeCheckClamp<double,     ld, double,     ld>(), "");
static_assert(TypeCheckClamp<double,     ld,     ld,     ld>(), "");
static_assert(TypeCheckClamp<    ld,  float,  float,     ld>(), "");
static_assert(TypeCheckClamp<    ld,  float, double,     ld>(), "");
static_assert(TypeCheckClamp<    ld,  float,     ld,     ld>(), "");
static_assert(TypeCheckClamp<    ld, double,  float,     ld>(), "");
static_assert(TypeCheckClamp<    ld, double, double,     ld>(), "");
static_assert(TypeCheckClamp<    ld, double,     ld,     ld>(), "");
static_assert(TypeCheckClamp<    ld,     ld,  float,     ld>(), "");
static_assert(TypeCheckClamp<    ld,     ld, double,     ld>(), "");
static_assert(TypeCheckClamp<    ld,     ld,     ld,     ld>(), "");

// clang-format on

// SafeMin/SafeMax: Check some cases of explicitly specified return type. The
// commented-out lines give compilation errors due to the requested return type
// being too small or requiring an int<->float conversion.
static_assert(TypeCheckMinR<int8_t, int8_t, int16_t>(), "");
// static_assert(TypeCheckMinR<int8_t, int8_t, float>(), "");
static_assert(TypeCheckMinR<uint32_t, uint64_t, uint32_t>(), "");
// static_assert(TypeCheckMaxR<uint64_t, float, float>(), "");
// static_assert(TypeCheckMaxR<uint64_t, double, float>(), "");
static_assert(TypeCheckMaxR<uint32_t, int32_t, uint32_t>(), "");
// static_assert(TypeCheckMaxR<uint32_t, int32_t, int32_t>(), "");

// SafeClamp: Check some cases of explicitly specified return type. The
// commented-out lines give compilation errors due to the requested return type
// being too small.
static_assert(TypeCheckClampR<int16_t, int8_t, uint8_t, int16_t>(), "");
static_assert(TypeCheckClampR<int16_t, int8_t, uint8_t, int32_t>(), "");
// static_assert(TypeCheckClampR<int16_t, int8_t, uint8_t, uint32_t>(), "");

template <typename T1, typename T2, typename Tmin, typename Tmax>
constexpr bool CheckMinMax(T1 a, T2 b, Tmin min, Tmax max) {
  return TypeCheckMinMax<T1, T2, Tmin, Tmax>() && SafeMin(a, b) == min &&
         SafeMax(a, b) == max;
}

template <typename T, typename L, typename H, typename R>
bool CheckClamp(T x, L min, H max, R clamped) {
  return TypeCheckClamp<T, L, H, R>() && SafeClamp(x, min, max) == clamped;
}

// SafeMin/SafeMax: Check a few values.
static_assert(CheckMinMax(int8_t{1}, int8_t{-1}, int8_t{-1}, int8_t{1}), "");
static_assert(CheckMinMax(uint8_t{1}, int8_t{-1}, int8_t{-1}, uint8_t{1}), "");
static_assert(CheckMinMax(uint8_t{5}, uint64_t{2}, uint8_t{2}, uint64_t{5}),
              "");
static_assert(CheckMinMax(std::numeric_limits<int32_t>::min(),
                          std::numeric_limits<uint32_t>::max(),
                          std::numeric_limits<int32_t>::min(),
                          std::numeric_limits<uint32_t>::max()),
              "");
static_assert(CheckMinMax(std::numeric_limits<int32_t>::min(),
                          std::numeric_limits<uint16_t>::max(),
                          std::numeric_limits<int32_t>::min(),
                          int32_t{std::numeric_limits<uint16_t>::max()}),
              "");
// static_assert(CheckMinMax(1.f, 2, 1.f, 2.f), "");
static_assert(CheckMinMax(1.f, 0.0, 0.0, 1.0), "");

// SafeClamp: Check a few values.
TEST(SafeMinmaxTest, Clamp) {
  EXPECT_TRUE(CheckClamp(int32_t{-1000000}, std::numeric_limits<int16_t>::min(),
                         std::numeric_limits<int16_t>::max(),
                         std::numeric_limits<int16_t>::min()));
  EXPECT_TRUE(CheckClamp(uint32_t{1000000}, std::numeric_limits<int16_t>::min(),
                         std::numeric_limits<int16_t>::max(),
                         std::numeric_limits<int16_t>::max()));
  EXPECT_TRUE(CheckClamp(3.f, -1.0, 1.f, 1.0));
  EXPECT_TRUE(CheckClamp(3.0, -1.f, 1.f, 1.0));
}

}  // namespace

// These functions aren't used in the tests, but it's useful to look at the
// compiler output for them, and verify that (1) the same-signedness Test*Safe
// functions result in exactly the same code as their Test*Ref counterparts,
// and that (2) the mixed-signedness Test*Safe functions have just a few extra
// arithmetic and logic instructions (but no extra control flow instructions).

// clang-format off
int32_t  TestMinRef(  int32_t a,  int32_t b) { return std::min(a, b); }
uint32_t TestMinRef( uint32_t a, uint32_t b) { return std::min(a, b); }
int32_t  TestMinSafe( int32_t a,  int32_t b) { return SafeMin(a, b); }
int32_t  TestMinSafe( int32_t a, uint32_t b) { return SafeMin(a, b); }
int32_t  TestMinSafe(uint32_t a,  int32_t b) { return SafeMin(a, b); }
uint32_t TestMinSafe(uint32_t a, uint32_t b) { return SafeMin(a, b); }
// clang-format on

int32_t TestClampRef(int32_t x, int32_t a, int32_t b) {
  return std::max(a, std::min(x, b));
}
uint32_t TestClampRef(uint32_t x, uint32_t a, uint32_t b) {
  return std::max(a, std::min(x, b));
}
int32_t TestClampSafe(int32_t x, int32_t a, int32_t b) {
  return SafeClamp(x, a, b);
}
int32_t TestClampSafe(int32_t x, int32_t a, uint32_t b) {
  return SafeClamp(x, a, b);
}
int32_t TestClampSafe(int32_t x, uint32_t a, int32_t b) {
  return SafeClamp(x, a, b);
}
uint32_t TestClampSafe(int32_t x, uint32_t a, uint32_t b) {
  return SafeClamp(x, a, b);
}
int32_t TestClampSafe(uint32_t x, int32_t a, int32_t b) {
  return SafeClamp(x, a, b);
}
uint32_t TestClampSafe(uint32_t x, int32_t a, uint32_t b) {
  return SafeClamp(x, a, b);
}
int32_t TestClampSafe(uint32_t x, uint32_t a, int32_t b) {
  return SafeClamp(x, a, b);
}
uint32_t TestClampSafe(uint32_t x, uint32_t a, uint32_t b) {
  return SafeClamp(x, a, b);
}

}  // namespace rtc
