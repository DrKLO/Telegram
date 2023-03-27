/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/metrics/global_metrics_logger_and_exporter.h"

#include <memory>
#include <utility>
#include <vector>

#include "api/test/metrics/metrics_exporter.h"
#include "api/test/metrics/metrics_logger.h"
#include "api/test/metrics/metrics_logger_and_exporter.h"
#include "rtc_base/checks.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {
namespace test {

DefaultMetricsLogger* GetGlobalMetricsLogger() {
  static DefaultMetricsLogger* logger_ =
      new DefaultMetricsLogger(Clock::GetRealTimeClock());
  return logger_;
}

bool ExportPerfMetric(MetricsLogger& logger,
                      std::vector<std::unique_ptr<MetricsExporter>> exporters) {
  std::vector<Metric> metrics = logger.GetCollectedMetrics();
  bool success = true;
  for (auto& exporter : exporters) {
    bool export_result = exporter->Export(metrics);
    success = success && export_result;
  }
  return success;
}

}  // namespace test
}  // namespace webrtc
