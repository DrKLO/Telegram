// Copyright 2019 The Chromium Authors. All rights reserved.
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
const size_t kStep = sizeof(int);

void CallRecordAddress(int iterations, size_t addr_count) {
  for (int i = 0; i < iterations; i++) {
    for (size_t caller_addr = kStartOfTextForTesting + kStep;
         caller_addr < addr_count; caller_addr += kStep) {
      for (size_t callee_addr = caller_addr + kStep; callee_addr < addr_count;
           callee_addr += kStep) {
        RecordAddressForTesting(callee_addr, caller_addr);
      }
    }
  }
}

void RunBenchmark(size_t iterations, size_t addresses_count, int threads) {
  ResetForTesting();
  auto iterate = [iterations, addresses_count]() {
    CallRecordAddress(iterations, addresses_count);
  };
  if (threads != 1) {
    for (int i = 0; i < threads - 1; ++i)
      std::thread(iterate).detach();
  }
  auto tick = base::TimeTicks::Now();
  iterate();
  auto tock = base::TimeTicks::Now();
  double nanos = static_cast<double>((tock - tick).InNanoseconds());
  size_t addresses = (addresses_count - kStartOfTextForTesting - 1) / kStep;
  double calls_count = (addresses * (addresses - 1)) / 2;
  auto ns_per_call = nanos / (iterations * calls_count);
  auto modifier =
      base::StringPrintf("_%zu_%zu_%d", iterations, addresses_count, threads);
  perf_test::PrintResult("RecordAddressCostPerCall", modifier, "", ns_per_call,
                         "ns", true);
}

void CheckValid(size_t iterations, size_t addr_count) {
  // |reached| is expected to be ordered by callee offset
  auto reached = GetOrderedOffsetsForTesting();
  size_t buckets_per_callee = 9;  // kTotalBuckets * 2 + 1.
  size_t callers_per_callee = 3;
  size_t addresses = (addr_count - kStartOfTextForTesting - 1) / kStep;
  EXPECT_EQ((addresses - 1) * buckets_per_callee, reached.size());
  size_t expected_callee = kStartOfTextForTesting + 2 * kStep;

  for (size_t i = 0; i < reached.size(); i += buckets_per_callee) {
    EXPECT_EQ(reached[i] / 4, (expected_callee - kStartOfTextForTesting) / 4);
    size_t callee_index = i / buckets_per_callee;
    for (size_t j = 0; j < callers_per_callee; j++) {
      EXPECT_EQ(reached[i + j * 2 + 1],
                j > callee_index ? 0UL : (j + 1) * kStep);
      EXPECT_EQ(reached[i + j * 2 + 2], j > callee_index ? 0UL : iterations);
    }
    size_t misses = callee_index > 2 ? (callee_index - 2) * iterations : 0UL;
    EXPECT_EQ(reached[i + 7], 0UL);
    EXPECT_EQ(reached[i + 8], misses);
    expected_callee += kStep;
  }
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

TEST_F(OrderfileInstrumentationTest, SequentialTest_10_5000) {
  size_t iterations = 10;
  size_t addr_count = 5000;
  ResetForTesting();
  CallRecordAddress(iterations, addr_count);
  Disable();
  CheckValid(iterations, addr_count);
}

TEST_F(OrderfileInstrumentationTest, SequentialTest_10_10000) {
  size_t iterations = 10;
  size_t addr_count = 10000;
  ResetForTesting();
  CallRecordAddress(iterations, addr_count);
  Disable();
  CheckValid(iterations, addr_count);
}

TEST_F(OrderfileInstrumentationTest, OutOfBoundsCaller) {
  ResetForTesting();
  RecordAddressForTesting(1234, kStartOfTextForTesting);
  RecordAddressForTesting(1234, kEndOfTextForTesting + 1);
  Disable();
  auto reached = GetOrderedOffsetsForTesting();
  EXPECT_EQ(reached.size(), 9UL);
  EXPECT_EQ(reached[0] / 4, (1234 - kStartOfTextForTesting) / 4);
  for (size_t i = 1; i < 8; i++) {
    EXPECT_EQ(reached[i], 0UL);
  }
  EXPECT_EQ(reached[8], 2UL);
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_10_2000) {
  RunBenchmark(10, 2000, 1);
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_100_2000) {
  RunBenchmark(100, 2000, 1);
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_1000_2000_2) {
  RunBenchmark(100, 2000, 2);
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_1000_2000_3) {
  RunBenchmark(100, 2000, 3);
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_1000_2000_4) {
  RunBenchmark(100, 2000, 4);
}

TEST(OrderfileInstrumentationPerfTest, RecordAddress_1000_2000_6) {
  RunBenchmark(100, 2000, 6);
}

}  // namespace orderfile
}  // namespace android
}  // namespace base

// Custom runner implementation since base's one requires JNI on Android.
int main(int argc, char** argv) {
  testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
