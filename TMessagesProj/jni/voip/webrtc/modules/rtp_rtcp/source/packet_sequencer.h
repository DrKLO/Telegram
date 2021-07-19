/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_RTP_RTCP_SOURCE_PACKET_SEQUENCER_H_
#define MODULES_RTP_RTCP_SOURCE_PACKET_SEQUENCER_H_

#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

// Helper class used to assign RTP sequence numbers and populate some fields for
// padding packets based on the last sequenced packets.
// This class is not thread safe, the caller must provide that.
class PacketSequencer {
 public:
  // If |require_marker_before_media_padding_| is true, padding packets on the
  // media ssrc is not allowed unless the last sequenced media packet had the
  // marker bit set (i.e. don't insert padding packets between the first and
  // last packets of a video frame).
  PacketSequencer(uint32_t media_ssrc,
                  uint32_t rtx_ssrc,
                  bool require_marker_before_media_padding,
                  Clock* clock);

  // Assigns sequence number, and in the case of non-RTX padding also timestamps
  // and payload type.
  // Returns false if sequencing failed, which it can do for instance if the
  // packet to squence is padding on the media ssrc, but the media is mid frame
  // (the last marker bit is false).
  bool Sequence(RtpPacketToSend& packet);

  void set_media_sequence_number(uint16_t sequence_number) {
    media_sequence_number_ = sequence_number;
  }
  void set_rtx_sequence_number(uint16_t sequence_number) {
    rtx_sequence_number_ = sequence_number;
  }

  void SetRtpState(const RtpState& state);
  void PupulateRtpState(RtpState& state) const;

  uint16_t media_sequence_number() const { return media_sequence_number_; }
  uint16_t rtx_sequence_number() const { return rtx_sequence_number_; }

 private:
  void UpdateLastPacketState(const RtpPacketToSend& packet);
  bool PopulatePaddingFields(RtpPacketToSend& packet);

  const uint32_t media_ssrc_;
  const uint32_t rtx_ssrc_;
  const bool require_marker_before_media_padding_;
  Clock* const clock_;

  uint16_t media_sequence_number_;
  uint16_t rtx_sequence_number_;

  int8_t last_payload_type_;
  uint32_t last_rtp_timestamp_;
  int64_t last_capture_time_ms_;
  int64_t last_timestamp_time_ms_;
  bool last_packet_marker_bit_;
};

}  // namespace webrtc

#endif  // MODULES_RTP_RTCP_SOURCE_PACKET_SEQUENCER_H_
