/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/metrics/stdout_metrics_exporter.h"

#include <stdio.h>

#include <cmath>
#include <string>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/test/metrics/metric.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {
namespace test {
namespace {

// Returns positive integral part of the number.
int64_t IntegralPart(double value) {
  return std::lround(std::floor(std::abs(value)));
}

void AppendWithPrecision(double value,
                         int digits_after_comma,
                         rtc::StringBuilder& out) {
  int64_t multiplier = std::lround(std::pow(10, digits_after_comma));
  int64_t integral_part = IntegralPart(value);
  double decimal_part = std::abs(value) - integral_part;

  // If decimal part has leading zeros then when it will be multiplied on
  // `multiplier`, leading zeros will be lost. To preserve them we add "1"
  // so then leading digit will be greater than 0 and won't be removed.
  //
  // During conversion to the string leading digit has to be stripped.
  //
  // Also due to rounding it may happen that leading digit may be incremented,
  // like with `digits_after_comma` 3 number 1.9995 will be rounded to 2. In
  // such case this increment has to be propagated to the `integral_part`.
  int64_t decimal_holder = std::lround((1 + decimal_part) * multiplier);
  if (decimal_holder >= 2 * multiplier) {
    // Rounding incremented added leading digit, so we need to transfer 1 to
    // integral part.
    integral_part++;
    decimal_holder -= multiplier;
  }
  // Remove trailing zeros.
  while (decimal_holder % 10 == 0) {
    decimal_holder /= 10;
  }

  // Print serialized number to output.
  if (value < 0) {
    out << "-";
  }
  out << integral_part;
  if (decimal_holder != 1) {
    out << "." << std::to_string(decimal_holder).substr(1, digits_after_comma);
  }
}

}  // namespace

StdoutMetricsExporter::StdoutMetricsExporter() : output_(stdout) {}

bool StdoutMetricsExporter::Export(rtc::ArrayView<const Metric> metrics) {
  for (const Metric& metric : metrics) {
    PrintMetric(metric);
  }
  return true;
}

void StdoutMetricsExporter::PrintMetric(const Metric& metric) {
  rtc::StringBuilder value_stream;
  value_stream << metric.test_case << " / " << metric.name << "= {mean=";
  if (metric.stats.mean.has_value()) {
    AppendWithPrecision(*metric.stats.mean, 8, value_stream);
  } else {
    value_stream << "-";
  }
  value_stream << ", stddev=";
  if (metric.stats.stddev.has_value()) {
    AppendWithPrecision(*metric.stats.stddev, 8, value_stream);
  } else {
    value_stream << "-";
  }
  value_stream << "} " << ToString(metric.unit) << " ("
               << ToString(metric.improvement_direction) << ")";

  fprintf(output_, "RESULT: %s\n", value_stream.str().c_str());
}

}  // namespace test
}  // namespace webrtc
