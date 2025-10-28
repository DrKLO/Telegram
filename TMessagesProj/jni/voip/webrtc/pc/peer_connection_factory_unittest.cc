/*
 *  Copyright 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/peer_connection_factory.h"

#include <algorithm>
#include <memory>
#include <utility>
#include <vector>

#include "api/audio/audio_mixer.h"
#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/audio_codecs/builtin_audio_encoder_factory.h"
#include "api/create_peerconnection_factory.h"
#include "api/data_channel_interface.h"
#include "api/enable_media.h"
#include "api/environment/environment_factory.h"
#include "api/jsep.h"
#include "api/media_stream_interface.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "api/test/mock_packet_socket_factory.h"
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
#include "media/base/fake_frame_source.h"
#include "modules/audio_device/include/audio_device.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "p2p/base/fake_port_allocator.h"
#include "p2p/base/port.h"
#include "p2p/base/port_allocator.h"
#include "p2p/base/port_interface.h"
#include "pc/test/fake_audio_capture_module.h"
#include "pc/test/fake_video_track_source.h"
#include "pc/test/mock_peer_connection_observers.h"
#include "rtc_base/gunit.h"
#include "rtc_base/internal/default_socket_server.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/time_utils.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/scoped_key_value_config.h"

#ifdef WEBRTC_ANDROID
#include "pc/test/android_test_initializer.h"
#endif
#include "pc/test/fake_rtc_certificate_generator.h"
#include "pc/test/fake_video_track_renderer.h"

namespace webrtc {
namespace {

using ::testing::_;
using ::testing::AtLeast;
using ::testing::InvokeWithoutArgs;
using ::testing::NiceMock;
using ::testing::Return;
using ::testing::UnorderedElementsAre;

static const char kStunIceServer[] = "stun:stun.l.google.com:19302";
static const char kTurnIceServer[] = "turn:test.com:1234";
static const char kTurnIceServerWithTransport[] =
    "turn:hello.com?transport=tcp";
static const char kSecureTurnIceServer[] = "turns:hello.com?transport=tcp";
static const char kSecureTurnIceServerWithoutTransportParam[] =
    "turns:hello.com:443";
static const char kSecureTurnIceServerWithoutTransportAndPortParam[] =
    "turns:hello.com";
static const char kTurnIceServerWithNoUsernameInUri[] = "turn:test.com:1234";
static const char kTurnPassword[] = "turnpassword";
static const int kDefaultStunPort = 3478;
static const int kDefaultStunTlsPort = 5349;
static const char kTurnUsername[] = "test";
static const char kStunIceServerWithIPv4Address[] = "stun:1.2.3.4:1234";
static const char kStunIceServerWithIPv4AddressWithoutPort[] = "stun:1.2.3.4";
static const char kStunIceServerWithIPv6Address[] = "stun:[2401:fa00:4::]:1234";
static const char kStunIceServerWithIPv6AddressWithoutPort[] =
    "stun:[2401:fa00:4::]";
static const char kTurnIceServerWithIPv6Address[] = "turn:[2401:fa00:4::]:1234";

class NullPeerConnectionObserver : public PeerConnectionObserver {
 public:
  virtual ~NullPeerConnectionObserver() = default;
  void OnSignalingChange(
      PeerConnectionInterface::SignalingState new_state) override {}
  void OnAddStream(rtc::scoped_refptr<MediaStreamInterface> stream) override {}
  void OnRemoveStream(
      rtc::scoped_refptr<MediaStreamInterface> stream) override {}
  void OnDataChannel(
      rtc::scoped_refptr<DataChannelInterface> data_channel) override {}
  void OnRenegotiationNeeded() override {}
  void OnIceConnectionChange(
      PeerConnectionInterface::IceConnectionState new_state) override {}
  void OnIceGatheringChange(
      PeerConnectionInterface::IceGatheringState new_state) override {}
  void OnIceCandidate(const IceCandidateInterface* candidate) override {}
};

class MockNetworkManager : public rtc::NetworkManager {
 public:
  MOCK_METHOD(void, StartUpdating, (), (override));
  MOCK_METHOD(void, StopUpdating, (), (override));
  MOCK_METHOD(std::vector<const rtc::Network*>,
              GetNetworks,
              (),
              (const override));
  MOCK_METHOD(std::vector<const rtc::Network*>,
              GetAnyAddressNetworks,
              (),
              (override));
};

class PeerConnectionFactoryTest : public ::testing::Test {
 public:
  PeerConnectionFactoryTest()
      : socket_server_(rtc::CreateDefaultSocketServer()),
        main_thread_(socket_server_.get()) {}

 private:
  void SetUp() {
#ifdef WEBRTC_ANDROID
    InitializeAndroidObjects();
#endif
    // Use fake audio device module since we're only testing the interface
    // level, and using a real one could make tests flaky e.g. when run in
    // parallel.
    factory_ = CreatePeerConnectionFactory(
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

    ASSERT_TRUE(factory_.get() != NULL);
    packet_socket_factory_.reset(
        new rtc::BasicPacketSocketFactory(socket_server_.get()));
    port_allocator_.reset(new cricket::FakePortAllocator(
        rtc::Thread::Current(), packet_socket_factory_.get(), &field_trials_));
    raw_port_allocator_ = port_allocator_.get();
  }

 protected:
  void VerifyStunServers(cricket::ServerAddresses stun_servers) {
    EXPECT_EQ(stun_servers, raw_port_allocator_->stun_servers());
  }

  void VerifyTurnServers(std::vector<cricket::RelayServerConfig> turn_servers) {
    EXPECT_EQ(turn_servers.size(), raw_port_allocator_->turn_servers().size());
    for (size_t i = 0; i < turn_servers.size(); ++i) {
      ASSERT_EQ(1u, turn_servers[i].ports.size());
      EXPECT_EQ(1u, raw_port_allocator_->turn_servers()[i].ports.size());
      EXPECT_EQ(
          turn_servers[i].ports[0].address.ToString(),
          raw_port_allocator_->turn_servers()[i].ports[0].address.ToString());
      EXPECT_EQ(turn_servers[i].ports[0].proto,
                raw_port_allocator_->turn_servers()[i].ports[0].proto);
      EXPECT_EQ(turn_servers[i].credentials.username,
                raw_port_allocator_->turn_servers()[i].credentials.username);
      EXPECT_EQ(turn_servers[i].credentials.password,
                raw_port_allocator_->turn_servers()[i].credentials.password);
    }
  }

  void VerifyAudioCodecCapability(const RtpCodecCapability& codec) {
    EXPECT_EQ(codec.kind, cricket::MEDIA_TYPE_AUDIO);
    EXPECT_FALSE(codec.name.empty());
    EXPECT_GT(codec.clock_rate, 0);
    EXPECT_GT(codec.num_channels, 0);
  }

  void VerifyVideoCodecCapability(const RtpCodecCapability& codec,
                                  bool sender) {
    EXPECT_EQ(codec.kind, cricket::MEDIA_TYPE_VIDEO);
    EXPECT_FALSE(codec.name.empty());
    EXPECT_GT(codec.clock_rate, 0);
    if (sender) {
      if (codec.name == "VP8" || codec.name == "H264") {
        EXPECT_THAT(
            codec.scalability_modes,
            UnorderedElementsAre(ScalabilityMode::kL1T1, ScalabilityMode::kL1T2,
                                 ScalabilityMode::kL1T3))
            << "Codec: " << codec.name;
      } else if (codec.name == "VP9" || codec.name == "AV1") {
        EXPECT_THAT(
            codec.scalability_modes,
            UnorderedElementsAre(
                // clang-format off
                ScalabilityMode::kL1T1,
                ScalabilityMode::kL1T2,
                ScalabilityMode::kL1T3,
                ScalabilityMode::kL2T1,
                ScalabilityMode::kL2T1h,
                ScalabilityMode::kL2T1_KEY,
                ScalabilityMode::kL2T2,
                ScalabilityMode::kL2T2h,
                ScalabilityMode::kL2T2_KEY,
                ScalabilityMode::kL2T2_KEY_SHIFT,
                ScalabilityMode::kL2T3,
                ScalabilityMode::kL2T3h,
                ScalabilityMode::kL2T3_KEY,
                ScalabilityMode::kL3T1,
                ScalabilityMode::kL3T1h,
                ScalabilityMode::kL3T1_KEY,
                ScalabilityMode::kL3T2,
                ScalabilityMode::kL3T2h,
                ScalabilityMode::kL3T2_KEY,
                ScalabilityMode::kL3T3,
                ScalabilityMode::kL3T3h,
                ScalabilityMode::kL3T3_KEY,
                ScalabilityMode::kS2T1,
                ScalabilityMode::kS2T1h,
                ScalabilityMode::kS2T2,
                ScalabilityMode::kS2T2h,
                ScalabilityMode::kS2T3,
                ScalabilityMode::kS2T3h,
                ScalabilityMode::kS3T1,
                ScalabilityMode::kS3T1h,
                ScalabilityMode::kS3T2,
                ScalabilityMode::kS3T2h,
                ScalabilityMode::kS3T3,
                ScalabilityMode::kS3T3h)
            // clang-format on
            )
            << "Codec: " << codec.name;
      } else {
        EXPECT_TRUE(codec.scalability_modes.empty());
      }
    } else {
      EXPECT_TRUE(codec.scalability_modes.empty());
    }
  }

  test::ScopedKeyValueConfig field_trials_;
  std::unique_ptr<rtc::SocketServer> socket_server_;
  rtc::AutoSocketServerThread main_thread_;
  rtc::scoped_refptr<PeerConnectionFactoryInterface> factory_;
  NullPeerConnectionObserver observer_;
  std::unique_ptr<rtc::PacketSocketFactory> packet_socket_factory_;
  std::unique_ptr<cricket::FakePortAllocator> port_allocator_;
  // Since the PC owns the port allocator after it's been initialized,
  // this should only be used when known to be safe.
  cricket::FakePortAllocator* raw_port_allocator_;
};

// Since there is no public PeerConnectionFactory API to control RTX usage, need
// to reconstruct factory with our own ConnectionContext.
rtc::scoped_refptr<PeerConnectionFactoryInterface>
CreatePeerConnectionFactoryWithRtxDisabled() {
  PeerConnectionFactoryDependencies pcf_dependencies;
  pcf_dependencies.signaling_thread = rtc::Thread::Current();
  pcf_dependencies.worker_thread = rtc::Thread::Current();
  pcf_dependencies.network_thread = rtc::Thread::Current();
  pcf_dependencies.task_queue_factory = CreateDefaultTaskQueueFactory();

  pcf_dependencies.adm = FakeAudioCaptureModule::Create();
  pcf_dependencies.audio_encoder_factory = CreateBuiltinAudioEncoderFactory();
  pcf_dependencies.audio_decoder_factory = CreateBuiltinAudioDecoderFactory();
  pcf_dependencies.video_encoder_factory =
      std::make_unique<VideoEncoderFactoryTemplate<
          LibvpxVp8EncoderTemplateAdapter, LibvpxVp9EncoderTemplateAdapter,
          OpenH264EncoderTemplateAdapter, LibaomAv1EncoderTemplateAdapter>>();
  pcf_dependencies.video_decoder_factory =
      std::make_unique<VideoDecoderFactoryTemplate<
          LibvpxVp8DecoderTemplateAdapter, LibvpxVp9DecoderTemplateAdapter,
          OpenH264DecoderTemplateAdapter, Dav1dDecoderTemplateAdapter>>(),
  EnableMedia(pcf_dependencies);

  rtc::scoped_refptr<ConnectionContext> context =
      ConnectionContext::Create(CreateEnvironment(), &pcf_dependencies);
  context->set_use_rtx(false);
  return rtc::make_ref_counted<PeerConnectionFactory>(context,
                                                      &pcf_dependencies);
}

// Verify creation of PeerConnection using internal ADM, video factory and
// internal libjingle threads.
// TODO(henrika): disabling this test since relying on real audio can result in
// flaky tests and focus on details that are out of scope for you might expect
// for a PeerConnectionFactory unit test.
// See https://bugs.chromium.org/p/webrtc/issues/detail?id=7806 for details.
TEST(PeerConnectionFactoryTestInternal, DISABLED_CreatePCUsingInternalModules) {
#ifdef WEBRTC_ANDROID
  InitializeAndroidObjects();
#endif

  rtc::scoped_refptr<PeerConnectionFactoryInterface> factory(
      CreatePeerConnectionFactory(
          nullptr /* network_thread */, nullptr /* worker_thread */,
          nullptr /* signaling_thread */, nullptr /* default_adm */,
          CreateBuiltinAudioEncoderFactory(),
          CreateBuiltinAudioDecoderFactory(),
          nullptr /* video_encoder_factory */,
          nullptr /* video_decoder_factory */, nullptr /* audio_mixer */,
          nullptr /* audio_processing */));

  NullPeerConnectionObserver observer;
  PeerConnectionInterface::RTCConfiguration config;
  config.sdp_semantics = SdpSemantics::kUnifiedPlan;

  std::unique_ptr<FakeRTCCertificateGenerator> cert_generator(
      new FakeRTCCertificateGenerator());
  PeerConnectionDependencies pc_dependencies(&observer);
  pc_dependencies.cert_generator = std::move(cert_generator);
  auto result =
      factory->CreatePeerConnectionOrError(config, std::move(pc_dependencies));

  EXPECT_TRUE(result.ok());
}

TEST_F(PeerConnectionFactoryTest, CheckRtpSenderAudioCapabilities) {
  RtpCapabilities audio_capabilities =
      factory_->GetRtpSenderCapabilities(cricket::MEDIA_TYPE_AUDIO);
  EXPECT_FALSE(audio_capabilities.codecs.empty());
  for (const auto& codec : audio_capabilities.codecs) {
    VerifyAudioCodecCapability(codec);
  }
  EXPECT_FALSE(audio_capabilities.header_extensions.empty());
  for (const auto& header_extension : audio_capabilities.header_extensions) {
    EXPECT_FALSE(header_extension.uri.empty());
  }
}

TEST_F(PeerConnectionFactoryTest, CheckRtpSenderVideoCapabilities) {
  RtpCapabilities video_capabilities =
      factory_->GetRtpSenderCapabilities(cricket::MEDIA_TYPE_VIDEO);
  EXPECT_FALSE(video_capabilities.codecs.empty());
  for (const auto& codec : video_capabilities.codecs) {
    VerifyVideoCodecCapability(codec, true);
  }
  EXPECT_FALSE(video_capabilities.header_extensions.empty());
  for (const auto& header_extension : video_capabilities.header_extensions) {
    EXPECT_FALSE(header_extension.uri.empty());
  }
}

TEST_F(PeerConnectionFactoryTest, CheckRtpSenderRtxEnabledCapabilities) {
  RtpCapabilities video_capabilities =
      factory_->GetRtpSenderCapabilities(cricket::MEDIA_TYPE_VIDEO);
  const auto it = std::find_if(
      video_capabilities.codecs.begin(), video_capabilities.codecs.end(),
      [](const auto& c) { return c.name == cricket::kRtxCodecName; });
  EXPECT_TRUE(it != video_capabilities.codecs.end());
}

TEST(PeerConnectionFactoryTestInternal, CheckRtpSenderRtxDisabledCapabilities) {
  auto factory = CreatePeerConnectionFactoryWithRtxDisabled();
  RtpCapabilities video_capabilities =
      factory->GetRtpSenderCapabilities(cricket::MEDIA_TYPE_VIDEO);
  const auto it = std::find_if(
      video_capabilities.codecs.begin(), video_capabilities.codecs.end(),
      [](const auto& c) { return c.name == cricket::kRtxCodecName; });
  EXPECT_TRUE(it == video_capabilities.codecs.end());
}

TEST_F(PeerConnectionFactoryTest, CheckRtpSenderDataCapabilities) {
  RtpCapabilities data_capabilities =
      factory_->GetRtpSenderCapabilities(cricket::MEDIA_TYPE_DATA);
  EXPECT_TRUE(data_capabilities.codecs.empty());
  EXPECT_TRUE(data_capabilities.header_extensions.empty());
}

TEST_F(PeerConnectionFactoryTest, CheckRtpReceiverAudioCapabilities) {
  RtpCapabilities audio_capabilities =
      factory_->GetRtpReceiverCapabilities(cricket::MEDIA_TYPE_AUDIO);
  EXPECT_FALSE(audio_capabilities.codecs.empty());
  for (const auto& codec : audio_capabilities.codecs) {
    VerifyAudioCodecCapability(codec);
  }
  EXPECT_FALSE(audio_capabilities.header_extensions.empty());
  for (const auto& header_extension : audio_capabilities.header_extensions) {
    EXPECT_FALSE(header_extension.uri.empty());
  }
}

TEST_F(PeerConnectionFactoryTest, CheckRtpReceiverVideoCapabilities) {
  RtpCapabilities video_capabilities =
      factory_->GetRtpReceiverCapabilities(cricket::MEDIA_TYPE_VIDEO);
  EXPECT_FALSE(video_capabilities.codecs.empty());
  for (const auto& codec : video_capabilities.codecs) {
    VerifyVideoCodecCapability(codec, false);
  }
  EXPECT_FALSE(video_capabilities.header_extensions.empty());
  for (const auto& header_extension : video_capabilities.header_extensions) {
    EXPECT_FALSE(header_extension.uri.empty());
  }
}

TEST_F(PeerConnectionFactoryTest, CheckRtpReceiverRtxEnabledCapabilities) {
  RtpCapabilities video_capabilities =
      factory_->GetRtpReceiverCapabilities(cricket::MEDIA_TYPE_VIDEO);
  const auto it = std::find_if(
      video_capabilities.codecs.begin(), video_capabilities.codecs.end(),
      [](const auto& c) { return c.name == cricket::kRtxCodecName; });
  EXPECT_TRUE(it != video_capabilities.codecs.end());
}

TEST(PeerConnectionFactoryTestInternal,
     CheckRtpReceiverRtxDisabledCapabilities) {
  auto factory = CreatePeerConnectionFactoryWithRtxDisabled();
  RtpCapabilities video_capabilities =
      factory->GetRtpReceiverCapabilities(cricket::MEDIA_TYPE_VIDEO);
  const auto it = std::find_if(
      video_capabilities.codecs.begin(), video_capabilities.codecs.end(),
      [](const auto& c) { return c.name == cricket::kRtxCodecName; });
  EXPECT_TRUE(it == video_capabilities.codecs.end());
}

TEST_F(PeerConnectionFactoryTest, CheckRtpReceiverDataCapabilities) {
  RtpCapabilities data_capabilities =
      factory_->GetRtpReceiverCapabilities(cricket::MEDIA_TYPE_DATA);
  EXPECT_TRUE(data_capabilities.codecs.empty());
  EXPECT_TRUE(data_capabilities.header_extensions.empty());
}

// This test verifies creation of PeerConnection with valid STUN and TURN
// configuration. Also verifies the URL's parsed correctly as expected.
TEST_F(PeerConnectionFactoryTest, CreatePCUsingIceServers) {
  PeerConnectionInterface::RTCConfiguration config;
  config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  PeerConnectionInterface::IceServer ice_server;
  ice_server.uri = kStunIceServer;
  config.servers.push_back(ice_server);
  ice_server.uri = kTurnIceServer;
  ice_server.username = kTurnUsername;
  ice_server.password = kTurnPassword;
  config.servers.push_back(ice_server);
  ice_server.uri = kTurnIceServerWithTransport;
  ice_server.username = kTurnUsername;
  ice_server.password = kTurnPassword;
  config.servers.push_back(ice_server);
  PeerConnectionDependencies pc_dependencies(&observer_);
  pc_dependencies.cert_generator =
      std::make_unique<FakeRTCCertificateGenerator>();
  pc_dependencies.allocator = std::move(port_allocator_);
  auto result =
      factory_->CreatePeerConnectionOrError(config, std::move(pc_dependencies));
  ASSERT_TRUE(result.ok());
  cricket::ServerAddresses stun_servers;
  rtc::SocketAddress stun1("stun.l.google.com", 19302);
  stun_servers.insert(stun1);
  VerifyStunServers(stun_servers);
  std::vector<cricket::RelayServerConfig> turn_servers;
  cricket::RelayServerConfig turn1("test.com", 1234, kTurnUsername,
                                   kTurnPassword, cricket::PROTO_UDP);
  turn_servers.push_back(turn1);
  cricket::RelayServerConfig turn2("hello.com", kDefaultStunPort, kTurnUsername,
                                   kTurnPassword, cricket::PROTO_TCP);
  turn_servers.push_back(turn2);
  VerifyTurnServers(turn_servers);
}

// This test verifies creation of PeerConnection with valid STUN and TURN
// configuration. Also verifies the list of URL's parsed correctly as expected.
TEST_F(PeerConnectionFactoryTest, CreatePCUsingIceServersUrls) {
  PeerConnectionInterface::RTCConfiguration config;
  config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  PeerConnectionInterface::IceServer ice_server;
  ice_server.urls.push_back(kStunIceServer);
  ice_server.urls.push_back(kTurnIceServer);
  ice_server.urls.push_back(kTurnIceServerWithTransport);
  ice_server.username = kTurnUsername;
  ice_server.password = kTurnPassword;
  config.servers.push_back(ice_server);
  PeerConnectionDependencies pc_dependencies(&observer_);
  pc_dependencies.cert_generator =
      std::make_unique<FakeRTCCertificateGenerator>();
  pc_dependencies.allocator = std::move(port_allocator_);
  auto result =
      factory_->CreatePeerConnectionOrError(config, std::move(pc_dependencies));
  ASSERT_TRUE(result.ok());
  cricket::ServerAddresses stun_servers;
  rtc::SocketAddress stun1("stun.l.google.com", 19302);
  stun_servers.insert(stun1);
  VerifyStunServers(stun_servers);
  std::vector<cricket::RelayServerConfig> turn_servers;
  cricket::RelayServerConfig turn1("test.com", 1234, kTurnUsername,
                                   kTurnPassword, cricket::PROTO_UDP);
  turn_servers.push_back(turn1);
  cricket::RelayServerConfig turn2("hello.com", kDefaultStunPort, kTurnUsername,
                                   kTurnPassword, cricket::PROTO_TCP);
  turn_servers.push_back(turn2);
  VerifyTurnServers(turn_servers);
}

TEST_F(PeerConnectionFactoryTest, CreatePCUsingNoUsernameInUri) {
  PeerConnectionInterface::RTCConfiguration config;
  config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  PeerConnectionInterface::IceServer ice_server;
  ice_server.uri = kStunIceServer;
  config.servers.push_back(ice_server);
  ice_server.uri = kTurnIceServerWithNoUsernameInUri;
  ice_server.username = kTurnUsername;
  ice_server.password = kTurnPassword;
  config.servers.push_back(ice_server);
  PeerConnectionDependencies pc_dependencies(&observer_);
  pc_dependencies.cert_generator =
      std::make_unique<FakeRTCCertificateGenerator>();
  pc_dependencies.allocator = std::move(port_allocator_);
  auto result =
      factory_->CreatePeerConnectionOrError(config, std::move(pc_dependencies));
  ASSERT_TRUE(result.ok());
  std::vector<cricket::RelayServerConfig> turn_servers;
  cricket::RelayServerConfig turn("test.com", 1234, kTurnUsername,
                                  kTurnPassword, cricket::PROTO_UDP);
  turn_servers.push_back(turn);
  VerifyTurnServers(turn_servers);
}

// This test verifies the PeerConnection created properly with TURN url which
// has transport parameter in it.
TEST_F(PeerConnectionFactoryTest, CreatePCUsingTurnUrlWithTransportParam) {
  PeerConnectionInterface::RTCConfiguration config;
  config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  PeerConnectionInterface::IceServer ice_server;
  ice_server.uri = kTurnIceServerWithTransport;
  ice_server.username = kTurnUsername;
  ice_server.password = kTurnPassword;
  config.servers.push_back(ice_server);
  PeerConnectionDependencies pc_dependencies(&observer_);
  pc_dependencies.cert_generator =
      std::make_unique<FakeRTCCertificateGenerator>();
  pc_dependencies.allocator = std::move(port_allocator_);
  auto result =
      factory_->CreatePeerConnectionOrError(config, std::move(pc_dependencies));
  ASSERT_TRUE(result.ok());
  std::vector<cricket::RelayServerConfig> turn_servers;
  cricket::RelayServerConfig turn("hello.com", kDefaultStunPort, kTurnUsername,
                                  kTurnPassword, cricket::PROTO_TCP);
  turn_servers.push_back(turn);
  VerifyTurnServers(turn_servers);
}

TEST_F(PeerConnectionFactoryTest, CreatePCUsingSecureTurnUrl) {
  PeerConnectionInterface::RTCConfiguration config;
  config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  PeerConnectionInterface::IceServer ice_server;
  ice_server.uri = kSecureTurnIceServer;
  ice_server.username = kTurnUsername;
  ice_server.password = kTurnPassword;
  config.servers.push_back(ice_server);
  ice_server.uri = kSecureTurnIceServerWithoutTransportParam;
  ice_server.username = kTurnUsername;
  ice_server.password = kTurnPassword;
  config.servers.push_back(ice_server);
  ice_server.uri = kSecureTurnIceServerWithoutTransportAndPortParam;
  ice_server.username = kTurnUsername;
  ice_server.password = kTurnPassword;
  config.servers.push_back(ice_server);
  PeerConnectionDependencies pc_dependencies(&observer_);
  pc_dependencies.cert_generator =
      std::make_unique<FakeRTCCertificateGenerator>();
  pc_dependencies.allocator = std::move(port_allocator_);
  auto result =
      factory_->CreatePeerConnectionOrError(config, std::move(pc_dependencies));
  ASSERT_TRUE(result.ok());
  std::vector<cricket::RelayServerConfig> turn_servers;
  cricket::RelayServerConfig turn1("hello.com", kDefaultStunTlsPort,
                                   kTurnUsername, kTurnPassword,
                                   cricket::PROTO_TLS);
  turn_servers.push_back(turn1);
  // TURNS with transport param should be default to tcp.
  cricket::RelayServerConfig turn2("hello.com", 443, kTurnUsername,
                                   kTurnPassword, cricket::PROTO_TLS);
  turn_servers.push_back(turn2);
  cricket::RelayServerConfig turn3("hello.com", kDefaultStunTlsPort,
                                   kTurnUsername, kTurnPassword,
                                   cricket::PROTO_TLS);
  turn_servers.push_back(turn3);
  VerifyTurnServers(turn_servers);
}

TEST_F(PeerConnectionFactoryTest, CreatePCUsingIPLiteralAddress) {
  PeerConnectionInterface::RTCConfiguration config;
  config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  PeerConnectionInterface::IceServer ice_server;
  ice_server.uri = kStunIceServerWithIPv4Address;
  config.servers.push_back(ice_server);
  ice_server.uri = kStunIceServerWithIPv4AddressWithoutPort;
  config.servers.push_back(ice_server);
  ice_server.uri = kStunIceServerWithIPv6Address;
  config.servers.push_back(ice_server);
  ice_server.uri = kStunIceServerWithIPv6AddressWithoutPort;
  config.servers.push_back(ice_server);
  ice_server.uri = kTurnIceServerWithIPv6Address;
  ice_server.username = kTurnUsername;
  ice_server.password = kTurnPassword;
  config.servers.push_back(ice_server);
  PeerConnectionDependencies pc_dependencies(&observer_);
  pc_dependencies.cert_generator =
      std::make_unique<FakeRTCCertificateGenerator>();
  pc_dependencies.allocator = std::move(port_allocator_);
  auto result =
      factory_->CreatePeerConnectionOrError(config, std::move(pc_dependencies));
  ASSERT_TRUE(result.ok());
  cricket::ServerAddresses stun_servers;
  rtc::SocketAddress stun1("1.2.3.4", 1234);
  stun_servers.insert(stun1);
  rtc::SocketAddress stun2("1.2.3.4", 3478);
  stun_servers.insert(stun2);  // Default port
  rtc::SocketAddress stun3("2401:fa00:4::", 1234);
  stun_servers.insert(stun3);
  rtc::SocketAddress stun4("2401:fa00:4::", 3478);
  stun_servers.insert(stun4);  // Default port
  VerifyStunServers(stun_servers);

  std::vector<cricket::RelayServerConfig> turn_servers;
  cricket::RelayServerConfig turn1("2401:fa00:4::", 1234, kTurnUsername,
                                   kTurnPassword, cricket::PROTO_UDP);
  turn_servers.push_back(turn1);
  VerifyTurnServers(turn_servers);
}

// This test verifies the captured stream is rendered locally using a
// local video track.
TEST_F(PeerConnectionFactoryTest, LocalRendering) {
  rtc::scoped_refptr<FakeVideoTrackSource> source =
      FakeVideoTrackSource::Create(/*is_screencast=*/false);

  cricket::FakeFrameSource frame_source(1280, 720,
                                        rtc::kNumMicrosecsPerSec / 30);

  ASSERT_TRUE(source.get() != NULL);
  rtc::scoped_refptr<VideoTrackInterface> track(
      factory_->CreateVideoTrack(source, "testlabel"));
  ASSERT_TRUE(track.get() != NULL);
  FakeVideoTrackRenderer local_renderer(track.get());

  EXPECT_EQ(0, local_renderer.num_rendered_frames());
  source->InjectFrame(frame_source.GetFrame());
  EXPECT_EQ(1, local_renderer.num_rendered_frames());
  EXPECT_FALSE(local_renderer.black_frame());

  track->set_enabled(false);
  source->InjectFrame(frame_source.GetFrame());
  EXPECT_EQ(2, local_renderer.num_rendered_frames());
  EXPECT_TRUE(local_renderer.black_frame());

  track->set_enabled(true);
  source->InjectFrame(frame_source.GetFrame());
  EXPECT_EQ(3, local_renderer.num_rendered_frames());
  EXPECT_FALSE(local_renderer.black_frame());
}

TEST(PeerConnectionFactoryDependenciesTest, UsesNetworkManager) {
  constexpr TimeDelta kWaitTimeout = TimeDelta::Seconds(10);
  auto mock_network_manager = std::make_unique<NiceMock<MockNetworkManager>>();

  rtc::Event called;
  EXPECT_CALL(*mock_network_manager, StartUpdating())
      .Times(AtLeast(1))
      .WillRepeatedly(InvokeWithoutArgs([&] { called.Set(); }));

  PeerConnectionFactoryDependencies pcf_dependencies;
  pcf_dependencies.network_manager = std::move(mock_network_manager);

  rtc::scoped_refptr<PeerConnectionFactoryInterface> pcf =
      CreateModularPeerConnectionFactory(std::move(pcf_dependencies));

  PeerConnectionInterface::RTCConfiguration config;
  config.ice_candidate_pool_size = 2;
  NullPeerConnectionObserver observer;
  auto pc = pcf->CreatePeerConnectionOrError(
      config, PeerConnectionDependencies(&observer));
  ASSERT_TRUE(pc.ok());

  called.Wait(kWaitTimeout);
}

TEST(PeerConnectionFactoryDependenciesTest, UsesPacketSocketFactory) {
  constexpr TimeDelta kWaitTimeout = TimeDelta::Seconds(10);
  auto mock_socket_factory =
      std::make_unique<NiceMock<rtc::MockPacketSocketFactory>>();

  rtc::Event called;
  EXPECT_CALL(*mock_socket_factory, CreateUdpSocket(_, _, _))
      .WillOnce(InvokeWithoutArgs([&] {
        called.Set();
        return nullptr;
      }))
      .WillRepeatedly(Return(nullptr));

  PeerConnectionFactoryDependencies pcf_dependencies;
  pcf_dependencies.packet_socket_factory = std::move(mock_socket_factory);

  rtc::scoped_refptr<PeerConnectionFactoryInterface> pcf =
      CreateModularPeerConnectionFactory(std::move(pcf_dependencies));

  // By default, localhost addresses are ignored, which makes tests fail if test
  // machine is offline.
  PeerConnectionFactoryInterface::Options options;
  options.network_ignore_mask = 0;
  pcf->SetOptions(options);

  PeerConnectionInterface::RTCConfiguration config;
  config.ice_candidate_pool_size = 2;
  NullPeerConnectionObserver observer;
  auto pc = pcf->CreatePeerConnectionOrError(
      config, PeerConnectionDependencies(&observer));
  ASSERT_TRUE(pc.ok());

  called.Wait(kWaitTimeout);
}

}  // namespace
}  // namespace webrtc
