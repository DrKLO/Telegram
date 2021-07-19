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

#include "modules/congestion_controller/goog_cc/acknowledged_bitrate_estimator.h"
#include "modules/congestion_controller/goog_cc/robust_throughput_estimator.h"
#include "rtc_base/logging.h"

namespace webrtc {

constexpr char RobustThroughputEstimatorSettings::kKey[];

RobustThroughputEstimatorSettings::RobustThroughputEstimatorSettings(
    const WebRtcKeyValueConfig* key_value_config) {
  Parser()->Parse(
      key_value_config->Lookup(RobustThroughputEstimatorSettings::kKey));
  if (min_packets < 10 || kMaxPackets < min_packets) {
    RTC_LOG(LS_WARNING) << "Window size must be between 10 and " << kMaxPackets
                        << " packets";
    min_packets = 20;
  }
  if (initial_packets < 10 || kMaxPackets < initial_packets) {
    RTC_LOG(LS_WARNING) << "Initial size must be between 10 and " << kMaxPackets
                        << " packets";
    initial_packets = 20;
  }
  initial_packets = std::min(initial_packets, min_packets);
  if (window_duration < TimeDelta::Millis(100) ||
      TimeDelta::Millis(2000) < window_duration) {
    RTC_LOG(LS_WARNING) << "Window duration must be between 100 and 2000 ms";
    window_duration = TimeDelta::Millis(500);
  }
  if (unacked_weight < 0.0 || 1.0 < unacked_weight) {
    RTC_LOG(LS_WARNING)
        << "Weight for prior unacked size must be between 0 and 1.";
    unacked_weight = 1.0;
  }
}

std::unique_ptr<StructParametersParser>
RobustThroughputEstimatorSettings::Parser() {
  return StructParametersParser::Create("enabled", &enabled,                  //
                                        "reduce_bias", &reduce_bias,          //
                                        "assume_shared_link",                 //
                                        &assume_shared_link,                  //
                                        "min_packets", &min_packets,          //
                                        "window_duration", &window_duration,  //
                                        "initial_packets", &initial_packets,  //
                                        "unacked_weight", &unacked_weight);
}

AcknowledgedBitrateEstimatorInterface::
    ~AcknowledgedBitrateEstimatorInterface() {}

std::unique_ptr<AcknowledgedBitrateEstimatorInterface>
AcknowledgedBitrateEstimatorInterface::Create(
    const WebRtcKeyValueConfig* key_value_config) {
  RobustThroughputEstimatorSettings simplified_estimator_settings(
      key_value_config);
  if (simplified_estimator_settings.enabled) {
    return std::make_unique<RobustThroughputEstimator>(
        simplified_estimator_settings);
  }
  return std::make_unique<AcknowledgedBitrateEstimator>(key_value_config);
}

}  // namespace webrtc
