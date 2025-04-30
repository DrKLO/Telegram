/*
 *  Copyright 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdint.h>

#include <cstdlib>
#include <iterator>
#include <string>
#include <tuple>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/types/optional.h"
#include "api/data_channel_interface.h"
#include "api/dtls_transport_interface.h"
#include "api/peer_connection_interface.h"
#include "api/scoped_refptr.h"
#include "api/sctp_transport_interface.h"
#include "api/stats/rtc_stats_report.h"
#include "api/stats/rtcstats_objects.h"
#include "api/units/time_delta.h"
#include "p2p/base/transport_description.h"
#include "p2p/base/transport_info.h"
#include "pc/media_session.h"
#include "pc/session_description.h"
#include "pc/test/integration_test_helpers.h"
#include "pc/test/mock_peer_connection_observers.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/fake_clock.h"
#include "rtc_base/gunit.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/virtual_socket_server.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {

namespace {

// All tests in this file require SCTP support.
#ifdef WEBRTC_HAVE_SCTP

#if defined(WEBRTC_ANDROID)
// Disable heavy tests running on low-end Android devices.
#define DISABLED_ON_ANDROID(t) DISABLED_##t
#else
#define DISABLED_ON_ANDROID(t) t
#endif

class DataChannelIntegrationTest
    : public PeerConnectionIntegrationBaseTest,
      public ::testing::WithParamInterface<std::tuple<SdpSemantics, bool>> {
 protected:
  DataChannelIntegrationTest()
      : PeerConnectionIntegrationBaseTest(std::get<0>(GetParam())),
        allow_media_(std::get<1>(GetParam())) {}
  bool allow_media() { return allow_media_; }

  bool CreatePeerConnectionWrappers() {
    if (allow_media_) {
      return PeerConnectionIntegrationBaseTest::CreatePeerConnectionWrappers();
    }
    return PeerConnectionIntegrationBaseTest::
        CreatePeerConnectionWrappersWithoutMediaEngine();
  }

 private:
  // True if media is allowed to be added
  const bool allow_media_;
};

// Fake clock must be set before threads are started to prevent race on
// Set/GetClockForTesting().
// To achieve that, multiple inheritance is used as a mixin pattern
// where order of construction is finely controlled.
// This also ensures peerconnection is closed before switching back to non-fake
// clock, avoiding other races and DCHECK failures such as in rtp_sender.cc.
class FakeClockForTest : public rtc::ScopedFakeClock {
 protected:
  FakeClockForTest() {
    // Some things use a time of "0" as a special value, so we need to start out
    // the fake clock at a nonzero time.
    // TODO(deadbeef): Fix this.
    AdvanceTime(TimeDelta::Seconds(1));
  }

  // Explicit handle.
  ScopedFakeClock& FakeClock() { return *this; }
};

class DataChannelIntegrationTestPlanB
    : public PeerConnectionIntegrationBaseTest {
 protected:
  DataChannelIntegrationTestPlanB()
      : PeerConnectionIntegrationBaseTest(SdpSemantics::kPlanB_DEPRECATED) {}
};

class DataChannelIntegrationTestUnifiedPlan
    : public PeerConnectionIntegrationBaseTest {
 protected:
  DataChannelIntegrationTestUnifiedPlan()
      : PeerConnectionIntegrationBaseTest(SdpSemantics::kUnifiedPlan) {}
};

void MakeActiveSctpOffer(cricket::SessionDescription* desc) {
  auto& transport_infos = desc->transport_infos();
  for (auto& transport_info : transport_infos) {
    transport_info.description.connection_role = cricket::CONNECTIONROLE_ACTIVE;
  }
}

// This test causes a PeerConnection to enter Disconnected state, and
// sends data on a DataChannel while disconnected.
// The data should be surfaced when the connection reestablishes.
TEST_P(DataChannelIntegrationTest, DataChannelWhileDisconnected) {
  CreatePeerConnectionWrappers();
  ConnectFakeSignaling();
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer(), kDefaultTimeout);
  std::string data1 = "hello first";
  caller()->data_channel()->Send(DataBuffer(data1));
  EXPECT_EQ_WAIT(data1, callee()->data_observer()->last_message(),
                 kDefaultTimeout);
  // Cause a network outage
  virtual_socket_server()->set_drop_probability(1.0);
  EXPECT_EQ_WAIT(PeerConnectionInterface::kIceConnectionDisconnected,
                 caller()->standardized_ice_connection_state(),
                 kDefaultTimeout);
  std::string data2 = "hello second";
  caller()->data_channel()->Send(DataBuffer(data2));
  // Remove the network outage. The connection should reestablish.
  virtual_socket_server()->set_drop_probability(0.0);
  EXPECT_EQ_WAIT(data2, callee()->data_observer()->last_message(),
                 kDefaultTimeout);
}

// This test causes a PeerConnection to enter Disconnected state,
// sends data on a DataChannel while disconnected, and then triggers
// an ICE restart.
// The data should be surfaced when the connection reestablishes.
TEST_P(DataChannelIntegrationTest, DataChannelWhileDisconnectedIceRestart) {
  CreatePeerConnectionWrappers();
  ConnectFakeSignaling();
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer(), kDefaultTimeout);
  std::string data1 = "hello first";
  caller()->data_channel()->Send(DataBuffer(data1));
  EXPECT_EQ_WAIT(data1, callee()->data_observer()->last_message(),
                 kDefaultTimeout);
  // Cause a network outage
  virtual_socket_server()->set_drop_probability(1.0);
  ASSERT_EQ_WAIT(PeerConnectionInterface::kIceConnectionDisconnected,
                 caller()->standardized_ice_connection_state(),
                 kDefaultTimeout);
  std::string data2 = "hello second";
  caller()->data_channel()->Send(DataBuffer(data2));

  // Trigger an ICE restart. The signaling channel is not affected by
  // the network outage.
  caller()->SetOfferAnswerOptions(IceRestartOfferAnswerOptions());
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  // Remove the network outage. The connection should reestablish.
  virtual_socket_server()->set_drop_probability(0.0);
  EXPECT_EQ_WAIT(data2, callee()->data_observer()->last_message(),
                 kDefaultTimeout);
}

// This test sets up a call between two parties with audio, video and an SCTP
// data channel.
TEST_P(DataChannelIntegrationTest, EndToEndCallWithSctpDataChannel) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  // Expect that data channel created on caller side will show up for callee as
  // well.
  caller()->CreateDataChannel();
  if (allow_media()) {
    caller()->AddAudioVideoTracks();
    callee()->AddAudioVideoTracks();
  }
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  if (allow_media()) {
    // Ensure the existence of the SCTP data channel didn't impede audio/video.
    MediaExpectations media_expectations;
    media_expectations.ExpectBidirectionalAudioAndVideo();
    ASSERT_TRUE(ExpectNewFrames(media_expectations));
  }
  // Caller data channel should already exist (it created one). Callee data
  // channel may not exist yet, since negotiation happens in-band, not in SDP.
  ASSERT_NE(nullptr, caller()->data_channel());
  ASSERT_TRUE_WAIT(callee()->data_channel() != nullptr, kDefaultTimeout);
  EXPECT_TRUE_WAIT(caller()->data_observer()->IsOpen(), kDefaultTimeout);
  EXPECT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);

  // Ensure data can be sent in both directions.
  std::string data = "hello world";
  caller()->data_channel()->Send(DataBuffer(data));
  EXPECT_EQ_WAIT(data, callee()->data_observer()->last_message(),
                 kDefaultTimeout);
  callee()->data_channel()->Send(DataBuffer(data));
  EXPECT_EQ_WAIT(data, caller()->data_observer()->last_message(),
                 kDefaultTimeout);
}

// This test sets up a call between two parties with an SCTP
// data channel only, and sends messages of various sizes.
TEST_P(DataChannelIntegrationTest,
       EndToEndCallWithSctpDataChannelVariousSizes) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  // Expect that data channel created on caller side will show up for callee as
  // well.
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  // Caller data channel should already exist (it created one). Callee data
  // channel may not exist yet, since negotiation happens in-band, not in SDP.
  ASSERT_NE(nullptr, caller()->data_channel());
  ASSERT_TRUE_WAIT(callee()->data_channel() != nullptr, kDefaultTimeout);
  EXPECT_TRUE_WAIT(caller()->data_observer()->IsOpen(), kDefaultTimeout);
  EXPECT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);

  for (int message_size = 1; message_size < 100000; message_size *= 2) {
    std::string data(message_size, 'a');
    caller()->data_channel()->Send(DataBuffer(data));
    EXPECT_EQ_WAIT(data, callee()->data_observer()->last_message(),
                   kDefaultTimeout);
    callee()->data_channel()->Send(DataBuffer(data));
    EXPECT_EQ_WAIT(data, caller()->data_observer()->last_message(),
                   kDefaultTimeout);
  }
  // Specifically probe the area around the MTU size.
  for (int message_size = 1100; message_size < 1300; message_size += 1) {
    std::string data(message_size, 'a');
    caller()->data_channel()->Send(DataBuffer(data));
    EXPECT_EQ_WAIT(data, callee()->data_observer()->last_message(),
                   kDefaultTimeout);
    callee()->data_channel()->Send(DataBuffer(data));
    EXPECT_EQ_WAIT(data, caller()->data_observer()->last_message(),
                   kDefaultTimeout);
  }
}

// This test sets up a call between two parties with an SCTP
// data channel only, and sends empty messages
TEST_P(DataChannelIntegrationTest,
       EndToEndCallWithSctpDataChannelEmptyMessages) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  // Expect that data channel created on caller side will show up for callee as
  // well.
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  // Caller data channel should already exist (it created one). Callee data
  // channel may not exist yet, since negotiation happens in-band, not in SDP.
  ASSERT_NE(nullptr, caller()->data_channel());
  ASSERT_TRUE_WAIT(callee()->data_channel() != nullptr, kDefaultTimeout);
  EXPECT_TRUE_WAIT(caller()->data_observer()->IsOpen(), kDefaultTimeout);
  EXPECT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);

  // Ensure data can be sent in both directions.
  // Sending empty string data
  std::string data = "";
  caller()->data_channel()->Send(DataBuffer(data));
  EXPECT_EQ_WAIT(1u, callee()->data_observer()->received_message_count(),
                 kDefaultTimeout);
  EXPECT_TRUE(callee()->data_observer()->last_message().empty());
  EXPECT_FALSE(callee()->data_observer()->messages().back().binary);
  callee()->data_channel()->Send(DataBuffer(data));
  EXPECT_EQ_WAIT(1u, caller()->data_observer()->received_message_count(),
                 kDefaultTimeout);
  EXPECT_TRUE(caller()->data_observer()->last_message().empty());
  EXPECT_FALSE(caller()->data_observer()->messages().back().binary);

  // Sending empty binary data
  rtc::CopyOnWriteBuffer empty_buffer;
  caller()->data_channel()->Send(DataBuffer(empty_buffer, true));
  EXPECT_EQ_WAIT(2u, callee()->data_observer()->received_message_count(),
                 kDefaultTimeout);
  EXPECT_TRUE(callee()->data_observer()->last_message().empty());
  EXPECT_TRUE(callee()->data_observer()->messages().back().binary);
  callee()->data_channel()->Send(DataBuffer(empty_buffer, true));
  EXPECT_EQ_WAIT(2u, caller()->data_observer()->received_message_count(),
                 kDefaultTimeout);
  EXPECT_TRUE(caller()->data_observer()->last_message().empty());
  EXPECT_TRUE(caller()->data_observer()->messages().back().binary);
}

TEST_P(DataChannelIntegrationTest,
       EndToEndCallWithSctpDataChannelLowestSafeMtu) {
  // The lowest payload size limit that's tested and found safe for this
  // application. Note that this is not the safe limit under all conditions;
  // in particular, the default is not the largest DTLS signature, and
  // this test does not use TURN.
  const size_t kLowestSafePayloadSizeLimit = 1225;

  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  // Expect that data channel created on caller side will show up for callee as
  // well.
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  // Caller data channel should already exist (it created one). Callee data
  // channel may not exist yet, since negotiation happens in-band, not in SDP.
  ASSERT_NE(nullptr, caller()->data_channel());
  ASSERT_TRUE_WAIT(callee()->data_channel() != nullptr, kDefaultTimeout);
  EXPECT_TRUE_WAIT(caller()->data_observer()->IsOpen(), kDefaultTimeout);
  EXPECT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);

  virtual_socket_server()->set_max_udp_payload(kLowestSafePayloadSizeLimit);
  for (int message_size = 1140; message_size < 1240; message_size += 1) {
    std::string data(message_size, 'a');
    caller()->data_channel()->Send(DataBuffer(data));
    ASSERT_EQ_WAIT(data, callee()->data_observer()->last_message(),
                   kDefaultTimeout);
    callee()->data_channel()->Send(DataBuffer(data));
    ASSERT_EQ_WAIT(data, caller()->data_observer()->last_message(),
                   kDefaultTimeout);
  }
}

// This test verifies that lowering the MTU of the connection will cause
// the datachannel to not transmit reliably.
// The purpose of this test is to ensure that we know how a too-small MTU
// error manifests itself.
TEST_P(DataChannelIntegrationTest, EndToEndCallWithSctpDataChannelHarmfulMtu) {
  // The lowest payload size limit that's tested and found safe for this
  // application in this configuration (see test above).
  const size_t kLowestSafePayloadSizeLimit = 1225;
  // The size of the smallest message that fails to be delivered.
  const size_t kMessageSizeThatIsNotDelivered = 1157;

  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_NE(nullptr, caller()->data_channel());
  ASSERT_TRUE_WAIT(callee()->data_channel() != nullptr, kDefaultTimeout);
  EXPECT_TRUE_WAIT(caller()->data_observer()->IsOpen(), kDefaultTimeout);
  EXPECT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);

  virtual_socket_server()->set_max_udp_payload(kLowestSafePayloadSizeLimit - 1);
  // Probe for an undelivered or slowly delivered message. The exact
  // size limit seems to be dependent on the message history, so make the
  // code easily able to find the current value.
  bool failure_seen = false;
  for (size_t message_size = 1110; message_size < 1400; message_size++) {
    const size_t message_count =
        callee()->data_observer()->received_message_count();
    const std::string data(message_size, 'a');
    caller()->data_channel()->Send(DataBuffer(data));
    // Wait a very short time for the message to be delivered.
    // Note: Waiting only 10 ms is too short for Windows bots; they will
    // flakily fail at a random frame.
    WAIT(callee()->data_observer()->received_message_count() > message_count,
         100);
    if (callee()->data_observer()->received_message_count() == message_count) {
      ASSERT_EQ(kMessageSizeThatIsNotDelivered, message_size);
      failure_seen = true;
      break;
    }
  }
  ASSERT_TRUE(failure_seen);
}

// Ensure that when the callee closes an SCTP data channel, the closing
// procedure results in the data channel being closed for the caller as well.
TEST_P(DataChannelIntegrationTest, CalleeClosesSctpDataChannel) {
  // Same procedure as above test.
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->CreateDataChannel();
  if (allow_media()) {
    caller()->AddAudioVideoTracks();
    callee()->AddAudioVideoTracks();
  }
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_NE(nullptr, caller()->data_channel());
  ASSERT_TRUE_WAIT(callee()->data_channel() != nullptr, kDefaultTimeout);
  ASSERT_TRUE_WAIT(caller()->data_observer()->IsOpen(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);

  // Close the data channel on the callee side, and wait for it to reach the
  // "closed" state on both sides.
  callee()->data_channel()->Close();

  DataChannelInterface::DataState expected_states[] = {
      DataChannelInterface::DataState::kConnecting,
      DataChannelInterface::DataState::kOpen,
      DataChannelInterface::DataState::kClosing,
      DataChannelInterface::DataState::kClosed};

  EXPECT_EQ_WAIT(DataChannelInterface::DataState::kClosed,
                 caller()->data_observer()->state(), kDefaultTimeout);
  EXPECT_THAT(caller()->data_observer()->states(),
              ::testing::ElementsAreArray(expected_states));

  EXPECT_EQ_WAIT(DataChannelInterface::DataState::kClosed,
                 callee()->data_observer()->state(), kDefaultTimeout);
  EXPECT_THAT(callee()->data_observer()->states(),
              ::testing::ElementsAreArray(expected_states));
}

TEST_P(DataChannelIntegrationTest, SctpDataChannelConfigSentToOtherSide) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  DataChannelInit init;
  init.id = 53;
  init.maxRetransmits = 52;
  caller()->CreateDataChannel("data-channel", &init);
  if (allow_media()) {
    caller()->AddAudioVideoTracks();
    callee()->AddAudioVideoTracks();
  }
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_channel() != nullptr, kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);
  // Since "negotiated" is false, the "id" parameter should be ignored.
  EXPECT_NE(init.id, callee()->data_channel()->id());
  EXPECT_EQ("data-channel", callee()->data_channel()->label());
  EXPECT_EQ(init.maxRetransmits, callee()->data_channel()->maxRetransmits());
  EXPECT_FALSE(callee()->data_channel()->negotiated());
}

// Test sctp's ability to process unordered data stream, where data actually
// arrives out of order using simulated delays. Previously there have been some
// bugs in this area.
TEST_P(DataChannelIntegrationTest, StressTestUnorderedSctpDataChannel) {
  // Introduce random network delays.
  // Otherwise it's not a true "unordered" test.
  virtual_socket_server()->set_delay_mean(20);
  virtual_socket_server()->set_delay_stddev(5);
  virtual_socket_server()->UpdateDelayDistribution();
  // Normal procedure, but with unordered data channel config.
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  DataChannelInit init;
  init.ordered = false;
  caller()->CreateDataChannel(&init);
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_NE(nullptr, caller()->data_channel());
  ASSERT_TRUE_WAIT(callee()->data_channel() != nullptr, kDefaultTimeout);
  ASSERT_TRUE_WAIT(caller()->data_observer()->IsOpen(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);

  static constexpr int kNumMessages = 100;
  // Deliberately chosen to be larger than the MTU so messages get fragmented.
  static constexpr size_t kMaxMessageSize = 4096;
  // Create and send random messages.
  std::vector<std::string> sent_messages;
  for (int i = 0; i < kNumMessages; ++i) {
    size_t length =
        (rand() % kMaxMessageSize) + 1;  // NOLINT (rand_r instead of rand)
    std::string message;
    ASSERT_TRUE(rtc::CreateRandomString(length, &message));
    caller()->data_channel()->Send(DataBuffer(message));
    callee()->data_channel()->Send(DataBuffer(message));
    sent_messages.push_back(message);
  }

  // Wait for all messages to be received.
  EXPECT_EQ_WAIT(rtc::checked_cast<size_t>(kNumMessages),
                 caller()->data_observer()->received_message_count(),
                 kDefaultTimeout);
  EXPECT_EQ_WAIT(rtc::checked_cast<size_t>(kNumMessages),
                 callee()->data_observer()->received_message_count(),
                 kDefaultTimeout);

  // Sort and compare to make sure none of the messages were corrupted.
  std::vector<std::string> caller_received_messages;
  absl::c_transform(caller()->data_observer()->messages(),
                    std::back_inserter(caller_received_messages),
                    [](const auto& a) { return a.data; });

  std::vector<std::string> callee_received_messages;
  absl::c_transform(callee()->data_observer()->messages(),
                    std::back_inserter(callee_received_messages),
                    [](const auto& a) { return a.data; });

  absl::c_sort(sent_messages);
  absl::c_sort(caller_received_messages);
  absl::c_sort(callee_received_messages);
  EXPECT_EQ(sent_messages, caller_received_messages);
  EXPECT_EQ(sent_messages, callee_received_messages);
}

// Repeatedly open and close data channels on a peer connection to check that
// the channels are properly negotiated and SCTP stream IDs properly recycled.
TEST_P(DataChannelIntegrationTest, StressTestOpenCloseChannelNoDelay) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();

  int channel_id = 0;
  const size_t kChannelCount = 8;
  const size_t kIterations = 10;
  bool has_negotiated = false;

  DataChannelInit init;
  for (size_t repeats = 0; repeats < kIterations; ++repeats) {
    RTC_LOG(LS_INFO) << "Iteration " << (repeats + 1) << "/" << kIterations;

    for (size_t i = 0; i < kChannelCount; ++i) {
      rtc::StringBuilder sb;
      sb << "channel-" << channel_id++;
      caller()->CreateDataChannel(sb.Release(), &init);
    }
    ASSERT_EQ(caller()->data_channels().size(), kChannelCount);

    if (!has_negotiated) {
      caller()->CreateAndSetAndSignalOffer();
      ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
      has_negotiated = true;
    }

    for (size_t i = 0; i < kChannelCount; ++i) {
      ASSERT_EQ_WAIT(caller()->data_channels()[i]->state(),
                     DataChannelInterface::DataState::kOpen, kDefaultTimeout);
      RTC_LOG(LS_INFO) << "Caller Channel "
                       << caller()->data_channels()[i]->label() << " with id "
                       << caller()->data_channels()[i]->id() << " is open.";
    }
    ASSERT_EQ_WAIT(callee()->data_channels().size(), kChannelCount,
                   kDefaultTimeout);
    for (size_t i = 0; i < kChannelCount; ++i) {
      ASSERT_EQ_WAIT(callee()->data_channels()[i]->state(),
                     DataChannelInterface::DataState::kOpen, kDefaultTimeout);
      RTC_LOG(LS_INFO) << "Callee Channel "
                       << callee()->data_channels()[i]->label() << " with id "
                       << callee()->data_channels()[i]->id() << " is open.";
    }

    // Closing from both sides to attempt creating races.
    // A real application would likely only close from one side.
    for (size_t i = 0; i < kChannelCount; ++i) {
      if (i % 3 == 0) {
        callee()->data_channels()[i]->Close();
        caller()->data_channels()[i]->Close();
      } else {
        caller()->data_channels()[i]->Close();
        callee()->data_channels()[i]->Close();
      }
    }

    for (size_t i = 0; i < kChannelCount; ++i) {
      ASSERT_EQ_WAIT(caller()->data_channels()[i]->state(),
                     DataChannelInterface::DataState::kClosed, kDefaultTimeout);
      ASSERT_EQ_WAIT(callee()->data_channels()[i]->state(),
                     DataChannelInterface::DataState::kClosed, kDefaultTimeout);
    }

    caller()->data_channels().clear();
    caller()->data_observers().clear();
    callee()->data_channels().clear();
    callee()->data_observers().clear();
  }
}

// Repeatedly open and close data channels on a peer connection to check that
// the channels are properly negotiated and SCTP stream IDs properly recycled.
// Some delay is added for better coverage.
TEST_P(DataChannelIntegrationTest, StressTestOpenCloseChannelWithDelay) {
  // Simulate some network delay
  virtual_socket_server()->set_delay_mean(20);
  virtual_socket_server()->set_delay_stddev(5);
  virtual_socket_server()->UpdateDelayDistribution();

  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();

  int channel_id = 0;
  const size_t kChannelCount = 8;
  const size_t kIterations = 10;
  bool has_negotiated = false;

  DataChannelInit init;
  for (size_t repeats = 0; repeats < kIterations; ++repeats) {
    RTC_LOG(LS_INFO) << "Iteration " << (repeats + 1) << "/" << kIterations;

    for (size_t i = 0; i < kChannelCount; ++i) {
      rtc::StringBuilder sb;
      sb << "channel-" << channel_id++;
      caller()->CreateDataChannel(sb.Release(), &init);
    }
    ASSERT_EQ(caller()->data_channels().size(), kChannelCount);

    if (!has_negotiated) {
      caller()->CreateAndSetAndSignalOffer();
      ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
      has_negotiated = true;
    }

    for (size_t i = 0; i < kChannelCount; ++i) {
      ASSERT_EQ_WAIT(caller()->data_channels()[i]->state(),
                     DataChannelInterface::DataState::kOpen, kDefaultTimeout);
      RTC_LOG(LS_INFO) << "Caller Channel "
                       << caller()->data_channels()[i]->label() << " with id "
                       << caller()->data_channels()[i]->id() << " is open.";
    }
    ASSERT_EQ_WAIT(callee()->data_channels().size(), kChannelCount,
                   kDefaultTimeout);
    for (size_t i = 0; i < kChannelCount; ++i) {
      ASSERT_EQ_WAIT(callee()->data_channels()[i]->state(),
                     DataChannelInterface::DataState::kOpen, kDefaultTimeout);
      RTC_LOG(LS_INFO) << "Callee Channel "
                       << callee()->data_channels()[i]->label() << " with id "
                       << callee()->data_channels()[i]->id() << " is open.";
    }

    // Closing from both sides to attempt creating races.
    // A real application would likely only close from one side.
    for (size_t i = 0; i < kChannelCount; ++i) {
      if (i % 3 == 0) {
        callee()->data_channels()[i]->Close();
        caller()->data_channels()[i]->Close();
      } else {
        caller()->data_channels()[i]->Close();
        callee()->data_channels()[i]->Close();
      }
    }

    for (size_t i = 0; i < kChannelCount; ++i) {
      ASSERT_EQ_WAIT(caller()->data_channels()[i]->state(),
                     DataChannelInterface::DataState::kClosed, kDefaultTimeout);
      ASSERT_EQ_WAIT(callee()->data_channels()[i]->state(),
                     DataChannelInterface::DataState::kClosed, kDefaultTimeout);
    }

    caller()->data_channels().clear();
    caller()->data_observers().clear();
    callee()->data_channels().clear();
    callee()->data_observers().clear();
  }
}

// This test sets up a call between two parties with audio, and video. When
// audio and video are setup and flowing, an SCTP data channel is negotiated.
TEST_P(DataChannelIntegrationTest, AddSctpDataChannelInSubsequentOffer) {
  // This test can't be performed without media.
  if (!allow_media()) {
    return;
  }
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  // Do initial offer/answer with audio/video.
  caller()->AddAudioVideoTracks();
  callee()->AddAudioVideoTracks();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  // Create data channel and do new offer and answer.
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  // Caller data channel should already exist (it created one). Callee data
  // channel may not exist yet, since negotiation happens in-band, not in SDP.
  ASSERT_NE(nullptr, caller()->data_channel());
  ASSERT_TRUE_WAIT(callee()->data_channel() != nullptr, kDefaultTimeout);
  EXPECT_TRUE_WAIT(caller()->data_observer()->IsOpen(), kDefaultTimeout);
  EXPECT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);
  // Ensure data can be sent in both directions.
  std::string data = "hello world";
  caller()->data_channel()->Send(DataBuffer(data));
  EXPECT_EQ_WAIT(data, callee()->data_observer()->last_message(),
                 kDefaultTimeout);
  callee()->data_channel()->Send(DataBuffer(data));
  EXPECT_EQ_WAIT(data, caller()->data_observer()->last_message(),
                 kDefaultTimeout);
}

// Set up a connection initially just using SCTP data channels, later
// upgrading to audio/video, ensuring frames are received end-to-end.
// Effectively the inverse of the test above. This was broken in M57; see
// https://crbug.com/711243
TEST_P(DataChannelIntegrationTest, SctpDataChannelToAudioVideoUpgrade) {
  // This test can't be performed without media.
  if (!allow_media()) {
    return;
  }
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  // Do initial offer/answer with just data channel.
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  // Wait until data can be sent over the data channel.
  ASSERT_TRUE_WAIT(callee()->data_channel() != nullptr, kDefaultTimeout);
  ASSERT_TRUE_WAIT(caller()->data_observer()->IsOpen(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);

  // Do subsequent offer/answer with two-way audio and video. Audio and video
  // should end up bundled on the DTLS/ICE transport already used for data.
  caller()->AddAudioVideoTracks();
  callee()->AddAudioVideoTracks();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  MediaExpectations media_expectations;
  media_expectations.ExpectBidirectionalAudioAndVideo();
  ASSERT_TRUE(ExpectNewFrames(media_expectations));
}

static void MakeSpecCompliantSctpOffer(cricket::SessionDescription* desc) {
  cricket::SctpDataContentDescription* dcd_offer =
      GetFirstSctpDataContentDescription(desc);
  // See https://crbug.com/webrtc/11211 - this function is a no-op
  ASSERT_TRUE(dcd_offer);
  dcd_offer->set_use_sctpmap(false);
  dcd_offer->set_protocol("UDP/DTLS/SCTP");
}

// Test that the data channel works when a spec-compliant SCTP m= section is
// offered (using "a=sctp-port" instead of "a=sctpmap", and using
// "UDP/DTLS/SCTP" as the protocol).
TEST_P(DataChannelIntegrationTest,
       DataChannelWorksWhenSpecCompliantSctpOfferReceived) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->CreateDataChannel();
  caller()->SetGeneratedSdpMunger(MakeSpecCompliantSctpOffer);
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_channel() != nullptr, kDefaultTimeout);
  EXPECT_TRUE_WAIT(caller()->data_observer()->IsOpen(), kDefaultTimeout);
  EXPECT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);

  // Ensure data can be sent in both directions.
  std::string data = "hello world";
  caller()->data_channel()->Send(DataBuffer(data));
  EXPECT_EQ_WAIT(data, callee()->data_observer()->last_message(),
                 kDefaultTimeout);
  callee()->data_channel()->Send(DataBuffer(data));
  EXPECT_EQ_WAIT(data, caller()->data_observer()->last_message(),
                 kDefaultTimeout);
}

// Test that after closing PeerConnections, they stop sending any packets
// (ICE, DTLS, RTP...).
TEST_P(DataChannelIntegrationTest, ClosingConnectionStopsPacketFlow) {
  // This test can't be performed without media.
  if (!allow_media()) {
    return;
  }
  // Set up audio/video/data, wait for some frames to be received.
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->AddAudioVideoTracks();
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  MediaExpectations media_expectations;
  media_expectations.CalleeExpectsSomeAudioAndVideo();
  ASSERT_TRUE(ExpectNewFrames(media_expectations));
  // Close PeerConnections.
  ClosePeerConnections();
  // Pump messages for a second, and ensure no new packets end up sent.
  uint32_t sent_packets_a = virtual_socket_server()->sent_packets();
  WAIT(false, 1000);
  uint32_t sent_packets_b = virtual_socket_server()->sent_packets();
  EXPECT_EQ(sent_packets_a, sent_packets_b);
}

TEST_P(DataChannelIntegrationTest, DtlsRoleIsSetNormally) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->CreateDataChannel();
  ASSERT_FALSE(caller()->pc()->GetSctpTransport());
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(caller()->data_observer()->IsOpen(), kDefaultTimeout);
  ASSERT_TRUE(caller()->pc()->GetSctpTransport());
  ASSERT_TRUE(
      caller()->pc()->GetSctpTransport()->Information().dtls_transport());
  EXPECT_TRUE(caller()
                  ->pc()
                  ->GetSctpTransport()
                  ->Information()
                  .dtls_transport()
                  ->Information()
                  .role());
  EXPECT_EQ(caller()
                ->pc()
                ->GetSctpTransport()
                ->Information()
                .dtls_transport()
                ->Information()
                .role(),
            DtlsTransportTlsRole::kServer);
  EXPECT_EQ(callee()
                ->pc()
                ->GetSctpTransport()
                ->Information()
                .dtls_transport()
                ->Information()
                .role(),
            DtlsTransportTlsRole::kClient);
  // ID should be assigned according to the odd/even rule based on role;
  // client gets even numbers, server gets odd ones. RFC 8832 section 6.
  // TODO(hta): Test multiple channels.
  EXPECT_EQ(caller()->data_channel()->id(), 1);
}

TEST_P(DataChannelIntegrationTest, DtlsRoleIsSetWhenReversed) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->CreateDataChannel();
  callee()->SetReceivedSdpMunger(MakeActiveSctpOffer);
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(caller()->data_observer()->IsOpen(), kDefaultTimeout);
  EXPECT_TRUE(caller()
                  ->pc()
                  ->GetSctpTransport()
                  ->Information()
                  .dtls_transport()
                  ->Information()
                  .role());
  EXPECT_EQ(caller()
                ->pc()
                ->GetSctpTransport()
                ->Information()
                .dtls_transport()
                ->Information()
                .role(),
            DtlsTransportTlsRole::kClient);
  EXPECT_EQ(callee()
                ->pc()
                ->GetSctpTransport()
                ->Information()
                .dtls_transport()
                ->Information()
                .role(),
            DtlsTransportTlsRole::kServer);
  // ID should be assigned according to the odd/even rule based on role;
  // client gets even numbers, server gets odd ones. RFC 8832 section 6.
  // TODO(hta): Test multiple channels.
  EXPECT_EQ(caller()->data_channel()->id(), 0);
}

TEST_P(DataChannelIntegrationTest,
       DtlsRoleIsSetWhenReversedWithChannelCollision) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->CreateDataChannel();

  callee()->SetReceivedSdpMunger([this](cricket::SessionDescription* desc) {
    MakeActiveSctpOffer(desc);
    callee()->CreateDataChannel();
  });
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(caller()->data_observer()->IsOpen(), kDefaultTimeout);
  ASSERT_EQ_WAIT(callee()->data_channels().size(), 2U, kDefaultTimeout);
  ASSERT_EQ_WAIT(caller()->data_channels().size(), 2U, kDefaultTimeout);
  EXPECT_TRUE(caller()
                  ->pc()
                  ->GetSctpTransport()
                  ->Information()
                  .dtls_transport()
                  ->Information()
                  .role());
  EXPECT_EQ(caller()
                ->pc()
                ->GetSctpTransport()
                ->Information()
                .dtls_transport()
                ->Information()
                .role(),
            DtlsTransportTlsRole::kClient);
  EXPECT_EQ(callee()
                ->pc()
                ->GetSctpTransport()
                ->Information()
                .dtls_transport()
                ->Information()
                .role(),
            DtlsTransportTlsRole::kServer);
  // ID should be assigned according to the odd/even rule based on role;
  // client gets even numbers, server gets odd ones. RFC 8832 section 6.
  ASSERT_EQ(caller()->data_channels().size(), 2U);
  ASSERT_EQ(callee()->data_channels().size(), 2U);
  EXPECT_EQ(caller()->data_channels()[0]->id(), 0);
  EXPECT_EQ(caller()->data_channels()[1]->id(), 1);
  EXPECT_EQ(callee()->data_channels()[0]->id(), 1);
  EXPECT_EQ(callee()->data_channels()[1]->id(), 0);
}

// Test that transport stats are generated by the RTCStatsCollector for a
// connection that only involves data channels. This is a regression test for
// crbug.com/826972.
TEST_P(DataChannelIntegrationTest,
       TransportStatsReportedForDataChannelOnlyConnection) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->CreateDataChannel();

  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_channel(), kDefaultTimeout);

  auto caller_report = caller()->NewGetStats();
  EXPECT_EQ(1u, caller_report->GetStatsOfType<RTCTransportStats>().size());
  auto callee_report = callee()->NewGetStats();
  EXPECT_EQ(1u, callee_report->GetStatsOfType<RTCTransportStats>().size());
}

TEST_P(DataChannelIntegrationTest, QueuedPacketsGetDeliveredInReliableMode) {
  CreatePeerConnectionWrappers();
  ConnectFakeSignaling();
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_channel(), kDefaultTimeout);

  caller()->data_channel()->Send(DataBuffer("hello first"));
  ASSERT_EQ_WAIT(1u, callee()->data_observer()->received_message_count(),
                 kDefaultTimeout);
  // Cause a temporary network outage
  virtual_socket_server()->set_drop_probability(1.0);
  for (int i = 1; i <= 10; i++) {
    caller()->data_channel()->Send(DataBuffer("Sent while blocked"));
  }
  // Nothing should be delivered during outage. Short wait.
  EXPECT_EQ_WAIT(1u, callee()->data_observer()->received_message_count(), 10);
  // Reverse outage
  virtual_socket_server()->set_drop_probability(0.0);
  // All packets should be delivered.
  EXPECT_EQ_WAIT(11u, callee()->data_observer()->received_message_count(),
                 kDefaultTimeout);
}

TEST_P(DataChannelIntegrationTest, QueuedPacketsGetDroppedInUnreliableMode) {
  CreatePeerConnectionWrappers();
  ConnectFakeSignaling();
  DataChannelInit init;
  init.maxRetransmits = 0;
  init.ordered = false;
  caller()->CreateDataChannel(&init);
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_channel(), kDefaultTimeout);
  caller()->data_channel()->Send(DataBuffer("hello first"));
  ASSERT_EQ_WAIT(1u, callee()->data_observer()->received_message_count(),
                 kDefaultTimeout);
  // Cause a temporary network outage
  virtual_socket_server()->set_drop_probability(1.0);
  // Send a few packets. Note that all get dropped only when all packets
  // fit into the receiver receive window/congestion window, so that they
  // actually get sent.
  for (int i = 1; i <= 10; i++) {
    caller()->data_channel()->Send(DataBuffer("Sent while blocked"));
  }
  // Nothing should be delivered during outage.
  // We do a short wait to verify that delivery count is still 1.
  WAIT(false, 10);
  EXPECT_EQ(1u, callee()->data_observer()->received_message_count());
  // Reverse the network outage.
  virtual_socket_server()->set_drop_probability(0.0);
  // Send a new packet, and wait for it to be delivered.
  caller()->data_channel()->Send(DataBuffer("After block"));
  EXPECT_EQ_WAIT("After block", callee()->data_observer()->last_message(),
                 kDefaultTimeout);
  // Some messages should be lost, but first and last message should have
  // been delivered.
  // First, check that the protocol guarantee is preserved.
  EXPECT_GT(11u, callee()->data_observer()->received_message_count());
  EXPECT_LE(2u, callee()->data_observer()->received_message_count());
  // Then, check that observed behavior (lose all messages) has not changed
  EXPECT_EQ(2u, callee()->data_observer()->received_message_count());
}

TEST_P(DataChannelIntegrationTest,
       QueuedPacketsGetDroppedInLifetimeLimitedMode) {
  CreatePeerConnectionWrappers();
  ConnectFakeSignaling();
  DataChannelInit init;
  init.maxRetransmitTime = 1;
  init.ordered = false;
  caller()->CreateDataChannel(&init);
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_channel(), kDefaultTimeout);
  caller()->data_channel()->Send(DataBuffer("hello first"));
  ASSERT_EQ_WAIT(1u, callee()->data_observer()->received_message_count(),
                 kDefaultTimeout);
  // Cause a temporary network outage
  virtual_socket_server()->set_drop_probability(1.0);
  for (int i = 1; i <= 200; i++) {
    caller()->data_channel()->Send(DataBuffer("Sent while blocked"));
  }
  // Nothing should be delivered during outage.
  // We do a short wait to verify that delivery count is still 1,
  // and to make sure max packet lifetime (which is in ms) is exceeded.
  WAIT(false, 10);
  EXPECT_EQ(1u, callee()->data_observer()->received_message_count());
  // Reverse the network outage.
  virtual_socket_server()->set_drop_probability(0.0);
  // Send a new packet, and wait for it to be delivered.
  caller()->data_channel()->Send(DataBuffer("After block"));
  EXPECT_EQ_WAIT("After block", callee()->data_observer()->last_message(),
                 kDefaultTimeout);
  // Some messages should be lost, but first and last message should have
  // been delivered.
  // First, check that the protocol guarantee is preserved.
  EXPECT_GT(202u, callee()->data_observer()->received_message_count());
  EXPECT_LE(2u, callee()->data_observer()->received_message_count());
  // Then, check that observed behavior (lose some messages) has not changed
  // DcSctp loses all messages. This is correct.
  EXPECT_EQ(2u, callee()->data_observer()->received_message_count());
}

TEST_P(DataChannelIntegrationTest,
       DISABLED_ON_ANDROID(SomeQueuedPacketsGetDroppedInMaxRetransmitsMode)) {
  CreatePeerConnectionWrappers();
  ConnectFakeSignaling();
  DataChannelInit init;
  init.maxRetransmits = 0;
  init.ordered = false;
  caller()->CreateDataChannel(&init);
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_channel(), kDefaultTimeout);
  caller()->data_channel()->Send(DataBuffer("hello first"));
  ASSERT_EQ_WAIT(1u, callee()->data_observer()->received_message_count(),
                 kDefaultTimeout);
  // Cause a temporary network outage
  virtual_socket_server()->set_drop_probability(1.0);
  // Fill the buffer until queued data starts to build
  size_t packet_counter = 0;
  while (caller()->data_channel()->buffered_amount() < 1 &&
         packet_counter < 10000) {
    packet_counter++;
    caller()->data_channel()->Send(DataBuffer("Sent while blocked"));
  }
  if (caller()->data_channel()->buffered_amount()) {
    RTC_LOG(LS_INFO) << "Buffered data after " << packet_counter << " packets";
  } else {
    RTC_LOG(LS_INFO) << "No buffered data after " << packet_counter
                     << " packets";
  }
  // Nothing should be delivered during outage.
  // We do a short wait to verify that delivery count is still 1.
  WAIT(false, 10);
  EXPECT_EQ(1u, callee()->data_observer()->received_message_count());
  // Reverse the network outage.
  virtual_socket_server()->set_drop_probability(0.0);
  // Send a new packet, and wait for it to be delivered.
  caller()->data_channel()->Send(DataBuffer("After block"));
  EXPECT_EQ_WAIT("After block", callee()->data_observer()->last_message(),
                 kDefaultTimeout);
  // Some messages should be lost, but first and last message should have
  // been delivered.
  // Due to the fact that retransmissions are only counted when the packet
  // goes on the wire, NOT when they are stalled in queue due to
  // congestion, we expect some of the packets to be delivered, because
  // congestion prevented them from being sent.
  // Citation: https://tools.ietf.org/html/rfc7496#section-3.1

  // First, check that the protocol guarantee is preserved.
  EXPECT_GT(packet_counter,
            callee()->data_observer()->received_message_count());
  EXPECT_LE(2u, callee()->data_observer()->received_message_count());
  // Then, check that observed behavior (lose between 100 and 200 messages)
  // has not changed.
  // Usrsctp behavior is different on Android (177) and other platforms (122).
  // Dcsctp loses 432 packets.
  EXPECT_GT(2 + packet_counter - 100,
            callee()->data_observer()->received_message_count());
  EXPECT_LT(2 + packet_counter - 500,
            callee()->data_observer()->received_message_count());
}

INSTANTIATE_TEST_SUITE_P(DataChannelIntegrationTest,
                         DataChannelIntegrationTest,
                         Combine(Values(SdpSemantics::kPlanB_DEPRECATED,
                                        SdpSemantics::kUnifiedPlan),
                                 testing::Bool()));

TEST_F(DataChannelIntegrationTestUnifiedPlan,
       EndToEndCallWithBundledSctpDataChannel) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->CreateDataChannel();
  caller()->AddAudioVideoTracks();
  callee()->AddAudioVideoTracks();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(caller()->pc()->GetSctpTransport(), kDefaultTimeout);
  ASSERT_EQ_WAIT(SctpTransportState::kConnected,
                 caller()->pc()->GetSctpTransport()->Information().state(),
                 kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_channel(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);
}

TEST_F(DataChannelIntegrationTestUnifiedPlan,
       EndToEndCallWithDataChannelOnlyConnects) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_channel(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);
  ASSERT_TRUE(caller()->data_observer()->IsOpen());
}

TEST_F(DataChannelIntegrationTestUnifiedPlan, DataChannelClosesWhenClosed) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);
  caller()->data_channel()->Close();
  ASSERT_TRUE_WAIT(!callee()->data_observer()->IsOpen(), kDefaultTimeout);
}

TEST_F(DataChannelIntegrationTestUnifiedPlan,
       DataChannelClosesWhenClosedReverse) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);
  callee()->data_channel()->Close();
  ASSERT_TRUE_WAIT(!caller()->data_observer()->IsOpen(), kDefaultTimeout);
}

TEST_F(DataChannelIntegrationTestUnifiedPlan,
       DataChannelClosesWhenPeerConnectionClosed) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->CreateDataChannel();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer(), kDefaultTimeout);
  ASSERT_TRUE_WAIT(callee()->data_observer()->IsOpen(), kDefaultTimeout);
  caller()->pc()->Close();
  ASSERT_TRUE_WAIT(!callee()->data_observer()->IsOpen(), kDefaultTimeout);
}

#endif  // WEBRTC_HAVE_SCTP

}  // namespace

}  // namespace webrtc
