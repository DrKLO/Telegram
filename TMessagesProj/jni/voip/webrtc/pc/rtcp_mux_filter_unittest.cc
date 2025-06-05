/*
 *  Copyright 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/rtcp_mux_filter.h"

#include "test/gtest.h"

TEST(RtcpMuxFilterTest, IsActiveSender) {
  cricket::RtcpMuxFilter filter;
  // Init state - not active
  EXPECT_FALSE(filter.IsActive());
  EXPECT_FALSE(filter.IsProvisionallyActive());
  EXPECT_FALSE(filter.IsFullyActive());
  // After sent offer, demux should not be active.
  filter.SetOffer(true, cricket::CS_LOCAL);
  EXPECT_FALSE(filter.IsActive());
  EXPECT_FALSE(filter.IsProvisionallyActive());
  EXPECT_FALSE(filter.IsFullyActive());
  // Remote accepted, filter is now active.
  filter.SetAnswer(true, cricket::CS_REMOTE);
  EXPECT_TRUE(filter.IsActive());
  EXPECT_FALSE(filter.IsProvisionallyActive());
  EXPECT_TRUE(filter.IsFullyActive());
}

// Test that we can receive provisional answer and final answer.
TEST(RtcpMuxFilterTest, ReceivePrAnswer) {
  cricket::RtcpMuxFilter filter;
  filter.SetOffer(true, cricket::CS_LOCAL);
  // Received provisional answer with mux enabled.
  EXPECT_TRUE(filter.SetProvisionalAnswer(true, cricket::CS_REMOTE));
  // We are now provisionally active since both sender and receiver support mux.
  EXPECT_TRUE(filter.IsActive());
  EXPECT_TRUE(filter.IsProvisionallyActive());
  EXPECT_FALSE(filter.IsFullyActive());
  // Received provisional answer with mux disabled.
  EXPECT_TRUE(filter.SetProvisionalAnswer(false, cricket::CS_REMOTE));
  // We are now inactive since the receiver doesn't support mux.
  EXPECT_FALSE(filter.IsActive());
  EXPECT_FALSE(filter.IsProvisionallyActive());
  EXPECT_FALSE(filter.IsFullyActive());
  // Received final answer with mux enabled.
  EXPECT_TRUE(filter.SetAnswer(true, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.IsActive());
  EXPECT_FALSE(filter.IsProvisionallyActive());
  EXPECT_TRUE(filter.IsFullyActive());
}

TEST(RtcpMuxFilterTest, IsActiveReceiver) {
  cricket::RtcpMuxFilter filter;
  // Init state - not active.
  EXPECT_FALSE(filter.IsActive());
  EXPECT_FALSE(filter.IsProvisionallyActive());
  EXPECT_FALSE(filter.IsFullyActive());
  // After received offer, demux should not be active
  filter.SetOffer(true, cricket::CS_REMOTE);
  EXPECT_FALSE(filter.IsActive());
  EXPECT_FALSE(filter.IsProvisionallyActive());
  EXPECT_FALSE(filter.IsFullyActive());
  // We accept, filter is now active
  filter.SetAnswer(true, cricket::CS_LOCAL);
  EXPECT_TRUE(filter.IsActive());
  EXPECT_FALSE(filter.IsProvisionallyActive());
  EXPECT_TRUE(filter.IsFullyActive());
}

// Test that we can send provisional answer and final answer.
TEST(RtcpMuxFilterTest, SendPrAnswer) {
  cricket::RtcpMuxFilter filter;
  filter.SetOffer(true, cricket::CS_REMOTE);
  // Send provisional answer with mux enabled.
  EXPECT_TRUE(filter.SetProvisionalAnswer(true, cricket::CS_LOCAL));
  EXPECT_TRUE(filter.IsActive());
  EXPECT_TRUE(filter.IsProvisionallyActive());
  EXPECT_FALSE(filter.IsFullyActive());
  // Received provisional answer with mux disabled.
  EXPECT_TRUE(filter.SetProvisionalAnswer(false, cricket::CS_LOCAL));
  EXPECT_FALSE(filter.IsActive());
  EXPECT_FALSE(filter.IsProvisionallyActive());
  EXPECT_FALSE(filter.IsFullyActive());
  // Send final answer with mux enabled.
  EXPECT_TRUE(filter.SetAnswer(true, cricket::CS_LOCAL));
  EXPECT_TRUE(filter.IsActive());
  EXPECT_FALSE(filter.IsProvisionallyActive());
  EXPECT_TRUE(filter.IsFullyActive());
}

// Test that we can enable the filter in an update.
// We can not disable the filter later since that would mean we need to
// recreate a rtcp transport channel.
TEST(RtcpMuxFilterTest, EnableFilterDuringUpdate) {
  cricket::RtcpMuxFilter filter;
  EXPECT_FALSE(filter.IsActive());
  EXPECT_TRUE(filter.SetOffer(false, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.SetAnswer(false, cricket::CS_LOCAL));
  EXPECT_FALSE(filter.IsActive());

  EXPECT_TRUE(filter.SetOffer(true, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.SetAnswer(true, cricket::CS_LOCAL));
  EXPECT_TRUE(filter.IsActive());

  EXPECT_FALSE(filter.SetOffer(false, cricket::CS_REMOTE));
  EXPECT_FALSE(filter.SetAnswer(false, cricket::CS_LOCAL));
  EXPECT_TRUE(filter.IsActive());
}

// Test that SetOffer can be called twice.
TEST(RtcpMuxFilterTest, SetOfferTwice) {
  cricket::RtcpMuxFilter filter;

  EXPECT_TRUE(filter.SetOffer(true, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.SetOffer(true, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.SetAnswer(true, cricket::CS_LOCAL));
  EXPECT_TRUE(filter.IsActive());

  cricket::RtcpMuxFilter filter2;
  EXPECT_TRUE(filter2.SetOffer(false, cricket::CS_LOCAL));
  EXPECT_TRUE(filter2.SetOffer(false, cricket::CS_LOCAL));
  EXPECT_TRUE(filter2.SetAnswer(false, cricket::CS_REMOTE));
  EXPECT_FALSE(filter2.IsActive());
}

// Test that the filter can be enabled twice.
TEST(RtcpMuxFilterTest, EnableFilterTwiceDuringUpdate) {
  cricket::RtcpMuxFilter filter;

  EXPECT_TRUE(filter.SetOffer(true, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.SetAnswer(true, cricket::CS_LOCAL));
  EXPECT_TRUE(filter.IsActive());

  EXPECT_TRUE(filter.SetOffer(true, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.SetAnswer(true, cricket::CS_LOCAL));
  EXPECT_TRUE(filter.IsActive());
}

// Test that the filter can be kept disabled during updates.
TEST(RtcpMuxFilterTest, KeepFilterDisabledDuringUpdate) {
  cricket::RtcpMuxFilter filter;

  EXPECT_TRUE(filter.SetOffer(false, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.SetAnswer(false, cricket::CS_LOCAL));
  EXPECT_FALSE(filter.IsActive());

  EXPECT_TRUE(filter.SetOffer(false, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.SetAnswer(false, cricket::CS_LOCAL));
  EXPECT_FALSE(filter.IsActive());
}

// Test that we can SetActive and then can't deactivate.
TEST(RtcpMuxFilterTest, SetActiveCantDeactivate) {
  cricket::RtcpMuxFilter filter;

  filter.SetActive();
  EXPECT_TRUE(filter.IsActive());

  EXPECT_FALSE(filter.SetOffer(false, cricket::CS_LOCAL));
  EXPECT_TRUE(filter.IsActive());
  EXPECT_TRUE(filter.SetOffer(true, cricket::CS_LOCAL));
  EXPECT_TRUE(filter.IsActive());

  EXPECT_FALSE(filter.SetProvisionalAnswer(false, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.IsActive());
  EXPECT_TRUE(filter.SetProvisionalAnswer(true, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.IsActive());

  EXPECT_FALSE(filter.SetAnswer(false, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.IsActive());
  EXPECT_TRUE(filter.SetAnswer(true, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.IsActive());

  EXPECT_FALSE(filter.SetOffer(false, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.IsActive());
  EXPECT_TRUE(filter.SetOffer(true, cricket::CS_REMOTE));
  EXPECT_TRUE(filter.IsActive());

  EXPECT_FALSE(filter.SetProvisionalAnswer(false, cricket::CS_LOCAL));
  EXPECT_TRUE(filter.IsActive());
  EXPECT_TRUE(filter.SetProvisionalAnswer(true, cricket::CS_LOCAL));
  EXPECT_TRUE(filter.IsActive());

  EXPECT_FALSE(filter.SetAnswer(false, cricket::CS_LOCAL));
  EXPECT_TRUE(filter.IsActive());
  EXPECT_TRUE(filter.SetAnswer(true, cricket::CS_LOCAL));
  EXPECT_TRUE(filter.IsActive());
}
