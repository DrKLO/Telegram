/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <string>
#include <tuple>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/jsep.h"
#include "api/media_types.h"
#include "api/peer_connection_interface.h"
#include "api/rtc_error.h"
#include "api/rtc_event_log/rtc_event_log_factory.h"
#include "api/rtc_event_log/rtc_event_log_factory_interface.h"
#include "api/rtp_parameters.h"
#include "api/rtp_transceiver_direction.h"
#include "api/rtp_transceiver_interface.h"
#include "api/scoped_refptr.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "api/task_queue/task_queue_factory.h"
#include "media/base/fake_media_engine.h"
#include "media/base/media_engine.h"
#include "p2p/base/fake_port_allocator.h"
#include "p2p/base/port_allocator.h"
#include "pc/peer_connection_wrapper.h"
#include "pc/session_description.h"
#include "pc/test/enable_fake_media.h"
#include "pc/test/mock_peer_connection_observers.h"
#include "rtc_base/internal/default_socket_server.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/thread.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/scoped_key_value_config.h"

namespace webrtc {

using ::testing::Combine;
using ::testing::ElementsAre;
using ::testing::Field;
using ::testing::Return;
using ::testing::Values;

class PeerConnectionHeaderExtensionTest
    : public ::testing::TestWithParam<
          std::tuple<cricket::MediaType, SdpSemantics>> {
 protected:
  PeerConnectionHeaderExtensionTest()
      : socket_server_(rtc::CreateDefaultSocketServer()),
        main_thread_(socket_server_.get()),
        extensions_(
            {RtpHeaderExtensionCapability("uri1",
                                          1,
                                          RtpTransceiverDirection::kStopped),
             RtpHeaderExtensionCapability("uri2",
                                          2,
                                          RtpTransceiverDirection::kSendOnly),
             RtpHeaderExtensionCapability("uri3",
                                          3,
                                          RtpTransceiverDirection::kRecvOnly),
             RtpHeaderExtensionCapability(
                 "uri4",
                 4,
                 RtpTransceiverDirection::kSendRecv)}) {}

  std::unique_ptr<PeerConnectionWrapper> CreatePeerConnection(
      cricket::MediaType media_type,
      absl::optional<SdpSemantics> semantics) {
    auto media_engine = std::make_unique<cricket::FakeMediaEngine>();
    if (media_type == cricket::MediaType::MEDIA_TYPE_AUDIO)
      media_engine->fake_voice_engine()->SetRtpHeaderExtensions(extensions_);
    else
      media_engine->fake_video_engine()->SetRtpHeaderExtensions(extensions_);
    PeerConnectionFactoryDependencies factory_dependencies;
    factory_dependencies.network_thread = rtc::Thread::Current();
    factory_dependencies.worker_thread = rtc::Thread::Current();
    factory_dependencies.signaling_thread = rtc::Thread::Current();
    factory_dependencies.task_queue_factory = CreateDefaultTaskQueueFactory();
    EnableFakeMedia(factory_dependencies, std::move(media_engine));

    factory_dependencies.event_log_factory =
        std::make_unique<RtcEventLogFactory>();

    auto pc_factory =
        CreateModularPeerConnectionFactory(std::move(factory_dependencies));

    auto fake_port_allocator = std::make_unique<cricket::FakePortAllocator>(
        rtc::Thread::Current(),
        std::make_unique<rtc::BasicPacketSocketFactory>(socket_server_.get()),
        &field_trials_);
    auto observer = std::make_unique<MockPeerConnectionObserver>();
    PeerConnectionInterface::RTCConfiguration config;
    if (semantics)
      config.sdp_semantics = *semantics;
    PeerConnectionDependencies pc_dependencies(observer.get());
    pc_dependencies.allocator = std::move(fake_port_allocator);
    auto result = pc_factory->CreatePeerConnectionOrError(
        config, std::move(pc_dependencies));
    EXPECT_TRUE(result.ok());
    observer->SetPeerConnectionInterface(result.value().get());
    return std::make_unique<PeerConnectionWrapper>(
        pc_factory, result.MoveValue(), std::move(observer));
  }

  test::ScopedKeyValueConfig field_trials_;
  std::unique_ptr<rtc::SocketServer> socket_server_;
  rtc::AutoSocketServerThread main_thread_;
  std::vector<RtpHeaderExtensionCapability> extensions_;
};

TEST_P(PeerConnectionHeaderExtensionTest, TransceiverOffersHeaderExtensions) {
  cricket::MediaType media_type;
  SdpSemantics semantics;
  std::tie(media_type, semantics) = GetParam();
  if (semantics != SdpSemantics::kUnifiedPlan)
    return;
  std::unique_ptr<PeerConnectionWrapper> wrapper =
      CreatePeerConnection(media_type, semantics);
  auto transceiver = wrapper->AddTransceiver(media_type);
  EXPECT_EQ(transceiver->GetHeaderExtensionsToNegotiate(), extensions_);
}

TEST_P(PeerConnectionHeaderExtensionTest,
       SenderReceiverCapabilitiesReturnNotStoppedExtensions) {
  cricket::MediaType media_type;
  SdpSemantics semantics;
  std::tie(media_type, semantics) = GetParam();
  std::unique_ptr<PeerConnectionWrapper> wrapper =
      CreatePeerConnection(media_type, semantics);
  EXPECT_THAT(wrapper->pc_factory()
                  ->GetRtpSenderCapabilities(media_type)
                  .header_extensions,
              ElementsAre(Field(&RtpHeaderExtensionCapability::uri, "uri2"),
                          Field(&RtpHeaderExtensionCapability::uri, "uri3"),
                          Field(&RtpHeaderExtensionCapability::uri, "uri4")));
  EXPECT_EQ(wrapper->pc_factory()
                ->GetRtpReceiverCapabilities(media_type)
                .header_extensions,
            wrapper->pc_factory()
                ->GetRtpSenderCapabilities(media_type)
                .header_extensions);
}

TEST_P(PeerConnectionHeaderExtensionTest, OffersUnstoppedDefaultExtensions) {
  cricket::MediaType media_type;
  SdpSemantics semantics;
  std::tie(media_type, semantics) = GetParam();
  if (semantics != SdpSemantics::kUnifiedPlan)
    return;
  std::unique_ptr<PeerConnectionWrapper> wrapper =
      CreatePeerConnection(media_type, semantics);
  auto transceiver = wrapper->AddTransceiver(media_type);
  auto session_description = wrapper->CreateOffer();
  EXPECT_THAT(session_description->description()
                  ->contents()[0]
                  .media_description()
                  ->rtp_header_extensions(),
              ElementsAre(Field(&RtpExtension::uri, "uri2"),
                          Field(&RtpExtension::uri, "uri3"),
                          Field(&RtpExtension::uri, "uri4")));
}

TEST_P(PeerConnectionHeaderExtensionTest, OffersUnstoppedModifiedExtensions) {
  cricket::MediaType media_type;
  SdpSemantics semantics;
  std::tie(media_type, semantics) = GetParam();
  if (semantics != SdpSemantics::kUnifiedPlan)
    return;
  std::unique_ptr<PeerConnectionWrapper> wrapper =
      CreatePeerConnection(media_type, semantics);
  auto transceiver = wrapper->AddTransceiver(media_type);
  auto modified_extensions = transceiver->GetHeaderExtensionsToNegotiate();
  modified_extensions[0].direction = RtpTransceiverDirection::kSendRecv;
  modified_extensions[3].direction = RtpTransceiverDirection::kStopped;
  EXPECT_TRUE(
      transceiver->SetHeaderExtensionsToNegotiate(modified_extensions).ok());
  auto session_description = wrapper->CreateOffer();
  EXPECT_THAT(session_description->description()
                  ->contents()[0]
                  .media_description()
                  ->rtp_header_extensions(),
              ElementsAre(Field(&RtpExtension::uri, "uri1"),
                          Field(&RtpExtension::uri, "uri2"),
                          Field(&RtpExtension::uri, "uri3")));
}

TEST_P(PeerConnectionHeaderExtensionTest, AnswersUnstoppedModifiedExtensions) {
  cricket::MediaType media_type;
  SdpSemantics semantics;
  std::tie(media_type, semantics) = GetParam();
  if (semantics != SdpSemantics::kUnifiedPlan)
    return;
  std::unique_ptr<PeerConnectionWrapper> pc1 =
      CreatePeerConnection(media_type, semantics);
  std::unique_ptr<PeerConnectionWrapper> pc2 =
      CreatePeerConnection(media_type, semantics);
  auto transceiver1 = pc1->AddTransceiver(media_type);

  auto offer = pc1->CreateOfferAndSetAsLocal(
      PeerConnectionInterface::RTCOfferAnswerOptions());
  pc2->SetRemoteDescription(std::move(offer));

  ASSERT_EQ(pc2->pc()->GetTransceivers().size(), 1u);
  auto transceiver2 = pc2->pc()->GetTransceivers()[0];
  auto modified_extensions = transceiver2->GetHeaderExtensionsToNegotiate();
  // Don't offer uri4.
  modified_extensions[3].direction = RtpTransceiverDirection::kStopped;
  transceiver2->SetHeaderExtensionsToNegotiate(modified_extensions);

  auto answer = pc2->CreateAnswerAndSetAsLocal(
      PeerConnectionInterface::RTCOfferAnswerOptions());
  EXPECT_THAT(answer->description()
                  ->contents()[0]
                  .media_description()
                  ->rtp_header_extensions(),
              ElementsAre(Field(&RtpExtension::uri, "uri2"),
                          Field(&RtpExtension::uri, "uri3")));
}

TEST_P(PeerConnectionHeaderExtensionTest, NegotiatedExtensionsAreAccessible) {
  cricket::MediaType media_type;
  SdpSemantics semantics;
  std::tie(media_type, semantics) = GetParam();
  if (semantics != SdpSemantics::kUnifiedPlan)
    return;
  std::unique_ptr<PeerConnectionWrapper> pc1 =
      CreatePeerConnection(media_type, semantics);
  auto transceiver1 = pc1->AddTransceiver(media_type);
  auto modified_extensions = transceiver1->GetHeaderExtensionsToNegotiate();
  modified_extensions[3].direction = RtpTransceiverDirection::kStopped;
  transceiver1->SetHeaderExtensionsToNegotiate(modified_extensions);
  auto offer = pc1->CreateOfferAndSetAsLocal(
      PeerConnectionInterface::RTCOfferAnswerOptions());

  std::unique_ptr<PeerConnectionWrapper> pc2 =
      CreatePeerConnection(media_type, semantics);
  auto transceiver2 = pc2->AddTransceiver(media_type);
  pc2->SetRemoteDescription(std::move(offer));
  auto answer = pc2->CreateAnswerAndSetAsLocal(
      PeerConnectionInterface::RTCOfferAnswerOptions());
  pc1->SetRemoteDescription(std::move(answer));

  // PC1 has exts 2-4 unstopped and PC2 has exts 1-3 unstopped -> ext 2, 3
  // survives.
  EXPECT_THAT(transceiver1->GetNegotiatedHeaderExtensions(),
              ElementsAre(Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kSendRecv),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kSendRecv),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped)));
}

TEST_P(PeerConnectionHeaderExtensionTest, OfferedExtensionsArePerTransceiver) {
  cricket::MediaType media_type;
  SdpSemantics semantics;
  std::tie(media_type, semantics) = GetParam();
  if (semantics != SdpSemantics::kUnifiedPlan)
    return;
  std::unique_ptr<PeerConnectionWrapper> pc1 =
      CreatePeerConnection(media_type, semantics);
  auto transceiver1 = pc1->AddTransceiver(media_type);
  auto modified_extensions = transceiver1->GetHeaderExtensionsToNegotiate();
  modified_extensions[3].direction = RtpTransceiverDirection::kStopped;
  transceiver1->SetHeaderExtensionsToNegotiate(modified_extensions);
  auto transceiver2 = pc1->AddTransceiver(media_type);

  auto session_description = pc1->CreateOffer();
  EXPECT_THAT(session_description->description()
                  ->contents()[0]
                  .media_description()
                  ->rtp_header_extensions(),
              ElementsAre(Field(&RtpExtension::uri, "uri2"),
                          Field(&RtpExtension::uri, "uri3")));
  EXPECT_THAT(session_description->description()
                  ->contents()[1]
                  .media_description()
                  ->rtp_header_extensions(),
              ElementsAre(Field(&RtpExtension::uri, "uri2"),
                          Field(&RtpExtension::uri, "uri3"),
                          Field(&RtpExtension::uri, "uri4")));
}

TEST_P(PeerConnectionHeaderExtensionTest, RemovalAfterRenegotiation) {
  cricket::MediaType media_type;
  SdpSemantics semantics;
  std::tie(media_type, semantics) = GetParam();
  if (semantics != SdpSemantics::kUnifiedPlan)
    return;
  std::unique_ptr<PeerConnectionWrapper> pc1 =
      CreatePeerConnection(media_type, semantics);
  std::unique_ptr<PeerConnectionWrapper> pc2 =
      CreatePeerConnection(media_type, semantics);
  auto transceiver1 = pc1->AddTransceiver(media_type);

  auto offer = pc1->CreateOfferAndSetAsLocal(
      PeerConnectionInterface::RTCOfferAnswerOptions());
  pc2->SetRemoteDescription(std::move(offer));
  auto answer = pc2->CreateAnswerAndSetAsLocal(
      PeerConnectionInterface::RTCOfferAnswerOptions());
  pc1->SetRemoteDescription(std::move(answer));

  auto modified_extensions = transceiver1->GetHeaderExtensionsToNegotiate();
  modified_extensions[3].direction = RtpTransceiverDirection::kStopped;
  transceiver1->SetHeaderExtensionsToNegotiate(modified_extensions);
  auto session_description = pc1->CreateOffer();
  EXPECT_THAT(session_description->description()
                  ->contents()[0]
                  .media_description()
                  ->rtp_header_extensions(),
              ElementsAre(Field(&RtpExtension::uri, "uri2"),
                          Field(&RtpExtension::uri, "uri3")));
}

TEST_P(PeerConnectionHeaderExtensionTest,
       StoppedByDefaultExtensionCanBeActivatedByRemoteSdp) {
  cricket::MediaType media_type;
  SdpSemantics semantics;
  std::tie(media_type, semantics) = GetParam();
  if (semantics != SdpSemantics::kUnifiedPlan)
    return;
  std::unique_ptr<PeerConnectionWrapper> pc1 =
      CreatePeerConnection(media_type, semantics);
  std::unique_ptr<PeerConnectionWrapper> pc2 =
      CreatePeerConnection(media_type, semantics);
  auto transceiver1 = pc1->AddTransceiver(media_type);

  auto offer = pc1->CreateOfferAndSetAsLocal(
      PeerConnectionInterface::RTCOfferAnswerOptions());
  pc2->SetRemoteDescription(std::move(offer));
  auto answer = pc2->CreateAnswerAndSetAsLocal(
      PeerConnectionInterface::RTCOfferAnswerOptions());
  std::string sdp;
  ASSERT_TRUE(answer->ToString(&sdp));
  // We support uri1 but it is stopped by default. Let the remote reactivate it.
  sdp += "a=extmap:15 uri1\r\n";
  auto modified_answer = CreateSessionDescription(SdpType::kAnswer, sdp);
  pc1->SetRemoteDescription(std::move(modified_answer));
  EXPECT_THAT(transceiver1->GetNegotiatedHeaderExtensions(),
              ElementsAre(Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kSendRecv),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kSendRecv),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kSendRecv),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kSendRecv)));
}

TEST_P(PeerConnectionHeaderExtensionTest,
       UnknownExtensionInRemoteOfferDoesNotShowUp) {
  cricket::MediaType media_type;
  SdpSemantics semantics;
  std::tie(media_type, semantics) = GetParam();
  if (semantics != SdpSemantics::kUnifiedPlan)
    return;
  std::unique_ptr<PeerConnectionWrapper> pc =
      CreatePeerConnection(media_type, semantics);
  std::string sdp =
      "v=0\r\n"
      "o=- 0 3 IN IP4 127.0.0.1\r\n"
      "s=-\r\n"
      "t=0 0\r\n"
      "a=fingerprint:sha-256 "
      "A7:24:72:CA:6E:02:55:39:BA:66:DF:6E:CC:4C:D8:B0:1A:BF:1A:56:65:7D:F4:03:"
      "AD:7E:77:43:2A:29:EC:93\r\n"
      "a=ice-ufrag:6HHHdzzeIhkE0CKj\r\n"
      "a=ice-pwd:XYDGVpfvklQIEnZ6YnyLsAew\r\n";
  if (media_type == cricket::MEDIA_TYPE_AUDIO) {
    sdp +=
        "m=audio 9 RTP/AVPF 111\r\n"
        "a=rtpmap:111 fake_audio_codec/8000\r\n";
  } else {
    sdp +=
        "m=video 9 RTP/AVPF 111\r\n"
        "a=rtpmap:111 fake_video_codec/90000\r\n";
  }
  sdp +=
      "c=IN IP4 0.0.0.0\r\n"
      "a=rtcp-mux\r\n"
      "a=sendonly\r\n"
      "a=mid:audio\r\n"
      "a=setup:actpass\r\n"
      "a=extmap:1 urn:bogus\r\n";
  auto offer = CreateSessionDescription(SdpType::kOffer, sdp);
  pc->SetRemoteDescription(std::move(offer));
  pc->CreateAnswerAndSetAsLocal(
      PeerConnectionInterface::RTCOfferAnswerOptions());
  ASSERT_GT(pc->pc()->GetTransceivers().size(), 0u);
  auto transceiver = pc->pc()->GetTransceivers()[0];
  auto negotiated = transceiver->GetNegotiatedHeaderExtensions();
  EXPECT_EQ(negotiated.size(),
            transceiver->GetHeaderExtensionsToNegotiate().size());
  // All extensions are stopped, the "bogus" one does not show up.
  for (const auto& extension : negotiated) {
    EXPECT_EQ(extension.direction, RtpTransceiverDirection::kStopped);
    EXPECT_NE(extension.uri, "urn:bogus");
  }
}

// These tests are regression tests for behavior that the API
// enables in a proper way. It conflicts with the behavior
// of the API to only offer non-stopped extensions.
TEST_P(PeerConnectionHeaderExtensionTest,
       SdpMungingAnswerWithoutApiUsageEnablesExtensions) {
  cricket::MediaType media_type;
  SdpSemantics semantics;
  std::tie(media_type, semantics) = GetParam();
  if (semantics != SdpSemantics::kUnifiedPlan)
    return;
  std::unique_ptr<PeerConnectionWrapper> pc =
      CreatePeerConnection(media_type, semantics);
  std::string sdp =
      "v=0\r\n"
      "o=- 0 3 IN IP4 127.0.0.1\r\n"
      "s=-\r\n"
      "t=0 0\r\n"
      "a=fingerprint:sha-256 "
      "A7:24:72:CA:6E:02:55:39:BA:66:DF:6E:CC:4C:D8:B0:1A:BF:1A:56:65:7D:F4:03:"
      "AD:7E:77:43:2A:29:EC:93\r\n"
      "a=ice-ufrag:6HHHdzzeIhkE0CKj\r\n"
      "a=ice-pwd:XYDGVpfvklQIEnZ6YnyLsAew\r\n";
  if (media_type == cricket::MEDIA_TYPE_AUDIO) {
    sdp +=
        "m=audio 9 RTP/AVPF 111\r\n"
        "a=rtpmap:111 fake_audio_codec/8000\r\n";
  } else {
    sdp +=
        "m=video 9 RTP/AVPF 111\r\n"
        "a=rtpmap:111 fake_video_codec/90000\r\n";
  }
  sdp +=
      "c=IN IP4 0.0.0.0\r\n"
      "a=rtcp-mux\r\n"
      "a=sendrecv\r\n"
      "a=mid:audio\r\n"
      "a=setup:actpass\r\n"
      "a=extmap:1 uri1\r\n";
  auto offer = CreateSessionDescription(SdpType::kOffer, sdp);
  pc->SetRemoteDescription(std::move(offer));
  auto answer =
      pc->CreateAnswer(PeerConnectionInterface::RTCOfferAnswerOptions());
  std::string modified_sdp;
  ASSERT_TRUE(answer->ToString(&modified_sdp));
  modified_sdp += "a=extmap:1 uri1\r\n";
  auto modified_answer =
      CreateSessionDescription(SdpType::kAnswer, modified_sdp);
  ASSERT_TRUE(pc->SetLocalDescription(std::move(modified_answer)));

  auto session_description = pc->CreateOffer();
  EXPECT_THAT(session_description->description()
                  ->contents()[0]
                  .media_description()
                  ->rtp_header_extensions(),
              ElementsAre(Field(&RtpExtension::uri, "uri1"),
                          Field(&RtpExtension::uri, "uri2"),
                          Field(&RtpExtension::uri, "uri3"),
                          Field(&RtpExtension::uri, "uri4")));
}

TEST_P(PeerConnectionHeaderExtensionTest,
       SdpMungingOfferWithoutApiUsageEnablesExtensions) {
  cricket::MediaType media_type;
  SdpSemantics semantics;
  std::tie(media_type, semantics) = GetParam();
  if (semantics != SdpSemantics::kUnifiedPlan)
    return;
  std::unique_ptr<PeerConnectionWrapper> pc =
      CreatePeerConnection(media_type, semantics);
  pc->AddTransceiver(media_type);

  auto offer =
      pc->CreateOffer(PeerConnectionInterface::RTCOfferAnswerOptions());
  std::string modified_sdp;
  ASSERT_TRUE(offer->ToString(&modified_sdp));
  modified_sdp += "a=extmap:1 uri1\r\n";
  auto modified_offer = CreateSessionDescription(SdpType::kOffer, modified_sdp);
  ASSERT_TRUE(pc->SetLocalDescription(std::move(modified_offer)));

  auto offer2 =
      pc->CreateOffer(PeerConnectionInterface::RTCOfferAnswerOptions());
  EXPECT_THAT(offer2->description()
                  ->contents()[0]
                  .media_description()
                  ->rtp_header_extensions(),
              ElementsAre(Field(&RtpExtension::uri, "uri2"),
                          Field(&RtpExtension::uri, "uri3"),
                          Field(&RtpExtension::uri, "uri4"),
                          Field(&RtpExtension::uri, "uri1")));
}

TEST_P(PeerConnectionHeaderExtensionTest, EnablingExtensionsAfterRemoteOffer) {
  cricket::MediaType media_type;
  SdpSemantics semantics;
  std::tie(media_type, semantics) = GetParam();
  if (semantics != SdpSemantics::kUnifiedPlan)
    return;
  std::unique_ptr<PeerConnectionWrapper> pc =
      CreatePeerConnection(media_type, semantics);
  std::string sdp =
      "v=0\r\n"
      "o=- 0 3 IN IP4 127.0.0.1\r\n"
      "s=-\r\n"
      "t=0 0\r\n"
      "a=fingerprint:sha-256 "
      "A7:24:72:CA:6E:02:55:39:BA:66:DF:6E:CC:4C:D8:B0:1A:BF:1A:56:65:7D:F4:03:"
      "AD:7E:77:43:2A:29:EC:93\r\n"
      "a=ice-ufrag:6HHHdzzeIhkE0CKj\r\n"
      "a=ice-pwd:XYDGVpfvklQIEnZ6YnyLsAew\r\n";
  if (media_type == cricket::MEDIA_TYPE_AUDIO) {
    sdp +=
        "m=audio 9 RTP/AVPF 111\r\n"
        "a=rtpmap:111 fake_audio_codec/8000\r\n";
  } else {
    sdp +=
        "m=video 9 RTP/AVPF 111\r\n"
        "a=rtpmap:111 fake_video_codec/90000\r\n";
  }
  sdp +=
      "c=IN IP4 0.0.0.0\r\n"
      "a=rtcp-mux\r\n"
      "a=sendrecv\r\n"
      "a=mid:audio\r\n"
      "a=setup:actpass\r\n"
      "a=extmap:5 uri1\r\n";
  auto offer = CreateSessionDescription(SdpType::kOffer, sdp);
  pc->SetRemoteDescription(std::move(offer));

  ASSERT_GT(pc->pc()->GetTransceivers().size(), 0u);
  auto transceiver = pc->pc()->GetTransceivers()[0];
  auto modified_extensions = transceiver->GetHeaderExtensionsToNegotiate();
  modified_extensions[0].direction = RtpTransceiverDirection::kSendRecv;
  transceiver->SetHeaderExtensionsToNegotiate(modified_extensions);

  pc->CreateAnswerAndSetAsLocal(
      PeerConnectionInterface::RTCOfferAnswerOptions());

  auto session_description = pc->CreateOffer();
  auto extensions = session_description->description()
                        ->contents()[0]
                        .media_description()
                        ->rtp_header_extensions();
  EXPECT_THAT(extensions, ElementsAre(Field(&RtpExtension::uri, "uri1"),
                                      Field(&RtpExtension::uri, "uri2"),
                                      Field(&RtpExtension::uri, "uri3"),
                                      Field(&RtpExtension::uri, "uri4")));
  // Check uri1's id still matches the remote id.
  EXPECT_EQ(extensions[0].id, 5);
}

INSTANTIATE_TEST_SUITE_P(
    ,
    PeerConnectionHeaderExtensionTest,
    Combine(Values(SdpSemantics::kPlanB_DEPRECATED, SdpSemantics::kUnifiedPlan),
            Values(cricket::MediaType::MEDIA_TYPE_AUDIO,
                   cricket::MediaType::MEDIA_TYPE_VIDEO)),
    [](const testing::TestParamInfo<
        PeerConnectionHeaderExtensionTest::ParamType>& info) {
      cricket::MediaType media_type;
      SdpSemantics semantics;
      std::tie(media_type, semantics) = info.param;
      return (rtc::StringBuilder("With")
              << (semantics == SdpSemantics::kPlanB_DEPRECATED ? "PlanB"
                                                               : "UnifiedPlan")
              << "And"
              << (media_type == cricket::MediaType::MEDIA_TYPE_AUDIO ? "Voice"
                                                                     : "Video")
              << "Engine")
          .str();
    });

}  // namespace webrtc
