/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_format.h"

#include <memory>

#include "absl/types/variant.h"
#include "modules/rtp_rtcp/source/rtp_format_h264.h"
#ifndef DISABLE_H265
#include "modules/rtp_rtcp/source/rtp_format_h265.h"
#endif
#include "modules/rtp_rtcp/source/rtp_format_video_generic.h"
#include "modules/rtp_rtcp/source/rtp_format_vp8.h"
#include "modules/rtp_rtcp/source/rtp_format_vp9.h"
#include "modules/rtp_rtcp/source/rtp_packetizer_av1.h"
#include "modules/video_coding/codecs/h264/include/h264_globals.h"
#ifndef DISABLE_H265
#include "modules/video_coding/codecs/h265/include/h265_globals.h"
#endif
#include "modules/video_coding/codecs/vp8/include/vp8_globals.h"
#include "modules/video_coding/codecs/vp9/include/vp9_globals.h"
#include "rtc_base/checks.h"

namespace webrtc {

std::unique_ptr<RtpPacketizer> RtpPacketizer::Create(
    absl::optional<VideoCodecType> type,
    rtc::ArrayView<const uint8_t> payload,
    PayloadSizeLimits limits,
    // Codec-specific details.
    const RTPVideoHeader& rtp_video_header) {
  if (!type) {
    // Use raw packetizer.
    return std::make_unique<RtpPacketizerGeneric>(payload, limits);
  }

  switch (*type) {
    case kVideoCodecH264: {
      const auto& h264 =
          absl::get<RTPVideoHeaderH264>(rtp_video_header.video_type_header);
      return std::make_unique<RtpPacketizerH264>(payload, limits,
                                                 h264.packetization_mode);
    }
#ifndef DISABLE_H265
    case kVideoCodecH265: {
      const auto& h265 =
          absl::get<RTPVideoHeaderH265>(rtp_video_header.video_type_header);
      return std::make_unique<RtpPacketizerH265>(
          payload, limits, h265.packetization_mode);
    }
#endif
    case kVideoCodecVP8: {
      const auto& vp8 =
          absl::get<RTPVideoHeaderVP8>(rtp_video_header.video_type_header);
      return std::make_unique<RtpPacketizerVp8>(payload, limits, vp8);
    }
    case kVideoCodecVP9: {
      const auto& vp9 =
          absl::get<RTPVideoHeaderVP9>(rtp_video_header.video_type_header);
      return std::make_unique<RtpPacketizerVp9>(payload, limits, vp9);
    }
    case kVideoCodecAV1:
      return std::make_unique<RtpPacketizerAv1>(
          payload, limits, rtp_video_header.frame_type,
          rtp_video_header.is_last_frame_in_picture);
    default: {
      return std::make_unique<RtpPacketizerGeneric>(payload, limits,
                                                    rtp_video_header);
    }
  }
}

std::vector<int> RtpPacketizer::SplitAboutEqually(
    int payload_len,
    const PayloadSizeLimits& limits) {
  RTC_DCHECK_GT(payload_len, 0);
  // First or last packet larger than normal are unsupported.
  RTC_DCHECK_GE(limits.first_packet_reduction_len, 0);
  RTC_DCHECK_GE(limits.last_packet_reduction_len, 0);

  std::vector<int> result;
  if (limits.max_payload_len >=
      limits.single_packet_reduction_len + payload_len) {
    result.push_back(payload_len);
    return result;
  }
  if (limits.max_payload_len - limits.first_packet_reduction_len < 1 ||
      limits.max_payload_len - limits.last_packet_reduction_len < 1) {
    // Capacity is not enough to put a single byte into one of the packets.
    return result;
  }
  // First and last packet of the frame can be smaller. Pretend that it's
  // the same size, but we must write more payload to it.
  // Assume frame fits in single packet if packet has extra space for sum
  // of first and last packets reductions.
  int total_bytes = payload_len + limits.first_packet_reduction_len +
                    limits.last_packet_reduction_len;
  // Integer divisions with rounding up.
  int num_packets_left =
      (total_bytes + limits.max_payload_len - 1) / limits.max_payload_len;
  if (num_packets_left == 1) {
    // Single packet is a special case handled above.
    num_packets_left = 2;
  }

  if (payload_len < num_packets_left) {
    // Edge case where limits force to have more packets than there are payload
    // bytes. This may happen when there is single byte of payload that can't be
    // put into single packet if
    // first_packet_reduction + last_packet_reduction >= max_payload_len.
    return result;
  }

  int bytes_per_packet = total_bytes / num_packets_left;
  int num_larger_packets = total_bytes % num_packets_left;
  int remaining_data = payload_len;

  result.reserve(num_packets_left);
  bool first_packet = true;
  while (remaining_data > 0) {
    // Last num_larger_packets are 1 byte wider than the rest. Increase
    // per-packet payload size when needed.
    if (num_packets_left == num_larger_packets)
      ++bytes_per_packet;
    int current_packet_bytes = bytes_per_packet;
    if (first_packet) {
      if (current_packet_bytes > limits.first_packet_reduction_len + 1)
        current_packet_bytes -= limits.first_packet_reduction_len;
      else
        current_packet_bytes = 1;
    }
    if (current_packet_bytes > remaining_data) {
      current_packet_bytes = remaining_data;
    }
    // This is not the last packet in the whole payload, but there's no data
    // left for the last packet. Leave at least one byte for the last packet.
    if (num_packets_left == 2 && current_packet_bytes == remaining_data) {
      --current_packet_bytes;
    }
    result.push_back(current_packet_bytes);

    remaining_data -= current_packet_bytes;
    --num_packets_left;
    first_packet = false;
  }

  return result;
}

}  // namespace webrtc
