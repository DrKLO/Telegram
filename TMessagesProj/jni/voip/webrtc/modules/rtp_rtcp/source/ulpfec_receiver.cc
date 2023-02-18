/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/ulpfec_receiver.h"

#include <memory>
#include <utility>

#include "api/scoped_refptr.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

UlpfecReceiver::UlpfecReceiver(uint32_t ssrc,
                               int ulpfec_payload_type,
                               RecoveredPacketReceiver* callback,
                               rtc::ArrayView<const RtpExtension> extensions,
                               Clock* clock)
    : ssrc_(ssrc),
      ulpfec_payload_type_(ulpfec_payload_type),
      clock_(clock),
      extensions_(extensions),
      recovered_packet_callback_(callback),
      fec_(ForwardErrorCorrection::CreateUlpfec(ssrc_)) {
  // TODO(tommi, brandtr): Once considerations for red have been split
  // away from this implementation, we can require the ulpfec payload type
  // to always be valid and use uint8 for storage (as is done elsewhere).
  RTC_DCHECK_GE(ulpfec_payload_type_, -1);
}

UlpfecReceiver::~UlpfecReceiver() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  if (packet_counter_.first_packet_time != Timestamp::MinusInfinity()) {
    const Timestamp now = clock_->CurrentTime();
    TimeDelta elapsed = (now - packet_counter_.first_packet_time);
    if (elapsed.seconds() >= metrics::kMinRunTimeInSeconds) {
      if (packet_counter_.num_packets > 0) {
        RTC_HISTOGRAM_PERCENTAGE(
            "WebRTC.Video.ReceivedFecPacketsInPercent",
            static_cast<int>(packet_counter_.num_fec_packets * 100 /
                             packet_counter_.num_packets));
      }
      if (packet_counter_.num_fec_packets > 0) {
        RTC_HISTOGRAM_PERCENTAGE(
            "WebRTC.Video.RecoveredMediaPacketsInPercentOfFec",
            static_cast<int>(packet_counter_.num_recovered_packets * 100 /
                             packet_counter_.num_fec_packets));
      }
      if (ulpfec_payload_type_ != -1) {
        RTC_HISTOGRAM_COUNTS_10000(
            "WebRTC.Video.FecBitrateReceivedInKbps",
            static_cast<int>(packet_counter_.num_bytes * 8 / elapsed.seconds() /
                             1000));
      }
    }
  }

  received_packets_.clear();
  fec_->ResetState(&recovered_packets_);
}

FecPacketCounter UlpfecReceiver::GetPacketCounter() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return packet_counter_;
}

void UlpfecReceiver::SetRtpExtensions(
    rtc::ArrayView<const RtpExtension> extensions) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  extensions_.Reset(extensions);
}

//     0                   1                    2                   3
//     0 1 2 3 4 5 6 7 8 9 0 1 2 3  4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//    |F|   block PT  |  timestamp offset         |   block length    |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//
//
// RFC 2198          RTP Payload for Redundant Audio Data    September 1997
//
//    The bits in the header are specified as follows:
//
//    F: 1 bit First bit in header indicates whether another header block
//        follows.  If 1 further header blocks follow, if 0 this is the
//        last header block.
//        If 0 there is only 1 byte RED header
//
//    block PT: 7 bits RTP payload type for this block.
//
//    timestamp offset:  14 bits Unsigned offset of timestamp of this block
//        relative to timestamp given in RTP header.  The use of an unsigned
//        offset implies that redundant data must be sent after the primary
//        data, and is hence a time to be subtracted from the current
//        timestamp to determine the timestamp of the data for which this
//        block is the redundancy.
//
//    block length:  10 bits Length in bytes of the corresponding data
//        block excluding header.

bool UlpfecReceiver::AddReceivedRedPacket(const RtpPacketReceived& rtp_packet) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  // TODO(bugs.webrtc.org/11993): We get here via Call::DeliverRtp, so should be
  // moved to the network thread.

  if (rtp_packet.Ssrc() != ssrc_) {
    RTC_LOG(LS_WARNING)
        << "Received RED packet with different SSRC than expected; dropping.";
    return false;
  }
  if (rtp_packet.size() > IP_PACKET_SIZE) {
    RTC_LOG(LS_WARNING) << "Received RED packet with length exceeds maximum IP "
                           "packet size; dropping.";
    return false;
  }

  static constexpr uint8_t kRedHeaderLength = 1;

  if (rtp_packet.payload_size() == 0) {
    RTC_LOG(LS_WARNING) << "Corrupt/truncated FEC packet.";
    return false;
  }

  // Remove RED header of incoming packet and store as a virtual RTP packet.
  auto received_packet =
      std::make_unique<ForwardErrorCorrection::ReceivedPacket>();
  received_packet->pkt = new ForwardErrorCorrection::Packet();

  // Get payload type from RED header and sequence number from RTP header.
  uint8_t payload_type = rtp_packet.payload()[0] & 0x7f;
  received_packet->is_fec = payload_type == ulpfec_payload_type_;
  received_packet->is_recovered = rtp_packet.recovered();
  received_packet->ssrc = rtp_packet.Ssrc();
  received_packet->seq_num = rtp_packet.SequenceNumber();

  if (rtp_packet.payload()[0] & 0x80) {
    // f bit set in RED header, i.e. there are more than one RED header blocks.
    // WebRTC never generates multiple blocks in a RED packet for FEC.
    RTC_LOG(LS_WARNING) << "More than 1 block in RED packet is not supported.";
    return false;
  }

  ++packet_counter_.num_packets;
  packet_counter_.num_bytes += rtp_packet.size();
  if (packet_counter_.first_packet_time == Timestamp::MinusInfinity()) {
    packet_counter_.first_packet_time = clock_->CurrentTime();
  }

  if (received_packet->is_fec) {
    ++packet_counter_.num_fec_packets;
    // everything behind the RED header
    received_packet->pkt->data =
        rtp_packet.Buffer().Slice(rtp_packet.headers_size() + kRedHeaderLength,
                                  rtp_packet.payload_size() - kRedHeaderLength);
  } else {
    received_packet->pkt->data.EnsureCapacity(rtp_packet.size() -
                                              kRedHeaderLength);
    // Copy RTP header.
    received_packet->pkt->data.SetData(rtp_packet.data(),
                                       rtp_packet.headers_size());
    // Set payload type.
    uint8_t& payload_type_byte = received_packet->pkt->data.MutableData()[1];
    payload_type_byte &= 0x80;          // Reset RED payload type.
    payload_type_byte += payload_type;  // Set media payload type.
    // Copy payload and padding data, after the RED header.
    received_packet->pkt->data.AppendData(
        rtp_packet.data() + rtp_packet.headers_size() + kRedHeaderLength,
        rtp_packet.size() - rtp_packet.headers_size() - kRedHeaderLength);
  }

  if (received_packet->pkt->data.size() > 0) {
    received_packets_.push_back(std::move(received_packet));
  }
  return true;
}

void UlpfecReceiver::ProcessReceivedFec() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  // If we iterate over `received_packets_` and it contains a packet that cause
  // us to recurse back to this function (for example a RED packet encapsulating
  // a RED packet), then we will recurse forever. To avoid this we swap
  // `received_packets_` with an empty vector so that the next recursive call
  // wont iterate over the same packet again. This also solves the problem of
  // not modifying the vector we are currently iterating over (packets are added
  // in AddReceivedRedPacket).
  std::vector<std::unique_ptr<ForwardErrorCorrection::ReceivedPacket>>
      received_packets;
  received_packets.swap(received_packets_);

  for (const auto& received_packet : received_packets) {
    // Send received media packet to VCM.
    if (!received_packet->is_fec) {
      ForwardErrorCorrection::Packet* packet = received_packet->pkt.get();
      recovered_packet_callback_->OnRecoveredPacket(packet->data.data(),
                                                    packet->data.size());
      // Create a packet with the buffer to modify it.
      RtpPacketReceived rtp_packet;
      const uint8_t* const original_data = packet->data.cdata();
      if (!rtp_packet.Parse(packet->data)) {
        RTC_LOG(LS_WARNING) << "Corrupted media packet";
      } else {
        rtp_packet.IdentifyExtensions(extensions_);
        // Reset buffer reference, so zeroing would work on a buffer with a
        // single reference.
        packet->data = rtc::CopyOnWriteBuffer(0);
        rtp_packet.ZeroMutableExtensions();
        packet->data = rtp_packet.Buffer();
        // Ensure that zeroing of extensions was done in place.
        RTC_DCHECK_EQ(packet->data.cdata(), original_data);
      }
    }
    if (!received_packet->is_recovered) {
      // Do not pass recovered packets to FEC. Recovered packet might have
      // different set of the RTP header extensions and thus different byte
      // representation than the original packet, That will corrupt
      // FEC calculation.
      fec_->DecodeFec(*received_packet, &recovered_packets_);
    }
  }

  // Send any recovered media packets to VCM.
  for (const auto& recovered_packet : recovered_packets_) {
    if (recovered_packet->returned) {
      // Already sent to the VCM and the jitter buffer.
      continue;
    }
    ForwardErrorCorrection::Packet* packet = recovered_packet->pkt.get();
    ++packet_counter_.num_recovered_packets;
    // Set this flag first; in case the recovered packet carries a RED
    // header, OnRecoveredPacket will recurse back here.
    recovered_packet->returned = true;
    recovered_packet_callback_->OnRecoveredPacket(packet->data.data(),
                                                  packet->data.size());
  }
}

}  // namespace webrtc
