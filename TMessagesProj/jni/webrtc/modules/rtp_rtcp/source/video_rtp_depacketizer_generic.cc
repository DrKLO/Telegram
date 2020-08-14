/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/video_rtp_depacketizer_generic.h"

#include <stddef.h>
#include <stdint.h>

#include <utility>

#include "absl/types/optional.h"
#include "modules/rtp_rtcp/source/rtp_video_header.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {
constexpr uint8_t kKeyFrameBit = 0b0000'0001;
constexpr uint8_t kFirstPacketBit = 0b0000'0010;
// If this bit is set, there will be an extended header contained in this
// packet. This was added later so old clients will not send this.
constexpr uint8_t kExtendedHeaderBit = 0b0000'0100;

constexpr size_t kGenericHeaderLength = 1;
constexpr size_t kExtendedHeaderLength = 2;
}  // namespace

absl::optional<VideoRtpDepacketizer::ParsedRtpPayload>
VideoRtpDepacketizerGeneric::Parse(rtc::CopyOnWriteBuffer rtp_payload) {
  if (rtp_payload.size() == 0) {
    RTC_LOG(LS_WARNING) << "Empty payload.";
    return absl::nullopt;
  }
  absl::optional<ParsedRtpPayload> parsed(absl::in_place);
  const uint8_t* payload_data = rtp_payload.cdata();

  uint8_t generic_header = payload_data[0];
  size_t offset = kGenericHeaderLength;

  parsed->video_header.frame_type = (generic_header & kKeyFrameBit)
                                        ? VideoFrameType::kVideoFrameKey
                                        : VideoFrameType::kVideoFrameDelta;
  parsed->video_header.is_first_packet_in_frame =
      (generic_header & kFirstPacketBit) != 0;
  parsed->video_header.codec = kVideoCodecGeneric;
  parsed->video_header.width = 0;
  parsed->video_header.height = 0;

  if (generic_header & kExtendedHeaderBit) {
    if (rtp_payload.size() < offset + kExtendedHeaderLength) {
      RTC_LOG(LS_WARNING) << "Too short payload for generic header.";
      return absl::nullopt;
    }
    parsed->video_header.video_type_header
        .emplace<RTPVideoHeaderLegacyGeneric>()
        .picture_id = ((payload_data[1] & 0x7F) << 8) | payload_data[2];
    offset += kExtendedHeaderLength;
  }

  parsed->video_payload =
      rtp_payload.Slice(offset, rtp_payload.size() - offset);
  return parsed;
}
}  // namespace webrtc
