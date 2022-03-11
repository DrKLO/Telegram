/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_format_vp8_test_helper.h"

#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "test/gmock.h"
#include "test/gtest.h"

// VP8 payload descriptor
// https://datatracker.ietf.org/doc/html/rfc7741#section-4.2
//
//       0 1 2 3 4 5 6 7
//      +-+-+-+-+-+-+-+-+
//      |X|R|N|S|R| PID | (REQUIRED)
//      +-+-+-+-+-+-+-+-+
// X:   |I|L|T|K| RSV   | (OPTIONAL)
//      +-+-+-+-+-+-+-+-+
// I:   |M| PictureID   | (OPTIONAL)
//      +-+-+-+-+-+-+-+-+
//      |   PictureID   |
//      +-+-+-+-+-+-+-+-+
// L:   |   TL0PICIDX   | (OPTIONAL)
//      +-+-+-+-+-+-+-+-+
// T/K: |TID|Y| KEYIDX  | (OPTIONAL)
//      +-+-+-+-+-+-+-+-+

namespace webrtc {
namespace {

using ::testing::ElementsAreArray;

constexpr RtpPacketToSend::ExtensionManager* kNoExtensions = nullptr;

int Bit(uint8_t byte, int position) {
  return (byte >> position) & 0x01;
}

}  // namespace

RtpFormatVp8TestHelper::RtpFormatVp8TestHelper(const RTPVideoHeaderVP8* hdr,
                                               size_t payload_len)
    : hdr_info_(hdr), payload_(payload_len) {
  for (size_t i = 0; i < payload_.size(); ++i) {
    payload_[i] = i;
  }
}

RtpFormatVp8TestHelper::~RtpFormatVp8TestHelper() = default;

void RtpFormatVp8TestHelper::GetAllPacketsAndCheck(
    RtpPacketizerVp8* packetizer,
    rtc::ArrayView<const size_t> expected_sizes) {
  EXPECT_EQ(packetizer->NumPackets(), expected_sizes.size());
  const uint8_t* data_ptr = payload_.begin();
  RtpPacketToSend packet(kNoExtensions);
  for (size_t i = 0; i < expected_sizes.size(); ++i) {
    EXPECT_TRUE(packetizer->NextPacket(&packet));
    auto rtp_payload = packet.payload();
    EXPECT_EQ(rtp_payload.size(), expected_sizes[i]);

    int payload_offset = CheckHeader(rtp_payload, /*first=*/i == 0);
    // Verify that the payload (i.e., after the headers) of the packet is
    // identical to the expected (as found in data_ptr).
    auto vp8_payload = rtp_payload.subview(payload_offset);
    ASSERT_GE(payload_.end() - data_ptr, static_cast<int>(vp8_payload.size()));
    EXPECT_THAT(vp8_payload, ElementsAreArray(data_ptr, vp8_payload.size()));
    data_ptr += vp8_payload.size();
  }
  EXPECT_EQ(payload_.end() - data_ptr, 0);
}

int RtpFormatVp8TestHelper::CheckHeader(rtc::ArrayView<const uint8_t> buffer,
                                        bool first) {
  int x_bit = Bit(buffer[0], 7);
  EXPECT_EQ(Bit(buffer[0], 6), 0);  // Reserved.
  EXPECT_EQ(Bit(buffer[0], 5), hdr_info_->nonReference ? 1 : 0);
  EXPECT_EQ(Bit(buffer[0], 4), first ? 1 : 0);
  EXPECT_EQ(buffer[0] & 0x0f, 0);  // RtpPacketizerVp8 always uses partition 0.

  int payload_offset = 1;
  if (hdr_info_->pictureId != kNoPictureId ||
      hdr_info_->temporalIdx != kNoTemporalIdx ||
      hdr_info_->tl0PicIdx != kNoTl0PicIdx || hdr_info_->keyIdx != kNoKeyIdx) {
    EXPECT_EQ(x_bit, 1);
    ++payload_offset;
    CheckPictureID(buffer, &payload_offset);
    CheckTl0PicIdx(buffer, &payload_offset);
    CheckTIDAndKeyIdx(buffer, &payload_offset);
    EXPECT_EQ(buffer[1] & 0x07, 0);  // Reserved.
  } else {
    EXPECT_EQ(x_bit, 0);
  }

  return payload_offset;
}

// Verify that the I bit and the PictureID field are both set in accordance
// with the information in hdr_info_->pictureId.
void RtpFormatVp8TestHelper::CheckPictureID(
    rtc::ArrayView<const uint8_t> buffer,
    int* offset) {
  int i_bit = Bit(buffer[1], 7);
  if (hdr_info_->pictureId != kNoPictureId) {
    EXPECT_EQ(i_bit, 1);
    int two_byte_picture_id = Bit(buffer[*offset], 7);
    EXPECT_EQ(two_byte_picture_id, 1);
    EXPECT_EQ(buffer[*offset] & 0x7F, (hdr_info_->pictureId >> 8) & 0x7F);
    EXPECT_EQ(buffer[(*offset) + 1], hdr_info_->pictureId & 0xFF);
    (*offset) += 2;
  } else {
    EXPECT_EQ(i_bit, 0);
  }
}

// Verify that the L bit and the TL0PICIDX field are both set in accordance
// with the information in hdr_info_->tl0PicIdx.
void RtpFormatVp8TestHelper::CheckTl0PicIdx(
    rtc::ArrayView<const uint8_t> buffer,
    int* offset) {
  int l_bit = Bit(buffer[1], 6);
  if (hdr_info_->tl0PicIdx != kNoTl0PicIdx) {
    EXPECT_EQ(l_bit, 1);
    EXPECT_EQ(buffer[*offset], hdr_info_->tl0PicIdx);
    ++*offset;
  } else {
    EXPECT_EQ(l_bit, 0);
  }
}

// Verify that the T bit and the TL0PICIDX field, and the K bit and KEYIDX
// field are all set in accordance with the information in
// hdr_info_->temporalIdx and hdr_info_->keyIdx, respectively.
void RtpFormatVp8TestHelper::CheckTIDAndKeyIdx(
    rtc::ArrayView<const uint8_t> buffer,
    int* offset) {
  int t_bit = Bit(buffer[1], 5);
  int k_bit = Bit(buffer[1], 4);
  if (hdr_info_->temporalIdx == kNoTemporalIdx &&
      hdr_info_->keyIdx == kNoKeyIdx) {
    EXPECT_EQ(t_bit, 0);
    EXPECT_EQ(k_bit, 0);
    return;
  }
  int temporal_id = (buffer[*offset] & 0xC0) >> 6;
  int y_bit = Bit(buffer[*offset], 5);
  int key_idx = buffer[*offset] & 0x1f;
  if (hdr_info_->temporalIdx != kNoTemporalIdx) {
    EXPECT_EQ(t_bit, 1);
    EXPECT_EQ(temporal_id, hdr_info_->temporalIdx);
    EXPECT_EQ(y_bit, hdr_info_->layerSync ? 1 : 0);
  } else {
    EXPECT_EQ(t_bit, 0);
    EXPECT_EQ(temporal_id, 0);
    EXPECT_EQ(y_bit, 0);
  }
  if (hdr_info_->keyIdx != kNoKeyIdx) {
    EXPECT_EQ(k_bit, 1);
    EXPECT_EQ(key_idx, hdr_info_->keyIdx);
  } else {
    EXPECT_EQ(k_bit, 0);
    EXPECT_EQ(key_idx, 0);
  }
  ++*offset;
}

}  // namespace webrtc
