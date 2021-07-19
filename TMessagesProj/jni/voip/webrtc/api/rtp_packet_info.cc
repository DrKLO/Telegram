/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/rtp_packet_info.h"

#include <algorithm>
#include <utility>

namespace webrtc {

RtpPacketInfo::RtpPacketInfo()
    : ssrc_(0), rtp_timestamp_(0), receive_time_(Timestamp::MinusInfinity()) {}

RtpPacketInfo::RtpPacketInfo(
    uint32_t ssrc,
    std::vector<uint32_t> csrcs,
    uint32_t rtp_timestamp,
    absl::optional<uint8_t> audio_level,
    absl::optional<AbsoluteCaptureTime> absolute_capture_time,
    Timestamp receive_time)
    : ssrc_(ssrc),
      csrcs_(std::move(csrcs)),
      rtp_timestamp_(rtp_timestamp),
      audio_level_(audio_level),
      absolute_capture_time_(absolute_capture_time),
      receive_time_(receive_time) {}

RtpPacketInfo::RtpPacketInfo(const RTPHeader& rtp_header,
                             Timestamp receive_time)
    : ssrc_(rtp_header.ssrc),
      rtp_timestamp_(rtp_header.timestamp),
      receive_time_(receive_time) {
  const auto& extension = rtp_header.extension;
  const auto csrcs_count = std::min<size_t>(rtp_header.numCSRCs, kRtpCsrcSize);

  csrcs_.assign(&rtp_header.arrOfCSRCs[0], &rtp_header.arrOfCSRCs[csrcs_count]);

  if (extension.hasAudioLevel) {
    audio_level_ = extension.audioLevel;
  }

  absolute_capture_time_ = extension.absolute_capture_time;
}

RtpPacketInfo::RtpPacketInfo(
    uint32_t ssrc,
    std::vector<uint32_t> csrcs,
    uint32_t rtp_timestamp,
    absl::optional<uint8_t> audio_level,
    absl::optional<AbsoluteCaptureTime> absolute_capture_time,
    int64_t receive_time_ms)
    : RtpPacketInfo(ssrc,
                    csrcs,
                    rtp_timestamp,
                    audio_level,
                    absolute_capture_time,
                    Timestamp::Millis(receive_time_ms)) {}
RtpPacketInfo::RtpPacketInfo(const RTPHeader& rtp_header,
                             int64_t receive_time_ms)
    : RtpPacketInfo(rtp_header, Timestamp::Millis(receive_time_ms)) {}

bool operator==(const RtpPacketInfo& lhs, const RtpPacketInfo& rhs) {
  return (lhs.ssrc() == rhs.ssrc()) && (lhs.csrcs() == rhs.csrcs()) &&
         (lhs.rtp_timestamp() == rhs.rtp_timestamp()) &&
         (lhs.audio_level() == rhs.audio_level()) &&
         (lhs.absolute_capture_time() == rhs.absolute_capture_time()) &&
         (lhs.receive_time() == rhs.receive_time() &&
          (lhs.local_capture_clock_offset() ==
           rhs.local_capture_clock_offset()));
}

}  // namespace webrtc
