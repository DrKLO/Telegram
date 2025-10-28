/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file is intended for PeerConnection integration tests that are
// slow to execute (currently defined as more than 5 seconds per test).

#include <stdint.h>

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/dtmf_sender_interface.h"
#include "api/peer_connection_interface.h"
#include "api/rtp_receiver_interface.h"
#include "api/scoped_refptr.h"
#include "api/units/time_delta.h"
#include "p2p/base/port_allocator.h"
#include "p2p/base/port_interface.h"
#include "p2p/base/stun_server.h"
#include "p2p/base/test_stun_server.h"
#include "pc/test/integration_test_helpers.h"
#include "pc/test/mock_peer_connection_observers.h"
#include "rtc_base/fake_clock.h"
#include "rtc_base/fake_network.h"
#include "rtc_base/firewall_socket_server.h"
#include "rtc_base/gunit.h"
#include "rtc_base/logging.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/ssl_certificate.h"
#include "rtc_base/test_certificate_verifier.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {

namespace {

class PeerConnectionIntegrationTest
    : public PeerConnectionIntegrationBaseTest,
      public ::testing::WithParamInterface<SdpSemantics> {
 protected:
  PeerConnectionIntegrationTest()
      : PeerConnectionIntegrationBaseTest(GetParam()) {}
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
    AdvanceTime(TimeDelta::Seconds(1000));
  }

  // Explicit handle.
  ScopedFakeClock& FakeClock() { return *this; }
};

// Ensure FakeClockForTest is constructed first (see class for rationale).
class PeerConnectionIntegrationTestWithFakeClock
    : public FakeClockForTest,
      public PeerConnectionIntegrationTest {};

class PeerConnectionIntegrationTestPlanB
    : public PeerConnectionIntegrationBaseTest {
 protected:
  PeerConnectionIntegrationTestPlanB()
      : PeerConnectionIntegrationBaseTest(SdpSemantics::kPlanB_DEPRECATED) {}
};

class PeerConnectionIntegrationTestUnifiedPlan
    : public PeerConnectionIntegrationBaseTest {
 protected:
  PeerConnectionIntegrationTestUnifiedPlan()
      : PeerConnectionIntegrationBaseTest(SdpSemantics::kUnifiedPlan) {}
};

// Test the OnFirstPacketReceived callback from audio/video RtpReceivers.  This
// includes testing that the callback is invoked if an observer is connected
// after the first packet has already been received.
TEST_P(PeerConnectionIntegrationTest,
       RtpReceiverObserverOnFirstPacketReceived) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->AddAudioVideoTracks();
  callee()->AddAudioVideoTracks();
  // Start offer/answer exchange and wait for it to complete.
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  // Should be one receiver each for audio/video.
  EXPECT_EQ(2U, caller()->rtp_receiver_observers().size());
  EXPECT_EQ(2U, callee()->rtp_receiver_observers().size());
  // Wait for all "first packet received" callbacks to be fired.
  EXPECT_TRUE_WAIT(
      absl::c_all_of(caller()->rtp_receiver_observers(),
                     [](const std::unique_ptr<MockRtpReceiverObserver>& o) {
                       return o->first_packet_received();
                     }),
      kMaxWaitForFramesMs);
  EXPECT_TRUE_WAIT(
      absl::c_all_of(callee()->rtp_receiver_observers(),
                     [](const std::unique_ptr<MockRtpReceiverObserver>& o) {
                       return o->first_packet_received();
                     }),
      kMaxWaitForFramesMs);
  // If new observers are set after the first packet was already received, the
  // callback should still be invoked.
  caller()->ResetRtpReceiverObservers();
  callee()->ResetRtpReceiverObservers();
  EXPECT_EQ(2U, caller()->rtp_receiver_observers().size());
  EXPECT_EQ(2U, callee()->rtp_receiver_observers().size());
  EXPECT_TRUE(
      absl::c_all_of(caller()->rtp_receiver_observers(),
                     [](const std::unique_ptr<MockRtpReceiverObserver>& o) {
                       return o->first_packet_received();
                     }));
  EXPECT_TRUE(
      absl::c_all_of(callee()->rtp_receiver_observers(),
                     [](const std::unique_ptr<MockRtpReceiverObserver>& o) {
                       return o->first_packet_received();
                     }));
}

class DummyDtmfObserver : public DtmfSenderObserverInterface {
 public:
  DummyDtmfObserver() : completed_(false) {}

  // Implements DtmfSenderObserverInterface.
  void OnToneChange(const std::string& tone) override {
    tones_.push_back(tone);
    if (tone.empty()) {
      completed_ = true;
    }
  }

  const std::vector<std::string>& tones() const { return tones_; }
  bool completed() const { return completed_; }

 private:
  bool completed_;
  std::vector<std::string> tones_;
};

TEST_P(PeerConnectionIntegrationTest,
       SSLCertificateVerifierFailureUsedForTurnConnectionsFailsConnection) {
  static const rtc::SocketAddress turn_server_internal_address{"88.88.88.0",
                                                               3478};
  static const rtc::SocketAddress turn_server_external_address{"88.88.88.1", 0};

  // Enable TCP-TLS for the fake turn server. We need to pass in 88.88.88.0 so
  // that host name verification passes on the fake certificate.
  CreateTurnServer(turn_server_internal_address, turn_server_external_address,
                   cricket::PROTO_TLS, "88.88.88.0");

  PeerConnectionInterface::IceServer ice_server;
  ice_server.urls.push_back("turns:88.88.88.0:3478?transport=tcp");
  ice_server.username = "test";
  ice_server.password = "test";

  PeerConnectionInterface::RTCConfiguration client_1_config;
  client_1_config.servers.push_back(ice_server);
  client_1_config.type = PeerConnectionInterface::kRelay;

  PeerConnectionInterface::RTCConfiguration client_2_config;
  client_2_config.servers.push_back(ice_server);
  // Setting the type to kRelay forces the connection to go through a TURN
  // server.
  client_2_config.type = PeerConnectionInterface::kRelay;

  // Get a copy to the pointer so we can verify calls later.
  rtc::TestCertificateVerifier* client_1_cert_verifier =
      new rtc::TestCertificateVerifier();
  client_1_cert_verifier->verify_certificate_ = false;
  rtc::TestCertificateVerifier* client_2_cert_verifier =
      new rtc::TestCertificateVerifier();
  client_2_cert_verifier->verify_certificate_ = false;

  // Create the dependencies with the test certificate verifier.
  PeerConnectionDependencies client_1_deps(nullptr);
  client_1_deps.tls_cert_verifier =
      std::unique_ptr<rtc::TestCertificateVerifier>(client_1_cert_verifier);
  PeerConnectionDependencies client_2_deps(nullptr);
  client_2_deps.tls_cert_verifier =
      std::unique_ptr<rtc::TestCertificateVerifier>(client_2_cert_verifier);

  ASSERT_TRUE(CreatePeerConnectionWrappersWithConfigAndDeps(
      client_1_config, std::move(client_1_deps), client_2_config,
      std::move(client_2_deps)));
  ConnectFakeSignaling();

  // Set "offer to receive audio/video" without adding any tracks, so we just
  // set up ICE/DTLS with no media.
  PeerConnectionInterface::RTCOfferAnswerOptions options;
  options.offer_to_receive_audio = 1;
  options.offer_to_receive_video = 1;
  caller()->SetOfferAnswerOptions(options);
  caller()->CreateAndSetAndSignalOffer();
  bool wait_res = true;
  // TODO(bugs.webrtc.org/9219): When IceConnectionState is implemented
  // properly, should be able to just wait for a state of "failed" instead of
  // waiting a fixed 10 seconds.
  WAIT_(DtlsConnected(), kDefaultTimeout, wait_res);
  ASSERT_FALSE(wait_res);

  EXPECT_GT(client_1_cert_verifier->call_count_, 0u);
  EXPECT_GT(client_2_cert_verifier->call_count_, 0u);
}

// Test that we can get capture start ntp time.
TEST_P(PeerConnectionIntegrationTest, GetCaptureStartNtpTimeWithOldStatsApi) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->AddAudioTrack();

  callee()->AddAudioTrack();

  // Do offer/answer, wait for the callee to receive some frames.
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);

  // Get the remote audio track created on the receiver, so they can be used as
  // GetStats filters.
  auto receivers = callee()->pc()->GetReceivers();
  ASSERT_EQ(1u, receivers.size());
  auto remote_audio_track = receivers[0]->track();

  // Get the audio output level stats. Note that the level is not available
  // until an RTCP packet has been received.
  EXPECT_TRUE_WAIT(callee()->OldGetStatsForTrack(remote_audio_track.get())
                           ->CaptureStartNtpTime() > 0,
                   2 * kMaxWaitForFramesMs);
}

// Test that firewalling the ICE connection causes the clients to identify the
// disconnected state and then removing the firewall causes them to reconnect.
class PeerConnectionIntegrationIceStatesTest
    : public PeerConnectionIntegrationBaseTest,
      public ::testing::WithParamInterface<
          std::tuple<SdpSemantics, std::tuple<std::string, uint32_t>>> {
 protected:
  PeerConnectionIntegrationIceStatesTest()
      : PeerConnectionIntegrationBaseTest(std::get<0>(GetParam())) {
    port_allocator_flags_ = std::get<1>(std::get<1>(GetParam()));
  }

  void StartStunServer(const SocketAddress& server_address) {
    stun_server_ = cricket::TestStunServer::Create(firewall(), server_address,
                                                   *network_thread());
  }

  bool TestIPv6() {
    return (port_allocator_flags_ & cricket::PORTALLOCATOR_ENABLE_IPV6);
  }

  void SetPortAllocatorFlags() {
    PeerConnectionIntegrationBaseTest::SetPortAllocatorFlags(
        port_allocator_flags_, port_allocator_flags_);
  }

  std::vector<SocketAddress> CallerAddresses() {
    std::vector<SocketAddress> addresses;
    addresses.push_back(SocketAddress("1.1.1.1", 0));
    if (TestIPv6()) {
      addresses.push_back(SocketAddress("1111:0:a:b:c:d:e:f", 0));
    }
    return addresses;
  }

  std::vector<SocketAddress> CalleeAddresses() {
    std::vector<SocketAddress> addresses;
    addresses.push_back(SocketAddress("2.2.2.2", 0));
    if (TestIPv6()) {
      addresses.push_back(SocketAddress("2222:0:a:b:c:d:e:f", 0));
    }
    return addresses;
  }

  void SetUpNetworkInterfaces() {
    // Remove the default interfaces added by the test infrastructure.
    caller()->network_manager()->RemoveInterface(kDefaultLocalAddress);
    callee()->network_manager()->RemoveInterface(kDefaultLocalAddress);

    // Add network addresses for test.
    for (const auto& caller_address : CallerAddresses()) {
      caller()->network_manager()->AddInterface(caller_address);
    }
    for (const auto& callee_address : CalleeAddresses()) {
      callee()->network_manager()->AddInterface(callee_address);
    }
  }

 private:
  uint32_t port_allocator_flags_;
  cricket::TestStunServer::StunServerPtr stun_server_;
};

// Ensure FakeClockForTest is constructed first (see class for rationale).
class PeerConnectionIntegrationIceStatesTestWithFakeClock
    : public FakeClockForTest,
      public PeerConnectionIntegrationIceStatesTest {};

#if !defined(THREAD_SANITIZER)
// This test provokes TSAN errors. bugs.webrtc.org/11282

// Tests that the PeerConnection goes through all the ICE gathering/connection
// states over the duration of the call. This includes Disconnected and Failed
// states, induced by putting a firewall between the peers and waiting for them
// to time out.
TEST_P(PeerConnectionIntegrationIceStatesTestWithFakeClock, VerifyIceStates) {
  const SocketAddress kStunServerAddress =
      SocketAddress("99.99.99.1", cricket::STUN_SERVER_PORT);
  StartStunServer(kStunServerAddress);

  PeerConnectionInterface::RTCConfiguration config;
  PeerConnectionInterface::IceServer ice_stun_server;
  ice_stun_server.urls.push_back(
      "stun:" + kStunServerAddress.HostAsURIString() + ":" +
      kStunServerAddress.PortAsString());
  config.servers.push_back(ice_stun_server);

  ASSERT_TRUE(CreatePeerConnectionWrappersWithConfig(config, config));
  ConnectFakeSignaling();
  SetPortAllocatorFlags();
  SetUpNetworkInterfaces();
  caller()->AddAudioVideoTracks();
  callee()->AddAudioVideoTracks();

  // Initial state before anything happens.
  ASSERT_EQ(PeerConnectionInterface::kIceGatheringNew,
            caller()->ice_gathering_state());
  ASSERT_EQ(PeerConnectionInterface::kIceConnectionNew,
            caller()->ice_connection_state());
  ASSERT_EQ(PeerConnectionInterface::kIceConnectionNew,
            caller()->standardized_ice_connection_state());

  // Start the call by creating the offer, setting it as the local description,
  // then sending it to the peer who will respond with an answer. This happens
  // asynchronously so that we can watch the states as it runs in the
  // background.
  caller()->CreateAndSetAndSignalOffer();

  ASSERT_EQ_SIMULATED_WAIT(PeerConnectionInterface::kIceConnectionCompleted,
                           caller()->ice_connection_state(), kDefaultTimeout,
                           FakeClock());
  ASSERT_EQ_SIMULATED_WAIT(PeerConnectionInterface::kIceConnectionCompleted,
                           caller()->standardized_ice_connection_state(),
                           kDefaultTimeout, FakeClock());

  // Verify that the observer was notified of the intermediate transitions.
  EXPECT_THAT(caller()->ice_connection_state_history(),
              ElementsAre(PeerConnectionInterface::kIceConnectionChecking,
                          PeerConnectionInterface::kIceConnectionConnected,
                          PeerConnectionInterface::kIceConnectionCompleted));
  EXPECT_THAT(caller()->standardized_ice_connection_state_history(),
              ElementsAre(PeerConnectionInterface::kIceConnectionChecking,
                          PeerConnectionInterface::kIceConnectionConnected,
                          PeerConnectionInterface::kIceConnectionCompleted));
  EXPECT_THAT(
      caller()->peer_connection_state_history(),
      ElementsAre(PeerConnectionInterface::PeerConnectionState::kConnecting,
                  PeerConnectionInterface::PeerConnectionState::kConnected));
  EXPECT_THAT(caller()->ice_gathering_state_history(),
              ElementsAre(PeerConnectionInterface::kIceGatheringGathering,
                          PeerConnectionInterface::kIceGatheringComplete));

  // Block connections to/from the caller and wait for ICE to become
  // disconnected.
  for (const auto& caller_address : CallerAddresses()) {
    firewall()->AddRule(false, rtc::FP_ANY, rtc::FD_ANY, caller_address);
  }
  RTC_LOG(LS_INFO) << "Firewall rules applied";
  ASSERT_EQ_SIMULATED_WAIT(PeerConnectionInterface::kIceConnectionDisconnected,
                           caller()->ice_connection_state(), kDefaultTimeout,
                           FakeClock());
  ASSERT_EQ_SIMULATED_WAIT(PeerConnectionInterface::kIceConnectionDisconnected,
                           caller()->standardized_ice_connection_state(),
                           kDefaultTimeout, FakeClock());

  // Let ICE re-establish by removing the firewall rules.
  firewall()->ClearRules();
  RTC_LOG(LS_INFO) << "Firewall rules cleared";
  ASSERT_EQ_SIMULATED_WAIT(PeerConnectionInterface::kIceConnectionCompleted,
                           caller()->ice_connection_state(), kDefaultTimeout,
                           FakeClock());
  ASSERT_EQ_SIMULATED_WAIT(PeerConnectionInterface::kIceConnectionCompleted,
                           caller()->standardized_ice_connection_state(),
                           kDefaultTimeout, FakeClock());

  // According to RFC7675, if there is no response within 30 seconds then the
  // peer should consider the other side to have rejected the connection. This
  // is signaled by the state transitioning to "failed".
  constexpr int kConsentTimeout = 30000;
  for (const auto& caller_address : CallerAddresses()) {
    firewall()->AddRule(false, rtc::FP_ANY, rtc::FD_ANY, caller_address);
  }
  RTC_LOG(LS_INFO) << "Firewall rules applied again";
  ASSERT_EQ_SIMULATED_WAIT(PeerConnectionInterface::kIceConnectionFailed,
                           caller()->ice_connection_state(), kConsentTimeout,
                           FakeClock());
  ASSERT_EQ_SIMULATED_WAIT(PeerConnectionInterface::kIceConnectionFailed,
                           caller()->standardized_ice_connection_state(),
                           kConsentTimeout, FakeClock());
}
#endif

// This test sets up a call that's transferred to a new caller with a different
// DTLS fingerprint.
TEST_P(PeerConnectionIntegrationTest, CallTransferredForCallee) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->AddAudioVideoTracks();
  callee()->AddAudioVideoTracks();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);

  // Keep the original peer around which will still send packets to the
  // receiving client. These SRTP packets will be dropped.
  std::unique_ptr<PeerConnectionIntegrationWrapper> original_peer(
      SetCallerPcWrapperAndReturnCurrent(
          CreatePeerConnectionWrapperWithAlternateKey().release()));
  // TODO(deadbeef): Why do we call Close here? That goes against the comment
  // directly above.
  original_peer->pc()->Close();

  ConnectFakeSignaling();
  caller()->AddAudioVideoTracks();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  // Wait for some additional frames to be transmitted end-to-end.
  MediaExpectations media_expectations;
  media_expectations.ExpectBidirectionalAudioAndVideo();
  ASSERT_TRUE(ExpectNewFrames(media_expectations));
}

// This test sets up a call that's transferred to a new callee with a different
// DTLS fingerprint.
TEST_P(PeerConnectionIntegrationTest, CallTransferredForCaller) {
  ASSERT_TRUE(CreatePeerConnectionWrappers());
  ConnectFakeSignaling();
  caller()->AddAudioVideoTracks();
  callee()->AddAudioVideoTracks();
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);

  // Keep the original peer around which will still send packets to the
  // receiving client. These SRTP packets will be dropped.
  std::unique_ptr<PeerConnectionIntegrationWrapper> original_peer(
      SetCalleePcWrapperAndReturnCurrent(
          CreatePeerConnectionWrapperWithAlternateKey().release()));
  // TODO(deadbeef): Why do we call Close here? That goes against the comment
  // directly above.
  original_peer->pc()->Close();

  ConnectFakeSignaling();
  callee()->AddAudioVideoTracks();
  caller()->SetOfferAnswerOptions(IceRestartOfferAnswerOptions());
  caller()->CreateAndSetAndSignalOffer();
  ASSERT_TRUE_WAIT(SignalingStateStable(), kDefaultTimeout);
  // Wait for some additional frames to be transmitted end-to-end.
  MediaExpectations media_expectations;
  media_expectations.ExpectBidirectionalAudioAndVideo();
  ASSERT_TRUE(ExpectNewFrames(media_expectations));
}

INSTANTIATE_TEST_SUITE_P(PeerConnectionIntegrationTest,
                         PeerConnectionIntegrationTest,
                         Values(SdpSemantics::kPlanB_DEPRECATED,
                                SdpSemantics::kUnifiedPlan));

constexpr uint32_t kFlagsIPv4NoStun = cricket::PORTALLOCATOR_DISABLE_TCP |
                                      cricket::PORTALLOCATOR_DISABLE_STUN |
                                      cricket::PORTALLOCATOR_DISABLE_RELAY;
constexpr uint32_t kFlagsIPv6NoStun =
    cricket::PORTALLOCATOR_DISABLE_TCP | cricket::PORTALLOCATOR_DISABLE_STUN |
    cricket::PORTALLOCATOR_ENABLE_IPV6 | cricket::PORTALLOCATOR_DISABLE_RELAY;
constexpr uint32_t kFlagsIPv4Stun =
    cricket::PORTALLOCATOR_DISABLE_TCP | cricket::PORTALLOCATOR_DISABLE_RELAY;

INSTANTIATE_TEST_SUITE_P(
    PeerConnectionIntegrationTest,
    PeerConnectionIntegrationIceStatesTestWithFakeClock,
    Combine(Values(SdpSemantics::kPlanB_DEPRECATED, SdpSemantics::kUnifiedPlan),
            Values(std::make_pair("IPv4 no STUN", kFlagsIPv4NoStun),
                   std::make_pair("IPv6 no STUN", kFlagsIPv6NoStun),
                   std::make_pair("IPv4 with STUN", kFlagsIPv4Stun))));

}  // namespace
}  // namespace webrtc
