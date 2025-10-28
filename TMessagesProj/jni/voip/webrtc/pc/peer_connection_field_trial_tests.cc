/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file contains tests that verify that field trials do what they're
// supposed to do.

#include <set>

#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/audio_codecs/builtin_audio_encoder_factory.h"
#include "api/create_peerconnection_factory.h"
#include "api/enable_media_with_defaults.h"
#include "api/peer_connection_interface.h"
#include "api/stats/rtcstats_objects.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "api/video_codecs/builtin_video_decoder_factory.h"
#include "api/video_codecs/builtin_video_encoder_factory.h"
#include "media/engine/webrtc_media_engine.h"
#include "pc/peer_connection_wrapper.h"
#include "pc/session_description.h"
#include "pc/test/fake_audio_capture_module.h"
#include "pc/test/frame_generator_capturer_video_track_source.h"
#include "pc/test/peer_connection_test_wrapper.h"
#include "rtc_base/gunit.h"
#include "rtc_base/internal/default_socket_server.h"
#include "rtc_base/physical_socket_server.h"
#include "rtc_base/thread.h"
#include "test/gtest.h"
#include "test/scoped_key_value_config.h"

#ifdef WEBRTC_ANDROID
#include "pc/test/android_test_initializer.h"
#endif

namespace webrtc {

namespace {
static const int kDefaultTimeoutMs = 5000;

bool AddIceCandidates(PeerConnectionWrapper* peer,
                      std::vector<const IceCandidateInterface*> candidates) {
  for (const auto candidate : candidates) {
    if (!peer->pc()->AddIceCandidate(candidate)) {
      return false;
    }
  }
  return true;
}
}  // namespace

using RTCConfiguration = PeerConnectionInterface::RTCConfiguration;

class PeerConnectionFieldTrialTest : public ::testing::Test {
 protected:
  typedef std::unique_ptr<PeerConnectionWrapper> WrapperPtr;

  PeerConnectionFieldTrialTest()
      : clock_(Clock::GetRealTimeClock()),
        socket_server_(rtc::CreateDefaultSocketServer()),
        main_thread_(socket_server_.get()) {
#ifdef WEBRTC_ANDROID
    InitializeAndroidObjects();
#endif
    PeerConnectionInterface::IceServer ice_server;
    ice_server.uri = "stun:stun.l.google.com:19302";
    config_.servers.push_back(ice_server);
    config_.sdp_semantics = SdpSemantics::kUnifiedPlan;
  }

  void TearDown() override { pc_factory_ = nullptr; }

  void CreatePCFactory(std::unique_ptr<FieldTrialsView> field_trials) {
    PeerConnectionFactoryDependencies pcf_deps;
    pcf_deps.signaling_thread = rtc::Thread::Current();
    pcf_deps.trials = std::move(field_trials);
    pcf_deps.task_queue_factory = CreateDefaultTaskQueueFactory();
    pcf_deps.adm = FakeAudioCaptureModule::Create();
    EnableMediaWithDefaults(pcf_deps);
    pc_factory_ = CreateModularPeerConnectionFactory(std::move(pcf_deps));

    // Allow ADAPTER_TYPE_LOOPBACK to create PeerConnections with loopback in
    // this test.
    RTC_DCHECK(pc_factory_);
    PeerConnectionFactoryInterface::Options options;
    options.network_ignore_mask = 0;
    pc_factory_->SetOptions(options);
  }

  WrapperPtr CreatePeerConnection() {
    auto observer = std::make_unique<MockPeerConnectionObserver>();
    auto result = pc_factory_->CreatePeerConnectionOrError(
        config_, PeerConnectionDependencies(observer.get()));
    RTC_CHECK(result.ok());

    observer->SetPeerConnectionInterface(result.value().get());
    return std::make_unique<PeerConnectionWrapper>(
        pc_factory_, result.MoveValue(), std::move(observer));
  }

  Clock* const clock_;
  std::unique_ptr<rtc::SocketServer> socket_server_;
  rtc::AutoSocketServerThread main_thread_;
  rtc::scoped_refptr<PeerConnectionFactoryInterface> pc_factory_ = nullptr;
  PeerConnectionInterface::RTCConfiguration config_;
};

// Tests for the dependency descriptor field trial. The dependency descriptor
// field trial is implemented in media/engine/webrtc_video_engine.cc.
TEST_F(PeerConnectionFieldTrialTest, EnableDependencyDescriptorAdvertised) {
  std::unique_ptr<test::ScopedKeyValueConfig> field_trials =
      std::make_unique<test::ScopedKeyValueConfig>(
          "WebRTC-DependencyDescriptorAdvertised/Enabled/");
  CreatePCFactory(std::move(field_trials));

  WrapperPtr caller = CreatePeerConnection();
  caller->AddTransceiver(cricket::MEDIA_TYPE_VIDEO);

  auto offer = caller->CreateOffer();
  auto contents1 = offer->description()->contents();
  ASSERT_EQ(1u, contents1.size());

  const cricket::MediaContentDescription* media_description1 =
      contents1[0].media_description();
  EXPECT_EQ(cricket::MEDIA_TYPE_VIDEO, media_description1->type());
  const cricket::RtpHeaderExtensions& rtp_header_extensions1 =
      media_description1->rtp_header_extensions();

  bool found = absl::c_find_if(rtp_header_extensions1,
                               [](const RtpExtension& rtp_extension) {
                                 return rtp_extension.uri ==
                                        RtpExtension::kDependencyDescriptorUri;
                               }) != rtp_header_extensions1.end();
  EXPECT_TRUE(found);
}

// Tests that dependency descriptor RTP header extensions can be exchanged
// via SDP munging, even if dependency descriptor field trial is disabled.
TEST_F(PeerConnectionFieldTrialTest, InjectDependencyDescriptor) {
  std::unique_ptr<test::ScopedKeyValueConfig> field_trials =
      std::make_unique<test::ScopedKeyValueConfig>(
          "WebRTC-DependencyDescriptorAdvertised/Disabled/");
  CreatePCFactory(std::move(field_trials));

  WrapperPtr caller = CreatePeerConnection();
  WrapperPtr callee = CreatePeerConnection();
  caller->AddTransceiver(cricket::MEDIA_TYPE_VIDEO);

  auto offer = caller->CreateOffer();
  cricket::ContentInfos& contents1 = offer->description()->contents();
  ASSERT_EQ(1u, contents1.size());

  cricket::MediaContentDescription* media_description1 =
      contents1[0].media_description();
  EXPECT_EQ(cricket::MEDIA_TYPE_VIDEO, media_description1->type());
  cricket::RtpHeaderExtensions rtp_header_extensions1 =
      media_description1->rtp_header_extensions();

  bool found1 = absl::c_find_if(rtp_header_extensions1,
                                [](const RtpExtension& rtp_extension) {
                                  return rtp_extension.uri ==
                                         RtpExtension::kDependencyDescriptorUri;
                                }) != rtp_header_extensions1.end();
  EXPECT_FALSE(found1);

  std::set<int> existing_ids;
  for (const RtpExtension& rtp_extension : rtp_header_extensions1) {
    existing_ids.insert(rtp_extension.id);
  }

  // Find the currently unused RTP header extension ID.
  int insert_id = 1;
  std::set<int>::const_iterator iter = existing_ids.begin();
  while (true) {
    if (iter == existing_ids.end()) {
      break;
    }
    if (*iter != insert_id) {
      break;
    }
    insert_id++;
    iter++;
  }

  rtp_header_extensions1.emplace_back(RtpExtension::kDependencyDescriptorUri,
                                      insert_id);
  media_description1->set_rtp_header_extensions(rtp_header_extensions1);

  caller->SetLocalDescription(offer->Clone());

  ASSERT_TRUE(callee->SetRemoteDescription(std::move(offer)));
  auto answer = callee->CreateAnswer();

  cricket::ContentInfos& contents2 = answer->description()->contents();
  ASSERT_EQ(1u, contents2.size());

  cricket::MediaContentDescription* media_description2 =
      contents2[0].media_description();
  EXPECT_EQ(cricket::MEDIA_TYPE_VIDEO, media_description2->type());
  cricket::RtpHeaderExtensions rtp_header_extensions2 =
      media_description2->rtp_header_extensions();

  bool found2 = absl::c_find_if(rtp_header_extensions2,
                                [](const RtpExtension& rtp_extension) {
                                  return rtp_extension.uri ==
                                         RtpExtension::kDependencyDescriptorUri;
                                }) != rtp_header_extensions2.end();
  EXPECT_TRUE(found2);
}

// Test that the ability to emulate degraded networks works without crashing.
TEST_F(PeerConnectionFieldTrialTest, ApplyFakeNetworkConfig) {
  std::unique_ptr<test::ScopedKeyValueConfig> field_trials =
      std::make_unique<test::ScopedKeyValueConfig>(
          "WebRTC-FakeNetworkSendConfig/link_capacity_kbps:500/"
          "WebRTC-FakeNetworkReceiveConfig/loss_percent:1/");

  CreatePCFactory(std::move(field_trials));

  WrapperPtr caller = CreatePeerConnection();
  BitrateSettings bitrate_settings;
  bitrate_settings.start_bitrate_bps = 1'000'000;
  bitrate_settings.max_bitrate_bps = 1'000'000;
  caller->pc()->SetBitrate(bitrate_settings);
  FrameGeneratorCapturerVideoTrackSource::Config config;
  auto video_track_source =
      rtc::make_ref_counted<FrameGeneratorCapturerVideoTrackSource>(
          config, clock_, /*is_screencast=*/false);
  video_track_source->Start();
  caller->AddTrack(pc_factory_->CreateVideoTrack(video_track_source, "v"));
  WrapperPtr callee = CreatePeerConnection();

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));
  ASSERT_TRUE(
      caller->SetRemoteDescription(callee->CreateAnswerAndSetAsLocal()));

  // Do the SDP negotiation, and also exchange ice candidates.
  ASSERT_TRUE(caller->ExchangeOfferAnswerWith(callee.get()));
  ASSERT_TRUE_WAIT(
      caller->signaling_state() == PeerConnectionInterface::kStable,
      kDefaultTimeoutMs);
  ASSERT_TRUE_WAIT(caller->IsIceGatheringDone(), kDefaultTimeoutMs);
  ASSERT_TRUE_WAIT(callee->IsIceGatheringDone(), kDefaultTimeoutMs);

  // Connect an ICE candidate pairs.
  ASSERT_TRUE(
      AddIceCandidates(callee.get(), caller->observer()->GetAllCandidates()));
  ASSERT_TRUE(
      AddIceCandidates(caller.get(), callee->observer()->GetAllCandidates()));

  // This means that ICE and DTLS are connected.
  ASSERT_TRUE_WAIT(callee->IsIceConnected(), kDefaultTimeoutMs);
  ASSERT_TRUE_WAIT(caller->IsIceConnected(), kDefaultTimeoutMs);

  // Send packets for kDefaultTimeoutMs
  WAIT(false, kDefaultTimeoutMs);

  std::vector<const RTCOutboundRtpStreamStats*> outbound_rtp_stats =
      caller->GetStats()->GetStatsOfType<RTCOutboundRtpStreamStats>();
  ASSERT_GE(outbound_rtp_stats.size(), 1u);
  ASSERT_TRUE(outbound_rtp_stats[0]->target_bitrate.has_value());
  // Link capacity is limited to 500k, so BWE is expected to be close to 500k.
  ASSERT_LE(*outbound_rtp_stats[0]->target_bitrate, 500'000 * 1.1);
}

}  // namespace webrtc
