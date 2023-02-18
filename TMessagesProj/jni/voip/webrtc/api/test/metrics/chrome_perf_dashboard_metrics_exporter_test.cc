/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/metrics/chrome_perf_dashboard_metrics_exporter.h"

#include <fstream>
#include <map>
#include <vector>

#include "api/test/metrics/metric.h"
#include "api/units/timestamp.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"
#include "third_party/catapult/tracing/tracing/value/histogram.h"

namespace webrtc {
namespace test {
namespace {

using ::testing::DoubleNear;
using ::testing::Eq;
using ::testing::Test;

namespace proto = ::catapult::tracing::tracing::proto;

std::map<std::string, std::string> DefaultMetadata() {
  return std::map<std::string, std::string>{{"key", "value"}};
}

Metric::TimeSeries::Sample Sample(double value) {
  return Metric::TimeSeries::Sample{.timestamp = Timestamp::Seconds(1),
                                    .value = value,
                                    .sample_metadata = DefaultMetadata()};
}

std::string ReadFileAsString(const std::string& filename) {
  std::ifstream infile(filename, std::ios_base::binary);
  auto buffer = std::vector<char>(std::istreambuf_iterator<char>(infile),
                                  std::istreambuf_iterator<char>());
  return std::string(buffer.begin(), buffer.end());
}

class ChromePerfDashboardMetricsExporterTest : public Test {
 protected:
  ~ChromePerfDashboardMetricsExporterTest() override = default;

  void SetUp() override {
    temp_filename_ = webrtc::test::TempFilename(
        webrtc::test::OutputPath(),
        "chrome_perf_dashboard_metrics_exporter_test");
  }

  void TearDown() override {
    ASSERT_TRUE(webrtc::test::RemoveFile(temp_filename_));
  }

  std::string temp_filename_;
};

TEST_F(ChromePerfDashboardMetricsExporterTest, ExportMetricFormatCorrect) {
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

  ChromePerfDashboardMetricsExporter exporter(temp_filename_);

  ASSERT_TRUE(exporter.Export(std::vector<Metric>{metric1, metric2}));
  proto::HistogramSet actual_histogram_set;
  actual_histogram_set.ParseFromString(ReadFileAsString(temp_filename_));
  EXPECT_THAT(actual_histogram_set.histograms().size(), Eq(2));

  // Validate output for `metric1`
  EXPECT_THAT(actual_histogram_set.histograms(0).name(), Eq("test_metric1"));
  EXPECT_THAT(actual_histogram_set.histograms(0).unit().unit(),
              Eq(proto::Unit::MS_BEST_FIT_FORMAT));
  EXPECT_THAT(actual_histogram_set.histograms(0).unit().improvement_direction(),
              Eq(proto::ImprovementDirection::BIGGER_IS_BETTER));
  EXPECT_THAT(
      actual_histogram_set.histograms(0).diagnostics().diagnostic_map().size(),
      Eq(1lu));
  EXPECT_THAT(actual_histogram_set.histograms(0)
                  .diagnostics()
                  .diagnostic_map()
                  .at("stories")
                  .generic_set()
                  .values(0),
              Eq("\"test_case_name1\""));
  EXPECT_THAT(actual_histogram_set.histograms(0).sample_values().size(), Eq(2));
  EXPECT_THAT(actual_histogram_set.histograms(0).sample_values(0), Eq(10.0));
  EXPECT_THAT(actual_histogram_set.histograms(0).sample_values(1), Eq(20.0));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().count(), Eq(2));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().max(), Eq(20));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().meanlogs(),
              DoubleNear(2.64916, 0.1));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().mean(), Eq(15));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().min(), Eq(10));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().sum(), Eq(30));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().variance(), Eq(50));

  // Validate output for `metric2`
  EXPECT_THAT(actual_histogram_set.histograms(1).name(), Eq("test_metric2"));
  EXPECT_THAT(actual_histogram_set.histograms(1).unit().unit(),
              Eq(proto::Unit::BYTES_PER_SECOND));
  EXPECT_THAT(actual_histogram_set.histograms(1).unit().improvement_direction(),
              Eq(proto::ImprovementDirection::SMALLER_IS_BETTER));
  EXPECT_THAT(
      actual_histogram_set.histograms(1).diagnostics().diagnostic_map().size(),
      Eq(1lu));
  EXPECT_THAT(actual_histogram_set.histograms(1)
                  .diagnostics()
                  .diagnostic_map()
                  .at("stories")
                  .generic_set()
                  .values(0),
              Eq("\"test_case_name2\""));
  EXPECT_THAT(actual_histogram_set.histograms(1).sample_values().size(), Eq(2));
  EXPECT_THAT(actual_histogram_set.histograms(1).sample_values(0), Eq(2500.0));
  EXPECT_THAT(actual_histogram_set.histograms(1).sample_values(1), Eq(5000.0));
  EXPECT_THAT(actual_histogram_set.histograms(1).running().count(), Eq(2));
  EXPECT_THAT(actual_histogram_set.histograms(1).running().max(), Eq(5000));
  EXPECT_THAT(actual_histogram_set.histograms(1).running().meanlogs(),
              DoubleNear(8.17062, 0.1));
  EXPECT_THAT(actual_histogram_set.histograms(1).running().mean(), Eq(3750));
  EXPECT_THAT(actual_histogram_set.histograms(1).running().min(), Eq(2500));
  EXPECT_THAT(actual_histogram_set.histograms(1).running().sum(), Eq(7500));
  EXPECT_THAT(actual_histogram_set.histograms(1).running().variance(),
              Eq(3125000));
}

TEST_F(ChromePerfDashboardMetricsExporterTest,
       ExportEmptyMetricExportsZeroValue) {
  Metric metric{.name = "test_metric",
                .unit = Unit::kMilliseconds,
                .improvement_direction = ImprovementDirection::kBiggerIsBetter,
                .test_case = "test_case_name",
                .metric_metadata = DefaultMetadata(),
                .time_series = Metric::TimeSeries{.samples = {}},
                .stats = Metric::Stats{}};

  ChromePerfDashboardMetricsExporter exporter(temp_filename_);

  ASSERT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  proto::HistogramSet actual_histogram_set;
  actual_histogram_set.ParseFromString(ReadFileAsString(temp_filename_));
  EXPECT_THAT(actual_histogram_set.histograms().size(), Eq(1));

  // Validate values for `metric`
  EXPECT_THAT(actual_histogram_set.histograms(0).sample_values().size(), Eq(1));
  EXPECT_THAT(actual_histogram_set.histograms(0).sample_values(0), Eq(0.0));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().count(), Eq(1));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().max(),
              DoubleNear(0, 1e-6));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().meanlogs(), Eq(0));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().mean(), Eq(0));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().min(), Eq(0));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().sum(), Eq(0));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().variance(), Eq(0));
}

TEST_F(ChromePerfDashboardMetricsExporterTest,
       ExportMetricWithOnlyStatsExportsMeanValues) {
  Metric metric{.name = "test_metric",
                .unit = Unit::kMilliseconds,
                .improvement_direction = ImprovementDirection::kBiggerIsBetter,
                .test_case = "test_case_name",
                .metric_metadata = DefaultMetadata(),
                .time_series = Metric::TimeSeries{.samples = {}},
                .stats = Metric::Stats{
                    .mean = 15.0, .stddev = 5.0, .min = 10.0, .max = 20.0}};

  ChromePerfDashboardMetricsExporter exporter(temp_filename_);

  ASSERT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  proto::HistogramSet actual_histogram_set;
  actual_histogram_set.ParseFromString(ReadFileAsString(temp_filename_));
  EXPECT_THAT(actual_histogram_set.histograms().size(), Eq(1));

  // Validate values for `metric`
  EXPECT_THAT(actual_histogram_set.histograms(0).sample_values().size(), Eq(1));
  EXPECT_THAT(actual_histogram_set.histograms(0).sample_values(0), Eq(15.0));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().count(), Eq(1));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().max(), Eq(15));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().meanlogs(),
              DoubleNear(2.70805, 0.1));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().mean(), Eq(15));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().min(), Eq(15));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().sum(), Eq(15));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().variance(), Eq(0));
}

TEST_F(ChromePerfDashboardMetricsExporterTest,
       ExportMetricWithOnlyStatsConvertsMeanValuesWhenRequired) {
  Metric metric{.name = "test_metric",
                .unit = Unit::kKilobitsPerSecond,
                .improvement_direction = ImprovementDirection::kBiggerIsBetter,
                .test_case = "test_case_name",
                .metric_metadata = DefaultMetadata(),
                .time_series = Metric::TimeSeries{.samples = {}},
                .stats = Metric::Stats{
                    .mean = 15.0, .stddev = 5.0, .min = 10.0, .max = 20.0}};

  ChromePerfDashboardMetricsExporter exporter(temp_filename_);

  ASSERT_TRUE(exporter.Export(std::vector<Metric>{metric}));
  proto::HistogramSet actual_histogram_set;
  actual_histogram_set.ParseFromString(ReadFileAsString(temp_filename_));
  EXPECT_THAT(actual_histogram_set.histograms().size(), Eq(1));

  // Validate values for `metric`
  EXPECT_THAT(actual_histogram_set.histograms(0).sample_values().size(), Eq(1));
  EXPECT_THAT(actual_histogram_set.histograms(0).sample_values(0), Eq(1875.0));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().count(), Eq(1));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().max(), Eq(1875));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().meanlogs(),
              DoubleNear(7.53636, 0.1));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().mean(), Eq(1875));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().min(), Eq(1875));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().sum(), Eq(1875));
  EXPECT_THAT(actual_histogram_set.histograms(0).running().variance(), Eq(0));
}

}  // namespace
}  // namespace test
}  // namespace webrtc
