/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_RTP_PACKET_INFO_H_
#define API_RTP_PACKET_INFO_H_

#include <cstdint>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/rtp_headers.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

//
// Structure to hold information about a received |RtpPacket|. It is primarily
// used to carry per-packet information from when a packet is received until
// the information is passed to |SourceTracker|.
//
class RTC_EXPORT RtpPacketInfo {
 public:
  RtpPacketInfo();

  RtpPacketInfo(uint32_t ssrc,
                std::vector<uint32_t> csrcs,
                uint32_t rtp_timestamp,
                absl::optional<uint8_t> audio_level,
                absl::optional<AbsoluteCaptureTime> absolute_capture_time,
                int64_t receive_time_ms);

  RtpPacketInfo(const RTPHeader& rtp_header, int64_t receive_time_ms);

  RtpPacketInfo(const RtpPacketInfo& other) = default;
  RtpPacketInfo(RtpPacketInfo&& other) = default;
  RtpPacketInfo& operator=(const RtpPacketInfo& other) = default;
  RtpPacketInfo& operator=(RtpPacketInfo&& other) = default;

  uint32_t ssrc() const { return ssrc_; }
  void set_ssrc(uint32_t value) { ssrc_ = value; }

  const std::vector<uint32_t>& csrcs() const { return csrcs_; }
  void set_csrcs(std::vector<uint32_t> value) { csrcs_ = std::move(value); }

  uint32_t rtp_timestamp() const { return rtp_timestamp_; }
  void set_rtp_timestamp(uint32_t value) { rtp_timestamp_ = value; }

  absl::optional<uint8_t> audio_level() const { return audio_level_; }
  void set_audio_level(absl::optional<uint8_t> value) { audio_level_ = value; }

  const absl::optional<AbsoluteCaptureTime>& absolute_capture_time() const {
    return absolute_capture_time_;
  }
  void set_absolute_capture_time(
      const absl::optional<AbsoluteCaptureTime>& value) {
    absolute_capture_time_ = value;
  }

  int64_t receive_time_ms() const { return receive_time_ms_; }
  void set_receive_time_ms(int64_t value) { receive_time_ms_ = value; }

 private:
  // Fields from the RTP header:
  // https://tools.ietf.org/html/rfc3550#section-5.1
  uint32_t ssrc_;
  std::vector<uint32_t> csrcs_;
  uint32_t rtp_timestamp_;

  // Fields from the Audio Level header extension:
  // https://tools.ietf.org/html/rfc6464#section-3
  absl::optional<uint8_t> audio_level_;

  // Fields from the Absolute Capture Time header extension:
  // http://www.webrtc.org/experiments/rtp-hdrext/abs-capture-time
  absl::optional<AbsoluteCaptureTime> absolute_capture_time_;

  // Local |webrtc::Clock|-based timestamp of when the packet was received.
  int64_t receive_time_ms_;
};

bool operator==(const RtpPacketInfo& lhs, const RtpPacketInfo& rhs);

inline bool operator!=(const RtpPacketInfo& lhs, const RtpPacketInfo& rhs) {
  return !(lhs == rhs);
}

}  // namespace webrtc

#endif  // API_RTP_PACKET_INFO_H_
