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

namespace webrtc {

namespace {
// RED header is first byte of payload, if present.
constexpr size_t kRedForFecHeaderLength = 1;

// Timestamps use a 90kHz clock.
constexpr uint32_t kTimestampTicksPerMs = 90;
}  // namespace

PacketSequencer::PacketSequencer(uint32_t media_ssrc,
                                 uint32_t rtx_ssrc,
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
      last_packet_marker_bit_(false) {}

bool PacketSequencer::Sequence(RtpPacketToSend& packet) {
  if (packet.packet_type() == RtpPacketMediaType::kPadding &&
      !PopulatePaddingFields(packet)) {
    // This padding packet can't be sent with current state, return without
    // updating the sequence number.
    return false;
  }

  if (packet.Ssrc() == media_ssrc_) {
    packet.SetSequenceNumber(media_sequence_number_++);
    if (packet.packet_type() != RtpPacketMediaType::kPadding) {
      UpdateLastPacketState(packet);
    }
    return true;
  }

  RTC_DCHECK_EQ(packet.Ssrc(), rtx_ssrc_);
  packet.SetSequenceNumber(rtx_sequence_number_++);
  return true;
}

void PacketSequencer::SetRtpState(const RtpState& state) {
  media_sequence_number_ = state.sequence_number;
  last_rtp_timestamp_ = state.timestamp;
  last_capture_time_ms_ = state.capture_time_ms;
  last_timestamp_time_ms_ = state.last_timestamp_time_ms;
}

void PacketSequencer::PupulateRtpState(RtpState& state) const {
  state.sequence_number = media_sequence_number_;
  state.timestamp = last_rtp_timestamp_;
  state.capture_time_ms = last_capture_time_ms_;
  state.last_timestamp_time_ms = last_timestamp_time_ms_;
}

void PacketSequencer::UpdateLastPacketState(const RtpPacketToSend& packet) {
  // Remember marker bit to determine if padding can be inserted with
  // sequence number following |packet|.
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

bool PacketSequencer::PopulatePaddingFields(RtpPacketToSend& packet) {
  if (packet.Ssrc() == media_ssrc_) {
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

    packet.SetTimestamp(last_rtp_timestamp_);
    packet.set_capture_time_ms(last_capture_time_ms_);
    packet.SetPayloadType(last_payload_type_);
    return true;
  }

  RTC_DCHECK_EQ(packet.Ssrc(), rtx_ssrc_);
  if (packet.payload_size() > 0) {
    // This is payload padding packet, don't update timestamp fields.
    return true;
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

  return true;
}

}  // namespace webrtc
