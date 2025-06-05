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

#include <stdio.h>

#include <map>
#include <string>
#include <utility>

#include "api/test/metrics/metric.h"
#include "rtc_base/logging.h"
#include "test/testsupport/file_utils.h"

#if WEBRTC_ENABLE_PROTOBUF
#include "api/test/metrics/proto/metric.pb.h"
#endif

namespace webrtc {
namespace test {
namespace {

#if WEBRTC_ENABLE_PROTOBUF
webrtc::test_metrics::Unit ToProtoUnit(Unit unit) {
  switch (unit) {
    case Unit::kMilliseconds:
      return webrtc::test_metrics::Unit::MILLISECONDS;
    case Unit::kPercent:
      return webrtc::test_metrics::Unit::PERCENT;
    case Unit::kBytes:
      return webrtc::test_metrics::Unit::BYTES;
    case Unit::kKilobitsPerSecond:
      return webrtc::test_metrics::Unit::KILOBITS_PER_SECOND;
    case Unit::kHertz:
      return webrtc::test_metrics::Unit::HERTZ;
    case Unit::kUnitless:
      return webrtc::test_metrics::Unit::UNITLESS;
    case Unit::kCount:
      return webrtc::test_metrics::Unit::COUNT;
  }
}

webrtc::test_metrics::ImprovementDirection ToProtoImprovementDirection(
    ImprovementDirection direction) {
  switch (direction) {
    case ImprovementDirection::kBiggerIsBetter:
      return webrtc::test_metrics::ImprovementDirection::BIGGER_IS_BETTER;
    case ImprovementDirection::kNeitherIsBetter:
      return webrtc::test_metrics::ImprovementDirection::NEITHER_IS_BETTER;
    case ImprovementDirection::kSmallerIsBetter:
      return webrtc::test_metrics::ImprovementDirection::SMALLER_IS_BETTER;
  }
}

void SetTimeSeries(
    const Metric::TimeSeries& time_series,
    webrtc::test_metrics::Metric::TimeSeries* proto_time_series) {
  for (const Metric::TimeSeries::Sample& sample : time_series.samples) {
    webrtc::test_metrics::Metric::TimeSeries::Sample* proto_sample =
        proto_time_series->add_samples();
    proto_sample->set_value(sample.value);
    proto_sample->set_timestamp_us(sample.timestamp.us());
    for (const auto& [key, value] : sample.sample_metadata) {
      proto_sample->mutable_sample_metadata()->insert({key, value});
    }
  }
}

void SetStats(const Metric::Stats& stats,
              webrtc::test_metrics::Metric::Stats* proto_stats) {
  if (stats.mean.has_value()) {
    proto_stats->set_mean(*stats.mean);
  }
  if (stats.stddev.has_value()) {
    proto_stats->set_stddev(*stats.stddev);
  }
  if (stats.min.has_value()) {
    proto_stats->set_min(*stats.min);
  }
  if (stats.max.has_value()) {
    proto_stats->set_max(*stats.max);
  }
}

bool WriteMetricsToFile(const std::string& path,
                        const webrtc::test_metrics::MetricsSet& metrics_set) {
  std::string data;
  bool ok = metrics_set.SerializeToString(&data);
  if (!ok) {
    RTC_LOG(LS_ERROR) << "Failed to serialize histogram set to string";
    return false;
  }

  CreateDir(DirName(path));
  FILE* output = fopen(path.c_str(), "wb");
  if (output == NULL) {
    RTC_LOG(LS_ERROR) << "Failed to write to " << path;
    return false;
  }
  size_t written = fwrite(data.c_str(), sizeof(char), data.size(), output);
  fclose(output);

  if (written != data.size()) {
    size_t expected = data.size();
    RTC_LOG(LS_ERROR) << "Wrote " << written << ", tried to write " << expected;
    return false;
  }
  return true;
}
#endif  // WEBRTC_ENABLE_PROTOBUF

}  // namespace

MetricsSetProtoFileExporter::Options::Options(
    absl::string_view export_file_path)
    : export_file_path(export_file_path) {}
MetricsSetProtoFileExporter::Options::Options(
    absl::string_view export_file_path,
    bool export_whole_time_series)
    : export_file_path(export_file_path),
      export_whole_time_series(export_whole_time_series) {}
MetricsSetProtoFileExporter::Options::Options(
    absl::string_view export_file_path,
    std::map<std::string, std::string> metadata)
    : export_file_path(export_file_path), metadata(std::move(metadata)) {}

bool MetricsSetProtoFileExporter::Export(rtc::ArrayView<const Metric> metrics) {
#if WEBRTC_ENABLE_PROTOBUF
  webrtc::test_metrics::MetricsSet metrics_set;
  for (const auto& [key, value] : options_.metadata) {
    metrics_set.mutable_metadata()->insert({key, value});
  }
  for (const Metric& metric : metrics) {
    webrtc::test_metrics::Metric* metric_proto = metrics_set.add_metrics();
    metric_proto->set_name(metric.name);
    metric_proto->set_unit(ToProtoUnit(metric.unit));
    metric_proto->set_improvement_direction(
        ToProtoImprovementDirection(metric.improvement_direction));
    metric_proto->set_test_case(metric.test_case);
    for (const auto& [key, value] : metric.metric_metadata) {
      metric_proto->mutable_metric_metadata()->insert({key, value});
    }

    if (options_.export_whole_time_series) {
      SetTimeSeries(metric.time_series, metric_proto->mutable_time_series());
    }
    SetStats(metric.stats, metric_proto->mutable_stats());
  }

  return WriteMetricsToFile(options_.export_file_path, metrics_set);
#else
  RTC_LOG(LS_ERROR)
      << "Compile with protobuf support to properly use this class";
  return false;
#endif
}

}  // namespace test
}  // namespace webrtc
