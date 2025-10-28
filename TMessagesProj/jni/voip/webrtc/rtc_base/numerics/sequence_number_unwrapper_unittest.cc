/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/sequence_number_unwrapper.h"

#include <cstdint>

#include "test/gtest.h"

namespace webrtc {

TEST(SeqNumUnwrapper, PreserveStartValue) {
  SeqNumUnwrapper<uint8_t> unwrapper;
  EXPECT_EQ(123, unwrapper.Unwrap(123));
}

TEST(SeqNumUnwrapper, ForwardWrap) {
  SeqNumUnwrapper<uint8_t> unwrapper;
  EXPECT_EQ(255, unwrapper.Unwrap(255));
  EXPECT_EQ(256, unwrapper.Unwrap(0));
}

TEST(SeqNumUnwrapper, ForwardWrapWithDivisor) {
  SeqNumUnwrapper<uint8_t, 33> unwrapper;
  EXPECT_EQ(30, unwrapper.Unwrap(30));
  EXPECT_EQ(36, unwrapper.Unwrap(3));
}

TEST(SeqNumUnwrapper, BackWardWrap) {
  SeqNumUnwrapper<uint8_t> unwrapper;
  EXPECT_EQ(0, unwrapper.Unwrap(0));
  EXPECT_EQ(-2, unwrapper.Unwrap(254));
}

TEST(SeqNumUnwrapper, BackWardWrapWithDivisor) {
  SeqNumUnwrapper<uint8_t, 33> unwrapper;
  EXPECT_EQ(0, unwrapper.Unwrap(0));
  EXPECT_EQ(-2, unwrapper.Unwrap(31));
}

TEST(SeqNumUnwrapper, Unwrap) {
  SeqNumUnwrapper<uint16_t> unwrapper;
  const uint16_t kMax = std::numeric_limits<uint16_t>::max();
  const uint16_t kMaxDist = kMax / 2 + 1;

  EXPECT_EQ(0, unwrapper.Unwrap(0));
  EXPECT_EQ(kMaxDist, unwrapper.Unwrap(kMaxDist));
  EXPECT_EQ(0, unwrapper.Unwrap(0));

  EXPECT_EQ(kMaxDist, unwrapper.Unwrap(kMaxDist));
  EXPECT_EQ(kMax, unwrapper.Unwrap(kMax));
  EXPECT_EQ(kMax + 1, unwrapper.Unwrap(0));
  EXPECT_EQ(kMax, unwrapper.Unwrap(kMax));
  EXPECT_EQ(kMaxDist, unwrapper.Unwrap(kMaxDist));
  EXPECT_EQ(0, unwrapper.Unwrap(0));
}

TEST(SeqNumUnwrapper, UnwrapOddDivisor) {
  SeqNumUnwrapper<uint8_t, 11> unwrapper;

  EXPECT_EQ(10, unwrapper.Unwrap(10));
  EXPECT_EQ(11, unwrapper.Unwrap(0));
  EXPECT_EQ(16, unwrapper.Unwrap(5));
  EXPECT_EQ(21, unwrapper.Unwrap(10));
  EXPECT_EQ(22, unwrapper.Unwrap(0));
  EXPECT_EQ(17, unwrapper.Unwrap(6));
  EXPECT_EQ(12, unwrapper.Unwrap(1));
  EXPECT_EQ(7, unwrapper.Unwrap(7));
  EXPECT_EQ(2, unwrapper.Unwrap(2));
  EXPECT_EQ(0, unwrapper.Unwrap(0));
}

TEST(SeqNumUnwrapper, ManyForwardWraps) {
  const int kLargeNumber = 4711;
  const int kMaxStep = kLargeNumber / 2;
  const int kNumWraps = 100;
  SeqNumUnwrapper<uint16_t, kLargeNumber> unwrapper;

  uint16_t next_unwrap = 0;
  int64_t expected = 0;
  for (int i = 0; i < kNumWraps * 2 + 1; ++i) {
    EXPECT_EQ(expected, unwrapper.Unwrap(next_unwrap));
    expected += kMaxStep;
    next_unwrap = (next_unwrap + kMaxStep) % kLargeNumber;
  }
}

TEST(SeqNumUnwrapper, ManyBackwardWraps) {
  const int kLargeNumber = 4711;
  const int kMaxStep = kLargeNumber / 2;
  const int kNumWraps = 100;
  SeqNumUnwrapper<uint16_t, kLargeNumber> unwrapper;

  uint16_t next_unwrap = 0;
  int64_t expected = 0;
  for (uint16_t i = 0; i < kNumWraps * 2 + 1; ++i) {
    EXPECT_EQ(expected, unwrapper.Unwrap(next_unwrap));
    expected -= kMaxStep;
    next_unwrap = (next_unwrap + kMaxStep + 1) % kLargeNumber;
  }
}

TEST(SeqNumUnwrapper, Reset) {
  const uint16_t kMax = std::numeric_limits<uint16_t>::max();
  const uint16_t kMaxStep = kMax / 2;
  SeqNumUnwrapper<uint16_t> unwrapper;
  EXPECT_EQ(10, unwrapper.Unwrap(10));
  EXPECT_EQ(kMaxStep + 10, unwrapper.Unwrap(kMaxStep + 10));

  EXPECT_EQ(kMax + 3, unwrapper.PeekUnwrap(2));
  unwrapper.Reset();
  // After Reset() the range is reset back to the start.
  EXPECT_EQ(2, unwrapper.PeekUnwrap(2));
}

TEST(SeqNumUnwrapper, PeekUnwrap) {
  const uint16_t kMax = std::numeric_limits<uint16_t>::max();
  const uint16_t kMaxStep = kMax / 2;
  const uint16_t kMaxDist = kMaxStep + 1;
  SeqNumUnwrapper<uint16_t> unwrapper;
  // No previous unwraps, so PeekUnwrap(x) == x.
  EXPECT_EQ(10, unwrapper.PeekUnwrap(10));
  EXPECT_EQ(kMaxDist + 10, unwrapper.PeekUnwrap(kMaxDist + 10));

  EXPECT_EQ(10, unwrapper.Unwrap(10));
  EXPECT_EQ(12, unwrapper.PeekUnwrap(12));
  // State should not have updated, so kMaxDist + 12 should be negative.
  EXPECT_EQ(-kMaxDist + 12, unwrapper.Unwrap(kMaxDist + 12));

  // Test PeekUnwrap after around.
  unwrapper.Reset();
  EXPECT_EQ(kMaxStep, unwrapper.Unwrap(kMaxStep));
  EXPECT_EQ(2 * kMaxStep, unwrapper.Unwrap(2 * kMaxStep));
  EXPECT_EQ(kMax + 1, unwrapper.PeekUnwrap(0));
  // Wrap back to last range.
  EXPECT_EQ(kMax - 3, unwrapper.PeekUnwrap(kMax - 3));
}

}  // namespace webrtc
