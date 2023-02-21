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

#include <string>
#include <unordered_set>

#include "api/array_view.h"
#include "api/test/metrics/metric.h"
#include "test/testsupport/perf_test.h"

namespace webrtc {
namespace test {
namespace {

std::string ToPrintResultUnit(Unit unit) {
  switch (unit) {
    case Unit::kMilliseconds:
      return "msBestFitFormat";
    case Unit::kPercent:
      return "n%";
    case Unit::kBytes:
      return "sizeInBytes";
    case Unit::kKilobitsPerSecond:
      // PrintResults prefer Chrome Perf Dashboard units, which doesn't have
      // kpbs units, so we change the unit and value accordingly.
      return "bytesPerSecond";
    case Unit::kHertz:
      return "Hz";
    case Unit::kUnitless:
      return "unitless";
    case Unit::kCount:
      return "count";
  }
}

double ToPrintResultValue(double value, Unit unit) {
  switch (unit) {
    case Unit::kKilobitsPerSecond:
      // PrintResults prefer Chrome Perf Dashboard units, which doesn't have
      // kpbs units, so we change the unit and value accordingly.
      return value * 1000 / 8;
    default:
      return value;
  }
}

ImproveDirection ToPrintResultImproveDirection(ImprovementDirection direction) {
  switch (direction) {
    case ImprovementDirection::kBiggerIsBetter:
      return ImproveDirection::kBiggerIsBetter;
    case ImprovementDirection::kNeitherIsBetter:
      return ImproveDirection::kNone;
    case ImprovementDirection::kSmallerIsBetter:
      return ImproveDirection::kSmallerIsBetter;
  }
}

bool IsEmpty(const Metric::Stats& stats) {
  return !stats.mean.has_value() && !stats.stddev.has_value() &&
         !stats.min.has_value() && !stats.max.has_value();
}

bool NameEndsWithConnected(const std::string& name) {
  static const std::string suffix = "_connected";
  return name.size() >= suffix.size() &&
         0 == name.compare(name.size() - suffix.size(), suffix.size(), suffix);
}

}  // namespace

bool PrintResultProxyMetricsExporter::Export(
    rtc::ArrayView<const Metric> metrics) {
  static const std::unordered_set<std::string> per_call_metrics{
      "actual_encode_bitrate",
      "encode_frame_rate",
      "harmonic_framerate",
      "max_skipped",
      "min_psnr_dB",
      "retransmission_bitrate",
      "sent_packets_loss",
      "transmission_bitrate",
      "dropped_frames",
      "frames_in_flight",
      "rendered_frames",
      "average_receive_rate",
      "average_send_rate",
      "bytes_discarded_no_receiver",
      "bytes_received",
      "bytes_sent",
      "packets_discarded_no_receiver",
      "packets_received",
      "packets_sent",
      "payload_bytes_received",
      "payload_bytes_sent",
      "cpu_usage"};

  for (const Metric& metric : metrics) {
    if (metric.time_series.samples.empty() && IsEmpty(metric.stats)) {
      // If there were no data collected for the metric it is expected that 0
      // will be exported, so add 0 to the samples.
      PrintResult(metric.name, /*modifier=*/"", metric.test_case,
                  ToPrintResultValue(0, metric.unit),
                  ToPrintResultUnit(metric.unit), /*important=*/false,
                  ToPrintResultImproveDirection(metric.improvement_direction));
      continue;
    }

    if (metric.time_series.samples.empty()) {
      PrintResultMeanAndError(
          metric.name, /*modifier=*/"", metric.test_case,
          ToPrintResultValue(*metric.stats.mean, metric.unit),
          ToPrintResultValue(*metric.stats.stddev, metric.unit),
          ToPrintResultUnit(metric.unit),
          /*important=*/false,
          ToPrintResultImproveDirection(metric.improvement_direction));
      continue;
    }

    if (metric.time_series.samples.size() == 1lu &&
        (per_call_metrics.count(metric.name) > 0 ||
         NameEndsWithConnected(metric.name))) {
      // Increase backwards compatibility for 1 value use case.
      PrintResult(
          metric.name, /*modifier=*/"", metric.test_case,
          ToPrintResultValue(metric.time_series.samples[0].value, metric.unit),
          ToPrintResultUnit(metric.unit), /*important=*/false,
          ToPrintResultImproveDirection(metric.improvement_direction));
      continue;
    }

    SamplesStatsCounter counter;
    for (size_t i = 0; i < metric.time_series.samples.size(); ++i) {
      counter.AddSample(SamplesStatsCounter::StatsSample{
          .value = ToPrintResultValue(metric.time_series.samples[i].value,
                                      metric.unit),
          .time = metric.time_series.samples[i].timestamp,
          .metadata = metric.time_series.samples[i].sample_metadata});
    }

    PrintResult(metric.name, /*modifier=*/"", metric.test_case, counter,
                ToPrintResultUnit(metric.unit),
                /*important=*/false,
                ToPrintResultImproveDirection(metric.improvement_direction));
  }
  return true;
}

}  // namespace test
}  // namespace webrtc
