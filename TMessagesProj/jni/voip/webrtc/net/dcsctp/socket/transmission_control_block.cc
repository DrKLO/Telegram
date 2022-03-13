/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/socket/transmission_control_block.h"

#include <algorithm>
#include <cstdint>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "net/dcsctp/packet/chunk/data_chunk.h"
#include "net/dcsctp/packet/chunk/forward_tsn_chunk.h"
#include "net/dcsctp/packet/chunk/idata_chunk.h"
#include "net/dcsctp/packet/chunk/iforward_tsn_chunk.h"
#include "net/dcsctp/packet/chunk/reconfig_chunk.h"
#include "net/dcsctp/packet/chunk/sack_chunk.h"
#include "net/dcsctp/packet/sctp_packet.h"
#include "net/dcsctp/public/dcsctp_options.h"
#include "net/dcsctp/rx/data_tracker.h"
#include "net/dcsctp/rx/reassembly_queue.h"
#include "net/dcsctp/socket/capabilities.h"
#include "net/dcsctp/socket/stream_reset_handler.h"
#include "net/dcsctp/timer/timer.h"
#include "net/dcsctp/tx/retransmission_queue.h"
#include "net/dcsctp/tx/retransmission_timeout.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"

namespace dcsctp {

void TransmissionControlBlock::ObserveRTT(DurationMs rtt) {
  DurationMs prev_rto = rto_.rto();
  rto_.ObserveRTT(rtt);
  RTC_DLOG(LS_VERBOSE) << log_prefix_ << "new rtt=" << *rtt
                       << ", srtt=" << *rto_.srtt() << ", rto=" << *rto_.rto()
                       << " (" << *prev_rto << ")";
  t3_rtx_->set_duration(rto_.rto());

  DurationMs delayed_ack_tmo =
      std::min(rto_.rto() * 0.5, options_.delayed_ack_max_timeout);
  delayed_ack_timer_->set_duration(delayed_ack_tmo);
}

absl::optional<DurationMs> TransmissionControlBlock::OnRtxTimerExpiry() {
  TimeMs now = callbacks_.TimeMillis();
  RTC_DLOG(LS_INFO) << log_prefix_ << "Timer " << t3_rtx_->name()
                    << " has expired";
  if (cookie_echo_chunk_.has_value()) {
    // In the COOKIE_ECHO state, let the T1-COOKIE timer trigger
    // retransmissions, to avoid having two timers doing that.
    RTC_DLOG(LS_VERBOSE) << "Not retransmitting as T1-cookie is active.";
  } else {
    if (IncrementTxErrorCounter("t3-rtx expired")) {
      retransmission_queue_.HandleT3RtxTimerExpiry();
      SendBufferedPackets(now);
    }
  }
  return absl::nullopt;
}

absl::optional<DurationMs> TransmissionControlBlock::OnDelayedAckTimerExpiry() {
  data_tracker_.HandleDelayedAckTimerExpiry();
  MaybeSendSack();
  return absl::nullopt;
}

void TransmissionControlBlock::MaybeSendSack() {
  if (data_tracker_.ShouldSendAck(/*also_if_delayed=*/false)) {
    SctpPacket::Builder builder = PacketBuilder();
    builder.Add(
        data_tracker_.CreateSelectiveAck(reassembly_queue_.remaining_bytes()));
    Send(builder);
  }
}

void TransmissionControlBlock::MaybeSendForwardTsn(SctpPacket::Builder& builder,
                                                   TimeMs now) {
  if (now >= limit_forward_tsn_until_ &&
      retransmission_queue_.ShouldSendForwardTsn(now)) {
    if (capabilities_.message_interleaving) {
      builder.Add(retransmission_queue_.CreateIForwardTsn());
    } else {
      builder.Add(retransmission_queue_.CreateForwardTsn());
    }
    packet_sender_.Send(builder);
    // https://datatracker.ietf.org/doc/html/rfc3758
    // "IMPLEMENTATION NOTE: An implementation may wish to limit the number of
    // duplicate FORWARD TSN chunks it sends by ... waiting a full RTT before
    // sending a duplicate FORWARD TSN."
    // "Any delay applied to the sending of FORWARD TSN chunk SHOULD NOT exceed
    // 200ms and MUST NOT exceed 500ms".
    limit_forward_tsn_until_ = now + std::min(DurationMs(200), rto_.srtt());
  }
}

void TransmissionControlBlock::SendBufferedPackets(SctpPacket::Builder& builder,
                                                   TimeMs now) {
  // FORWARD-TSNs are sent as separate packets to avoid bugs.webrtc.org/12961.
  MaybeSendForwardTsn(builder, now);

  for (int packet_idx = 0;
       packet_idx < options_.max_burst && retransmission_queue_.can_send_data();
       ++packet_idx) {
    // Only add control chunks to the first packet that is sent, if sending
    // multiple packets in one go (as allowed by the congestion window).
    if (packet_idx == 0) {
      if (cookie_echo_chunk_.has_value()) {
        // https://tools.ietf.org/html/rfc4960#section-5.1
        // "The COOKIE ECHO chunk can be bundled with any pending outbound DATA
        // chunks, but it MUST be the first chunk in the packet..."
        RTC_DCHECK(builder.empty());
        builder.Add(*cookie_echo_chunk_);
      }

      // https://tools.ietf.org/html/rfc4960#section-6
      // "Before an endpoint transmits a DATA chunk, if any received DATA
      // chunks have not been acknowledged (e.g., due to delayed ack), the
      // sender should create a SACK and bundle it with the outbound DATA chunk,
      // as long as the size of the final SCTP packet does not exceed the
      // current MTU."
      if (data_tracker_.ShouldSendAck(/*also_if_delayed=*/true)) {
        builder.Add(data_tracker_.CreateSelectiveAck(
            reassembly_queue_.remaining_bytes()));
      }
      absl::optional<ReConfigChunk> reconfig =
          stream_reset_handler_.MakeStreamResetRequest();
      if (reconfig.has_value()) {
        builder.Add(*reconfig);
      }
    }

    auto chunks =
        retransmission_queue_.GetChunksToSend(now, builder.bytes_remaining());
    for (auto& elem : chunks) {
      TSN tsn = elem.first;
      Data data = std::move(elem.second);
      if (capabilities_.message_interleaving) {
        builder.Add(IDataChunk(tsn, std::move(data), false));
      } else {
        builder.Add(DataChunk(tsn, std::move(data), false));
      }
    }

    if (!packet_sender_.Send(builder)) {
      break;
    }

    if (cookie_echo_chunk_.has_value()) {
      // https://tools.ietf.org/html/rfc4960#section-5.1
      // "...  until the COOKIE ACK is returned the sender MUST NOT send any
      // other packets to the peer."
      break;
    }
  }
}

std::string TransmissionControlBlock::ToString() const {
  rtc::StringBuilder sb;

  sb.AppendFormat(
      "verification_tag=%08x, last_cumulative_ack=%u, capabilities=",
      *peer_verification_tag_, *data_tracker_.last_cumulative_acked_tsn());

  if (capabilities_.partial_reliability) {
    sb << "PR,";
  }
  if (capabilities_.message_interleaving) {
    sb << "IL,";
  }
  if (capabilities_.reconfig) {
    sb << "Reconfig,";
  }

  return sb.Release();
}

HandoverReadinessStatus TransmissionControlBlock::GetHandoverReadiness() const {
  HandoverReadinessStatus status;
  status.Add(data_tracker_.GetHandoverReadiness());
  status.Add(stream_reset_handler_.GetHandoverReadiness());
  status.Add(reassembly_queue_.GetHandoverReadiness());
  status.Add(retransmission_queue_.GetHandoverReadiness());
  return status;
}

void TransmissionControlBlock::AddHandoverState(
    DcSctpSocketHandoverState& state) {
  state.capabilities.partial_reliability = capabilities_.partial_reliability;
  state.capabilities.message_interleaving = capabilities_.message_interleaving;
  state.capabilities.reconfig = capabilities_.reconfig;

  state.my_verification_tag = my_verification_tag().value();
  state.peer_verification_tag = peer_verification_tag().value();
  state.my_initial_tsn = my_initial_tsn().value();
  state.peer_initial_tsn = peer_initial_tsn().value();
  state.tie_tag = tie_tag().value();

  data_tracker_.AddHandoverState(state);
  stream_reset_handler_.AddHandoverState(state);
  reassembly_queue_.AddHandoverState(state);
  retransmission_queue_.AddHandoverState(state);
}
}  // namespace dcsctp
