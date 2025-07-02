/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/rtp_packet_infos.h"
#include "api/units/time_delta.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {

TEST(RtpPacketInfoTest, Ssrc) {
  constexpr uint32_t kValue = 4038189233;

  RtpPacketInfo lhs;
  RtpPacketInfo rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs.set_ssrc(kValue);
  EXPECT_EQ(rhs.ssrc(), kValue);

  EXPECT_FALSE(lhs == rhs);
  EXPECT_TRUE(lhs != rhs);

  lhs = rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs = RtpPacketInfo();
  EXPECT_NE(rhs.ssrc(), kValue);

  rhs = RtpPacketInfo(/*ssrc=*/kValue, /*csrcs=*/{}, /*rtp_timestamp=*/{},
                      /*receive_time=*/Timestamp::Zero());
  EXPECT_EQ(rhs.ssrc(), kValue);
}

TEST(RtpPacketInfoTest, Csrcs) {
  const std::vector<uint32_t> value = {4038189233, 3016333617, 1207992985};

  RtpPacketInfo lhs;
  RtpPacketInfo rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs.set_csrcs(value);
  EXPECT_EQ(rhs.csrcs(), value);

  EXPECT_FALSE(lhs == rhs);
  EXPECT_TRUE(lhs != rhs);

  lhs = rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs = RtpPacketInfo();
  EXPECT_NE(rhs.csrcs(), value);

  rhs = RtpPacketInfo(/*ssrc=*/{}, /*csrcs=*/value, /*rtp_timestamp=*/{},
                      /*receive_time=*/Timestamp::Zero());
  EXPECT_EQ(rhs.csrcs(), value);
}

TEST(RtpPacketInfoTest, RtpTimestamp) {
  constexpr uint32_t kValue = 4038189233;

  RtpPacketInfo lhs;
  RtpPacketInfo rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs.set_rtp_timestamp(kValue);
  EXPECT_EQ(rhs.rtp_timestamp(), kValue);

  EXPECT_FALSE(lhs == rhs);
  EXPECT_TRUE(lhs != rhs);

  lhs = rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs = RtpPacketInfo();
  EXPECT_NE(rhs.rtp_timestamp(), kValue);

  rhs = RtpPacketInfo(/*ssrc=*/{}, /*csrcs=*/{}, /*rtp_timestamp=*/kValue,
                      /*receive_time=*/Timestamp::Zero());
  EXPECT_EQ(rhs.rtp_timestamp(), kValue);
}

TEST(RtpPacketInfoTest, ReceiveTimeMs) {
  constexpr Timestamp kValue = Timestamp::Micros(8868963877546349045LL);

  RtpPacketInfo lhs;
  RtpPacketInfo rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs.set_receive_time(kValue);
  EXPECT_EQ(rhs.receive_time(), kValue);

  EXPECT_FALSE(lhs == rhs);
  EXPECT_TRUE(lhs != rhs);

  lhs = rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs = RtpPacketInfo();
  EXPECT_NE(rhs.receive_time(), kValue);

  rhs = RtpPacketInfo(/*ssrc=*/{}, /*csrcs=*/{}, /*rtp_timestamp=*/{},
                      /*receive_time=*/kValue);
  EXPECT_EQ(rhs.receive_time(), kValue);
}

TEST(RtpPacketInfoTest, AudioLevel) {
  constexpr absl::optional<uint8_t> kValue = 31;

  RtpPacketInfo lhs;
  RtpPacketInfo rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs.set_audio_level(kValue);
  EXPECT_EQ(rhs.audio_level(), kValue);

  EXPECT_FALSE(lhs == rhs);
  EXPECT_TRUE(lhs != rhs);

  lhs = rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs = RtpPacketInfo();
  EXPECT_NE(rhs.audio_level(), kValue);

  rhs = RtpPacketInfo(/*ssrc=*/{}, /*csrcs=*/{}, /*rtp_timestamp=*/{},
                      /*receive_time=*/Timestamp::Zero());
  rhs.set_audio_level(kValue);
  EXPECT_EQ(rhs.audio_level(), kValue);
}

TEST(RtpPacketInfoTest, AbsoluteCaptureTime) {
  constexpr absl::optional<AbsoluteCaptureTime> kValue = AbsoluteCaptureTime{
      .absolute_capture_timestamp = 12, .estimated_capture_clock_offset = 34};

  RtpPacketInfo lhs;
  RtpPacketInfo rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs.set_absolute_capture_time(kValue);
  EXPECT_EQ(rhs.absolute_capture_time(), kValue);

  EXPECT_FALSE(lhs == rhs);
  EXPECT_TRUE(lhs != rhs);

  lhs = rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs = RtpPacketInfo();
  EXPECT_NE(rhs.absolute_capture_time(), kValue);

  rhs = RtpPacketInfo(/*ssrc=*/{}, /*csrcs=*/{}, /*rtp_timestamp=*/{},
                      /*receive_time=*/Timestamp::Zero());
  rhs.set_absolute_capture_time(kValue);
  EXPECT_EQ(rhs.absolute_capture_time(), kValue);
}

TEST(RtpPacketInfoTest, LocalCaptureClockOffset) {
  constexpr TimeDelta kValue = TimeDelta::Micros(8868963877546349045LL);

  RtpPacketInfo lhs;
  RtpPacketInfo rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs.set_local_capture_clock_offset(kValue);
  EXPECT_EQ(rhs.local_capture_clock_offset(), kValue);

  EXPECT_FALSE(lhs == rhs);
  EXPECT_TRUE(lhs != rhs);

  lhs = rhs;

  EXPECT_TRUE(lhs == rhs);
  EXPECT_FALSE(lhs != rhs);

  rhs = RtpPacketInfo();
  EXPECT_EQ(rhs.local_capture_clock_offset(), absl::nullopt);

  rhs = RtpPacketInfo(/*ssrc=*/{}, /*csrcs=*/{}, /*rtp_timestamp=*/{},
                      /*receive_time=*/Timestamp::Zero());
  rhs.set_local_capture_clock_offset(kValue);
  EXPECT_EQ(rhs.local_capture_clock_offset(), kValue);
}

}  // namespace webrtc
