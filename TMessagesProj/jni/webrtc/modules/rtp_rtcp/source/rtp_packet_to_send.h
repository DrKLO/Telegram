/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef MODULES_RTP_RTCP_SOURCE_RTP_PACKET_TO_SEND_H_
#define MODULES_RTP_RTCP_SOURCE_RTP_PACKET_TO_SEND_H_

#include <stddef.h>
#include <stdint.h>

#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/video/video_timing.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_packet.h"

namespace webrtc {
// Class to hold rtp packet with metadata for sender side.
class RtpPacketToSend : public RtpPacket {
 public:
  // RtpPacketToSend::Type is deprecated. Use RtpPacketMediaType directly.
  using Type = RtpPacketMediaType;

  explicit RtpPacketToSend(const ExtensionManager* extensions);
  RtpPacketToSend(const ExtensionManager* extensions, size_t capacity);
  RtpPacketToSend(const RtpPacketToSend& packet);
  RtpPacketToSend(RtpPacketToSend&& packet);

  RtpPacketToSend& operator=(const RtpPacketToSend& packet);
  RtpPacketToSend& operator=(RtpPacketToSend&& packet);

  ~RtpPacketToSend();

  // Time in local time base as close as it can to frame capture time.
  int64_t capture_time_ms() const { return capture_time_ms_; }

  void set_capture_time_ms(int64_t time) { capture_time_ms_ = time; }

  void set_packet_type(RtpPacketMediaType type) { packet_type_ = type; }
  absl::optional<RtpPacketMediaType> packet_type() const {
    return packet_type_;
  }

  // If this is a retransmission, indicates the sequence number of the original
  // media packet that this packet represents. If RTX is used this will likely
  // be different from SequenceNumber().
  void set_retransmitted_sequence_number(uint16_t sequence_number) {
    retransmitted_sequence_number_ = sequence_number;
  }
  absl::optional<uint16_t> retransmitted_sequence_number() {
    return retransmitted_sequence_number_;
  }

  void set_allow_retransmission(bool allow_retransmission) {
    allow_retransmission_ = allow_retransmission;
  }
  bool allow_retransmission() { return allow_retransmission_; }

  // Additional data bound to the RTP packet for use in application code,
  // outside of WebRTC.
  rtc::ArrayView<const uint8_t> application_data() const {
    return application_data_;
  }

  void set_application_data(rtc::ArrayView<const uint8_t> data) {
    application_data_.assign(data.begin(), data.end());
  }

  void set_packetization_finish_time_ms(int64_t time) {
    SetExtension<VideoTimingExtension>(
        VideoSendTiming::GetDeltaCappedMs(capture_time_ms_, time),
        VideoTimingExtension::kPacketizationFinishDeltaOffset);
  }

  void set_pacer_exit_time_ms(int64_t time) {
    SetExtension<VideoTimingExtension>(
        VideoSendTiming::GetDeltaCappedMs(capture_time_ms_, time),
        VideoTimingExtension::kPacerExitDeltaOffset);
  }

  void set_network_time_ms(int64_t time) {
    SetExtension<VideoTimingExtension>(
        VideoSendTiming::GetDeltaCappedMs(capture_time_ms_, time),
        VideoTimingExtension::kNetworkTimestampDeltaOffset);
  }

  void set_network2_time_ms(int64_t time) {
    SetExtension<VideoTimingExtension>(
        VideoSendTiming::GetDeltaCappedMs(capture_time_ms_, time),
        VideoTimingExtension::kNetwork2TimestampDeltaOffset);
  }

  // Indicates if packet is the first packet of a video frame.
  void set_first_packet_of_frame(bool is_first_packet) {
    is_first_packet_of_frame_ = is_first_packet;
  }
  bool is_first_packet_of_frame() const { return is_first_packet_of_frame_; }

  // Indicates if packet contains payload for a video key-frame.
  void set_is_key_frame(bool is_key_frame) { is_key_frame_ = is_key_frame; }
  bool is_key_frame() const { return is_key_frame_; }

  // Indicates if packets should be protected by FEC (Forward Error Correction).
  void set_fec_protect_packet(bool protect) { fec_protect_packet_ = protect; }
  bool fec_protect_packet() const { return fec_protect_packet_; }

  // Indicates if packet is using RED encapsulation, in accordance with
  // https://tools.ietf.org/html/rfc2198
  void set_is_red(bool is_red) { is_red_ = is_red; }
  bool is_red() const { return is_red_; }

 private:
  int64_t capture_time_ms_ = 0;
  absl::optional<RtpPacketMediaType> packet_type_;
  bool allow_retransmission_ = false;
  absl::optional<uint16_t> retransmitted_sequence_number_;
  std::vector<uint8_t> application_data_;
  bool is_first_packet_of_frame_ = false;
  bool is_key_frame_ = false;
  bool fec_protect_packet_ = false;
  bool is_red_ = false;
};

}  // namespace webrtc
#endif  // MODULES_RTP_RTCP_SOURCE_RTP_PACKET_TO_SEND_H_
