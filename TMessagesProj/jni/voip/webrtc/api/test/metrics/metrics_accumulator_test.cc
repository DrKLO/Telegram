/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/metrics/metrics_accumulator.h"

#include <map>
#include <vector>

#include "api/test/metrics/metric.h"
#include "api/units/timestamp.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {
namespace test {
namespace {

using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::SizeIs;

TEST(MetricsAccumulatorTest, AddSampleToTheNewMetricWillCreateOne) {
  MetricsAccumulator accumulator;
  ASSERT_TRUE(accumulator.AddSample(
      "metric_name", "test_case_name",
      /*value=*/10, Timestamp::Seconds(1),
      /*point_metadata=*/std::map<std::string, std::string>{{"key", "value"}}));

  std::vector<Metric> metrics = accumulator.GetCollectedMetrics();
  ASSERT_THAT(metrics, SizeIs(1));
  const Metric& metric = metrics[0];
  EXPECT_THAT(metric.name, Eq("metric_name"));
  EXPECT_THAT(metric.test_case, Eq("test_case_name"));
  EXPECT_THAT(metric.unit, Eq(Unit::kUnitless));
  EXPECT_THAT(metric.improvement_direction,
              Eq(ImprovementDirection::kNeitherIsBetter));
  EXPECT_THAT(metric.metric_metadata, IsEmpty());
  ASSERT_THAT(metric.time_series.samples, SizeIs(1));
  EXPECT_THAT(metric.time_series.samples[0].value, Eq(10.0));
  EXPECT_THAT(metric.time_series.samples[0].timestamp,
              Eq(Timestamp::Seconds(1)));
  EXPECT_THAT(metric.time_series.samples[0].sample_metadata,
              Eq(std::map<std::string, std::string>{{"key", "value"}}));
  ASSERT_THAT(metric.stats.mean, absl::optional<double>(10.0));
  ASSERT_THAT(metric.stats.stddev, absl::optional<double>(0.0));
  ASSERT_THAT(metric.stats.min, absl::optional<double>(10.0));
  ASSERT_THAT(metric.stats.max, absl::optional<double>(10.0));
}

TEST(MetricsAccumulatorTest, AddSamplesToExistingMetricWontCreateNewOne) {
  MetricsAccumulator accumulator;
  ASSERT_TRUE(accumulator.AddSample(
      "metric_name", "test_case_name",
      /*value=*/10, Timestamp::Seconds(1),
      /*point_metadata=*/
      std::map<std::string, std::string>{{"key1", "value1"}}));
  ASSERT_FALSE(accumulator.AddSample(
      "metric_name", "test_case_name",
      /*value=*/20, Timestamp::Seconds(2),
      /*point_metadata=*/
      std::map<std::string, std::string>{{"key2", "value2"}}));

  std::vector<Metric> metrics = accumulator.GetCollectedMetrics();
  ASSERT_THAT(metrics, SizeIs(1));
  const Metric& metric = metrics[0];
  EXPECT_THAT(metric.name, Eq("metric_name"));
  EXPECT_THAT(metric.test_case, Eq("test_case_name"));
  EXPECT_THAT(metric.unit, Eq(Unit::kUnitless));
  EXPECT_THAT(metric.improvement_direction,
              Eq(ImprovementDirection::kNeitherIsBetter));
  EXPECT_THAT(metric.metric_metadata, IsEmpty());
  ASSERT_THAT(metric.time_series.samples, SizeIs(2));
  EXPECT_THAT(metric.time_series.samples[0].value, Eq(10.0));
  EXPECT_THAT(metric.time_series.samples[0].timestamp,
              Eq(Timestamp::Seconds(1)));
  EXPECT_THAT(metric.time_series.samples[0].sample_metadata,
              Eq(std::map<std::string, std::string>{{"key1", "value1"}}));
  EXPECT_THAT(metric.time_series.samples[1].value, Eq(20.0));
  EXPECT_THAT(metric.time_series.samples[1].timestamp,
              Eq(Timestamp::Seconds(2)));
  EXPECT_THAT(metric.time_series.samples[1].sample_metadata,
              Eq(std::map<std::string, std::string>{{"key2", "value2"}}));
  ASSERT_THAT(metric.stats.mean, absl::optional<double>(15.0));
  ASSERT_THAT(metric.stats.stddev, absl::optional<double>(5.0));
  ASSERT_THAT(metric.stats.min, absl::optional<double>(10.0));
  ASSERT_THAT(metric.stats.max, absl::optional<double>(20.0));
}

TEST(MetricsAccumulatorTest, AddSampleToDifferentMetricsWillCreateBoth) {
  MetricsAccumulator accumulator;
  ASSERT_TRUE(accumulator.AddSample(
      "metric_name1", "test_case_name1",
      /*value=*/10, Timestamp::Seconds(1),
      /*point_metadata=*/
      std::map<std::string, std::string>{{"key1", "value1"}}));
  ASSERT_TRUE(accumulator.AddSample(
      "metric_name2", "test_case_name2",
      /*value=*/20, Timestamp::Seconds(2),
      /*point_metadata=*/
      std::map<std::string, std::string>{{"key2", "value2"}}));

  std::vector<Metric> metrics = accumulator.GetCollectedMetrics();
  ASSERT_THAT(metrics, SizeIs(2));
  EXPECT_THAT(metrics[0].name, Eq("metric_name1"));
  EXPECT_THAT(metrics[0].test_case, Eq("test_case_name1"));
  EXPECT_THAT(metrics[0].unit, Eq(Unit::kUnitless));
  EXPECT_THAT(metrics[0].improvement_direction,
              Eq(ImprovementDirection::kNeitherIsBetter));
  EXPECT_THAT(metrics[0].metric_metadata, IsEmpty());
  ASSERT_THAT(metrics[0].time_series.samples, SizeIs(1));
  EXPECT_THAT(metrics[0].time_series.samples[0].value, Eq(10.0));
  EXPECT_THAT(metrics[0].time_series.samples[0].timestamp,
              Eq(Timestamp::Seconds(1)));
  EXPECT_THAT(metrics[0].time_series.samples[0].sample_metadata,
              Eq(std::map<std::string, std::string>{{"key1", "value1"}}));
  ASSERT_THAT(metrics[0].stats.mean, absl::optional<double>(10.0));
  ASSERT_THAT(metrics[0].stats.stddev, absl::optional<double>(0.0));
  ASSERT_THAT(metrics[0].stats.min, absl::optional<double>(10.0));
  ASSERT_THAT(metrics[0].stats.max, absl::optional<double>(10.0));
  EXPECT_THAT(metrics[1].name, Eq("metric_name2"));
  EXPECT_THAT(metrics[1].test_case, Eq("test_case_name2"));
  EXPECT_THAT(metrics[1].unit, Eq(Unit::kUnitless));
  EXPECT_THAT(metrics[1].improvement_direction,
              Eq(ImprovementDirection::kNeitherIsBetter));
  EXPECT_THAT(metrics[1].metric_metadata, IsEmpty());
  ASSERT_THAT(metrics[1].time_series.samples, SizeIs(1));
  EXPECT_THAT(metrics[1].time_series.samples[0].value, Eq(20.0));
  EXPECT_THAT(metrics[1].time_series.samples[0].timestamp,
              Eq(Timestamp::Seconds(2)));
  EXPECT_THAT(metrics[1].time_series.samples[0].sample_metadata,
              Eq(std::map<std::string, std::string>{{"key2", "value2"}}));
  ASSERT_THAT(metrics[1].stats.mean, absl::optional<double>(20.0));
  ASSERT_THAT(metrics[1].stats.stddev, absl::optional<double>(0.0));
  ASSERT_THAT(metrics[1].stats.min, absl::optional<double>(20.0));
  ASSERT_THAT(metrics[1].stats.max, absl::optional<double>(20.0));
}

TEST(MetricsAccumulatorTest, AddMetadataToTheNewMetricWillCreateOne) {
  MetricsAccumulator accumulator;
  ASSERT_TRUE(accumulator.AddMetricMetadata(
      "metric_name", "test_case_name", Unit::kMilliseconds,
      ImprovementDirection::kBiggerIsBetter,
      /*metric_metadata=*/
      std::map<std::string, std::string>{{"key", "value"}}));

  std::vector<Metric> metrics = accumulator.GetCollectedMetrics();
  ASSERT_THAT(metrics, SizeIs(1));
  const Metric& metric = metrics[0];
  EXPECT_THAT(metric.name, Eq("metric_name"));
  EXPECT_THAT(metric.test_case, Eq("test_case_name"));
  EXPECT_THAT(metric.unit, Eq(Unit::kMilliseconds));
  EXPECT_THAT(metric.improvement_direction,
              Eq(ImprovementDirection::kBiggerIsBetter));
  EXPECT_THAT(metric.metric_metadata,
              Eq(std::map<std::string, std::string>{{"key", "value"}}));
  ASSERT_THAT(metric.time_series.samples, IsEmpty());
  ASSERT_THAT(metric.stats.mean, absl::nullopt);
  ASSERT_THAT(metric.stats.stddev, absl::nullopt);
  ASSERT_THAT(metric.stats.min, absl::nullopt);
  ASSERT_THAT(metric.stats.max, absl::nullopt);
}

TEST(MetricsAccumulatorTest,
     AddMetadataToTheExistingMetricWillOverwriteValues) {
  MetricsAccumulator accumulator;
  ASSERT_TRUE(accumulator.AddMetricMetadata(
      "metric_name", "test_case_name", Unit::kMilliseconds,
      ImprovementDirection::kBiggerIsBetter,
      /*metric_metadata=*/
      std::map<std::string, std::string>{{"key1", "value1"}}));

  ASSERT_FALSE(accumulator.AddMetricMetadata(
      "metric_name", "test_case_name", Unit::kBytes,
      ImprovementDirection::kSmallerIsBetter,
      /*metric_metadata=*/
      std::map<std::string, std::string>{{"key2", "value2"}}));

  std::vector<Metric> metrics = accumulator.GetCollectedMetrics();
  ASSERT_THAT(metrics, SizeIs(1));
  const Metric& metric = metrics[0];
  EXPECT_THAT(metric.name, Eq("metric_name"));
  EXPECT_THAT(metric.test_case, Eq("test_case_name"));
  EXPECT_THAT(metric.unit, Eq(Unit::kBytes));
  EXPECT_THAT(metric.improvement_direction,
              Eq(ImprovementDirection::kSmallerIsBetter));
  EXPECT_THAT(metric.metric_metadata,
              Eq(std::map<std::string, std::string>{{"key2", "value2"}}));
  ASSERT_THAT(metric.time_series.samples, IsEmpty());
  ASSERT_THAT(metric.stats.mean, absl::nullopt);
  ASSERT_THAT(metric.stats.stddev, absl::nullopt);
  ASSERT_THAT(metric.stats.min, absl::nullopt);
  ASSERT_THAT(metric.stats.max, absl::nullopt);
}

TEST(MetricsAccumulatorTest, AddMetadataToDifferentMetricsWillCreateBoth) {
  MetricsAccumulator accumulator;
  ASSERT_TRUE(accumulator.AddMetricMetadata(
      "metric_name1", "test_case_name1", Unit::kMilliseconds,
      ImprovementDirection::kBiggerIsBetter,
      /*metric_metadata=*/
      std::map<std::string, std::string>{{"key1", "value1"}}));

  ASSERT_TRUE(accumulator.AddMetricMetadata(
      "metric_name2", "test_case_name2", Unit::kBytes,
      ImprovementDirection::kSmallerIsBetter,
      /*metric_metadata=*/
      std::map<std::string, std::string>{{"key2", "value2"}}));

  std::vector<Metric> metrics = accumulator.GetCollectedMetrics();
  ASSERT_THAT(metrics, SizeIs(2));
  EXPECT_THAT(metrics[0].name, Eq("metric_name1"));
  EXPECT_THAT(metrics[0].test_case, Eq("test_case_name1"));
  EXPECT_THAT(metrics[0].unit, Eq(Unit::kMilliseconds));
  EXPECT_THAT(metrics[0].improvement_direction,
              Eq(ImprovementDirection::kBiggerIsBetter));
  EXPECT_THAT(metrics[0].metric_metadata,
              Eq(std::map<std::string, std::string>{{"key1", "value1"}}));
  ASSERT_THAT(metrics[0].time_series.samples, IsEmpty());
  ASSERT_THAT(metrics[0].stats.mean, absl::nullopt);
  ASSERT_THAT(metrics[0].stats.stddev, absl::nullopt);
  ASSERT_THAT(metrics[0].stats.min, absl::nullopt);
  ASSERT_THAT(metrics[0].stats.max, absl::nullopt);
  EXPECT_THAT(metrics[1].name, Eq("metric_name2"));
  EXPECT_THAT(metrics[1].test_case, Eq("test_case_name2"));
  EXPECT_THAT(metrics[1].unit, Eq(Unit::kBytes));
  EXPECT_THAT(metrics[1].improvement_direction,
              Eq(ImprovementDirection::kSmallerIsBetter));
  EXPECT_THAT(metrics[1].metric_metadata,
              Eq(std::map<std::string, std::string>{{"key2", "value2"}}));
  ASSERT_THAT(metrics[1].time_series.samples, IsEmpty());
  ASSERT_THAT(metrics[1].stats.mean, absl::nullopt);
  ASSERT_THAT(metrics[1].stats.stddev, absl::nullopt);
  ASSERT_THAT(metrics[1].stats.min, absl::nullopt);
  ASSERT_THAT(metrics[1].stats.max, absl::nullopt);
}

TEST(MetricsAccumulatorTest, AddMetadataAfterAddingSampleWontCreateNewMetric) {
  MetricsAccumulator accumulator;
  ASSERT_TRUE(accumulator.AddSample(
      "metric_name", "test_case_name",
      /*value=*/10, Timestamp::Seconds(1),
      /*point_metadata=*/
      std::map<std::string, std::string>{{"key_s", "value_s"}}));
  ASSERT_FALSE(accumulator.AddMetricMetadata(
      "metric_name", "test_case_name", Unit::kMilliseconds,
      ImprovementDirection::kBiggerIsBetter,
      /*metric_metadata=*/
      std::map<std::string, std::string>{{"key_m", "value_m"}}));

  std::vector<Metric> metrics = accumulator.GetCollectedMetrics();
  ASSERT_THAT(metrics, SizeIs(1));
  const Metric& metric = metrics[0];
  EXPECT_THAT(metric.name, Eq("metric_name"));
  EXPECT_THAT(metric.test_case, Eq("test_case_name"));
  EXPECT_THAT(metric.unit, Eq(Unit::kMilliseconds));
  EXPECT_THAT(metric.improvement_direction,
              Eq(ImprovementDirection::kBiggerIsBetter));
  EXPECT_THAT(metric.metric_metadata,
              Eq(std::map<std::string, std::string>{{"key_m", "value_m"}}));
  ASSERT_THAT(metric.time_series.samples, SizeIs(1));
  EXPECT_THAT(metric.time_series.samples[0].value, Eq(10.0));
  EXPECT_THAT(metric.time_series.samples[0].timestamp,
              Eq(Timestamp::Seconds(1)));
  EXPECT_THAT(metric.time_series.samples[0].sample_metadata,
              Eq(std::map<std::string, std::string>{{"key_s", "value_s"}}));
  ASSERT_THAT(metric.stats.mean, absl::optional<double>(10.0));
  ASSERT_THAT(metric.stats.stddev, absl::optional<double>(0.0));
  ASSERT_THAT(metric.stats.min, absl::optional<double>(10.0));
  ASSERT_THAT(metric.stats.max, absl::optional<double>(10.0));
}

TEST(MetricsAccumulatorTest, AddSampleAfterAddingMetadataWontCreateNewMetric) {
  MetricsAccumulator accumulator;
  ASSERT_TRUE(accumulator.AddMetricMetadata(
      "metric_name", "test_case_name", Unit::kMilliseconds,
      ImprovementDirection::kBiggerIsBetter,
      /*metric_metadata=*/
      std::map<std::string, std::string>{{"key_m", "value_m"}}));
  ASSERT_FALSE(accumulator.AddSample(
      "metric_name", "test_case_name",
      /*value=*/10, Timestamp::Seconds(1),
      /*point_metadata=*/
      std::map<std::string, std::string>{{"key_s", "value_s"}}));

  std::vector<Metric> metrics = accumulator.GetCollectedMetrics();
  ASSERT_THAT(metrics, SizeIs(1));
  const Metric& metric = metrics[0];
  EXPECT_THAT(metric.name, Eq("metric_name"));
  EXPECT_THAT(metric.test_case, Eq("test_case_name"));
  EXPECT_THAT(metric.unit, Eq(Unit::kMilliseconds));
  EXPECT_THAT(metric.improvement_direction,
              Eq(ImprovementDirection::kBiggerIsBetter));
  EXPECT_THAT(metric.metric_metadata,
              Eq(std::map<std::string, std::string>{{"key_m", "value_m"}}));
  ASSERT_THAT(metric.time_series.samples, SizeIs(1));
  EXPECT_THAT(metric.time_series.samples[0].value, Eq(10.0));
  EXPECT_THAT(metric.time_series.samples[0].timestamp,
              Eq(Timestamp::Seconds(1)));
  EXPECT_THAT(metric.time_series.samples[0].sample_metadata,
              Eq(std::map<std::string, std::string>{{"key_s", "value_s"}}));
  ASSERT_THAT(metric.stats.mean, absl::optional<double>(10.0));
  ASSERT_THAT(metric.stats.stddev, absl::optional<double>(0.0));
  ASSERT_THAT(metric.stats.min, absl::optional<double>(10.0));
  ASSERT_THAT(metric.stats.max, absl::optional<double>(10.0));
}

}  // namespace
}  // namespace test
}  // namespace webrtc
