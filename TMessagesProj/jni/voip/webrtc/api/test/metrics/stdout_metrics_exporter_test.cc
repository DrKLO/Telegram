/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/metrics/stdout_metrics_exporter.h"

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

Metric PsnrForTestFoo(double mean, double stddev) {
  return Metric{.name = "psnr",
                .unit = Unit::kUnitless,
                .improvement_direction = ImprovementDirection::kBiggerIsBetter,
                .test_case = "foo",
                .time_series = Metric::TimeSeries{},
                .stats = Metric::Stats{.mean = mean, .stddev = stddev}};
}

TEST(StdoutMetricsExporterTest, ExportMetricFormatCorrect) {
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
  StdoutMetricsExporter exporter;

  std::string expected =
      "RESULT: test_case_name1 / test_metric1= "
      "{mean=15, stddev=5} Milliseconds (BiggerIsBetter)\n"
      "RESULT: test_case_name2 / test_metric2= "
      "{mean=30, stddev=10} KilobitsPerSecond (SmallerIsBetter)\n";

  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric1, metric2}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

TEST(StdoutMetricsExporterNumberFormatTest, PositiveNumberMaxPrecision) {
  testing::internal::CaptureStdout();
  StdoutMetricsExporter exporter;

  Metric metric = PsnrForTestFoo(15.00000001, 0.00000001);
  std::string expected =
      "RESULT: foo / psnr= "
      "{mean=15.00000001, stddev=0.00000001} Unitless (BiggerIsBetter)\n";
  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

TEST(StdoutMetricsExporterNumberFormatTest,
     PositiveNumberTrailingZeroNotAdded) {
  testing::internal::CaptureStdout();
  StdoutMetricsExporter exporter;

  Metric metric = PsnrForTestFoo(15.12345, 0.12);
  std::string expected =
      "RESULT: foo / psnr= "
      "{mean=15.12345, stddev=0.12} Unitless (BiggerIsBetter)\n";
  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

TEST(StdoutMetricsExporterNumberFormatTest,
     PositiveNumberTrailingZeroAreRemoved) {
  testing::internal::CaptureStdout();
  StdoutMetricsExporter exporter;

  Metric metric = PsnrForTestFoo(15.123450000, 0.120000000);
  std::string expected =
      "RESULT: foo / psnr= "
      "{mean=15.12345, stddev=0.12} Unitless (BiggerIsBetter)\n";
  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

TEST(StdoutMetricsExporterNumberFormatTest,
     PositiveNumberRoundsUpOnPrecisionCorrectly) {
  testing::internal::CaptureStdout();
  StdoutMetricsExporter exporter;

  Metric metric = PsnrForTestFoo(15.000000009, 0.999999999);
  std::string expected =
      "RESULT: foo / psnr= "
      "{mean=15.00000001, stddev=1} Unitless (BiggerIsBetter)\n";
  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

TEST(StdoutMetricsExporterNumberFormatTest,
     PositiveNumberRoundsDownOnPrecisionCorrectly) {
  testing::internal::CaptureStdout();
  StdoutMetricsExporter exporter;

  Metric metric = PsnrForTestFoo(15.0000000049, 0.9999999949);
  std::string expected =
      "RESULT: foo / psnr= "
      "{mean=15, stddev=0.99999999} Unitless (BiggerIsBetter)\n";
  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

TEST(StdoutMetricsExporterNumberFormatTest, NegativeNumberMaxPrecision) {
  testing::internal::CaptureStdout();
  StdoutMetricsExporter exporter;

  Metric metric = PsnrForTestFoo(-15.00000001, -0.00000001);
  std::string expected =
      "RESULT: foo / psnr= "
      "{mean=-15.00000001, stddev=-0.00000001} Unitless (BiggerIsBetter)\n";
  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

TEST(StdoutMetricsExporterNumberFormatTest,
     NegativeNumberTrailingZeroNotAdded) {
  testing::internal::CaptureStdout();
  StdoutMetricsExporter exporter;

  Metric metric = PsnrForTestFoo(-15.12345, -0.12);
  std::string expected =
      "RESULT: foo / psnr= "
      "{mean=-15.12345, stddev=-0.12} Unitless (BiggerIsBetter)\n";
  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

TEST(StdoutMetricsExporterNumberFormatTest,
     NegativeNumberTrailingZeroAreRemoved) {
  testing::internal::CaptureStdout();
  StdoutMetricsExporter exporter;

  Metric metric = PsnrForTestFoo(-15.123450000, -0.120000000);
  std::string expected =
      "RESULT: foo / psnr= "
      "{mean=-15.12345, stddev=-0.12} Unitless (BiggerIsBetter)\n";
  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

TEST(StdoutMetricsExporterNumberFormatTest,
     NegativeNumberRoundsUpOnPrecisionCorrectly) {
  testing::internal::CaptureStdout();
  StdoutMetricsExporter exporter;

  Metric metric = PsnrForTestFoo(-15.000000009, -0.999999999);
  std::string expected =
      "RESULT: foo / psnr= "
      "{mean=-15.00000001, stddev=-1} Unitless (BiggerIsBetter)\n";
  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

TEST(StdoutMetricsExporterNumberFormatTest,
     NegativeNumberRoundsDownOnPrecisionCorrectly) {
  testing::internal::CaptureStdout();
  StdoutMetricsExporter exporter;

  Metric metric = PsnrForTestFoo(-15.0000000049, -0.9999999949);
  std::string expected =
      "RESULT: foo / psnr= "
      "{mean=-15, stddev=-0.99999999} Unitless (BiggerIsBetter)\n";
  EXPECT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  EXPECT_EQ(expected, testing::internal::GetCapturedStdout());
}

}  // namespace
}  // namespace test
}  // namespace webrtc
