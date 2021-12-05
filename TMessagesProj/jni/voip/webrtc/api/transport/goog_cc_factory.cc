/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/transport/goog_cc_factory.h"

#include <memory>
#include <utility>

#include "modules/congestion_controller/goog_cc/goog_cc_network_control.h"

namespace webrtc {
GoogCcNetworkControllerFactory::GoogCcNetworkControllerFactory(
    RtcEventLog* event_log)
    : event_log_(event_log) {}

GoogCcNetworkControllerFactory::GoogCcNetworkControllerFactory(
    NetworkStatePredictorFactoryInterface* network_state_predictor_factory) {
  factory_config_.network_state_predictor_factory =
      network_state_predictor_factory;
}

GoogCcNetworkControllerFactory::GoogCcNetworkControllerFactory(
    GoogCcFactoryConfig config)
    : factory_config_(std::move(config)) {}

std::unique_ptr<NetworkControllerInterface>
GoogCcNetworkControllerFactory::Create(NetworkControllerConfig config) {
  if (event_log_)
    config.event_log = event_log_;
  GoogCcConfig goog_cc_config;
  goog_cc_config.feedback_only = factory_config_.feedback_only;
  if (factory_config_.network_state_estimator_factory) {
    RTC_DCHECK(config.key_value_config);
    goog_cc_config.network_state_estimator =
        factory_config_.network_state_estimator_factory->Create(
            config.key_value_config);
  }
  if (factory_config_.network_state_predictor_factory) {
    goog_cc_config.network_state_predictor =
        factory_config_.network_state_predictor_factory
            ->CreateNetworkStatePredictor();
  }
  return std::make_unique<GoogCcNetworkController>(config,
                                                   std::move(goog_cc_config));
}

TimeDelta GoogCcNetworkControllerFactory::GetProcessInterval() const {
  const int64_t kUpdateIntervalMs = 25;
  return TimeDelta::Millis(kUpdateIntervalMs);
}

GoogCcFeedbackNetworkControllerFactory::GoogCcFeedbackNetworkControllerFactory(
    RtcEventLog* event_log)
    : GoogCcNetworkControllerFactory(event_log) {
  factory_config_.feedback_only = true;
}

}  // namespace webrtc
