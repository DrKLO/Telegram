/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/metrics/metrics_logger.h"

#include <map>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/numerics/samples_stats_counter.h"
#include "api/test/metrics/metric.h"
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

}  // namespace

void DefaultMetricsLogger::LogSingleValueMetric(
    absl::string_view name,
    absl::string_view test_case_name,
    double value,
    Unit unit,
    ImprovementDirection improvement_direction,
    std::map<std::string, std::string> metadata) {
  MutexLock lock(&mutex_);
  metrics_.push_back(Metric{
      .name = std::string(name),
      .unit = unit,
      .improvement_direction = improvement_direction,
      .test_case = std::string(test_case_name),
      .metric_metadata = std::move(metadata),
      .time_series =
          Metric::TimeSeries{.samples = std::vector{Metric::TimeSeries::Sample{
                                 .timestamp = Now(), .value = value}}},
      .stats = Metric::Stats{
          .mean = value, .stddev = absl::nullopt, .min = value, .max = value}});
}

void DefaultMetricsLogger::LogMetric(
    absl::string_view name,
    absl::string_view test_case_name,
    const SamplesStatsCounter& values,
    Unit unit,
    ImprovementDirection improvement_direction,
    std::map<std::string, std::string> metadata) {
  MutexLock lock(&mutex_);
  Metric::TimeSeries time_series;
  for (const SamplesStatsCounter::StatsSample& sample :
       values.GetTimedSamples()) {
    time_series.samples.push_back(
        Metric::TimeSeries::Sample{.timestamp = sample.time,
                                   .value = sample.value,
                                   .sample_metadata = sample.metadata});
  }

  metrics_.push_back(Metric{.name = std::string(name),
                            .unit = unit,
                            .improvement_direction = improvement_direction,
                            .test_case = std::string(test_case_name),
                            .metric_metadata = std::move(metadata),
                            .time_series = std::move(time_series),
                            .stats = ToStats(values)});
}

void DefaultMetricsLogger::LogMetric(
    absl::string_view name,
    absl::string_view test_case_name,
    const Metric::Stats& metric_stats,
    Unit unit,
    ImprovementDirection improvement_direction,
    std::map<std::string, std::string> metadata) {
  MutexLock lock(&mutex_);
  metrics_.push_back(Metric{.name = std::string(name),
                            .unit = unit,
                            .improvement_direction = improvement_direction,
                            .test_case = std::string(test_case_name),
                            .metric_metadata = std::move(metadata),
                            .time_series = Metric::TimeSeries{.samples = {}},
                            .stats = std::move(metric_stats)});
}

std::vector<Metric> DefaultMetricsLogger::GetCollectedMetrics() const {
  std::vector<Metric> out = metrics_accumulator_.GetCollectedMetrics();
  MutexLock lock(&mutex_);
  out.insert(out.end(), metrics_.begin(), metrics_.end());
  return out;
}

Timestamp DefaultMetricsLogger::Now() {
  return clock_->CurrentTime();
}

}  // namespace test
}  // namespace webrtc
