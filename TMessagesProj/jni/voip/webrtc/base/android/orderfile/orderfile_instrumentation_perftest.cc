// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/orderfile/orderfile_instrumentation.h"

#include <thread>

#include "base/android/library_loader/anchor_functions.h"
#include "base/strings/stringprintf.h"
#include "base/time/time.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "testing/perf/perf_test.h"

namespace base {
namespace android {
namespace orderfile {

namespace {

// Records |addresses_count| distinct addresses |iterations| times, in
// |threads|.
void RunBenchmark(int iterations, int addresses_count, int threads) {
  ResetForTesting();
  auto iterate = [iterations, addresses_count]() {
    for (int i = 0; i < iterations; i++) {
      for (size_t addr = kStartOfTextForTesting;
           addr < static_cast<size_t>(addresses_count); addr += sizeof(int)) {
        RecordAddressForTesting(addr);
      }
    }
  };
  if (threads != 1) {
    for (int i = 0; i < threads - 1; ++i)
      std::thread(iterate).detach();
  }
  auto tick = base::TimeTicks::Now();
  iterate();
  auto tock = base::TimeTicks::Now();
  double nanos = static_cast<double>((tock - tick).InNanoseconds());
  auto ns_per_call =
      nanos / (iterations * static_cast<double>(addresses_count));
  auto modifier =
      base::StringPrintf("_%d_%d_%d", iterations, addresses_count, threads);
  perf_test::PrintResult("RecordAddressCostPerCall", modifier, "", ns_per_call,
                         "ns", true);
}

}  // namespace

class OrderfileInstrumentationTest : public ::testing::Test {
  // Any tests need to run ResetForTesting() when they start. Because this
  // perftest is built with instrumentation enabled, all code including
  // ::testing::Test is instrumented. If ResetForTesting() is called earlier,
  // for example in setUp(), any test harness code between setUp() and the
  // actual test will change the instrumentation offset record in unpredictable
  // ways and make these tests unreliable.
};

TEST_F(OrderfileInstrumentationTest, RecordOffset) {
  ResetForTesting();
  size_t first = 1234, second = 1456;
  RecordAddressForTesting(first);
  RecordAddressForTesting(second);
  RecordAddressForTesting(first);      // No duplicates.
  RecordAddressForTesting(first + 1);  // 4 bytes granularity.
  Disable();

  auto reached = GetOrderedOffsetsForTesting();
  EXPECT_EQ(2UL, reached.size());
  EXPECT_EQ(first - kStartOfTextForTesting, reached[0]);
  EXPECT_EQ(second - kStartOfTextForTesting, reached[1]);
}

TEST_F(OrderfileInstrumentationTest, RecordingStops) {
  ResetForTesting();
  size_t first = 1234, second = 1456, third = 1789;
  RecordAddressForTesting(first);
  RecordAddressForTesting(second);
  Disable();
  RecordAddressForTesting(third);

  auto reached = GetOrderedOffsetsForTesting();
  ASSERT_EQ(2UL, reached.size());
  ASSERT_EQ(first - kStartOfTextForTesting, reached[0]);
  ASSERT_EQ(second - kStartOfTextForTesting, reached[1]);
}

TEST_F(OrderfileInstrumentationTest, OutOfBounds) {
  ResetForTesting();
  EXPECT_DEATH(RecordAddressForTesting(kEndOfTextForTesting + 100), "");
  EXPECT_DEATH(RecordAddressForTesting(kStartOfTextForTesting - 100), "");
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_10_10000) {
  RunBenchmark(10, 10000, 1);
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_100_10000) {
  RunBenchmark(100, 10000, 1);
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_10_100000) {
  RunBenchmark(10, 100000, 1);
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_100_100000) {
  RunBenchmark(100, 100000, 1);
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_1000_100000_2) {
  RunBenchmark(1000, 100000, 2);
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_1000_100000_3) {
  RunBenchmark(1000, 100000, 3);
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_1000_100000_4) {
  RunBenchmark(1000, 100000, 4);
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_1000_100000_6) {
  RunBenchmark(1000, 100000, 6);
}

}  // namespace orderfile
}  // namespace android
}  // namespace base

// Custom runner implementation since base's one requires JNI on Android.
int main(int argc, char** argv) {
  testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
