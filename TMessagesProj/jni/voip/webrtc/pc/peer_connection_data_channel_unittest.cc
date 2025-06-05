/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/jsep.h"
#include "api/media_types.h"
#include "api/peer_connection_interface.h"
#include "api/scoped_refptr.h"
#include "api/sctp_transport_interface.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "api/task_queue/task_queue_factory.h"
#include "api/transport/sctp_transport_factory_interface.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/port_allocator.h"
#include "pc/media_session.h"
#include "pc/peer_connection.h"
#include "pc/peer_connection_proxy.h"
#include "pc/peer_connection_wrapper.h"
#include "pc/sctp_transport.h"
#include "pc/sdp_utils.h"
#include "pc/session_description.h"
#include "pc/test/enable_fake_media.h"
#include "pc/test/mock_peer_connection_observers.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "rtc_base/thread.h"
#include "test/gmock.h"
#include "test/gtest.h"
#ifdef WEBRTC_ANDROID
#include "pc/test/android_test_initializer.h"
#endif
#include "rtc_base/virtual_socket_server.h"
#include "test/pc/sctp/fake_sctp_transport.h"

namespace webrtc {

using RTCConfiguration = PeerConnectionInterface::RTCConfiguration;
using RTCOfferAnswerOptions = PeerConnectionInterface::RTCOfferAnswerOptions;
using ::testing::HasSubstr;
using ::testing::Not;
using ::testing::Values;

namespace {

PeerConnectionFactoryDependencies CreatePeerConnectionFactoryDependencies() {
  PeerConnectionFactoryDependencies deps;
  deps.network_thread = rtc::Thread::Current();
  deps.worker_thread = rtc::Thread::Current();
  deps.signaling_thread = rtc::Thread::Current();
  deps.task_queue_factory = CreateDefaultTaskQueueFactory();
  EnableFakeMedia(deps);
  deps.sctp_factory = std::make_unique<FakeSctpTransportFactory>();
  return deps;
}

}  // namespace

class PeerConnectionWrapperForDataChannelTest : public PeerConnectionWrapper {
 public:
  using PeerConnectionWrapper::PeerConnectionWrapper;

  FakeSctpTransportFactory* sctp_transport_factory() {
    return sctp_transport_factory_;
  }

  void set_sctp_transport_factory(
      FakeSctpTransportFactory* sctp_transport_factory) {
    sctp_transport_factory_ = sctp_transport_factory;
  }

  absl::optional<std::string> sctp_mid() {
    return GetInternalPeerConnection()->sctp_mid();
  }

  absl::optional<std::string> sctp_transport_name() {
    return GetInternalPeerConnection()->sctp_transport_name();
  }

  PeerConnection* GetInternalPeerConnection() {
    auto* pci =
        static_cast<PeerConnectionProxyWithInternal<PeerConnectionInterface>*>(
            pc());
    return static_cast<PeerConnection*>(pci->internal());
  }

 private:
  FakeSctpTransportFactory* sctp_transport_factory_ = nullptr;
};

class PeerConnectionDataChannelBaseTest : public ::testing::Test {
 protected:
  typedef std::unique_ptr<PeerConnectionWrapperForDataChannelTest> WrapperPtr;

  explicit PeerConnectionDataChannelBaseTest(SdpSemantics sdp_semantics)
      : vss_(new rtc::VirtualSocketServer()),
        main_(vss_.get()),
        sdp_semantics_(sdp_semantics) {
#ifdef WEBRTC_ANDROID
    InitializeAndroidObjects();
#endif
  }

  WrapperPtr CreatePeerConnection() {
    return CreatePeerConnection(RTCConfiguration());
  }

  WrapperPtr CreatePeerConnection(const RTCConfiguration& config) {
    return CreatePeerConnection(config,
                                PeerConnectionFactoryInterface::Options());
  }

  WrapperPtr CreatePeerConnection(
      const RTCConfiguration& config,
      const PeerConnectionFactoryInterface::Options factory_options) {
    auto factory_deps = CreatePeerConnectionFactoryDependencies();
    FakeSctpTransportFactory* fake_sctp_transport_factory =
        static_cast<FakeSctpTransportFactory*>(factory_deps.sctp_factory.get());
    rtc::scoped_refptr<PeerConnectionFactoryInterface> pc_factory =
        CreateModularPeerConnectionFactory(std::move(factory_deps));
    pc_factory->SetOptions(factory_options);
    auto observer = std::make_unique<MockPeerConnectionObserver>();
    RTCConfiguration modified_config = config;
    modified_config.sdp_semantics = sdp_semantics_;
    auto result = pc_factory->CreatePeerConnectionOrError(
        modified_config, PeerConnectionDependencies(observer.get()));
    if (!result.ok()) {
      return nullptr;
    }

    observer->SetPeerConnectionInterface(result.value().get());
    auto wrapper = std::make_unique<PeerConnectionWrapperForDataChannelTest>(
        pc_factory, result.MoveValue(), std::move(observer));
    wrapper->set_sctp_transport_factory(fake_sctp_transport_factory);
    return wrapper;
  }

  // Accepts the same arguments as CreatePeerConnection and adds a default data
  // channel.
  template <typename... Args>
  WrapperPtr CreatePeerConnectionWithDataChannel(Args&&... args) {
    auto wrapper = CreatePeerConnection(std::forward<Args>(args)...);
    if (!wrapper) {
      return nullptr;
    }
    EXPECT_TRUE(wrapper->pc()->CreateDataChannelOrError("dc", nullptr).ok());
    return wrapper;
  }

  // Changes the SCTP data channel port on the given session description.
  void ChangeSctpPortOnDescription(cricket::SessionDescription* desc,
                                   int port) {
    auto* data_content = cricket::GetFirstDataContent(desc);
    RTC_DCHECK(data_content);
    auto* data_desc = data_content->media_description()->as_sctp();
    RTC_DCHECK(data_desc);
    data_desc->set_port(port);
  }

  std::unique_ptr<rtc::VirtualSocketServer> vss_;
  rtc::AutoSocketServerThread main_;
  const SdpSemantics sdp_semantics_;
};

class PeerConnectionDataChannelTest
    : public PeerConnectionDataChannelBaseTest,
      public ::testing::WithParamInterface<SdpSemantics> {
 protected:
  PeerConnectionDataChannelTest()
      : PeerConnectionDataChannelBaseTest(GetParam()) {}
};

class PeerConnectionDataChannelUnifiedPlanTest
    : public PeerConnectionDataChannelBaseTest {
 protected:
  PeerConnectionDataChannelUnifiedPlanTest()
      : PeerConnectionDataChannelBaseTest(SdpSemantics::kUnifiedPlan) {}
};

TEST_P(PeerConnectionDataChannelTest, InternalSctpTransportDeletedOnTeardown) {
  auto caller = CreatePeerConnectionWithDataChannel();

  ASSERT_TRUE(caller->SetLocalDescription(caller->CreateOffer()));
  EXPECT_TRUE(caller->sctp_transport_factory()->last_fake_sctp_transport());

  rtc::scoped_refptr<SctpTransportInterface> sctp_transport =
      caller->GetInternalPeerConnection()->GetSctpTransport();

  caller.reset();
  EXPECT_EQ(static_cast<SctpTransport*>(sctp_transport.get())->internal(),
            nullptr);
}

// Test that sctp_mid/sctp_transport_name (used for stats) are correct
// before and after BUNDLE is negotiated.
TEST_P(PeerConnectionDataChannelTest, SctpContentAndTransportNameSetCorrectly) {
  auto caller = CreatePeerConnection();
  auto callee = CreatePeerConnection();

  // Initially these fields should be empty.
  EXPECT_FALSE(caller->sctp_mid());
  EXPECT_FALSE(caller->sctp_transport_name());

  // Create offer with audio/video/data.
  // Default bundle policy is "balanced", so data should be using its own
  // transport.
  caller->AddAudioTrack("a");
  caller->AddVideoTrack("v");
  caller->pc()->CreateDataChannelOrError("dc", nullptr);

  auto offer = caller->CreateOffer();
  const auto& offer_contents = offer->description()->contents();
  ASSERT_EQ(cricket::MEDIA_TYPE_AUDIO,
            offer_contents[0].media_description()->type());
  std::string audio_mid = offer_contents[0].name;
  ASSERT_EQ(cricket::MEDIA_TYPE_DATA,
            offer_contents[2].media_description()->type());
  std::string data_mid = offer_contents[2].name;

  ASSERT_TRUE(
      caller->SetLocalDescription(CloneSessionDescription(offer.get())));
  ASSERT_TRUE(callee->SetRemoteDescription(std::move(offer)));

  ASSERT_TRUE(caller->sctp_mid());
  EXPECT_EQ(data_mid, *caller->sctp_mid());
  ASSERT_TRUE(caller->sctp_transport_name());
  EXPECT_EQ(data_mid, *caller->sctp_transport_name());

  // Create answer that finishes BUNDLE negotiation, which means everything
  // should be bundled on the first transport (audio).
  RTCOfferAnswerOptions options;
  options.use_rtp_mux = true;
  ASSERT_TRUE(
      caller->SetRemoteDescription(callee->CreateAnswerAndSetAsLocal()));

  ASSERT_TRUE(caller->sctp_mid());
  EXPECT_EQ(data_mid, *caller->sctp_mid());
  ASSERT_TRUE(caller->sctp_transport_name());
  EXPECT_EQ(audio_mid, *caller->sctp_transport_name());
}

TEST_P(PeerConnectionDataChannelTest,
       CreateOfferWithNoDataChannelsGivesNoDataSection) {
  auto caller = CreatePeerConnection();
  auto offer = caller->CreateOffer();

  EXPECT_FALSE(offer->description()->GetContentByName(cricket::CN_DATA));
  EXPECT_FALSE(offer->description()->GetTransportInfoByName(cricket::CN_DATA));
}

TEST_P(PeerConnectionDataChannelTest,
       CreateAnswerWithRemoteSctpDataChannelIncludesDataSection) {
  auto caller = CreatePeerConnectionWithDataChannel();
  auto callee = CreatePeerConnection();

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));

  auto answer = callee->CreateAnswer();
  ASSERT_TRUE(answer);
  auto* data_content = cricket::GetFirstDataContent(answer->description());
  ASSERT_TRUE(data_content);
  EXPECT_FALSE(data_content->rejected);
  EXPECT_TRUE(
      answer->description()->GetTransportInfoByName(data_content->name));
}

TEST_P(PeerConnectionDataChannelTest, SctpPortPropagatedFromSdpToTransport) {
  constexpr int kNewSendPort = 9998;
  constexpr int kNewRecvPort = 7775;

  auto caller = CreatePeerConnectionWithDataChannel();
  auto callee = CreatePeerConnectionWithDataChannel();

  auto offer = caller->CreateOffer();
  ChangeSctpPortOnDescription(offer->description(), kNewSendPort);
  ASSERT_TRUE(callee->SetRemoteDescription(std::move(offer)));

  auto answer = callee->CreateAnswer();
  ChangeSctpPortOnDescription(answer->description(), kNewRecvPort);
  std::string sdp;
  answer->ToString(&sdp);
  ASSERT_TRUE(callee->SetLocalDescription(std::move(answer)));
  auto* callee_transport =
      callee->sctp_transport_factory()->last_fake_sctp_transport();
  ASSERT_TRUE(callee_transport);
  EXPECT_EQ(kNewSendPort, callee_transport->remote_port());
  EXPECT_EQ(kNewRecvPort, callee_transport->local_port());
}

TEST_P(PeerConnectionDataChannelTest, ModernSdpSyntaxByDefault) {
  PeerConnectionInterface::RTCOfferAnswerOptions options;
  auto caller = CreatePeerConnectionWithDataChannel();
  auto offer = caller->CreateOffer(options);
  EXPECT_FALSE(cricket::GetFirstSctpDataContentDescription(offer->description())
                   ->use_sctpmap());
  std::string sdp;
  offer->ToString(&sdp);
  RTC_LOG(LS_ERROR) << sdp;
  EXPECT_THAT(sdp, HasSubstr(" UDP/DTLS/SCTP webrtc-datachannel"));
  EXPECT_THAT(sdp, Not(HasSubstr("a=sctpmap:")));
}

TEST_P(PeerConnectionDataChannelTest, ObsoleteSdpSyntaxIfSet) {
  PeerConnectionInterface::RTCOfferAnswerOptions options;
  options.use_obsolete_sctp_sdp = true;
  auto caller = CreatePeerConnectionWithDataChannel();
  auto offer = caller->CreateOffer(options);
  EXPECT_TRUE(cricket::GetFirstSctpDataContentDescription(offer->description())
                  ->use_sctpmap());
  std::string sdp;
  offer->ToString(&sdp);
  EXPECT_THAT(sdp, Not(HasSubstr(" UDP/DTLS/SCTP webrtc-datachannel")));
  EXPECT_THAT(sdp, HasSubstr("a=sctpmap:"));
}

INSTANTIATE_TEST_SUITE_P(PeerConnectionDataChannelTest,
                         PeerConnectionDataChannelTest,
                         Values(SdpSemantics::kPlanB_DEPRECATED,
                                SdpSemantics::kUnifiedPlan));

}  // namespace webrtc
