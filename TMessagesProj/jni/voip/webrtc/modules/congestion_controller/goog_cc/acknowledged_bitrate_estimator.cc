/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/goog_cc/acknowledged_bitrate_estimator.h"

#include <stddef.h>

#include <algorithm>
#include <memory>
#include <utility>

#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

AcknowledgedBitrateEstimator::AcknowledgedBitrateEstimator(
    const WebRtcKeyValueConfig* key_value_config)
    : AcknowledgedBitrateEstimator(
          key_value_config,
          std::make_unique<BitrateEstimator>(key_value_config)) {}

AcknowledgedBitrateEstimator::~AcknowledgedBitrateEstimator() {}

AcknowledgedBitrateEstimator::AcknowledgedBitrateEstimator(
    const WebRtcKeyValueConfig* key_value_config,
    std::unique_ptr<BitrateEstimator> bitrate_estimator)
    : in_alr_(false), bitrate_estimator_(std::move(bitrate_estimator)) {}

void AcknowledgedBitrateEstimator::IncomingPacketFeedbackVector(
    const std::vector<PacketResult>& packet_feedback_vector) {
  RTC_DCHECK(std::is_sorted(packet_feedback_vector.begin(),
                            packet_feedback_vector.end(),
                            PacketResult::ReceiveTimeOrder()));
  for (const auto& packet : packet_feedback_vector) {
    if (alr_ended_time_ && packet.sent_packet.send_time > *alr_ended_time_) {
      bitrate_estimator_->ExpectFastRateChange();
      alr_ended_time_.reset();
    }
    DataSize acknowledged_estimate = packet.sent_packet.size;
    acknowledged_estimate += packet.sent_packet.prior_unacked_data;
    bitrate_estimator_->Update(packet.receive_time, acknowledged_estimate,
                               in_alr_);
  }
}

absl::optional<DataRate> AcknowledgedBitrateEstimator::bitrate() const {
  return bitrate_estimator_->bitrate();
}

absl::optional<DataRate> AcknowledgedBitrateEstimator::PeekRate() const {
  return bitrate_estimator_->PeekRate();
}

void AcknowledgedBitrateEstimator::SetAlrEndedTime(Timestamp alr_ended_time) {
  alr_ended_time_.emplace(alr_ended_time);
}

void AcknowledgedBitrateEstimator::SetAlr(bool in_alr) {
  in_alr_ = in_alr;
}

}  // namespace webrtc
