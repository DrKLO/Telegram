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

#include <stdio.h>

#include <memory>
#include <string>
#include <vector>

#include "absl/memory/memory.h"
#include "absl/strings/string_view.h"
#include "api/array_view.h"
#include "api/test/metrics/metric.h"
#include "test/testsupport/file_utils.h"
#include "test/testsupport/perf_test_histogram_writer.h"
#include "test/testsupport/perf_test_result_writer.h"

namespace webrtc {
namespace test {
namespace {

std::string ToChromePerfDashboardUnit(Unit unit) {
  switch (unit) {
    case Unit::kMilliseconds:
      return "msBestFitFormat";
    case Unit::kPercent:
      return "n%";
    case Unit::kBytes:
      return "sizeInBytes";
    case Unit::kKilobitsPerSecond:
      // Chrome Perf Dashboard doesn't have kpbs units, so we change the unit
      // and value accordingly.
      return "bytesPerSecond";
    case Unit::kHertz:
      return "Hz";
    case Unit::kUnitless:
      return "unitless";
    case Unit::kCount:
      return "count";
  }
}

double ToChromePerfDashboardValue(double value, Unit unit) {
  switch (unit) {
    case Unit::kKilobitsPerSecond:
      // Chrome Perf Dashboard doesn't have kpbs units, so we change the unit
      // and value accordingly.
      return value * 1000 / 8;
    default:
      return value;
  }
}

ImproveDirection ToChromePerfDashboardImproveDirection(
    ImprovementDirection direction) {
  switch (direction) {
    case ImprovementDirection::kBiggerIsBetter:
      return ImproveDirection::kBiggerIsBetter;
    case ImprovementDirection::kNeitherIsBetter:
      return ImproveDirection::kNone;
    case ImprovementDirection::kSmallerIsBetter:
      return ImproveDirection::kSmallerIsBetter;
  }
}

bool WriteMetricsToFile(const std::string& path, const std::string& data) {
  CreateDir(DirName(path));
  FILE* output = fopen(path.c_str(), "wb");
  if (output == NULL) {
    printf("Failed to write to %s.\n", path.c_str());
    return false;
  }
  size_t written = fwrite(data.c_str(), sizeof(char), data.size(), output);
  fclose(output);

  if (written != data.size()) {
    size_t expected = data.size();
    printf("Wrote %zu, tried to write %zu\n", written, expected);
    return false;
  }
  return true;
}

bool IsEmpty(const Metric::Stats& stats) {
  return !stats.mean.has_value() && !stats.stddev.has_value() &&
         !stats.min.has_value() && !stats.max.has_value();
}

}  // namespace

ChromePerfDashboardMetricsExporter::ChromePerfDashboardMetricsExporter(
    absl::string_view export_file_path)
    : export_file_path_(export_file_path) {}

bool ChromePerfDashboardMetricsExporter::Export(
    rtc::ArrayView<const Metric> metrics) {
  std::unique_ptr<PerfTestResultWriter> writer =
      absl::WrapUnique<PerfTestResultWriter>(CreateHistogramWriter());
  for (const Metric& metric : metrics) {
    if (metric.time_series.samples.empty() && IsEmpty(metric.stats)) {
      // If there were no data collected for the metric it is expected that 0
      // will be exported, so add 0 to the samples.
      writer->LogResult(
          metric.name, metric.test_case,
          ToChromePerfDashboardValue(0, metric.unit),
          ToChromePerfDashboardUnit(metric.unit),
          /*important=*/false,
          ToChromePerfDashboardImproveDirection(metric.improvement_direction));
      continue;
    }

    if (metric.time_series.samples.empty()) {
      writer->LogResultMeanAndError(
          metric.name, metric.test_case,
          ToChromePerfDashboardValue(*metric.stats.mean, metric.unit),
          ToChromePerfDashboardValue(*metric.stats.stddev, metric.unit),
          ToChromePerfDashboardUnit(metric.unit),
          /*important=*/false,
          ToChromePerfDashboardImproveDirection(metric.improvement_direction));
      continue;
    }

    std::vector<double> samples(metric.time_series.samples.size());
    for (size_t i = 0; i < metric.time_series.samples.size(); ++i) {
      samples[i] = ToChromePerfDashboardValue(
          metric.time_series.samples[i].value, metric.unit);
    }
    writer->LogResultList(
        metric.name, metric.test_case, samples,
        ToChromePerfDashboardUnit(metric.unit),
        /*important=*/false,
        ToChromePerfDashboardImproveDirection(metric.improvement_direction));
  }
  return WriteMetricsToFile(export_file_path_, writer->Serialize());
}

}  // namespace test
}  // namespace webrtc
