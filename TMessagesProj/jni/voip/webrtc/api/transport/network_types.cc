/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/transport/network_types.h"

#include <algorithm>

namespace webrtc {
StreamsConfig::StreamsConfig() = default;
StreamsConfig::StreamsConfig(const StreamsConfig&) = default;
StreamsConfig::~StreamsConfig() = default;

TargetRateConstraints::TargetRateConstraints() = default;
TargetRateConstraints::TargetRateConstraints(const TargetRateConstraints&) =
    default;
TargetRateConstraints::~TargetRateConstraints() = default;

NetworkRouteChange::NetworkRouteChange() = default;
NetworkRouteChange::NetworkRouteChange(const NetworkRouteChange&) = default;
NetworkRouteChange::~NetworkRouteChange() = default;

PacketResult::PacketResult() = default;
PacketResult::PacketResult(const PacketResult& other) = default;
PacketResult::~PacketResult() = default;

bool PacketResult::ReceiveTimeOrder::operator()(const PacketResult& lhs,
                                                const PacketResult& rhs) {
  if (lhs.receive_time != rhs.receive_time)
    return lhs.receive_time < rhs.receive_time;
  if (lhs.sent_packet.send_time != rhs.sent_packet.send_time)
    return lhs.sent_packet.send_time < rhs.sent_packet.send_time;
  return lhs.sent_packet.sequence_number < rhs.sent_packet.sequence_number;
}

TransportPacketsFeedback::TransportPacketsFeedback() = default;
TransportPacketsFeedback::TransportPacketsFeedback(
    const TransportPacketsFeedback& other) = default;
TransportPacketsFeedback::~TransportPacketsFeedback() = default;

std::vector<PacketResult> TransportPacketsFeedback::ReceivedWithSendInfo()
    const {
  std::vector<PacketResult> res;
  for (const PacketResult& fb : packet_feedbacks) {
    if (fb.receive_time.IsFinite()) {
      res.push_back(fb);
    }
  }
  return res;
}

std::vector<PacketResult> TransportPacketsFeedback::LostWithSendInfo() const {
  std::vector<PacketResult> res;
  for (const PacketResult& fb : packet_feedbacks) {
    if (fb.receive_time.IsPlusInfinity()) {
      res.push_back(fb);
    }
  }
  return res;
}

std::vector<PacketResult> TransportPacketsFeedback::PacketsWithFeedback()
    const {
  return packet_feedbacks;
}

std::vector<PacketResult> TransportPacketsFeedback::SortedByReceiveTime()
    const {
  std::vector<PacketResult> res;
  for (const PacketResult& fb : packet_feedbacks) {
    if (fb.receive_time.IsFinite()) {
      res.push_back(fb);
    }
  }
  std::sort(res.begin(), res.end(), PacketResult::ReceiveTimeOrder());
  return res;
}

NetworkControlUpdate::NetworkControlUpdate() = default;
NetworkControlUpdate::NetworkControlUpdate(const NetworkControlUpdate&) =
    default;
NetworkControlUpdate::~NetworkControlUpdate() = default;

PacedPacketInfo::PacedPacketInfo() = default;

PacedPacketInfo::PacedPacketInfo(int probe_cluster_id,
                                 int probe_cluster_min_probes,
                                 int probe_cluster_min_bytes)
    : probe_cluster_id(probe_cluster_id),
      probe_cluster_min_probes(probe_cluster_min_probes),
      probe_cluster_min_bytes(probe_cluster_min_bytes) {}

bool PacedPacketInfo::operator==(const PacedPacketInfo& rhs) const {
  return send_bitrate_bps == rhs.send_bitrate_bps &&
         probe_cluster_id == rhs.probe_cluster_id &&
         probe_cluster_min_probes == rhs.probe_cluster_min_probes &&
         probe_cluster_min_bytes == rhs.probe_cluster_min_bytes;
}

ProcessInterval::ProcessInterval() = default;
ProcessInterval::ProcessInterval(const ProcessInterval&) = default;
ProcessInterval::~ProcessInterval() = default;

}  // namespace webrtc
