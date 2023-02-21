/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/metrics/print_result_proxy_metrics_exporter.h"

#include <map>
#include <string>
#include <vector>

#include "api/test/metrics/metric.h"
#include "api/units/timestamp.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {
namespace test {
namespace {

using ::testing::TestWithParam;

std::map<std::string, std::string> DefaultMetadata() {
  return std::map<std::string, std::string>{{"key", "value"}};
}

Metric::TimeSeries::Sample Sample(double value) {
  return Metric::TimeSeries::Sample{.timestamp = Timestamp::Seconds(1),
                                    .value = value,
                                    .sample_metadata = DefaultMetadata()};
}

TEST(PrintResultProxyMetricsExporterTest,
     ExportMetricsWithTimeSeriesFormatCorrect) {
  Metric metric1{
      .name = "test_metric1",
      .unit = Unit::kMilliseconds,
      .improvement_direction = ImprovementDirection::kBiggerIsBetter,
      .test_case = "test_case_name1",
      .metric_metadata = DefaultMetadata(),
      .time_series =
          Metric::TimeSeries{.samples = std::vector{Sample(10), Sample(20)}},
      .stats =
          Metric::Stats{.mean = 15.0, .stddev = 5.0, .min = 10.0, .max = 20.0}};
  Metric metric2{
      .name = "test_metric2",
      .unit = Unit::kKilobitsPerSecond,
      .improvement_direction = ImprovementDirection::kSmallerIsBetter,
      .test_case = "test_case_name2",
      .metric_metadata = DefaultMetadata(),
      .time_series =
          Metric::TimeSeries{.samples = std::vector{Sample(20), Sample(40)}},
      .stats = Metric::Stats{
          .mean = 30.0, .stddev = 10.0, .min = 20.0, .max = 40.0}};

  testing::internal::CaptureStdout();
  PrintResultProxyMetricsExporter exporter;

  std::string expected =
      "RESULT test_metric1: test_case_name1= {15,5} "
      "msBestFitFormat_biggerIsBetter\n"
      "RESULT test_metric2: test_case_name2= {3750,1250} "
      "bytesPerSecond_smallerIsBetter\n";

  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric1, metric2}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

TEST(PrintResultProxyMetricsExporterTest,
     ExportMetricsTimeSeriesOfSingleValueBackwardCompatibleFormat) {
  // This should be printed as {mean, stddev} despite only being a single data
  // point.
  Metric metric1{
      .name = "available_send_bandwidth",
      .unit = Unit::kKilobitsPerSecond,
      .improvement_direction = ImprovementDirection::kBiggerIsBetter,
      .test_case = "test_case/alice",
      .metric_metadata = DefaultMetadata(),
      .time_series = Metric::TimeSeries{.samples = std::vector{Sample(1000)}},
      .stats = Metric::Stats{
          .mean = 1000.0, .stddev = 0.0, .min = 1000.0, .max = 1000.0}};
  // This is a per-call metric that shouldn't have a stddev estimate.
  Metric metric2{
      .name = "min_psnr_dB",
      .unit = Unit::kUnitless,
      .improvement_direction = ImprovementDirection::kBiggerIsBetter,
      .test_case = "test_case/alice-video",
      .metric_metadata = DefaultMetadata(),
      .time_series = Metric::TimeSeries{.samples = std::vector{Sample(10)}},
      .stats =
          Metric::Stats{.mean = 10.0, .stddev = 0.0, .min = 10.0, .max = 10.0}};
  // This is a per-call metric that shouldn't have a stddev estimate.
  Metric metric3{
      .name = "alice_connected",
      .unit = Unit::kUnitless,
      .improvement_direction = ImprovementDirection::kBiggerIsBetter,
      .test_case = "test_case",
      .metric_metadata = DefaultMetadata(),
      .time_series = Metric::TimeSeries{.samples = std::vector{Sample(1)}},
      .stats =
          Metric::Stats{.mean = 1.0, .stddev = 0.0, .min = 1.0, .max = 1.0}};

  testing::internal::CaptureStdout();
  PrintResultProxyMetricsExporter exporter;

  std::string expected =
      "RESULT available_send_bandwidth: test_case/alice= {125000,0} "
      "bytesPerSecond_biggerIsBetter\n"
      "RESULT min_psnr_dB: test_case/alice-video= 10 "
      "unitless_biggerIsBetter\n"
      "RESULT alice_connected: test_case= 1 "
      "unitless_biggerIsBetter\n";

  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric1, metric2, metric3}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

TEST(PrintResultProxyMetricsExporterTest,
     ExportMetricsWithStatsOnlyFormatCorrect) {
  Metric metric1{.name = "test_metric1",
                 .unit = Unit::kMilliseconds,
                 .improvement_direction = ImprovementDirection::kBiggerIsBetter,
                 .test_case = "test_case_name1",
                 .metric_metadata = DefaultMetadata(),
                 .time_series = Metric::TimeSeries{.samples = {}},
                 .stats = Metric::Stats{
                     .mean = 15.0, .stddev = 5.0, .min = 10.0, .max = 20.0}};
  Metric metric2{
      .name = "test_metric2",
      .unit = Unit::kKilobitsPerSecond,
      .improvement_direction = ImprovementDirection::kSmallerIsBetter,
      .test_case = "test_case_name2",
      .metric_metadata = DefaultMetadata(),
      .time_series = Metric::TimeSeries{.samples = {}},
      .stats = Metric::Stats{
          .mean = 30.0, .stddev = 10.0, .min = 20.0, .max = 40.0}};

  testing::internal::CaptureStdout();
  PrintResultProxyMetricsExporter exporter;

  std::string expected =
      "RESULT test_metric1: test_case_name1= {15,5} "
      "msBestFitFormat_biggerIsBetter\n"
      "RESULT test_metric2: test_case_name2= {3750,1250} "
      "bytesPerSecond_smallerIsBetter\n";

  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric1, metric2}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

TEST(PrintResultProxyMetricsExporterTest, ExportEmptyMetricOnlyFormatCorrect) {
  Metric metric{.name = "test_metric",
                .unit = Unit::kMilliseconds,
                .improvement_direction = ImprovementDirection::kBiggerIsBetter,
                .test_case = "test_case_name",
                .metric_metadata = DefaultMetadata(),
                .time_series = Metric::TimeSeries{.samples = {}},
                .stats = Metric::Stats{}};

  testing::internal::CaptureStdout();
  PrintResultProxyMetricsExporter exporter;

  std::string expected =
      "RESULT test_metric: test_case_name= 0 "
      "msBestFitFormat_biggerIsBetter\n";

  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

}  // namespace
}  // namespace test
}  // namespace webrtc
