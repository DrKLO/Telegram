/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/packet_sequencer.h"

#include "rtc_base/checks.h"
#include "rtc_base/random.h"

namespace webrtc {

namespace {
// RED header is first byte of payload, if present.
constexpr size_t kRedForFecHeaderLength = 1;

// Timestamps use a 90kHz clock.
constexpr uint32_t kTimestampTicksPerMs = 90;
}  // namespace

PacketSequencer::PacketSequencer(uint32_t media_ssrc,
                                 absl::optional<uint32_t> rtx_ssrc,
                                 bool require_marker_before_media_padding,
                                 Clock* clock)
    : media_ssrc_(media_ssrc),
      rtx_ssrc_(rtx_ssrc),
      require_marker_before_media_padding_(require_marker_before_media_padding),
      clock_(clock),
      media_sequence_number_(0),
      rtx_sequence_number_(0),
      last_payload_type_(-1),
      last_rtp_timestamp_(0),
      last_capture_time_ms_(0),
      last_timestamp_time_ms_(0),
      last_packet_marker_bit_(false) {
  Random random(clock_->TimeInMicroseconds());
  // Random start, 16 bits. Upper half of range is avoided in order to prevent
  // wraparound issues during startup. Sequence number 0 is avoided for
  // historical reasons, presumably to avoid debugability or test usage
  // conflicts.
  constexpr uint16_t kMaxInitRtpSeqNumber = 0x7fff;  // 2^15 - 1.
  media_sequence_number_ = random.Rand(1, kMaxInitRtpSeqNumber);
  rtx_sequence_number_ = random.Rand(1, kMaxInitRtpSeqNumber);
}

void PacketSequencer::Sequence(RtpPacketToSend& packet) {
  if (packet.Ssrc() == media_ssrc_) {
    if (packet.packet_type() == RtpPacketMediaType::kRetransmission) {
      // Retransmission of an already sequenced packet, ignore.
      return;
    } else if (packet.packet_type() == RtpPacketMediaType::kPadding) {
      PopulatePaddingFields(packet);
    }
    packet.SetSequenceNumber(media_sequence_number_++);
    if (packet.packet_type() != RtpPacketMediaType::kPadding) {
      UpdateLastPacketState(packet);
    }
  } else if (packet.Ssrc() == rtx_ssrc_) {
    if (packet.packet_type() == RtpPacketMediaType::kPadding) {
      PopulatePaddingFields(packet);
    }
    packet.SetSequenceNumber(rtx_sequence_number_++);
  } else {
    RTC_DCHECK_NOTREACHED() << "Unexpected ssrc " << packet.Ssrc();
  }
}

void PacketSequencer::SetRtpState(const RtpState& state) {
  media_sequence_number_ = state.sequence_number;
  last_rtp_timestamp_ = state.timestamp;
  last_capture_time_ms_ = state.capture_time_ms;
  last_timestamp_time_ms_ = state.last_timestamp_time_ms;
}

void PacketSequencer::PopulateRtpState(RtpState& state) const {
  state.sequence_number = media_sequence_number_;
  state.timestamp = last_rtp_timestamp_;
  state.capture_time_ms = last_capture_time_ms_;
  state.last_timestamp_time_ms = last_timestamp_time_ms_;
}

void PacketSequencer::UpdateLastPacketState(const RtpPacketToSend& packet) {
  // Remember marker bit to determine if padding can be inserted with
  // sequence number following `packet`.
  last_packet_marker_bit_ = packet.Marker();
  // Remember media payload type to use in the padding packet if rtx is
  // disabled.
  if (packet.is_red()) {
    RTC_DCHECK_GE(packet.payload_size(), kRedForFecHeaderLength);
    last_payload_type_ = packet.PayloadBuffer()[0];
  } else {
    last_payload_type_ = packet.PayloadType();
  }
  // Save timestamps to generate timestamp field and extensions for the padding.
  last_rtp_timestamp_ = packet.Timestamp();
  last_timestamp_time_ms_ = clock_->TimeInMilliseconds();
  last_capture_time_ms_ = packet.capture_time_ms();
}

void PacketSequencer::PopulatePaddingFields(RtpPacketToSend& packet) {
  if (packet.Ssrc() == media_ssrc_) {
    RTC_DCHECK(CanSendPaddingOnMediaSsrc());

    packet.SetTimestamp(last_rtp_timestamp_);
    packet.set_capture_time_ms(last_capture_time_ms_);
    packet.SetPayloadType(last_payload_type_);
    return;
  }

  RTC_DCHECK(packet.Ssrc() == rtx_ssrc_);
  if (packet.payload_size() > 0) {
    // This is payload padding packet, don't update timestamp fields.
    return;
  }

  packet.SetTimestamp(last_rtp_timestamp_);
  packet.set_capture_time_ms(last_capture_time_ms_);

  // Only change the timestamp of padding packets sent over RTX.
  // Padding only packets over RTP has to be sent as part of a media
  // frame (and therefore the same timestamp).
  int64_t now_ms = clock_->TimeInMilliseconds();
  if (last_timestamp_time_ms_ > 0) {
    packet.SetTimestamp(packet.Timestamp() +
                        (now_ms - last_timestamp_time_ms_) *
                            kTimestampTicksPerMs);
    if (packet.capture_time_ms() > 0) {
      packet.set_capture_time_ms(packet.capture_time_ms() +
                                 (now_ms - last_timestamp_time_ms_));
    }
  }
}

bool PacketSequencer::CanSendPaddingOnMediaSsrc() const {
  if (last_payload_type_ == -1) {
    return false;
  }

  // Without RTX we can't send padding in the middle of frames.
  // For audio marker bits doesn't mark the end of a frame and frames
  // are usually a single packet, so for now we don't apply this rule
  // for audio.
  if (require_marker_before_media_padding_ && !last_packet_marker_bit_) {
    return false;
  }

  return true;
}

}  // namespace webrtc
