// Copyright (c) 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/hash/sha1.h"

#include <stddef.h>
#include <stdint.h>
#include <algorithm>
#include <string>
#include <vector>

#include "base/rand_util.h"
#include "base/strings/string_number_conversions.h"
#include "base/time/time.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "testing/perf/perf_result_reporter.h"

namespace {

constexpr int kBytesPerMegabyte = 1000000;

constexpr char kMetricPrefixSHA1[] = "SHA1.";
constexpr char kMetricRuntime[] = "runtime";
constexpr char kMetricThroughput[] = "throughput";
// Histograms automatically calculate mean, min, max, and standard deviation,
// but not median, so have a separate metric for our manually calculated median.
constexpr char kMetricMedianThroughput[] = "median_throughput";

perf_test::PerfResultReporter SetUpReporter(const std::string& story_name) {
  perf_test::PerfResultReporter reporter(kMetricPrefixSHA1, story_name);
  reporter.RegisterImportantMetric(kMetricRuntime, "us");
  reporter.RegisterImportantMetric(kMetricThroughput, "bytesPerSecond");
  reporter.RegisterImportantMetric(kMetricMedianThroughput, "bytesPerSecond");
  return reporter;
}

}  // namespace

static void Timing(const size_t len) {
  std::vector<uint8_t> buf(len);
  base::RandBytes(buf.data(), len);

  const int runs = 111;
  std::vector<base::TimeDelta> utime(runs);
  unsigned char digest[base::kSHA1Length];
  memset(digest, 0, base::kSHA1Length);

  double total_test_time = 0.0;
  for (int i = 0; i < runs; ++i) {
    auto start = base::TimeTicks::Now();
    base::SHA1HashBytes(buf.data(), len, digest);
    auto end = base::TimeTicks::Now();
    utime[i] = end - start;
    total_test_time += utime[i].InMicroseconds();
  }

  std::sort(utime.begin(), utime.end());
  const int med = runs / 2;

  // Simply dividing len by utime gets us MB/s, but we need B/s.
  // MB/s = (len / (bytes/megabytes)) / (usecs / usecs/sec)
  // MB/s = (len / 1,000,000)/(usecs / 1,000,000)
  // MB/s = (len * 1,000,000)/(usecs * 1,000,000)
  // MB/s = len/utime
  double median_rate = kBytesPerMegabyte * len / utime[med].InMicroseconds();
  // Convert to a comma-separated string so we can report every data point.
  std::string rates;
  for (const auto& t : utime) {
    rates +=
        base::NumberToString(kBytesPerMegabyte * len / t.InMicroseconds()) +
        ",";
  }
  // Strip off trailing comma.
  rates.pop_back();

  auto reporter = SetUpReporter(base::NumberToString(len) + "_bytes");
  reporter.AddResult(kMetricRuntime, total_test_time);
  reporter.AddResult(kMetricMedianThroughput, median_rate);
  reporter.AddResultList(kMetricThroughput, rates);
}

TEST(SHA1PerfTest, Speed) {
  Timing(1024 * 1024U >> 1);
  Timing(1024 * 1024U >> 5);
  Timing(1024 * 1024U >> 6);
  Timing(1024 * 1024U >> 7);
}
