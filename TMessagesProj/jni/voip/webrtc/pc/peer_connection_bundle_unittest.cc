/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stddef.h>

#include <cstdint>
#include <memory>
#include <ostream>
#include <string>
#include <tuple>
#include <type_traits>
#include <utility>
#include <vector>

#include "api/audio/audio_mixer.h"
#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/audio_codecs/builtin_audio_encoder_factory.h"
#include "api/candidate.h"
#include "api/create_peerconnection_factory.h"
#include "api/jsep.h"
#include "api/media_types.h"
#include "api/peer_connection_interface.h"
#include "api/rtp_receiver_interface.h"
#include "api/rtp_sender_interface.h"
#include "api/rtp_transceiver_interface.h"
#include "api/scoped_refptr.h"
#include "api/stats/rtc_stats.h"
#include "api/stats/rtc_stats_report.h"
#include "api/stats/rtcstats_objects.h"
#include "api/video_codecs/video_decoder_factory_template.h"
#include "api/video_codecs/video_decoder_factory_template_dav1d_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_libvpx_vp8_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_libvpx_vp9_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_open_h264_adapter.h"
#include "api/video_codecs/video_encoder_factory_template.h"
#include "api/video_codecs/video_encoder_factory_template_libaom_av1_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_libvpx_vp8_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_libvpx_vp9_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_open_h264_adapter.h"
#include "media/base/stream_params.h"
#include "modules/audio_device/include/audio_device.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/port.h"
#include "p2p/base/port_allocator.h"
#include "p2p/base/transport_info.h"
#include "p2p/client/basic_port_allocator.h"
#include "pc/channel.h"
#include "pc/peer_connection.h"
#include "pc/peer_connection_proxy.h"
#include "pc/peer_connection_wrapper.h"
#include "pc/rtp_transceiver.h"
#include "pc/rtp_transport_internal.h"
#include "pc/sdp_utils.h"
#include "pc/session_description.h"
#include "pc/test/integration_test_helpers.h"
#include "pc/test/mock_peer_connection_observers.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/net_helper.h"
#include "rtc_base/network.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/thread.h"
#include "test/gtest.h"
#ifdef WEBRTC_ANDROID
#include "pc/test/android_test_initializer.h"
#endif
#include "pc/test/fake_audio_capture_module.h"
#include "rtc_base/fake_network.h"
#include "rtc_base/gunit.h"
#include "rtc_base/virtual_socket_server.h"
#include "test/gmock.h"

namespace webrtc {

using BundlePolicy = PeerConnectionInterface::BundlePolicy;
using RTCConfiguration = PeerConnectionInterface::RTCConfiguration;
using RTCOfferAnswerOptions = PeerConnectionInterface::RTCOfferAnswerOptions;
using RtcpMuxPolicy = PeerConnectionInterface::RtcpMuxPolicy;
using rtc::SocketAddress;
using ::testing::Combine;
using ::testing::ElementsAre;
using ::testing::UnorderedElementsAre;
using ::testing::Values;

// TODO(steveanton): These tests should be rewritten to use the standard
// RtpSenderInterface/DtlsTransportInterface objects once they're available in
// the API. The RtpSender can be used to determine which transport a given media
// will use: https://www.w3.org/TR/webrtc/#dom-rtcrtpsender-transport
// Should also be able to remove GetTransceiversForTesting at that point.

class FakeNetworkManagerWithNoAnyNetwork : public rtc::FakeNetworkManager {
 public:
  std::vector<const rtc::Network*> GetAnyAddressNetworks() override {
    // This function allocates networks that are owned by the
    // NetworkManager. But some tests assume that they can release
    // all networks independent of the network manager.
    // In order to prevent use-after-free issues, don't allow this
    // function to have any effect when run in tests.
    RTC_LOG(LS_INFO) << "FakeNetworkManager::GetAnyAddressNetworks ignored";
    return {};
  }
};

class PeerConnectionWrapperForBundleTest : public PeerConnectionWrapper {
 public:
  using PeerConnectionWrapper::PeerConnectionWrapper;

  bool AddIceCandidateToMedia(cricket::Candidate* candidate,
                              cricket::MediaType media_type) {
    auto* desc = pc()->remote_description()->description();
    for (size_t i = 0; i < desc->contents().size(); i++) {
      const auto& content = desc->contents()[i];
      if (content.media_description()->type() == media_type) {
        candidate->set_transport_name(content.name);
        std::unique_ptr<IceCandidateInterface> jsep_candidate =
            CreateIceCandidate(content.name, i, *candidate);
        return pc()->AddIceCandidate(jsep_candidate.get());
      }
    }
    RTC_DCHECK_NOTREACHED();
    return false;
  }

  RtpTransportInternal* voice_rtp_transport() {
    return (voice_channel() ? voice_channel()->rtp_transport() : nullptr);
  }

  cricket::VoiceChannel* voice_channel() {
    auto transceivers = GetInternalPeerConnection()->GetTransceiversInternal();
    for (const auto& transceiver : transceivers) {
      if (transceiver->media_type() == cricket::MEDIA_TYPE_AUDIO) {
        return static_cast<cricket::VoiceChannel*>(
            transceiver->internal()->channel());
      }
    }
    return nullptr;
  }

  RtpTransportInternal* video_rtp_transport() {
    return (video_channel() ? video_channel()->rtp_transport() : nullptr);
  }

  cricket::VideoChannel* video_channel() {
    auto transceivers = GetInternalPeerConnection()->GetTransceiversInternal();
    for (const auto& transceiver : transceivers) {
      if (transceiver->media_type() == cricket::MEDIA_TYPE_VIDEO) {
        return static_cast<cricket::VideoChannel*>(
            transceiver->internal()->channel());
      }
    }
    return nullptr;
  }

  PeerConnection* GetInternalPeerConnection() {
    auto* pci =
        static_cast<PeerConnectionProxyWithInternal<PeerConnectionInterface>*>(
            pc());
    return static_cast<PeerConnection*>(pci->internal());
  }

  // Returns true if the stats indicate that an ICE connection is either in
  // progress or established with the given remote address.
  bool HasConnectionWithRemoteAddress(const SocketAddress& address) {
    auto report = GetStats();
    if (!report) {
      return false;
    }
    std::string matching_candidate_id;
    for (auto* ice_candidate_stats :
         report->GetStatsOfType<RTCRemoteIceCandidateStats>()) {
      if (*ice_candidate_stats->ip == address.HostAsURIString() &&
          *ice_candidate_stats->port == address.port()) {
        matching_candidate_id = ice_candidate_stats->id();
        break;
      }
    }
    if (matching_candidate_id.empty()) {
      return false;
    }
    for (auto* pair_stats :
         report->GetStatsOfType<RTCIceCandidatePairStats>()) {
      if (*pair_stats->remote_candidate_id == matching_candidate_id) {
        if (*pair_stats->state == "in-progress" ||
            *pair_stats->state == "succeeded") {
          return true;
        }
      }
    }
    return false;
  }

  rtc::FakeNetworkManager* network() { return network_; }

  void set_network(rtc::FakeNetworkManager* network) { network_ = network; }

 private:
  rtc::FakeNetworkManager* network_;
};

class PeerConnectionBundleBaseTest : public ::testing::Test {
 protected:
  typedef std::unique_ptr<PeerConnectionWrapperForBundleTest> WrapperPtr;

  explicit PeerConnectionBundleBaseTest(SdpSemantics sdp_semantics)
      : vss_(new rtc::VirtualSocketServer()),
        socket_factory_(new rtc::BasicPacketSocketFactory(vss_.get())),
        main_(vss_.get()),
        sdp_semantics_(sdp_semantics) {
#ifdef WEBRTC_ANDROID
    InitializeAndroidObjects();
#endif
    pc_factory_ = CreatePeerConnectionFactory(
        rtc::Thread::Current(), rtc::Thread::Current(), rtc::Thread::Current(),
        rtc::scoped_refptr<AudioDeviceModule>(FakeAudioCaptureModule::Create()),
        CreateBuiltinAudioEncoderFactory(), CreateBuiltinAudioDecoderFactory(),
        std::make_unique<VideoEncoderFactoryTemplate<
            LibvpxVp8EncoderTemplateAdapter, LibvpxVp9EncoderTemplateAdapter,
            OpenH264EncoderTemplateAdapter, LibaomAv1EncoderTemplateAdapter>>(),
        std::make_unique<VideoDecoderFactoryTemplate<
            LibvpxVp8DecoderTemplateAdapter, LibvpxVp9DecoderTemplateAdapter,
            OpenH264DecoderTemplateAdapter, Dav1dDecoderTemplateAdapter>>(),
        nullptr /* audio_mixer */, nullptr /* audio_processing */);
  }

  WrapperPtr CreatePeerConnection() {
    return CreatePeerConnection(RTCConfiguration());
  }

  WrapperPtr CreatePeerConnection(const RTCConfiguration& config) {
    auto* fake_network = NewFakeNetwork();
    auto port_allocator = std::make_unique<cricket::BasicPortAllocator>(
        fake_network, socket_factory_.get());
    port_allocator->set_flags(cricket::PORTALLOCATOR_DISABLE_TCP |
                              cricket::PORTALLOCATOR_DISABLE_RELAY);
    port_allocator->set_step_delay(cricket::kMinimumStepDelay);
    auto observer = std::make_unique<MockPeerConnectionObserver>();
    RTCConfiguration modified_config = config;
    modified_config.sdp_semantics = sdp_semantics_;
    PeerConnectionDependencies pc_dependencies(observer.get());
    pc_dependencies.allocator = std::move(port_allocator);
    auto result = pc_factory_->CreatePeerConnectionOrError(
        modified_config, std::move(pc_dependencies));
    if (!result.ok()) {
      return nullptr;
    }

    auto wrapper = std::make_unique<PeerConnectionWrapperForBundleTest>(
        pc_factory_, result.MoveValue(), std::move(observer));
    wrapper->set_network(fake_network);
    return wrapper;
  }

  // Accepts the same arguments as CreatePeerConnection and adds default audio
  // and video tracks.
  template <typename... Args>
  WrapperPtr CreatePeerConnectionWithAudioVideo(Args&&... args) {
    auto wrapper = CreatePeerConnection(std::forward<Args>(args)...);
    if (!wrapper) {
      return nullptr;
    }
    wrapper->AddAudioTrack("a");
    wrapper->AddVideoTrack("v");
    return wrapper;
  }

  cricket::Candidate CreateLocalUdpCandidate(
      const rtc::SocketAddress& address) {
    cricket::Candidate candidate;
    candidate.set_component(cricket::ICE_CANDIDATE_COMPONENT_DEFAULT);
    candidate.set_protocol(cricket::UDP_PROTOCOL_NAME);
    candidate.set_address(address);
    candidate.set_type(cricket::LOCAL_PORT_TYPE);
    return candidate;
  }

  rtc::FakeNetworkManager* NewFakeNetwork() {
    // The PeerConnection's port allocator is tied to the PeerConnection's
    // lifetime and expects the underlying NetworkManager to outlive it. If
    // PeerConnectionWrapper owned the NetworkManager, it would be destroyed
    // before the PeerConnection (since subclass members are destroyed before
    // base class members). Therefore, the test fixture will own all the fake
    // networks even though tests should access the fake network through the
    // PeerConnectionWrapper.
    auto* fake_network = new FakeNetworkManagerWithNoAnyNetwork();
    fake_networks_.emplace_back(fake_network);
    return fake_network;
  }

  std::unique_ptr<rtc::VirtualSocketServer> vss_;
  std::unique_ptr<rtc::BasicPacketSocketFactory> socket_factory_;
  rtc::AutoSocketServerThread main_;
  rtc::scoped_refptr<PeerConnectionFactoryInterface> pc_factory_;
  std::vector<std::unique_ptr<rtc::FakeNetworkManager>> fake_networks_;
  const SdpSemantics sdp_semantics_;
};

class PeerConnectionBundleTest
    : public PeerConnectionBundleBaseTest,
      public ::testing::WithParamInterface<SdpSemantics> {
 protected:
  PeerConnectionBundleTest() : PeerConnectionBundleBaseTest(GetParam()) {}
};

class PeerConnectionBundleTestUnifiedPlan
    : public PeerConnectionBundleBaseTest {
 protected:
  PeerConnectionBundleTestUnifiedPlan()
      : PeerConnectionBundleBaseTest(SdpSemantics::kUnifiedPlan) {}
};

SdpContentMutator RemoveRtcpMux() {
  return [](cricket::ContentInfo* content, cricket::TransportInfo* transport) {
    content->media_description()->set_rtcp_mux(false);
  };
}

std::vector<int> GetCandidateComponents(
    const std::vector<IceCandidateInterface*> candidates) {
  std::vector<int> components;
  components.reserve(candidates.size());
  for (auto* candidate : candidates) {
    components.push_back(candidate->candidate().component());
  }
  return components;
}

// Test that there are 2 local UDP candidates (1 RTP and 1 RTCP candidate) for
// each media section when disabling bundling and disabling RTCP multiplexing.
TEST_P(PeerConnectionBundleTest,
       TwoCandidatesForEachTransportWhenNoBundleNoRtcpMux) {
  const SocketAddress kCallerAddress("1.1.1.1", 0);
  const SocketAddress kCalleeAddress("2.2.2.2", 0);

  RTCConfiguration config;
  config.rtcp_mux_policy = PeerConnectionInterface::kRtcpMuxPolicyNegotiate;
  auto caller = CreatePeerConnectionWithAudioVideo(config);
  caller->network()->AddInterface(kCallerAddress);
  auto callee = CreatePeerConnectionWithAudioVideo(config);
  callee->network()->AddInterface(kCalleeAddress);

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));
  RTCOfferAnswerOptions options_no_bundle;
  options_no_bundle.use_rtp_mux = false;
  auto answer = callee->CreateAnswer(options_no_bundle);
  SdpContentsForEach(RemoveRtcpMux(), answer->description());
  ASSERT_TRUE(
      callee->SetLocalDescription(CloneSessionDescription(answer.get())));
  ASSERT_TRUE(caller->SetRemoteDescription(std::move(answer)));

  // Check that caller has separate RTP and RTCP candidates for each media.
  EXPECT_TRUE_WAIT(caller->IsIceGatheringDone(), kDefaultTimeout);
  EXPECT_THAT(
      GetCandidateComponents(caller->observer()->GetCandidatesByMline(0)),
      UnorderedElementsAre(cricket::ICE_CANDIDATE_COMPONENT_RTP,
                           cricket::ICE_CANDIDATE_COMPONENT_RTCP));
  EXPECT_THAT(
      GetCandidateComponents(caller->observer()->GetCandidatesByMline(1)),
      UnorderedElementsAre(cricket::ICE_CANDIDATE_COMPONENT_RTP,
                           cricket::ICE_CANDIDATE_COMPONENT_RTCP));

  // Check that callee has separate RTP and RTCP candidates for each media.
  EXPECT_TRUE_WAIT(callee->IsIceGatheringDone(), kDefaultTimeout);
  EXPECT_THAT(
      GetCandidateComponents(callee->observer()->GetCandidatesByMline(0)),
      UnorderedElementsAre(cricket::ICE_CANDIDATE_COMPONENT_RTP,
                           cricket::ICE_CANDIDATE_COMPONENT_RTCP));
  EXPECT_THAT(
      GetCandidateComponents(callee->observer()->GetCandidatesByMline(1)),
      UnorderedElementsAre(cricket::ICE_CANDIDATE_COMPONENT_RTP,
                           cricket::ICE_CANDIDATE_COMPONENT_RTCP));
}

// Test that there is 1 local UDP candidate for both RTP and RTCP for each media
// section when disabling bundle but enabling RTCP multiplexing.
TEST_P(PeerConnectionBundleTest,
       OneCandidateForEachTransportWhenNoBundleButRtcpMux) {
  const SocketAddress kCallerAddress("1.1.1.1", 0);

  auto caller = CreatePeerConnectionWithAudioVideo();
  caller->network()->AddInterface(kCallerAddress);
  auto callee = CreatePeerConnectionWithAudioVideo();

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));
  RTCOfferAnswerOptions options_no_bundle;
  options_no_bundle.use_rtp_mux = false;
  ASSERT_TRUE(
      caller->SetRemoteDescription(callee->CreateAnswer(options_no_bundle)));

  EXPECT_TRUE_WAIT(caller->IsIceGatheringDone(), kDefaultTimeout);

  EXPECT_EQ(1u, caller->observer()->GetCandidatesByMline(0).size());
  EXPECT_EQ(1u, caller->observer()->GetCandidatesByMline(1).size());
}

// Test that there is 1 local UDP candidate in only the first media section when
// bundling and enabling RTCP multiplexing.
TEST_P(PeerConnectionBundleTest,
       OneCandidateOnlyOnFirstTransportWhenBundleAndRtcpMux) {
  const SocketAddress kCallerAddress("1.1.1.1", 0);

  RTCConfiguration config;
  config.bundle_policy = BundlePolicy::kBundlePolicyMaxBundle;
  auto caller = CreatePeerConnectionWithAudioVideo(config);
  caller->network()->AddInterface(kCallerAddress);
  auto callee = CreatePeerConnectionWithAudioVideo(config);

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));
  ASSERT_TRUE(caller->SetRemoteDescription(callee->CreateAnswer()));

  EXPECT_TRUE_WAIT(caller->IsIceGatheringDone(), kDefaultTimeout);

  EXPECT_EQ(1u, caller->observer()->GetCandidatesByMline(0).size());
  EXPECT_EQ(0u, caller->observer()->GetCandidatesByMline(1).size());
}

// It will fail if the offerer uses the mux-BUNDLE policy but the answerer
// doesn't support BUNDLE.
TEST_P(PeerConnectionBundleTest, MaxBundleNotSupportedInAnswer) {
  RTCConfiguration config;
  config.bundle_policy = BundlePolicy::kBundlePolicyMaxBundle;
  auto caller = CreatePeerConnectionWithAudioVideo(config);
  auto callee = CreatePeerConnectionWithAudioVideo();

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));
  bool equal_before =
      (caller->voice_rtp_transport() == caller->video_rtp_transport());
  EXPECT_EQ(true, equal_before);
  RTCOfferAnswerOptions options;
  options.use_rtp_mux = false;
  EXPECT_FALSE(
      caller->SetRemoteDescription(callee->CreateAnswerAndSetAsLocal(options)));
}

// The following parameterized test verifies that an offer/answer with varying
// bundle policies and either bundle in the answer or not will produce the
// expected RTP transports for audio and video. In particular, for bundling we
// care about whether they are separate transports or the same.

enum class BundleIncluded { kBundleInAnswer, kBundleNotInAnswer };
std::ostream& operator<<(std::ostream& out, BundleIncluded value) {
  switch (value) {
    case BundleIncluded::kBundleInAnswer:
      return out << "bundle in answer";
    case BundleIncluded::kBundleNotInAnswer:
      return out << "bundle not in answer";
  }
  return out << "unknown";
}

class PeerConnectionBundleMatrixTest
    : public PeerConnectionBundleBaseTest,
      public ::testing::WithParamInterface<
          std::tuple<SdpSemantics,
                     std::tuple<BundlePolicy, BundleIncluded, bool, bool>>> {
 protected:
  PeerConnectionBundleMatrixTest()
      : PeerConnectionBundleBaseTest(std::get<0>(GetParam())) {
    auto param = std::get<1>(GetParam());
    bundle_policy_ = std::get<0>(param);
    bundle_included_ = std::get<1>(param);
    expected_same_before_ = std::get<2>(param);
    expected_same_after_ = std::get<3>(param);
  }

  PeerConnectionInterface::BundlePolicy bundle_policy_;
  BundleIncluded bundle_included_;
  bool expected_same_before_;
  bool expected_same_after_;
};

TEST_P(PeerConnectionBundleMatrixTest,
       VerifyTransportsBeforeAndAfterSettingRemoteAnswer) {
  RTCConfiguration config;
  config.bundle_policy = bundle_policy_;
  auto caller = CreatePeerConnectionWithAudioVideo(config);
  auto callee = CreatePeerConnectionWithAudioVideo();

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));
  bool equal_before =
      (caller->voice_rtp_transport() == caller->video_rtp_transport());
  EXPECT_EQ(expected_same_before_, equal_before);

  RTCOfferAnswerOptions options;
  options.use_rtp_mux = (bundle_included_ == BundleIncluded::kBundleInAnswer);
  ASSERT_TRUE(
      caller->SetRemoteDescription(callee->CreateAnswerAndSetAsLocal(options)));
  bool equal_after =
      (caller->voice_rtp_transport() == caller->video_rtp_transport());
  EXPECT_EQ(expected_same_after_, equal_after);
}

// The max-bundle policy means we should anticipate bundling being negotiated,
// and multiplex audio/video from the start.
// For all other policies, bundling should only be enabled if negotiated by the
// answer.
INSTANTIATE_TEST_SUITE_P(
    PeerConnectionBundleTest,
    PeerConnectionBundleMatrixTest,
    Combine(Values(SdpSemantics::kPlanB_DEPRECATED, SdpSemantics::kUnifiedPlan),
            Values(std::make_tuple(BundlePolicy::kBundlePolicyBalanced,
                                   BundleIncluded::kBundleInAnswer,
                                   false,
                                   true),
                   std::make_tuple(BundlePolicy::kBundlePolicyBalanced,
                                   BundleIncluded::kBundleNotInAnswer,
                                   false,
                                   false),
                   std::make_tuple(BundlePolicy::kBundlePolicyMaxBundle,
                                   BundleIncluded::kBundleInAnswer,
                                   true,
                                   true),
                   std::make_tuple(BundlePolicy::kBundlePolicyMaxCompat,
                                   BundleIncluded::kBundleInAnswer,
                                   false,
                                   true),
                   std::make_tuple(BundlePolicy::kBundlePolicyMaxCompat,
                                   BundleIncluded::kBundleNotInAnswer,
                                   false,
                                   false))));

// Test that the audio/video transports on the callee side are the same before
// and after setting a local answer when max BUNDLE is enabled and an offer with
// BUNDLE is received.
TEST_P(PeerConnectionBundleTest,
       TransportsSameForMaxBundleWithBundleInRemoteOffer) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  RTCConfiguration config;
  config.bundle_policy = BundlePolicy::kBundlePolicyMaxBundle;
  auto callee = CreatePeerConnectionWithAudioVideo(config);

  RTCOfferAnswerOptions options_with_bundle;
  options_with_bundle.use_rtp_mux = true;
  ASSERT_TRUE(callee->SetRemoteDescription(
      caller->CreateOfferAndSetAsLocal(options_with_bundle)));

  EXPECT_EQ(callee->voice_rtp_transport(), callee->video_rtp_transport());

  ASSERT_TRUE(callee->SetLocalDescription(callee->CreateAnswer()));

  EXPECT_EQ(callee->voice_rtp_transport(), callee->video_rtp_transport());
}

TEST_P(PeerConnectionBundleTest,
       FailToSetRemoteOfferWithNoBundleWhenBundlePolicyMaxBundle) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  RTCConfiguration config;
  config.bundle_policy = BundlePolicy::kBundlePolicyMaxBundle;
  auto callee = CreatePeerConnectionWithAudioVideo(config);

  RTCOfferAnswerOptions options_no_bundle;
  options_no_bundle.use_rtp_mux = false;
  EXPECT_FALSE(callee->SetRemoteDescription(
      caller->CreateOfferAndSetAsLocal(options_no_bundle)));
}

// Test that if the media section which has the bundled transport is rejected,
// then the peers still connect and the bundled transport switches to the other
// media section.
// Note: This is currently failing because of the following bug:
// https://bugs.chromium.org/p/webrtc/issues/detail?id=6280
TEST_P(PeerConnectionBundleTest,
       DISABLED_SuccessfullyNegotiateMaxBundleIfBundleTransportMediaRejected) {
  RTCConfiguration config;
  config.bundle_policy = BundlePolicy::kBundlePolicyMaxBundle;
  auto caller = CreatePeerConnectionWithAudioVideo(config);
  auto callee = CreatePeerConnection();
  callee->AddVideoTrack("v");

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));

  RTCOfferAnswerOptions options;
  options.offer_to_receive_audio = 0;
  ASSERT_TRUE(
      caller->SetRemoteDescription(callee->CreateAnswerAndSetAsLocal(options)));

  EXPECT_FALSE(caller->voice_rtp_transport());
  EXPECT_TRUE(caller->video_rtp_transport());
}

// When requiring RTCP multiplexing, the PeerConnection never makes RTCP
// transport channels.
TEST_P(PeerConnectionBundleTest, NeverCreateRtcpTransportWithRtcpMuxRequired) {
  RTCConfiguration config;
  config.rtcp_mux_policy = RtcpMuxPolicy::kRtcpMuxPolicyRequire;
  auto caller = CreatePeerConnectionWithAudioVideo(config);
  auto callee = CreatePeerConnectionWithAudioVideo();

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));

  EXPECT_FALSE(caller->voice_rtp_transport()->rtcp_mux_enabled());
  EXPECT_FALSE(caller->video_rtp_transport()->rtcp_mux_enabled());

  ASSERT_TRUE(
      caller->SetRemoteDescription(callee->CreateAnswerAndSetAsLocal()));

  EXPECT_TRUE(caller->voice_rtp_transport()->rtcp_mux_enabled());
  EXPECT_TRUE(caller->video_rtp_transport()->rtcp_mux_enabled());
}

// When negotiating RTCP multiplexing, the PeerConnection makes RTCP transports
// when the offer is sent, but will destroy them once the remote answer is set.
TEST_P(PeerConnectionBundleTest,
       CreateRtcpTransportOnlyBeforeAnswerWithRtcpMuxNegotiate) {
  RTCConfiguration config;
  config.rtcp_mux_policy = RtcpMuxPolicy::kRtcpMuxPolicyNegotiate;
  auto caller = CreatePeerConnectionWithAudioVideo(config);
  auto callee = CreatePeerConnectionWithAudioVideo();

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));

  EXPECT_FALSE(caller->voice_rtp_transport()->rtcp_mux_enabled());
  EXPECT_FALSE(caller->video_rtp_transport()->rtcp_mux_enabled());

  ASSERT_TRUE(
      caller->SetRemoteDescription(callee->CreateAnswerAndSetAsLocal()));

  EXPECT_TRUE(caller->voice_rtp_transport()->rtcp_mux_enabled());
  EXPECT_TRUE(caller->video_rtp_transport()->rtcp_mux_enabled());
}

TEST_P(PeerConnectionBundleTest, FailToSetDescriptionWithBundleAndNoRtcpMux) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnectionWithAudioVideo();

  RTCOfferAnswerOptions options;
  options.use_rtp_mux = true;

  auto offer = caller->CreateOffer(options);
  SdpContentsForEach(RemoveRtcpMux(), offer->description());

  std::string error;
  EXPECT_FALSE(caller->SetLocalDescription(CloneSessionDescription(offer.get()),
                                           &error));
  EXPECT_EQ(
      "Failed to set local offer sdp: rtcp-mux must be enabled when BUNDLE is "
      "enabled.",
      error);

  EXPECT_FALSE(callee->SetRemoteDescription(std::move(offer), &error));
  EXPECT_EQ(
      "Failed to set remote offer sdp: rtcp-mux must be enabled when BUNDLE is "
      "enabled.",
      error);
}

// Test that candidates sent to the "video" transport do not get pushed down to
// the "audio" transport channel when bundling.
TEST_P(PeerConnectionBundleTest,
       IgnoreCandidatesForUnusedTransportWhenBundling) {
  const SocketAddress kAudioAddress1("1.1.1.1", 1111);
  const SocketAddress kAudioAddress2("2.2.2.2", 2222);
  const SocketAddress kVideoAddress("3.3.3.3", 3333);
  const SocketAddress kCallerAddress("4.4.4.4", 0);
  const SocketAddress kCalleeAddress("5.5.5.5", 0);

  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnectionWithAudioVideo();

  caller->network()->AddInterface(kCallerAddress);
  callee->network()->AddInterface(kCalleeAddress);

  RTCOfferAnswerOptions options;
  options.use_rtp_mux = true;

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));
  ASSERT_TRUE(
      caller->SetRemoteDescription(callee->CreateAnswerAndSetAsLocal(options)));

  // The way the *_WAIT checks work is they only wait if the condition fails,
  // which does not help in the case where state is not changing. This is
  // problematic in this test since we want to verify that adding a video
  // candidate does _not_ change state. So we interleave candidates and assume
  // that messages are executed in the order they were posted.

  cricket::Candidate audio_candidate1 = CreateLocalUdpCandidate(kAudioAddress1);
  ASSERT_TRUE(caller->AddIceCandidateToMedia(&audio_candidate1,
                                             cricket::MEDIA_TYPE_AUDIO));

  cricket::Candidate video_candidate = CreateLocalUdpCandidate(kVideoAddress);
  ASSERT_TRUE(caller->AddIceCandidateToMedia(&video_candidate,
                                             cricket::MEDIA_TYPE_VIDEO));

  cricket::Candidate audio_candidate2 = CreateLocalUdpCandidate(kAudioAddress2);
  ASSERT_TRUE(caller->AddIceCandidateToMedia(&audio_candidate2,
                                             cricket::MEDIA_TYPE_AUDIO));

  EXPECT_TRUE_WAIT(caller->HasConnectionWithRemoteAddress(kAudioAddress1),
                   kDefaultTimeout);
  EXPECT_TRUE_WAIT(caller->HasConnectionWithRemoteAddress(kAudioAddress2),
                   kDefaultTimeout);
  EXPECT_FALSE(caller->HasConnectionWithRemoteAddress(kVideoAddress));
}

// Test that the transport used by both audio and video is the transport
// associated with the first MID in the answer BUNDLE group, even if it's in a
// different order from the offer.
TEST_P(PeerConnectionBundleTest, BundleOnFirstMidInAnswer) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnectionWithAudioVideo();

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));

  auto* old_video_transport = caller->video_rtp_transport();

  auto answer = callee->CreateAnswer();
  auto* old_bundle_group =
      answer->description()->GetGroupByName(cricket::GROUP_TYPE_BUNDLE);
  std::string first_mid = old_bundle_group->content_names()[0];
  std::string second_mid = old_bundle_group->content_names()[1];
  answer->description()->RemoveGroupByName(cricket::GROUP_TYPE_BUNDLE);

  cricket::ContentGroup new_bundle_group(cricket::GROUP_TYPE_BUNDLE);
  new_bundle_group.AddContentName(second_mid);
  new_bundle_group.AddContentName(first_mid);
  answer->description()->AddGroup(new_bundle_group);

  ASSERT_TRUE(caller->SetRemoteDescription(std::move(answer)));

  EXPECT_EQ(old_video_transport, caller->video_rtp_transport());
  EXPECT_EQ(caller->voice_rtp_transport(), caller->video_rtp_transport());
}

// This tests that applying description with conflicted RTP demuxing criteria
// will fail when using BUNDLE.
TEST_P(PeerConnectionBundleTest, ApplyDescriptionWithSameSsrcsBundledFails) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnectionWithAudioVideo();

  RTCOfferAnswerOptions options;
  options.use_rtp_mux = true;
  auto offer = caller->CreateOffer(options);
  EXPECT_TRUE(
      caller->SetLocalDescription(CloneSessionDescription(offer.get())));
  // Modify the remote SDP to make two m= sections have the same SSRC.
  ASSERT_GE(offer->description()->contents().size(), 2U);
  ReplaceFirstSsrc(offer->description()
                       ->contents()[0]
                       .media_description()
                       ->mutable_streams()[0],
                   1111222);
  ReplaceFirstSsrc(offer->description()
                       ->contents()[1]
                       .media_description()
                       ->mutable_streams()[0],
                   1111222);

  EXPECT_TRUE(callee->SetRemoteDescription(std::move(offer)));
  // When BUNDLE is enabled, applying the description is expected to fail
  // because the demuxing criteria can not be satisfied.
  auto answer = callee->CreateAnswer(options);
  EXPECT_FALSE(callee->SetLocalDescription(std::move(answer)));
}

// A variant of the above, without BUNDLE duplicate SSRCs are allowed.
TEST_P(PeerConnectionBundleTest,
       ApplyDescriptionWithSameSsrcsUnbundledSucceeds) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnectionWithAudioVideo();

  RTCOfferAnswerOptions options;
  options.use_rtp_mux = false;
  auto offer = caller->CreateOffer(options);
  EXPECT_TRUE(
      caller->SetLocalDescription(CloneSessionDescription(offer.get())));
  // Modify the remote SDP to make two m= sections have the same SSRC.
  ASSERT_GE(offer->description()->contents().size(), 2U);
  ReplaceFirstSsrc(offer->description()
                       ->contents()[0]
                       .media_description()
                       ->mutable_streams()[0],
                   1111222);
  ReplaceFirstSsrc(offer->description()
                       ->contents()[1]
                       .media_description()
                       ->mutable_streams()[0],
                   1111222);
  EXPECT_TRUE(callee->SetRemoteDescription(std::move(offer)));

  // Without BUNDLE, demuxing is done per-transport.
  auto answer = callee->CreateAnswer(options);
  EXPECT_TRUE(callee->SetLocalDescription(std::move(answer)));
}

// This tests that changing the pre-negotiated BUNDLE tag is not supported.
TEST_P(PeerConnectionBundleTest, RejectDescriptionChangingBundleTag) {
  RTCConfiguration config;
  config.bundle_policy = BundlePolicy::kBundlePolicyMaxBundle;
  auto caller = CreatePeerConnectionWithAudioVideo(config);
  auto callee = CreatePeerConnectionWithAudioVideo(config);

  RTCOfferAnswerOptions options;
  options.use_rtp_mux = true;
  auto offer = caller->CreateOfferAndSetAsLocal(options);

  // Create a new bundle-group with different bundled_mid.
  auto* old_bundle_group =
      offer->description()->GetGroupByName(cricket::GROUP_TYPE_BUNDLE);
  std::string first_mid = old_bundle_group->content_names()[0];
  std::string second_mid = old_bundle_group->content_names()[1];
  cricket::ContentGroup new_bundle_group(cricket::GROUP_TYPE_BUNDLE);
  new_bundle_group.AddContentName(second_mid);

  auto re_offer = CloneSessionDescription(offer.get());
  callee->SetRemoteDescription(std::move(offer));
  auto answer = callee->CreateAnswer(options);
  // Reject the first MID.
  answer->description()->contents()[0].rejected = true;
  // Remove the first MID from the bundle group.
  answer->description()->RemoveGroupByName(cricket::GROUP_TYPE_BUNDLE);
  answer->description()->AddGroup(new_bundle_group);
  // The answer is expected to be rejected.
  EXPECT_FALSE(caller->SetRemoteDescription(std::move(answer)));

  // Do the same thing for re-offer.
  re_offer->description()->contents()[0].rejected = true;
  re_offer->description()->RemoveGroupByName(cricket::GROUP_TYPE_BUNDLE);
  re_offer->description()->AddGroup(new_bundle_group);
  // The re-offer is expected to be rejected.
  EXPECT_FALSE(caller->SetLocalDescription(std::move(re_offer)));
}

// This tests that removing contents from BUNDLE group and reject the whole
// BUNDLE group could work. This is a regression test for
// (https://bugs.chromium.org/p/chromium/issues/detail?id=827917)
#ifdef HAVE_SCTP
TEST_P(PeerConnectionBundleTest, RemovingContentAndRejectBundleGroup) {
  RTCConfiguration config;
  config.bundle_policy = BundlePolicy::kBundlePolicyMaxBundle;
  auto caller = CreatePeerConnectionWithAudioVideo(config);
  caller->CreateDataChannel("dc");

  auto offer = caller->CreateOfferAndSetAsLocal();
  auto re_offer = CloneSessionDescription(offer.get());

  // Removing the second MID from the BUNDLE group.
  auto* old_bundle_group =
      offer->description()->GetGroupByName(cricket::GROUP_TYPE_BUNDLE);
  std::string first_mid = old_bundle_group->content_names()[0];
  std::string third_mid = old_bundle_group->content_names()[2];
  cricket::ContentGroup new_bundle_group(cricket::GROUP_TYPE_BUNDLE);
  new_bundle_group.AddContentName(first_mid);
  new_bundle_group.AddContentName(third_mid);

  // Reject the entire new bundle group.
  re_offer->description()->contents()[0].rejected = true;
  re_offer->description()->contents()[2].rejected = true;
  re_offer->description()->RemoveGroupByName(cricket::GROUP_TYPE_BUNDLE);
  re_offer->description()->AddGroup(new_bundle_group);

  EXPECT_TRUE(caller->SetLocalDescription(std::move(re_offer)));
}
#endif

// This tests that the BUNDLE group in answer should be a subset of the offered
// group.
TEST_P(PeerConnectionBundleTest, AddContentToBundleGroupInAnswerNotSupported) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnectionWithAudioVideo();

  auto offer = caller->CreateOffer();
  std::string first_mid = offer->description()->contents()[0].name;
  std::string second_mid = offer->description()->contents()[1].name;

  cricket::ContentGroup bundle_group(cricket::GROUP_TYPE_BUNDLE);
  bundle_group.AddContentName(first_mid);
  offer->description()->RemoveGroupByName(cricket::GROUP_TYPE_BUNDLE);
  offer->description()->AddGroup(bundle_group);
  EXPECT_TRUE(
      caller->SetLocalDescription(CloneSessionDescription(offer.get())));
  EXPECT_TRUE(callee->SetRemoteDescription(std::move(offer)));

  auto answer = callee->CreateAnswer();
  bundle_group.AddContentName(second_mid);
  answer->description()->RemoveGroupByName(cricket::GROUP_TYPE_BUNDLE);
  answer->description()->AddGroup(bundle_group);

  // The answer is expected to be rejected because second mid is not in the
  // offered BUNDLE group.
  EXPECT_FALSE(callee->SetLocalDescription(std::move(answer)));
}

// This tests that the BUNDLE group with non-existing MID should be rejectd.
TEST_P(PeerConnectionBundleTest, RejectBundleGroupWithNonExistingMid) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnectionWithAudioVideo();

  auto offer = caller->CreateOffer();
  auto invalid_bundle_group =
      *offer->description()->GetGroupByName(cricket::GROUP_TYPE_BUNDLE);
  invalid_bundle_group.AddContentName("non-existing-MID");
  offer->description()->RemoveGroupByName(cricket::GROUP_TYPE_BUNDLE);
  offer->description()->AddGroup(invalid_bundle_group);

  EXPECT_FALSE(
      caller->SetLocalDescription(CloneSessionDescription(offer.get())));
  EXPECT_FALSE(callee->SetRemoteDescription(std::move(offer)));
}

// This tests that an answer shouldn't be able to remove an m= section from an
// established group without rejecting it.
TEST_P(PeerConnectionBundleTest, RemoveContentFromBundleGroup) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnectionWithAudioVideo();

  EXPECT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));
  EXPECT_TRUE(
      caller->SetRemoteDescription(callee->CreateAnswerAndSetAsLocal()));

  EXPECT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));
  auto answer = callee->CreateAnswer();
  std::string second_mid = answer->description()->contents()[1].name;

  auto invalid_bundle_group =
      *answer->description()->GetGroupByName(cricket::GROUP_TYPE_BUNDLE);
  invalid_bundle_group.RemoveContentName(second_mid);
  answer->description()->RemoveGroupByName(cricket::GROUP_TYPE_BUNDLE);
  answer->description()->AddGroup(invalid_bundle_group);

  EXPECT_FALSE(
      callee->SetLocalDescription(CloneSessionDescription(answer.get())));
}

INSTANTIATE_TEST_SUITE_P(PeerConnectionBundleTest,
                         PeerConnectionBundleTest,
                         Values(SdpSemantics::kPlanB_DEPRECATED,
                                SdpSemantics::kUnifiedPlan));

// According to RFC5888, if an endpoint understands the semantics of an
// "a=group", it MUST return an answer with that group. So, an empty BUNDLE
// group is valid when the answerer rejects all m= sections (by stopping all
// transceivers), meaning there's nothing to bundle.
//
// Only writing this test for Unified Plan mode, since there's no way to reject
// m= sections in answers for Plan B without SDP munging.
TEST_F(PeerConnectionBundleTestUnifiedPlan,
       EmptyBundleGroupCreatedInAnswerWhenAppropriate) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnection();

  EXPECT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));

  // Stop all transceivers, causing all m= sections to be rejected.
  for (const auto& transceiver : callee->pc()->GetTransceivers()) {
    transceiver->StopInternal();
  }
  EXPECT_TRUE(
      caller->SetRemoteDescription(callee->CreateAnswerAndSetAsLocal()));

  // Verify that the answer actually contained an empty bundle group.
  const SessionDescriptionInterface* desc = callee->pc()->local_description();
  ASSERT_NE(nullptr, desc);
  const cricket::ContentGroup* bundle_group =
      desc->description()->GetGroupByName(cricket::GROUP_TYPE_BUNDLE);
  ASSERT_NE(nullptr, bundle_group);
  EXPECT_TRUE(bundle_group->content_names().empty());
}

TEST_F(PeerConnectionBundleTestUnifiedPlan, MultipleBundleGroups) {
  auto caller = CreatePeerConnection();
  caller->AddAudioTrack("0_audio");
  caller->AddAudioTrack("1_audio");
  caller->AddVideoTrack("2_audio");
  caller->AddVideoTrack("3_audio");
  auto callee = CreatePeerConnection();

  auto offer = caller->CreateOffer(RTCOfferAnswerOptions());
  // Modify the GROUP to have two BUNDLEs. We know that the MIDs will be 0,1,2,4
  // because our implementation has predictable MIDs.
  offer->description()->RemoveGroupByName(cricket::GROUP_TYPE_BUNDLE);
  cricket::ContentGroup bundle_group1(cricket::GROUP_TYPE_BUNDLE);
  bundle_group1.AddContentName("0");
  bundle_group1.AddContentName("1");
  cricket::ContentGroup bundle_group2(cricket::GROUP_TYPE_BUNDLE);
  bundle_group2.AddContentName("2");
  bundle_group2.AddContentName("3");
  offer->description()->AddGroup(bundle_group1);
  offer->description()->AddGroup(bundle_group2);

  EXPECT_TRUE(
      caller->SetLocalDescription(CloneSessionDescription(offer.get())));
  EXPECT_TRUE(callee->SetRemoteDescription(std::move(offer)));
  auto answer = callee->CreateAnswer();
  EXPECT_TRUE(
      callee->SetLocalDescription(CloneSessionDescription(answer.get())));
  EXPECT_TRUE(caller->SetRemoteDescription(std::move(answer)));

  // Verify bundling on sender side.
  auto senders = caller->pc()->GetSenders();
  ASSERT_EQ(senders.size(), 4u);
  auto sender0_transport = senders[0]->dtls_transport();
  auto sender1_transport = senders[1]->dtls_transport();
  auto sender2_transport = senders[2]->dtls_transport();
  auto sender3_transport = senders[3]->dtls_transport();
  EXPECT_EQ(sender0_transport, sender1_transport);
  EXPECT_EQ(sender2_transport, sender3_transport);
  EXPECT_NE(sender0_transport, sender2_transport);

  // Verify bundling on receiver side.
  auto receivers = callee->pc()->GetReceivers();
  ASSERT_EQ(receivers.size(), 4u);
  auto receiver0_transport = receivers[0]->dtls_transport();
  auto receiver1_transport = receivers[1]->dtls_transport();
  auto receiver2_transport = receivers[2]->dtls_transport();
  auto receiver3_transport = receivers[3]->dtls_transport();
  EXPECT_EQ(receiver0_transport, receiver1_transport);
  EXPECT_EQ(receiver2_transport, receiver3_transport);
  EXPECT_NE(receiver0_transport, receiver2_transport);
}

// Test that, with the "max-compat" bundle policy, it's possible to add an m=
// section that's not part of an existing bundle group.
TEST_F(PeerConnectionBundleTestUnifiedPlan, AddNonBundledSection) {
  RTCConfiguration config;
  config.bundle_policy = PeerConnectionInterface::kBundlePolicyMaxCompat;
  auto caller = CreatePeerConnection(config);
  caller->AddAudioTrack("0_audio");
  caller->AddAudioTrack("1_audio");
  auto callee = CreatePeerConnection(config);

  // Establish an existing BUNDLE group.
  auto offer = caller->CreateOffer(RTCOfferAnswerOptions());
  EXPECT_TRUE(
      caller->SetLocalDescription(CloneSessionDescription(offer.get())));
  EXPECT_TRUE(callee->SetRemoteDescription(std::move(offer)));
  auto answer = callee->CreateAnswer();
  EXPECT_TRUE(
      callee->SetLocalDescription(CloneSessionDescription(answer.get())));
  EXPECT_TRUE(caller->SetRemoteDescription(std::move(answer)));

  // Add a track but munge SDP so it's not part of the bundle group.
  caller->AddAudioTrack("3_audio");
  offer = caller->CreateOffer(RTCOfferAnswerOptions());
  offer->description()->RemoveGroupByName(cricket::GROUP_TYPE_BUNDLE);
  cricket::ContentGroup bundle_group(cricket::GROUP_TYPE_BUNDLE);
  bundle_group.AddContentName("0");
  bundle_group.AddContentName("1");
  offer->description()->AddGroup(bundle_group);
  EXPECT_TRUE(
      caller->SetLocalDescription(CloneSessionDescription(offer.get())));
  EXPECT_TRUE(callee->SetRemoteDescription(std::move(offer)));
  answer = callee->CreateAnswer();
  EXPECT_TRUE(
      callee->SetLocalDescription(CloneSessionDescription(answer.get())));
  EXPECT_TRUE(caller->SetRemoteDescription(std::move(answer)));

  // Verify bundling on the sender side.
  auto senders = caller->pc()->GetSenders();
  ASSERT_EQ(senders.size(), 3u);
  auto sender0_transport = senders[0]->dtls_transport();
  auto sender1_transport = senders[1]->dtls_transport();
  auto sender2_transport = senders[2]->dtls_transport();
  EXPECT_EQ(sender0_transport, sender1_transport);
  EXPECT_NE(sender0_transport, sender2_transport);

  // Verify bundling on receiver side.
  auto receivers = callee->pc()->GetReceivers();
  ASSERT_EQ(receivers.size(), 3u);
  auto receiver0_transport = receivers[0]->dtls_transport();
  auto receiver1_transport = receivers[1]->dtls_transport();
  auto receiver2_transport = receivers[2]->dtls_transport();
  EXPECT_EQ(receiver0_transport, receiver1_transport);
  EXPECT_NE(receiver0_transport, receiver2_transport);
}

}  // namespace webrtc
