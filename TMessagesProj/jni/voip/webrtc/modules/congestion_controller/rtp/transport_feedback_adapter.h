/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_CONGESTION_CONTROLLER_RTP_TRANSPORT_FEEDBACK_ADAPTER_H_
#define MODULES_CONGESTION_CONTROLLER_RTP_TRANSPORT_FEEDBACK_ADAPTER_H_

#include <deque>
#include <map>
#include <utility>
#include <vector>

#include "api/transport/network_types.h"
#include "modules/include/module_common_types_public.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "rtc_base/network/sent_packet.h"
#include "rtc_base/network_route.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/thread_checker.h"

namespace webrtc {

struct PacketFeedback {
  PacketFeedback() = default;
  // Time corresponding to when this object was created.
  Timestamp creation_time = Timestamp::MinusInfinity();
  SentPacket sent;
  // Time corresponding to when the packet was received. Timestamped with the
  // receiver's clock. For unreceived packet, Timestamp::PlusInfinity() is
  // used.
  Timestamp receive_time = Timestamp::PlusInfinity();

  // The network route that this packet is associated with.
  rtc::NetworkRoute network_route;
};

class InFlightBytesTracker {
 public:
  void AddInFlightPacketBytes(const PacketFeedback& packet);
  void RemoveInFlightPacketBytes(const PacketFeedback& packet);
  DataSize GetOutstandingData(const rtc::NetworkRoute& network_route) const;

 private:
  struct NetworkRouteComparator {
    bool operator()(const rtc::NetworkRoute& a,
                    const rtc::NetworkRoute& b) const;
  };
  std::map<rtc::NetworkRoute, DataSize, NetworkRouteComparator> in_flight_data_;
};

class TransportFeedbackAdapter {
 public:
  TransportFeedbackAdapter();

  void AddPacket(const RtpPacketSendInfo& packet_info,
                 size_t overhead_bytes,
                 Timestamp creation_time);
  absl::optional<SentPacket> ProcessSentPacket(
      const rtc::SentPacket& sent_packet);

  absl::optional<TransportPacketsFeedback> ProcessTransportFeedback(
      const rtcp::TransportFeedback& feedback,
      Timestamp feedback_receive_time);

  void SetNetworkRoute(const rtc::NetworkRoute& network_route);

  DataSize GetOutstandingData() const;

 private:
  enum class SendTimeHistoryStatus { kNotAdded, kOk, kDuplicate };

  std::vector<PacketResult> ProcessTransportFeedbackInner(
      const rtcp::TransportFeedback& feedback,
      Timestamp feedback_receive_time);

  DataSize pending_untracked_size_ = DataSize::Zero();
  Timestamp last_send_time_ = Timestamp::MinusInfinity();
  Timestamp last_untracked_send_time_ = Timestamp::MinusInfinity();
  SequenceNumberUnwrapper seq_num_unwrapper_;
  std::map<int64_t, PacketFeedback> history_;

  // Sequence numbers are never negative, using -1 as it always < a real
  // sequence number.
  int64_t last_ack_seq_num_ = -1;
  InFlightBytesTracker in_flight_;

  Timestamp current_offset_ = Timestamp::MinusInfinity();
  TimeDelta last_timestamp_ = TimeDelta::MinusInfinity();

  rtc::NetworkRoute network_route_;
};

}  // namespace webrtc

#endif  // MODULES_CONGESTION_CONTROLLER_RTP_TRANSPORT_FEEDBACK_ADAPTER_H_
