/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/pcc/rtt_tracker.h"

#include <algorithm>

namespace webrtc {
namespace pcc {

RttTracker::RttTracker(TimeDelta initial_rtt, double alpha)
    : rtt_estimate_(initial_rtt), alpha_(alpha) {}

void RttTracker::OnPacketsFeedback(
    const std::vector<PacketResult>& packet_feedbacks,
    Timestamp feedback_received_time) {
  TimeDelta packet_rtt = TimeDelta::MinusInfinity();
  for (const PacketResult& packet_result : packet_feedbacks) {
    if (packet_result.receive_time.IsInfinite())
      continue;
    packet_rtt = std::max<TimeDelta>(
        packet_rtt,
        feedback_received_time - packet_result.sent_packet.send_time);
  }
  if (packet_rtt.IsFinite())
    rtt_estimate_ = (1 - alpha_) * rtt_estimate_ + alpha_ * packet_rtt;
}

TimeDelta RttTracker::GetRtt() const {
  return rtt_estimate_;
}

}  // namespace pcc
}  // namespace webrtc
