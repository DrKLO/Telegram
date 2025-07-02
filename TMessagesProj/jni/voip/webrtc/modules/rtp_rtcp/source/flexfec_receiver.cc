/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/include/flexfec_receiver.h"

#include <string.h>

#include "api/array_view.h"
#include "api/scoped_refptr.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace {

// Minimum header size (in bytes) of a well-formed non-singular FlexFEC packet.
constexpr size_t kMinFlexfecHeaderSize = 20;

// How often to log the recovered packets to the text log.
constexpr TimeDelta kPacketLogInterval = TimeDelta::Seconds(10);

}  // namespace

FlexfecReceiver::FlexfecReceiver(
    uint32_t ssrc,
    uint32_t protected_media_ssrc,
    RecoveredPacketReceiver* recovered_packet_receiver)
    : FlexfecReceiver(Clock::GetRealTimeClock(),
                      ssrc,
                      protected_media_ssrc,
                      recovered_packet_receiver) {}

FlexfecReceiver::FlexfecReceiver(
    Clock* clock,
    uint32_t ssrc,
    uint32_t protected_media_ssrc,
    RecoveredPacketReceiver* recovered_packet_receiver)
    : ssrc_(ssrc),
      protected_media_ssrc_(protected_media_ssrc),
      erasure_code_(
          ForwardErrorCorrection::CreateFlexfec(ssrc, protected_media_ssrc)),
      recovered_packet_receiver_(recovered_packet_receiver),
      clock_(clock) {
  // It's OK to create this object on a different thread/task queue than
  // the one used during main operation.
  sequence_checker_.Detach();
}

FlexfecReceiver::~FlexfecReceiver() = default;

void FlexfecReceiver::OnRtpPacket(const RtpPacketReceived& packet) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  // If this packet was recovered, it might be originating from
  // ProcessReceivedPacket in this object. To avoid lifetime issues with
  // `recovered_packets_`, we therefore break the cycle here.
  // This might reduce decoding efficiency a bit, since we can't disambiguate
  // recovered packets by RTX from recovered packets by FlexFEC.
  if (packet.recovered())
    return;

  std::unique_ptr<ForwardErrorCorrection::ReceivedPacket> received_packet =
      AddReceivedPacket(packet);
  if (!received_packet)
    return;

  ProcessReceivedPacket(*received_packet);
}

FecPacketCounter FlexfecReceiver::GetPacketCounter() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return packet_counter_;
}

// TODO(eladalon): Consider using packet.recovered() to avoid processing
// recovered packets here.
std::unique_ptr<ForwardErrorCorrection::ReceivedPacket>
FlexfecReceiver::AddReceivedPacket(const RtpPacketReceived& packet) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  // RTP packets with a full base header (12 bytes), but without payload,
  // could conceivably be useful in the decoding. Therefore we check
  // with a non-strict inequality here.
  RTC_DCHECK_GE(packet.size(), kRtpHeaderSize);

  // Demultiplex based on SSRC, and insert into erasure code decoder.
  std::unique_ptr<ForwardErrorCorrection::ReceivedPacket> received_packet(
      new ForwardErrorCorrection::ReceivedPacket());
  received_packet->seq_num = packet.SequenceNumber();
  received_packet->ssrc = packet.Ssrc();
  received_packet->extensions = packet.extension_manager();
  if (received_packet->ssrc == ssrc_) {
    // This is a FlexFEC packet.
    if (packet.payload_size() < kMinFlexfecHeaderSize) {
      RTC_LOG(LS_WARNING) << "Truncated FlexFEC packet, discarding.";
      return nullptr;
    }
    received_packet->is_fec = true;
    ++packet_counter_.num_fec_packets;

    // Insert packet payload into erasure code.
    received_packet->pkt = rtc::scoped_refptr<ForwardErrorCorrection::Packet>(
        new ForwardErrorCorrection::Packet());
    received_packet->pkt->data =
        packet.Buffer().Slice(packet.headers_size(), packet.payload_size());
  } else {
    // This is a media packet, or a FlexFEC packet belonging to some
    // other FlexFEC stream.
    if (received_packet->ssrc != protected_media_ssrc_) {
      return nullptr;
    }
    received_packet->is_fec = false;

    // Insert entire packet into erasure code.
    // Create a copy and fill with zeros all mutable extensions.
    received_packet->pkt = rtc::scoped_refptr<ForwardErrorCorrection::Packet>(
        new ForwardErrorCorrection::Packet());
    RtpPacketReceived packet_copy(packet);
    packet_copy.ZeroMutableExtensions();
    received_packet->pkt->data = packet_copy.Buffer();
  }

  ++packet_counter_.num_packets;

  return received_packet;
}

// Note that the implementation of this member function and the implementation
// in UlpfecReceiver::ProcessReceivedFec() are slightly different.
// This implementation only returns _recovered_ media packets through the
// callback, whereas the implementation in UlpfecReceiver returns _all inserted_
// media packets through the callback. The latter behaviour makes sense
// for ULPFEC, since the ULPFEC receiver is owned by the RtpVideoStreamReceiver.
// Here, however, the received media pipeline is more decoupled from the
// FlexFEC decoder, and we therefore do not interfere with the reception
// of non-recovered media packets.
void FlexfecReceiver::ProcessReceivedPacket(
    const ForwardErrorCorrection::ReceivedPacket& received_packet) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  // Decode.
  ForwardErrorCorrection::DecodeFecResult decode_result =
      erasure_code_->DecodeFec(received_packet, &recovered_packets_);

  if (decode_result.num_recovered_packets == 0) {
    return;
  }

  // Return recovered packets through callback.
  for (const auto& recovered_packet : recovered_packets_) {
    RTC_CHECK(recovered_packet);
    if (recovered_packet->returned) {
      continue;
    }
    ++packet_counter_.num_recovered_packets;
    // Set this flag first, since OnRecoveredPacket may end up here
    // again, with the same packet.
    recovered_packet->returned = true;
    RTC_CHECK_GE(recovered_packet->pkt->data.size(), kRtpHeaderSize);

    RtpPacketReceived parsed_packet(&received_packet.extensions);
    if (!parsed_packet.Parse(recovered_packet->pkt->data)) {
      continue;
    }
    parsed_packet.set_recovered(true);

    // TODO(brandtr): Update here when we support protecting audio packets too.
    parsed_packet.set_payload_type_frequency(kVideoPayloadTypeFrequency);
    recovered_packet_receiver_->OnRecoveredPacket(parsed_packet);

    // Periodically log the incoming packets at LS_INFO.
    Timestamp now = clock_->CurrentTime();
    bool should_log_periodically =
        now - last_recovered_packet_ > kPacketLogInterval;
    if (RTC_LOG_CHECK_LEVEL(LS_VERBOSE) || should_log_periodically) {
      rtc::LoggingSeverity level =
          should_log_periodically ? rtc::LS_INFO : rtc::LS_VERBOSE;
      RTC_LOG_V(level) << "Recovered media packet with SSRC: "
                       << parsed_packet.Ssrc() << " seq "
                       << parsed_packet.SequenceNumber() << " recovered length "
                       << recovered_packet->pkt->data.size()
                       << " received length "
                       << received_packet.pkt->data.size()
                       << " from FlexFEC stream with SSRC: " << ssrc_;
      if (should_log_periodically) {
        last_recovered_packet_ = now;
      }
    }
  }
}

}  // namespace webrtc
