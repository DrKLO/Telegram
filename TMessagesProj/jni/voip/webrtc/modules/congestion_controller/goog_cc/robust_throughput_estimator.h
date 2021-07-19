/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_CONGESTION_CONTROLLER_GOOG_CC_ROBUST_THROUGHPUT_ESTIMATOR_H_
#define MODULES_CONGESTION_CONTROLLER_GOOG_CC_ROBUST_THROUGHPUT_ESTIMATOR_H_

#include <deque>
#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/transport/network_types.h"
#include "api/transport/webrtc_key_value_config.h"
#include "api/units/data_rate.h"
#include "modules/congestion_controller/goog_cc/acknowledged_bitrate_estimator_interface.h"

namespace webrtc {

class RobustThroughputEstimator : public AcknowledgedBitrateEstimatorInterface {
 public:
  explicit RobustThroughputEstimator(
      const RobustThroughputEstimatorSettings& settings);
  ~RobustThroughputEstimator() override;

  void IncomingPacketFeedbackVector(
      const std::vector<PacketResult>& packet_feedback_vector) override;

  absl::optional<DataRate> bitrate() const override;

  absl::optional<DataRate> PeekRate() const override { return bitrate(); }
  void SetAlr(bool /*in_alr*/) override {}
  void SetAlrEndedTime(Timestamp /*alr_ended_time*/) override {}

 private:
  const RobustThroughputEstimatorSettings settings_;
  std::deque<PacketResult> window_;
};

}  // namespace webrtc

#endif  // MODULES_CONGESTION_CONTROLLER_GOOG_CC_ROBUST_THROUGHPUT_ESTIMATOR_H_
