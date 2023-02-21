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
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/numerics/samples_stats_counter.h"
#include "api/test/metrics/metric.h"
#include "api/units/timestamp.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {
namespace test {
namespace {

Metric::Stats ToStats(const SamplesStatsCounter& values) {
  if (values.IsEmpty()) {
    return Metric::Stats();
  }
  return Metric::Stats{.mean = values.GetAverage(),
                       .stddev = values.GetStandardDeviation(),
                       .min = values.GetMin(),
                       .max = values.GetMax()};
}

Metric SetTimeseries(const Metric& prototype,
                     const SamplesStatsCounter& counter) {
  Metric output(prototype);
  Metric::TimeSeries time_series;
  for (const SamplesStatsCounter::StatsSample& sample :
       counter.GetTimedSamples()) {
    time_series.samples.push_back(
        Metric::TimeSeries::Sample{.timestamp = sample.time,
                                   .value = sample.value,
                                   .sample_metadata = sample.metadata});
  }
  output.time_series = std::move(time_series);
  output.stats = ToStats(counter);
  return output;
}

}  // namespace

bool operator<(const MetricsAccumulator::MetricKey& a,
               const MetricsAccumulator::MetricKey& b) {
  if (a.test_case_name < b.test_case_name) {
    return true;
  } else if (a.test_case_name > b.test_case_name) {
    return false;
  } else {
    return a.metric_name < b.metric_name;
  }
}

bool MetricsAccumulator::AddSample(
    absl::string_view metric_name,
    absl::string_view test_case_name,
    double value,
    Timestamp timestamp,
    std::map<std::string, std::string> point_metadata) {
  MutexLock lock(&mutex_);
  bool created;
  MetricValue* metric_value =
      GetOrCreateMetric(metric_name, test_case_name, &created);
  metric_value->counter.AddSample(
      SamplesStatsCounter::StatsSample{.value = value,
                                       .time = timestamp,
                                       .metadata = std::move(point_metadata)});
  return created;
}

bool MetricsAccumulator::AddMetricMetadata(
    absl::string_view metric_name,
    absl::string_view test_case_name,
    Unit unit,
    ImprovementDirection improvement_direction,
    std::map<std::string, std::string> metric_metadata) {
  MutexLock lock(&mutex_);
  bool created;
  MetricValue* metric_value =
      GetOrCreateMetric(metric_name, test_case_name, &created);
  metric_value->metric.unit = unit;
  metric_value->metric.improvement_direction = improvement_direction;
  metric_value->metric.metric_metadata = std::move(metric_metadata);
  return created;
}

std::vector<Metric> MetricsAccumulator::GetCollectedMetrics() const {
  MutexLock lock(&mutex_);
  std::vector<Metric> out;
  out.reserve(metrics_.size());
  for (const auto& [unused_key, metric_value] : metrics_) {
    out.push_back(SetTimeseries(metric_value.metric, metric_value.counter));
  }
  return out;
}

MetricsAccumulator::MetricValue* MetricsAccumulator::GetOrCreateMetric(
    absl::string_view metric_name,
    absl::string_view test_case_name,
    bool* created) {
  MetricKey key(metric_name, test_case_name);
  auto it = metrics_.find(key);
  if (it != metrics_.end()) {
    *created = false;
    return &it->second;
  }
  *created = true;

  Metric metric{
      .name = key.metric_name,
      .unit = Unit::kUnitless,
      .improvement_direction = ImprovementDirection::kNeitherIsBetter,
      .test_case = key.test_case_name,
  };
  return &metrics_.emplace(key, MetricValue{.metric = std::move(metric)})
              .first->second;
}

}  // namespace test
}  // namespace webrtc
