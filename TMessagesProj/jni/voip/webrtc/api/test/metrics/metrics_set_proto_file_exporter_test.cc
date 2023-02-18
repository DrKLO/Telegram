/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/metrics/metrics_set_proto_file_exporter.h"

#include <fstream>
#include <map>
#include <string>
#include <vector>

#include "api/test/metrics/metric.h"
#include "api/test/metrics/proto/metric.pb.h"
#include "api/units/timestamp.h"
#include "rtc_base/protobuf_utils.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {
namespace test {
namespace {

using ::testing::Eq;
using ::testing::Test;

namespace proto = ::webrtc::test_metrics;

std::string ReadFileAsString(const std::string& filename) {
  std::ifstream infile(filename, std::ios_base::binary);
  auto buffer = std::vector<char>(std::istreambuf_iterator<char>(infile),
                                  std::istreambuf_iterator<char>());
  return std::string(buffer.begin(), buffer.end());
}

std::map<std::string, std::string> DefaultMetadata() {
  return std::map<std::string, std::string>{{"key", "value"}};
}

Metric::TimeSeries::Sample Sample(double value) {
  return Metric::TimeSeries::Sample{.timestamp = Timestamp::Seconds(1),
                                    .value = value,
                                    .sample_metadata = DefaultMetadata()};
}

void AssertSamplesEqual(const proto::Metric::TimeSeries::Sample& actual_sample,
                        const Metric::TimeSeries::Sample& expected_sample) {
  EXPECT_THAT(actual_sample.value(), Eq(expected_sample.value));
  EXPECT_THAT(actual_sample.timestamp_us(), Eq(expected_sample.timestamp.us()));
  EXPECT_THAT(actual_sample.sample_metadata().size(),
              Eq(expected_sample.sample_metadata.size()));
  for (const auto& [key, value] : expected_sample.sample_metadata) {
    EXPECT_THAT(actual_sample.sample_metadata().at(key), Eq(value));
  }
}

class MetricsSetProtoFileExporterTest : public Test {
 protected:
  ~MetricsSetProtoFileExporterTest() override = default;

  void SetUp() override {
    temp_filename_ = webrtc::test::TempFilename(
        webrtc::test::OutputPath(), "metrics_set_proto_file_exporter_test");
  }

  void TearDown() override {
    ASSERT_TRUE(webrtc::test::RemoveFile(temp_filename_));
  }

  std::string temp_filename_;
};

TEST_F(MetricsSetProtoFileExporterTest, MetricsAreExportedCorrectly) {
  MetricsSetProtoFileExporter::Options options(temp_filename_);
  MetricsSetProtoFileExporter exporter(options);

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

  ASSERT_TRUE(exporter.Export(std::vector<Metric>{metric1, metric2}));
  webrtc::test_metrics::MetricsSet actual_metrics_set;
  actual_metrics_set.ParseFromString(ReadFileAsString(temp_filename_));
  EXPECT_THAT(actual_metrics_set.metrics().size(), Eq(2));

  EXPECT_THAT(actual_metrics_set.metrics(0).name(), Eq("test_metric1"));
  EXPECT_THAT(actual_metrics_set.metrics(0).test_case(), Eq("test_case_name1"));
  EXPECT_THAT(actual_metrics_set.metrics(0).unit(),
              Eq(proto::Unit::MILLISECONDS));
  EXPECT_THAT(actual_metrics_set.metrics(0).improvement_direction(),
              Eq(proto::ImprovementDirection::BIGGER_IS_BETTER));
  EXPECT_THAT(actual_metrics_set.metrics(0).metric_metadata().size(), Eq(1lu));
  EXPECT_THAT(actual_metrics_set.metrics(0).metric_metadata().at("key"),
              Eq("value"));
  EXPECT_THAT(actual_metrics_set.metrics(0).time_series().samples().size(),
              Eq(2));
  AssertSamplesEqual(actual_metrics_set.metrics(0).time_series().samples(0),
                     Sample(10.0));
  AssertSamplesEqual(actual_metrics_set.metrics(0).time_series().samples(1),
                     Sample(20.0));
  EXPECT_THAT(actual_metrics_set.metrics(0).stats().mean(), Eq(15.0));
  EXPECT_THAT(actual_metrics_set.metrics(0).stats().stddev(), Eq(5.0));
  EXPECT_THAT(actual_metrics_set.metrics(0).stats().min(), Eq(10.0));
  EXPECT_THAT(actual_metrics_set.metrics(0).stats().max(), Eq(20.0));

  EXPECT_THAT(actual_metrics_set.metrics(1).name(), Eq("test_metric2"));
  EXPECT_THAT(actual_metrics_set.metrics(1).test_case(), Eq("test_case_name2"));
  EXPECT_THAT(actual_metrics_set.metrics(1).unit(),
              Eq(proto::Unit::KILOBITS_PER_SECOND));
  EXPECT_THAT(actual_metrics_set.metrics(1).improvement_direction(),
              Eq(proto::ImprovementDirection::SMALLER_IS_BETTER));
  EXPECT_THAT(actual_metrics_set.metrics(1).metric_metadata().size(), Eq(1lu));
  EXPECT_THAT(actual_metrics_set.metrics(1).metric_metadata().at("key"),
              Eq("value"));
  EXPECT_THAT(actual_metrics_set.metrics(1).time_series().samples().size(),
              Eq(2));
  AssertSamplesEqual(actual_metrics_set.metrics(1).time_series().samples(0),
                     Sample(20.0));
  AssertSamplesEqual(actual_metrics_set.metrics(1).time_series().samples(1),
                     Sample(40.0));
  EXPECT_THAT(actual_metrics_set.metrics(1).stats().mean(), Eq(30.0));
  EXPECT_THAT(actual_metrics_set.metrics(1).stats().stddev(), Eq(10.0));
  EXPECT_THAT(actual_metrics_set.metrics(1).stats().min(), Eq(20.0));
  EXPECT_THAT(actual_metrics_set.metrics(1).stats().max(), Eq(40.0));
}

}  // namespace
}  // namespace test
}  // namespace webrtc
