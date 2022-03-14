/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/call_config.h"

#include "rtc_base/checks.h"

namespace webrtc {

CallConfig::CallConfig(RtcEventLog* event_log,
                       TaskQueueBase* network_task_queue /* = nullptr*/)
    : event_log(event_log), network_task_queue_(network_task_queue) {
  RTC_DCHECK(event_log);
}

CallConfig::CallConfig(const CallConfig& config) = default;

RtpTransportConfig CallConfig::ExtractTransportConfig() const {
  RtpTransportConfig transportConfig;
  transportConfig.bitrate_config = bitrate_config;
  transportConfig.event_log = event_log;
  transportConfig.network_controller_factory = network_controller_factory;
  transportConfig.network_state_predictor_factory =
      network_state_predictor_factory;
  transportConfig.task_queue_factory = task_queue_factory;
  transportConfig.trials = trials;

  return transportConfig;
}

CallConfig::~CallConfig() = default;

}  // namespace webrtc
