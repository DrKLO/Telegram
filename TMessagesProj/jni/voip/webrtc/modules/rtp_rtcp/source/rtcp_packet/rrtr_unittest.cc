/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_packet/rrtr.h"

#include "test/gtest.h"

using webrtc::rtcp::Rrtr;

namespace webrtc {
namespace {

const uint32_t kNtpSec = 0x12345678;
const uint32_t kNtpFrac = 0x23456789;
const uint8_t kBlock[] = {0x04, 0x00, 0x00, 0x02, 0x12, 0x34,
                          0x56, 0x78, 0x23, 0x45, 0x67, 0x89};
const size_t kBlockSizeBytes = sizeof(kBlock);
static_assert(
    kBlockSizeBytes == Rrtr::kLength,
    "Size of manually created Rrtr block should match class constant");

TEST(RtcpPacketRrtrTest, Create) {
  uint8_t buffer[Rrtr::kLength];
  Rrtr rrtr;
  rrtr.SetNtp(NtpTime(kNtpSec, kNtpFrac));

  rrtr.Create(buffer);
  EXPECT_EQ(0, memcmp(buffer, kBlock, kBlockSizeBytes));
}

TEST(RtcpPacketRrtrTest, Parse) {
  Rrtr read_rrtr;
  read_rrtr.Parse(kBlock);

  // Run checks on const object to ensure all accessors have const modifier.
  const Rrtr& parsed = read_rrtr;

  EXPECT_EQ(kNtpSec, parsed.ntp().seconds());
  EXPECT_EQ(kNtpFrac, parsed.ntp().fractions());
}

}  // namespace
}  // namespace webrtc
