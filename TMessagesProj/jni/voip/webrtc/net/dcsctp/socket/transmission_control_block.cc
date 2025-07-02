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
#include "api/units/time_delta.h"
#include "net/dcsctp/packet/chunk/data_chunk.h"
#include "net/dcsctp/packet/chunk/forward_tsn_chunk.h"
#include "net/dcsctp/packet/chunk/idata_chunk.h"
#include "net/dcsctp/packet/chunk/iforward_tsn_chunk.h"
#include "net/dcsctp/packet/chunk/reconfig_chunk.h"
#include "net/dcsctp/packet/chunk/sack_chunk.h"
#include "net/dcsctp/packet/sctp_packet.h"
#include "net/dcsctp/public/dcsctp_options.h"
#include "net/dcsctp/public/types.h"
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
using ::webrtc::TimeDelta;
using ::webrtc::Timestamp;

TransmissionControlBlock::TransmissionControlBlock(
    TimerManager& timer_manager,
    absl::string_view log_prefix,
    const DcSctpOptions& options,
    const Capabilities& capabilities,
    DcSctpSocketCallbacks& callbacks,
    SendQueue& send_queue,
    VerificationTag my_verification_tag,
    TSN my_initial_tsn,
    VerificationTag peer_verification_tag,
    TSN peer_initial_tsn,
    size_t a_rwnd,
    TieTag tie_tag,
    PacketSender& packet_sender,
    std::function<bool()> is_connection_established)
    : log_prefix_(log_prefix),
      options_(options),
      timer_manager_(timer_manager),
      capabilities_(capabilities),
      callbacks_(callbacks),
      t3_rtx_(timer_manager_.CreateTimer(
          "t3-rtx",
          absl::bind_front(&TransmissionControlBlock::OnRtxTimerExpiry, this),
          TimerOptions(options.rto_initial.ToTimeDelta(),
                       TimerBackoffAlgorithm::kExponential,
                       /*max_restarts=*/absl::nullopt,
                       options.max_timer_backoff_duration.has_value()
                           ? options.max_timer_backoff_duration->ToTimeDelta()
                           : TimeDelta::PlusInfinity()))),
      delayed_ack_timer_(timer_manager_.CreateTimer(
          "delayed-ack",
          absl::bind_front(&TransmissionControlBlock::OnDelayedAckTimerExpiry,
                           this),
          TimerOptions(options.delayed_ack_max_timeout.ToTimeDelta(),
                       TimerBackoffAlgorithm::kExponential,
                       /*max_restarts=*/0,
                       /*max_backoff_duration=*/TimeDelta::PlusInfinity(),
                       webrtc::TaskQueueBase::DelayPrecision::kHigh))),
      my_verification_tag_(my_verification_tag),
      my_initial_tsn_(my_initial_tsn),
      peer_verification_tag_(peer_verification_tag),
      peer_initial_tsn_(peer_initial_tsn),
      tie_tag_(tie_tag),
      is_connection_established_(std::move(is_connection_established)),
      packet_sender_(packet_sender),
      rto_(options),
      tx_error_counter_(log_prefix, options),
      data_tracker_(log_prefix, delayed_ack_timer_.get(), peer_initial_tsn),
      reassembly_queue_(log_prefix,
                        peer_initial_tsn,
                        options.max_receiver_window_buffer_size,
                        capabilities.message_interleaving),
      retransmission_queue_(
          log_prefix,
          &callbacks_,
          my_initial_tsn,
          a_rwnd,
          send_queue,
          absl::bind_front(&TransmissionControlBlock::ObserveRTT, this),
          [this]() { tx_error_counter_.Clear(); },
          *t3_rtx_,
          options,
          capabilities.partial_reliability,
          capabilities.message_interleaving),
      stream_reset_handler_(log_prefix,
                            this,
                            &timer_manager,
                            &data_tracker_,
                            &reassembly_queue_,
                            &retransmission_queue_),
      heartbeat_handler_(log_prefix, options, this, &timer_manager_) {
  send_queue.EnableMessageInterleaving(capabilities.message_interleaving);
}

void TransmissionControlBlock::ObserveRTT(TimeDelta rtt) {
  TimeDelta prev_rto = rto_.rto();
  rto_.ObserveRTT(rtt);
  RTC_DLOG(LS_VERBOSE) << log_prefix_ << "new rtt=" << webrtc::ToString(rtt)
                       << ", srtt=" << webrtc::ToString(rto_.srtt())
                       << ", rto=" << webrtc::ToString(rto_.rto()) << " ("
                       << webrtc::ToString(prev_rto) << ")";
  t3_rtx_->set_duration(rto_.rto());

  TimeDelta delayed_ack_tmo = std::min(
      rto_.rto() * 0.5, options_.delayed_ack_max_timeout.ToTimeDelta());
  delayed_ack_timer_->set_duration(delayed_ack_tmo);
}

TimeDelta TransmissionControlBlock::OnRtxTimerExpiry() {
  Timestamp now = callbacks_.Now();
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
  return TimeDelta::Zero();
}

TimeDelta TransmissionControlBlock::OnDelayedAckTimerExpiry() {
  data_tracker_.HandleDelayedAckTimerExpiry();
  MaybeSendSack();
  return TimeDelta::Zero();
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
                                                   Timestamp now) {
  if (now >= limit_forward_tsn_until_ &&
      retransmission_queue_.ShouldSendForwardTsn(now)) {
    if (capabilities_.message_interleaving) {
      builder.Add(retransmission_queue_.CreateIForwardTsn());
    } else {
      builder.Add(retransmission_queue_.CreateForwardTsn());
    }
    Send(builder);
    // https://datatracker.ietf.org/doc/html/rfc3758
    // "IMPLEMENTATION NOTE: An implementation may wish to limit the number of
    // duplicate FORWARD TSN chunks it sends by ... waiting a full RTT before
    // sending a duplicate FORWARD TSN."
    // "Any delay applied to the sending of FORWARD TSN chunk SHOULD NOT exceed
    // 200ms and MUST NOT exceed 500ms".
    limit_forward_tsn_until_ =
        now + std::min(TimeDelta::Millis(200), rto_.srtt());
  }
}

void TransmissionControlBlock::MaybeSendFastRetransmit() {
  if (!retransmission_queue_.has_data_to_be_fast_retransmitted()) {
    return;
  }

  // https://datatracker.ietf.org/doc/html/rfc4960#section-7.2.4
  // "Determine how many of the earliest (i.e., lowest TSN) DATA chunks marked
  // for retransmission will fit into a single packet, subject to constraint of
  // the path MTU of the destination transport address to which the packet is
  // being sent.  Call this value K. Retransmit those K DATA chunks in a single
  // packet.  When a Fast Retransmit is being performed, the sender SHOULD
  // ignore the value of cwnd and SHOULD NOT delay retransmission for this
  // single packet."

  SctpPacket::Builder builder(peer_verification_tag_, options_);
  auto chunks = retransmission_queue_.GetChunksForFastRetransmit(
      builder.bytes_remaining());
  for (auto& [tsn, data] : chunks) {
    if (capabilities_.message_interleaving) {
      builder.Add(IDataChunk(tsn, std::move(data), false));
    } else {
      builder.Add(DataChunk(tsn, std::move(data), false));
    }
  }
  Send(builder);
}

void TransmissionControlBlock::SendBufferedPackets(SctpPacket::Builder& builder,
                                                   Timestamp now) {
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
      MaybeSendForwardTsn(builder, now);
      absl::optional<ReConfigChunk> reconfig =
          stream_reset_handler_.MakeStreamResetRequest();
      if (reconfig.has_value()) {
        builder.Add(*reconfig);
      }
    }

    auto chunks =
        retransmission_queue_.GetChunksToSend(now, builder.bytes_remaining());
    for (auto& [tsn, data] : chunks) {
      if (capabilities_.message_interleaving) {
        builder.Add(IDataChunk(tsn, std::move(data), false));
      } else {
        builder.Add(DataChunk(tsn, std::move(data), false));
      }
    }

    // https://www.ietf.org/archive/id/draft-tuexen-tsvwg-sctp-zero-checksum-02.html#section-4.2
    // "When an end point sends a packet containing a COOKIE ECHO chunk, it MUST
    // include a correct CRC32c checksum in the packet containing the COOKIE
    // ECHO chunk."
    bool write_checksum =
        !capabilities_.zero_checksum || cookie_echo_chunk_.has_value();
    if (!packet_sender_.Send(builder, write_checksum)) {
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
  if (capabilities_.zero_checksum) {
    sb << "ZeroChecksum,";
  }
  sb << " max_in=" << capabilities_.negotiated_maximum_incoming_streams;
  sb << " max_out=" << capabilities_.negotiated_maximum_outgoing_streams;

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
  state.capabilities.zero_checksum = capabilities_.zero_checksum;
  state.capabilities.negotiated_maximum_incoming_streams =
      capabilities_.negotiated_maximum_incoming_streams;
  state.capabilities.negotiated_maximum_outgoing_streams =
      capabilities_.negotiated_maximum_outgoing_streams;

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

void TransmissionControlBlock::RestoreFromState(
    const DcSctpSocketHandoverState& state) {
  data_tracker_.RestoreFromState(state);
  retransmission_queue_.RestoreFromState(state);
  reassembly_queue_.RestoreFromState(state);
}
}  // namespace dcsctp
