/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/goog_cc/acknowledged_bitrate_estimator_interface.h"

#include <algorithm>
#include <memory>

#include "api/field_trials_view.h"
#include "api/units/time_delta.h"
#include "modules/congestion_controller/goog_cc/acknowledged_bitrate_estimator.h"
#include "modules/congestion_controller/goog_cc/robust_throughput_estimator.h"
#include "rtc_base/experiments/struct_parameters_parser.h"
#include "rtc_base/logging.h"

namespace webrtc {

constexpr char RobustThroughputEstimatorSettings::kKey[];

RobustThroughputEstimatorSettings::RobustThroughputEstimatorSettings(
    const FieldTrialsView* key_value_config) {
  Parser()->Parse(
      key_value_config->Lookup(RobustThroughputEstimatorSettings::kKey));
  if (window_packets < 10 || 1000 < window_packets) {
    RTC_LOG(LS_WARNING) << "Window size must be between 10 and 1000 packets";
    window_packets = 20;
  }
  if (max_window_packets < 10 || 1000 < max_window_packets) {
    RTC_LOG(LS_WARNING)
        << "Max window size must be between 10 and 1000 packets";
    max_window_packets = 500;
  }
  max_window_packets = std::max(max_window_packets, window_packets);

  if (required_packets < 10 || 1000 < required_packets) {
    RTC_LOG(LS_WARNING) << "Required number of initial packets must be between "
                           "10 and 1000 packets";
    required_packets = 10;
  }
  required_packets = std::min(required_packets, window_packets);

  if (min_window_duration < TimeDelta::Millis(100) ||
      TimeDelta::Millis(3000) < min_window_duration) {
    RTC_LOG(LS_WARNING) << "Window duration must be between 100 and 3000 ms";
    min_window_duration = TimeDelta::Millis(750);
  }
  if (max_window_duration < TimeDelta::Seconds(1) ||
      TimeDelta::Seconds(15) < max_window_duration) {
    RTC_LOG(LS_WARNING) << "Max window duration must be between 1 and 15 s";
    max_window_duration = TimeDelta::Seconds(5);
  }
  min_window_duration = std::min(min_window_duration, max_window_duration);

  if (unacked_weight < 0.0 || 1.0 < unacked_weight) {
    RTC_LOG(LS_WARNING)
        << "Weight for prior unacked size must be between 0 and 1.";
    unacked_weight = 1.0;
  }
}

std::unique_ptr<StructParametersParser>
RobustThroughputEstimatorSettings::Parser() {
  return StructParametersParser::Create(
      "enabled", &enabled,                          //
      "window_packets", &window_packets,            //
      "max_window_packets", &max_window_packets,    //
      "window_duration", &min_window_duration,      //
      "max_window_duration", &max_window_duration,  //
      "required_packets", &required_packets,        //
      "unacked_weight", &unacked_weight);
}

AcknowledgedBitrateEstimatorInterface::
    ~AcknowledgedBitrateEstimatorInterface() {}

std::unique_ptr<AcknowledgedBitrateEstimatorInterface>
AcknowledgedBitrateEstimatorInterface::Create(
    const FieldTrialsView* key_value_config) {
  RobustThroughputEstimatorSettings simplified_estimator_settings(
      key_value_config);
  if (simplified_estimator_settings.enabled) {
    return std::make_unique<RobustThroughputEstimator>(
        simplified_estimator_settings);
  }
  return std::make_unique<AcknowledgedBitrateEstimator>(key_value_config);
}

}  // namespace webrtc
