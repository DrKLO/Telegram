/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_CONGESTION_CONTROLLER_GOOG_CC_ACKNOWLEDGED_BITRATE_ESTIMATOR_INTERFACE_H_
#define MODULES_CONGESTION_CONTROLLER_GOOG_CC_ACKNOWLEDGED_BITRATE_ESTIMATOR_INTERFACE_H_

#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/transport/network_types.h"
#include "api/transport/webrtc_key_value_config.h"
#include "api/units/data_rate.h"
#include "rtc_base/experiments/struct_parameters_parser.h"

namespace webrtc {

struct RobustThroughputEstimatorSettings {
  static constexpr char kKey[] = "WebRTC-Bwe-RobustThroughputEstimatorSettings";
  static constexpr size_t kMaxPackets = 500;

  RobustThroughputEstimatorSettings() = delete;
  explicit RobustThroughputEstimatorSettings(
      const WebRtcKeyValueConfig* key_value_config);

  bool enabled = false;  // Set to true to use RobustThroughputEstimator.

  // The estimator handles delay spikes by removing the largest receive time
  // gap, but this introduces some bias that may lead to overestimation when
  // there isn't any delay spike. If |reduce_bias| is true, we instead replace
  // the largest receive time gap by the second largest. This reduces the bias
  // at the cost of not completely removing the genuine delay spikes.
  bool reduce_bias = true;

  // If |assume_shared_link| is false, we ignore the size of the first packet
  // when computing the receive rate. Otherwise, we remove half of the first
  // and last packet's sizes.
  bool assume_shared_link = false;

  // The estimator window keeps at least |min_packets| packets and up to
  // kMaxPackets received during the last |window_duration|.
  unsigned min_packets = 20;
  TimeDelta window_duration = TimeDelta::Millis(500);

  // The estimator window requires at least |initial_packets| packets received
  // over at least |initial_duration|.
  unsigned initial_packets = 20;

  // If audio packets are included in allocation, but not in bandwidth
  // estimation and the sent audio packets get double counted,
  // then it might be useful to reduce the weight to 0.5.
  double unacked_weight = 1.0;

  std::unique_ptr<StructParametersParser> Parser();
};

class AcknowledgedBitrateEstimatorInterface {
 public:
  static std::unique_ptr<AcknowledgedBitrateEstimatorInterface> Create(
      const WebRtcKeyValueConfig* key_value_config);
  virtual ~AcknowledgedBitrateEstimatorInterface();

  virtual void IncomingPacketFeedbackVector(
      const std::vector<PacketResult>& packet_feedback_vector) = 0;
  virtual absl::optional<DataRate> bitrate() const = 0;
  virtual absl::optional<DataRate> PeekRate() const = 0;
  virtual void SetAlr(bool in_alr) = 0;
  virtual void SetAlrEndedTime(Timestamp alr_ended_time) = 0;
};

}  // namespace webrtc

#endif  // MODULES_CONGESTION_CONTROLLER_GOOG_CC_ACKNOWLEDGED_BITRATE_ESTIMATOR_INTERFACE_H_
