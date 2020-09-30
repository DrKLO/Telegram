/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_format_video_generic.h"

#include <assert.h>
#include <string.h>

#include "absl/types/optional.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

static const size_t kGenericHeaderLength = 1;
static const size_t kExtendedHeaderLength = 2;

RtpPacketizerGeneric::RtpPacketizerGeneric(
    rtc::ArrayView<const uint8_t> payload,
    PayloadSizeLimits limits,
    const RTPVideoHeader& rtp_video_header)
    : remaining_payload_(payload) {
  BuildHeader(rtp_video_header);

  limits.max_payload_len -= header_size_;
  payload_sizes_ = SplitAboutEqually(payload.size(), limits);
  current_packet_ = payload_sizes_.begin();
}

RtpPacketizerGeneric::RtpPacketizerGeneric(
    rtc::ArrayView<const uint8_t> payload,
    PayloadSizeLimits limits)
    : header_size_(0), remaining_payload_(payload) {
  payload_sizes_ = SplitAboutEqually(payload.size(), limits);
  current_packet_ = payload_sizes_.begin();
}

RtpPacketizerGeneric::~RtpPacketizerGeneric() = default;

size_t RtpPacketizerGeneric::NumPackets() const {
  return payload_sizes_.end() - current_packet_;
}

bool RtpPacketizerGeneric::NextPacket(RtpPacketToSend* packet) {
  RTC_DCHECK(packet);
  if (current_packet_ == payload_sizes_.end())
    return false;

  size_t next_packet_payload_len = *current_packet_;

  uint8_t* out_ptr =
      packet->AllocatePayload(header_size_ + next_packet_payload_len);
  RTC_CHECK(out_ptr);

  if (header_size_ > 0) {
    memcpy(out_ptr, header_, header_size_);
    // Remove first-packet bit, following packets are intermediate.
    header_[0] &= ~RtpFormatVideoGeneric::kFirstPacketBit;
  }

  memcpy(out_ptr + header_size_, remaining_payload_.data(),
         next_packet_payload_len);

  remaining_payload_ = remaining_payload_.subview(next_packet_payload_len);

  ++current_packet_;

  // Packets left to produce and data left to split should end at the same time.
  RTC_DCHECK_EQ(current_packet_ == payload_sizes_.end(),
                remaining_payload_.empty());

  packet->SetMarker(remaining_payload_.empty());
  return true;
}

void RtpPacketizerGeneric::BuildHeader(const RTPVideoHeader& rtp_video_header) {
  header_size_ = kGenericHeaderLength;
  header_[0] = RtpFormatVideoGeneric::kFirstPacketBit;
  if (rtp_video_header.frame_type == VideoFrameType::kVideoFrameKey) {
    header_[0] |= RtpFormatVideoGeneric::kKeyFrameBit;
  }
  if (const auto* generic_header = absl::get_if<RTPVideoHeaderLegacyGeneric>(
          &rtp_video_header.video_type_header)) {
    // Store bottom 15 bits of the picture id. Only 15 bits are used for
    // compatibility with other packetizer implemenetations.
    uint16_t picture_id = generic_header->picture_id;
    header_[0] |= RtpFormatVideoGeneric::kExtendedHeaderBit;
    header_[1] = (picture_id >> 8) & 0x7F;
    header_[2] = picture_id & 0xFF;
    header_size_ += kExtendedHeaderLength;
  }
}
}  // namespace webrtc
