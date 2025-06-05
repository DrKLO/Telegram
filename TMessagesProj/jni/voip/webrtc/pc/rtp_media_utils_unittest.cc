/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/rtp_media_utils.h"

#include <tuple>

#include "test/gtest.h"

namespace webrtc {

using ::testing::Bool;
using ::testing::Combine;
using ::testing::Values;
using ::testing::ValuesIn;

RtpTransceiverDirection kAllDirections[] = {
    RtpTransceiverDirection::kSendRecv, RtpTransceiverDirection::kSendOnly,
    RtpTransceiverDirection::kRecvOnly, RtpTransceiverDirection::kInactive};

class EnumerateAllDirectionsTest
    : public ::testing::TestWithParam<RtpTransceiverDirection> {};

// Test that converting the direction to send/recv and back again results in the
// same direction.
TEST_P(EnumerateAllDirectionsTest, TestIdentity) {
  RtpTransceiverDirection direction = GetParam();

  bool send = RtpTransceiverDirectionHasSend(direction);
  bool recv = RtpTransceiverDirectionHasRecv(direction);

  EXPECT_EQ(direction, RtpTransceiverDirectionFromSendRecv(send, recv));
}

// Test that reversing the direction is equivalent to swapping send/recv.
TEST_P(EnumerateAllDirectionsTest, TestReversedSwapped) {
  RtpTransceiverDirection direction = GetParam();

  bool send = RtpTransceiverDirectionHasSend(direction);
  bool recv = RtpTransceiverDirectionHasRecv(direction);

  EXPECT_EQ(RtpTransceiverDirectionFromSendRecv(recv, send),
            RtpTransceiverDirectionReversed(direction));
}

// Test that reversing the direction twice results in the same direction.
TEST_P(EnumerateAllDirectionsTest, TestReversedIdentity) {
  RtpTransceiverDirection direction = GetParam();

  EXPECT_EQ(direction, RtpTransceiverDirectionReversed(
                           RtpTransceiverDirectionReversed(direction)));
}

INSTANTIATE_TEST_SUITE_P(RtpTransceiverDirectionTest,
                         EnumerateAllDirectionsTest,
                         ValuesIn(kAllDirections));

class EnumerateAllDirectionsAndBool
    : public ::testing::TestWithParam<
          std::tuple<RtpTransceiverDirection, bool>> {};

TEST_P(EnumerateAllDirectionsAndBool, TestWithSendSet) {
  RtpTransceiverDirection direction = std::get<0>(GetParam());
  bool send = std::get<1>(GetParam());

  RtpTransceiverDirection result =
      RtpTransceiverDirectionWithSendSet(direction, send);

  EXPECT_EQ(send, RtpTransceiverDirectionHasSend(result));
  EXPECT_EQ(RtpTransceiverDirectionHasRecv(direction),
            RtpTransceiverDirectionHasRecv(result));
}

TEST_P(EnumerateAllDirectionsAndBool, TestWithRecvSet) {
  RtpTransceiverDirection direction = std::get<0>(GetParam());
  bool recv = std::get<1>(GetParam());

  RtpTransceiverDirection result =
      RtpTransceiverDirectionWithRecvSet(direction, recv);

  EXPECT_EQ(RtpTransceiverDirectionHasSend(direction),
            RtpTransceiverDirectionHasSend(result));
  EXPECT_EQ(recv, RtpTransceiverDirectionHasRecv(result));
}

INSTANTIATE_TEST_SUITE_P(RtpTransceiverDirectionTest,
                         EnumerateAllDirectionsAndBool,
                         Combine(ValuesIn(kAllDirections), Bool()));

}  // namespace webrtc
