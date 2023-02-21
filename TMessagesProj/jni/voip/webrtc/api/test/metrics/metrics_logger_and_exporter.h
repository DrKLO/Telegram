/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_METRICS_METRICS_LOGGER_AND_EXPORTER_H_
#define API_TEST_METRICS_METRICS_LOGGER_AND_EXPORTER_H_

#include <map>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/numerics/samples_stats_counter.h"
#include "api/test/metrics/metric.h"
#include "api/test/metrics/metrics_exporter.h"
#include "api/test/metrics/metrics_logger.h"
#include "rtc_base/synchronization/mutex.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {
namespace test {

// Combines metrics logging and exporting to provide simple API to automatically
// export metrics at the end of the scope.
class MetricsLoggerAndExporter : public MetricsLogger {
 public:
  // `crash_on_export_failure` - makes MetricsLoggerAndExporter to crash if
  // any of exporters failed to export data.
  MetricsLoggerAndExporter(
      webrtc::Clock* clock,
      std::vector<std::unique_ptr<MetricsExporter>> exporters,
      bool crash_on_export_failure = true)
      : clock_(clock),
        crash_on_export_failure_(crash_on_export_failure),
        exporters_(std::move(exporters)) {}
  ~MetricsLoggerAndExporter() override;

  // Adds a metric with a single value.
  // `metadata` - metric's level metadata to add.
  void LogSingleValueMetric(
      absl::string_view name,
      absl::string_view test_case_name,
      double value,
      Unit unit,
      ImprovementDirection improvement_direction,
      std::map<std::string, std::string> metadata = {}) override;

  // Adds metrics with a time series created based on the provided `values`.
  // `metadata` - metric's level metadata to add.
  void LogMetric(absl::string_view name,
                 absl::string_view test_case_name,
                 const SamplesStatsCounter& values,
                 Unit unit,
                 ImprovementDirection improvement_direction,
                 std::map<std::string, std::string> metadata = {}) override;

  // Adds metric with a time series with only stats object and without actual
  // collected values.
  // `metadata` - metric's level metadata to add.
  void LogMetric(absl::string_view name,
                 absl::string_view test_case_name,
                 const Metric::Stats& metric_stats,
                 Unit unit,
                 ImprovementDirection improvement_direction,
                 std::map<std::string, std::string> metadata = {}) override;

  // Returns all metrics collected by this logger.
  std::vector<Metric> GetCollectedMetrics() const override {
    MutexLock lock(&mutex_);
    return metrics_;
  }

 private:
  webrtc::Timestamp Now();
  bool Export();

  webrtc::Clock* const clock_;
  const bool crash_on_export_failure_;

  mutable Mutex mutex_;
  std::vector<Metric> metrics_ RTC_GUARDED_BY(mutex_);
  std::vector<std::unique_ptr<MetricsExporter>> exporters_;
};

}  // namespace test
}  // namespace webrtc

#endif  // API_TEST_METRICS_METRICS_LOGGER_AND_EXPORTER_H_
