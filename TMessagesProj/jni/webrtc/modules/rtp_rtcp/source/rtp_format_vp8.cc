/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_format_vp8.h"

#include <stdint.h>
#include <string.h>  // memcpy

#include <vector>

#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_vp8.h"
#include "modules/video_coding/codecs/interface/common_constants.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

constexpr int kXBit = 0x80;
constexpr int kNBit = 0x20;
constexpr int kSBit = 0x10;
constexpr int kKeyIdxField = 0x1F;
constexpr int kIBit = 0x80;
constexpr int kLBit = 0x40;
constexpr int kTBit = 0x20;
constexpr int kKBit = 0x10;
constexpr int kYBit = 0x20;

bool ValidateHeader(const RTPVideoHeaderVP8& hdr_info) {
  if (hdr_info.pictureId != kNoPictureId) {
    RTC_DCHECK_GE(hdr_info.pictureId, 0);
    RTC_DCHECK_LE(hdr_info.pictureId, 0x7FFF);
  }
  if (hdr_info.tl0PicIdx != kNoTl0PicIdx) {
    RTC_DCHECK_GE(hdr_info.tl0PicIdx, 0);
    RTC_DCHECK_LE(hdr_info.tl0PicIdx, 0xFF);
  }
  if (hdr_info.temporalIdx != kNoTemporalIdx) {
    RTC_DCHECK_GE(hdr_info.temporalIdx, 0);
    RTC_DCHECK_LE(hdr_info.temporalIdx, 3);
  } else {
    RTC_DCHECK(!hdr_info.layerSync);
  }
  if (hdr_info.keyIdx != kNoKeyIdx) {
    RTC_DCHECK_GE(hdr_info.keyIdx, 0);
    RTC_DCHECK_LE(hdr_info.keyIdx, 0x1F);
  }
  return true;
}

}  // namespace

RtpPacketizerVp8::RtpPacketizerVp8(rtc::ArrayView<const uint8_t> payload,
                                   PayloadSizeLimits limits,
                                   const RTPVideoHeaderVP8& hdr_info)
    : hdr_(BuildHeader(hdr_info)), remaining_payload_(payload) {
  limits.max_payload_len -= hdr_.size();
  payload_sizes_ = SplitAboutEqually(payload.size(), limits);
  current_packet_ = payload_sizes_.begin();
}

RtpPacketizerVp8::~RtpPacketizerVp8() = default;

size_t RtpPacketizerVp8::NumPackets() const {
  return payload_sizes_.end() - current_packet_;
}

bool RtpPacketizerVp8::NextPacket(RtpPacketToSend* packet) {
  RTC_DCHECK(packet);
  if (current_packet_ == payload_sizes_.end()) {
    return false;
  }

  size_t packet_payload_len = *current_packet_;
  ++current_packet_;

  uint8_t* buffer = packet->AllocatePayload(hdr_.size() + packet_payload_len);
  RTC_CHECK(buffer);

  memcpy(buffer, hdr_.data(), hdr_.size());
  memcpy(buffer + hdr_.size(), remaining_payload_.data(), packet_payload_len);

  remaining_payload_ = remaining_payload_.subview(packet_payload_len);
  hdr_[0] &= (~kSBit);  //  Clear 'Start of partition' bit.
  packet->SetMarker(current_packet_ == payload_sizes_.end());
  return true;
}

// Write the VP8 payload descriptor.
//       0
//       0 1 2 3 4 5 6 7 8
//      +-+-+-+-+-+-+-+-+-+
//      |X| |N|S| PART_ID |
//      +-+-+-+-+-+-+-+-+-+
// X:   |I|L|T|K|         | (mandatory if any of the below are used)
//      +-+-+-+-+-+-+-+-+-+
// I:   |PictureID   (16b)| (optional)
//      +-+-+-+-+-+-+-+-+-+
// L:   |   TL0PIC_IDX    | (optional)
//      +-+-+-+-+-+-+-+-+-+
// T/K: |TID:Y|  KEYIDX   | (optional)
//      +-+-+-+-+-+-+-+-+-+
RtpPacketizerVp8::RawHeader RtpPacketizerVp8::BuildHeader(
    const RTPVideoHeaderVP8& header) {
  RTC_DCHECK(ValidateHeader(header));

  RawHeader result;
  bool tid_present = header.temporalIdx != kNoTemporalIdx;
  bool keyid_present = header.keyIdx != kNoKeyIdx;
  bool tl0_pid_present = header.tl0PicIdx != kNoTl0PicIdx;
  bool pid_present = header.pictureId != kNoPictureId;
  uint8_t x_field = 0;
  if (pid_present)
    x_field |= kIBit;
  if (tl0_pid_present)
    x_field |= kLBit;
  if (tid_present)
    x_field |= kTBit;
  if (keyid_present)
    x_field |= kKBit;

  uint8_t flags = 0;
  if (x_field != 0)
    flags |= kXBit;
  if (header.nonReference)
    flags |= kNBit;
  // Create header as first packet in the frame. NextPacket() will clear it
  // after first use.
  flags |= kSBit;
  result.push_back(flags);
  if (x_field == 0) {
    return result;
  }
  result.push_back(x_field);
  if (pid_present) {
    const uint16_t pic_id = static_cast<uint16_t>(header.pictureId);
    result.push_back(0x80 | ((pic_id >> 8) & 0x7F));
    result.push_back(pic_id & 0xFF);
  }
  if (tl0_pid_present) {
    result.push_back(header.tl0PicIdx);
  }
  if (tid_present || keyid_present) {
    uint8_t data_field = 0;
    if (tid_present) {
      data_field |= header.temporalIdx << 6;
      if (header.layerSync)
        data_field |= kYBit;
    }
    if (keyid_present) {
      data_field |= (header.keyIdx & kKeyIdxField);
    }
    result.push_back(data_field);
  }
  return result;
}

}  // namespace webrtc
