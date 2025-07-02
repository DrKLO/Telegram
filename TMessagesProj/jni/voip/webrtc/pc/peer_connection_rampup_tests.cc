/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/audio/audio_mixer.h"
#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/audio_codecs/builtin_audio_encoder_factory.h"
#include "api/audio_options.h"
#include "api/create_peerconnection_factory.h"
#include "api/jsep.h"
#include "api/media_stream_interface.h"
#include "api/peer_connection_interface.h"
#include "api/rtc_error.h"
#include "api/scoped_refptr.h"
#include "api/stats/rtc_stats.h"
#include "api/stats/rtc_stats_report.h"
#include "api/stats/rtcstats_objects.h"
#include "api/test/metrics/global_metrics_logger_and_exporter.h"
#include "api/test/metrics/metric.h"
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
#include "modules/audio_device/include/audio_device.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "p2p/base/port_allocator.h"
#include "p2p/base/port_interface.h"
#include "p2p/base/test_turn_server.h"
#include "p2p/client/basic_port_allocator.h"
#include "pc/peer_connection.h"
#include "pc/peer_connection_wrapper.h"
#include "pc/test/fake_audio_capture_module.h"
#include "pc/test/frame_generator_capturer_video_track_source.h"
#include "pc/test/mock_peer_connection_observers.h"
#include "rtc_base/checks.h"
#include "rtc_base/fake_network.h"
#include "rtc_base/firewall_socket_server.h"
#include "rtc_base/gunit.h"
#include "rtc_base/helpers.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/socket_factory.h"
#include "rtc_base/ssl_certificate.h"
#include "rtc_base/task_queue_for_test.h"
#include "rtc_base/test_certificate_verifier.h"
#include "rtc_base/thread.h"
#include "rtc_base/virtual_socket_server.h"
#include "system_wrappers/include/clock.h"
#include "test/gtest.h"

namespace webrtc {
namespace {

using ::webrtc::test::GetGlobalMetricsLogger;
using ::webrtc::test::ImprovementDirection;
using ::webrtc::test::Unit;

static const int kDefaultTestTimeMs = 15000;
static const int kRampUpTimeMs = 5000;
static const int kPollIntervalTimeMs = 50;
static const int kDefaultTimeoutMs = 10000;
static const rtc::SocketAddress kDefaultLocalAddress("1.1.1.1", 0);
static const char kTurnInternalAddress[] = "88.88.88.0";
static const char kTurnExternalAddress[] = "88.88.88.1";
static const int kTurnInternalPort = 3478;
static const int kTurnExternalPort = 0;
// The video's configured max bitrate in webrtcvideoengine.cc is 1.7 Mbps.
// Setting the network bandwidth to 1 Mbps allows the video's bitrate to push
// the network's limitations.
static const int kNetworkBandwidth = 1000000;

}  // namespace

using RTCConfiguration = PeerConnectionInterface::RTCConfiguration;

// This is an end to end test to verify that BWE is functioning when setting
// up a one to one call at the PeerConnection level. The intention of the test
// is to catch potential regressions for different ICE path configurations. The
// test uses a VirtualSocketServer for it's underlying simulated network and
// fake audio and video sources. The test is based upon rampup_tests.cc, but
// instead is at the PeerConnection level and uses a different fake network
// (rampup_tests.cc uses SimulatedNetwork). In the future, this test could
// potentially test different network conditions and test video quality as well
// (video_quality_test.cc does this, but at the call level).
//
// The perf test results are printed using the perf test support. If the
// isolated_script_test_perf_output flag is specified in test_main.cc, then
// the results are written to a JSON formatted file for the Chrome perf
// dashboard. Since this test is a webrtc_perf_test, it will be run in the perf
// console every webrtc commit.
class PeerConnectionWrapperForRampUpTest : public PeerConnectionWrapper {
 public:
  using PeerConnectionWrapper::PeerConnectionWrapper;

  PeerConnectionWrapperForRampUpTest(
      rtc::scoped_refptr<PeerConnectionFactoryInterface> pc_factory,
      rtc::scoped_refptr<PeerConnectionInterface> pc,
      std::unique_ptr<MockPeerConnectionObserver> observer)
      : PeerConnectionWrapper::PeerConnectionWrapper(pc_factory,
                                                     pc,
                                                     std::move(observer)) {}

  bool AddIceCandidates(std::vector<const IceCandidateInterface*> candidates) {
    bool success = true;
    for (const auto candidate : candidates) {
      if (!pc()->AddIceCandidate(candidate)) {
        success = false;
      }
    }
    return success;
  }

  rtc::scoped_refptr<VideoTrackInterface> CreateLocalVideoTrack(
      FrameGeneratorCapturerVideoTrackSource::Config config,
      Clock* clock) {
    video_track_sources_.emplace_back(
        rtc::make_ref_counted<FrameGeneratorCapturerVideoTrackSource>(
            config, clock, /*is_screencast=*/false));
    video_track_sources_.back()->Start();
    return rtc::scoped_refptr<VideoTrackInterface>(
        pc_factory()->CreateVideoTrack(video_track_sources_.back(),
                                       rtc::CreateRandomUuid()));
  }

  rtc::scoped_refptr<AudioTrackInterface> CreateLocalAudioTrack(
      const cricket::AudioOptions options) {
    rtc::scoped_refptr<AudioSourceInterface> source =
        pc_factory()->CreateAudioSource(options);
    return pc_factory()->CreateAudioTrack(rtc::CreateRandomUuid(),
                                          source.get());
  }

 private:
  std::vector<rtc::scoped_refptr<FrameGeneratorCapturerVideoTrackSource>>
      video_track_sources_;
};

// TODO(shampson): Paramaterize the test to run for both Plan B & Unified Plan.
class PeerConnectionRampUpTest : public ::testing::Test {
 public:
  PeerConnectionRampUpTest()
      : clock_(Clock::GetRealTimeClock()),
        virtual_socket_server_(new rtc::VirtualSocketServer()),
        firewall_socket_server_(
            new rtc::FirewallSocketServer(virtual_socket_server_.get())),
        firewall_socket_factory_(
            new rtc::BasicPacketSocketFactory(firewall_socket_server_.get())),
        network_thread_(new rtc::Thread(firewall_socket_server_.get())),
        worker_thread_(rtc::Thread::Create()) {
    network_thread_->SetName("PCNetworkThread", this);
    worker_thread_->SetName("PCWorkerThread", this);
    RTC_CHECK(network_thread_->Start());
    RTC_CHECK(worker_thread_->Start());

    virtual_socket_server_->set_bandwidth(kNetworkBandwidth / 8);
    pc_factory_ = CreatePeerConnectionFactory(
        network_thread_.get(), worker_thread_.get(), rtc::Thread::Current(),
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

  virtual ~PeerConnectionRampUpTest() {
    SendTask(network_thread(), [this] { turn_servers_.clear(); });
  }

  bool CreatePeerConnectionWrappers(const RTCConfiguration& caller_config,
                                    const RTCConfiguration& callee_config) {
    caller_ = CreatePeerConnectionWrapper(caller_config);
    callee_ = CreatePeerConnectionWrapper(callee_config);
    return caller_ && callee_;
  }

  std::unique_ptr<PeerConnectionWrapperForRampUpTest>
  CreatePeerConnectionWrapper(const RTCConfiguration& config) {
    auto* fake_network_manager = new rtc::FakeNetworkManager();
    fake_network_manager->AddInterface(kDefaultLocalAddress);
    fake_network_managers_.emplace_back(fake_network_manager);

    auto observer = std::make_unique<MockPeerConnectionObserver>();
    PeerConnectionDependencies dependencies(observer.get());
    cricket::BasicPortAllocator* port_allocator =
        new cricket::BasicPortAllocator(fake_network_manager,
                                        firewall_socket_factory_.get());

    port_allocator->set_step_delay(cricket::kDefaultStepDelay);
    dependencies.allocator =
        std::unique_ptr<cricket::BasicPortAllocator>(port_allocator);
    dependencies.tls_cert_verifier =
        std::make_unique<rtc::TestCertificateVerifier>();

    auto result = pc_factory_->CreatePeerConnectionOrError(
        config, std::move(dependencies));
    if (!result.ok()) {
      return nullptr;
    }

    return std::make_unique<PeerConnectionWrapperForRampUpTest>(
        pc_factory_, result.MoveValue(), std::move(observer));
  }

  void SetupOneWayCall() {
    ASSERT_TRUE(caller_);
    ASSERT_TRUE(callee_);
    FrameGeneratorCapturerVideoTrackSource::Config config;
    caller_->AddTrack(caller_->CreateLocalVideoTrack(config, clock_));
    // Disable highpass filter so that we can get all the test audio frames.
    cricket::AudioOptions options;
    options.highpass_filter = false;
    caller_->AddTrack(caller_->CreateLocalAudioTrack(options));

    // Do the SDP negotiation, and also exchange ice candidates.
    ASSERT_TRUE(caller_->ExchangeOfferAnswerWith(callee_.get()));
    ASSERT_TRUE_WAIT(
        caller_->signaling_state() == PeerConnectionInterface::kStable,
        kDefaultTimeoutMs);
    ASSERT_TRUE_WAIT(caller_->IsIceGatheringDone(), kDefaultTimeoutMs);
    ASSERT_TRUE_WAIT(callee_->IsIceGatheringDone(), kDefaultTimeoutMs);

    // Connect an ICE candidate pairs.
    ASSERT_TRUE(
        callee_->AddIceCandidates(caller_->observer()->GetAllCandidates()));
    ASSERT_TRUE(
        caller_->AddIceCandidates(callee_->observer()->GetAllCandidates()));
    // This means that ICE and DTLS are connected.
    ASSERT_TRUE_WAIT(callee_->IsIceConnected(), kDefaultTimeoutMs);
    ASSERT_TRUE_WAIT(caller_->IsIceConnected(), kDefaultTimeoutMs);
  }

  void CreateTurnServer(cricket::ProtocolType type,
                        const std::string& common_name = "test turn server") {
    rtc::Thread* thread = network_thread();
    rtc::SocketFactory* factory = firewall_socket_server_.get();
    std::unique_ptr<cricket::TestTurnServer> turn_server;
    SendTask(network_thread_.get(), [&] {
      static const rtc::SocketAddress turn_server_internal_address{
          kTurnInternalAddress, kTurnInternalPort};
      static const rtc::SocketAddress turn_server_external_address{
          kTurnExternalAddress, kTurnExternalPort};
      turn_server = std::make_unique<cricket::TestTurnServer>(
          thread, factory, turn_server_internal_address,
          turn_server_external_address, type, true /*ignore_bad_certs=*/,
          common_name);
    });
    turn_servers_.push_back(std::move(turn_server));
  }

  // First runs the call for kRampUpTimeMs to ramp up the bandwidth estimate.
  // Then runs the test for the remaining test time, grabbing the bandwidth
  // estimation stat, every kPollIntervalTimeMs. When finished, averages the
  // bandwidth estimations and prints the bandwidth estimation result as a perf
  // metric.
  void RunTest(const std::string& test_string) {
    rtc::Thread::Current()->ProcessMessages(kRampUpTimeMs);
    int number_of_polls =
        (kDefaultTestTimeMs - kRampUpTimeMs) / kPollIntervalTimeMs;
    int total_bwe = 0;
    for (int i = 0; i < number_of_polls; ++i) {
      rtc::Thread::Current()->ProcessMessages(kPollIntervalTimeMs);
      total_bwe += static_cast<int>(GetCallerAvailableBitrateEstimate());
    }
    double average_bandwidth_estimate = total_bwe / number_of_polls;
    std::string value_description =
        "bwe_after_" + std::to_string(kDefaultTestTimeMs / 1000) + "_seconds";
    GetGlobalMetricsLogger()->LogSingleValueMetric(
        "peerconnection_ramp_up_" + test_string, value_description,
        average_bandwidth_estimate, Unit::kUnitless,
        ImprovementDirection::kNeitherIsBetter);
  }

  rtc::Thread* network_thread() { return network_thread_.get(); }

  rtc::FirewallSocketServer* firewall_socket_server() {
    return firewall_socket_server_.get();
  }

  PeerConnectionWrapperForRampUpTest* caller() { return caller_.get(); }

  PeerConnectionWrapperForRampUpTest* callee() { return callee_.get(); }

 private:
  // Gets the caller's outgoing available bitrate from the stats. Returns 0 if
  // something went wrong. It takes the outgoing bitrate from the current
  // selected ICE candidate pair's stats.
  double GetCallerAvailableBitrateEstimate() {
    auto stats = caller_->GetStats();
    auto transport_stats = stats->GetStatsOfType<RTCTransportStats>();
    if (transport_stats.size() == 0u ||
        !transport_stats[0]->selected_candidate_pair_id.has_value()) {
      return 0;
    }
    std::string selected_ice_id =
        transport_stats[0]
            ->GetAttribute(transport_stats[0]->selected_candidate_pair_id)
            .ToString();
    // Use the selected ICE candidate pair ID to get the appropriate ICE stats.
    const RTCIceCandidatePairStats ice_candidate_pair_stats =
        stats->Get(selected_ice_id)->cast_to<const RTCIceCandidatePairStats>();
    if (ice_candidate_pair_stats.available_outgoing_bitrate.has_value()) {
      return *ice_candidate_pair_stats.available_outgoing_bitrate;
    }
    // We couldn't get the `available_outgoing_bitrate` for the active candidate
    // pair.
    return 0;
  }

  Clock* const clock_;
  // The turn servers should be accessed & deleted on the network thread to
  // avoid a race with the socket read/write which occurs on the network thread.
  std::vector<std::unique_ptr<cricket::TestTurnServer>> turn_servers_;
  // `virtual_socket_server_` is used by `network_thread_` so it must be
  // destroyed later.
  // TODO(bugs.webrtc.org/7668): We would like to update the virtual network we
  // use for this test. VirtualSocketServer isn't ideal because:
  // 1) It uses the same queue & network capacity for both directions.
  // 2) VirtualSocketServer implements how the network bandwidth affects the
  //    send delay differently than the SimulatedNetwork, used by the
  //    FakeNetworkPipe. It would be ideal if all of levels of virtual
  //    networks used in testing were consistent.
  // We would also like to update this test to record the time to ramp up,
  // down, and back up (similar to in rampup_tests.cc). This is problematic with
  // the VirtualSocketServer. The first ramp down time is very noisy and the
  // second ramp up time can take up to 300 seconds, most likely due to a built
  // up queue.
  std::unique_ptr<rtc::VirtualSocketServer> virtual_socket_server_;
  std::unique_ptr<rtc::FirewallSocketServer> firewall_socket_server_;
  std::unique_ptr<rtc::BasicPacketSocketFactory> firewall_socket_factory_;

  std::unique_ptr<rtc::Thread> network_thread_;
  std::unique_ptr<rtc::Thread> worker_thread_;
  // The `pc_factory` uses `network_thread_` & `worker_thread_`, so it must be
  // destroyed first.
  std::vector<std::unique_ptr<rtc::FakeNetworkManager>> fake_network_managers_;
  rtc::scoped_refptr<PeerConnectionFactoryInterface> pc_factory_;
  std::unique_ptr<PeerConnectionWrapperForRampUpTest> caller_;
  std::unique_ptr<PeerConnectionWrapperForRampUpTest> callee_;
};

TEST_F(PeerConnectionRampUpTest, Bwe_After_TurnOverTCP) {
  CreateTurnServer(cricket::ProtocolType::PROTO_TCP);
  PeerConnectionInterface::IceServer ice_server;
  std::string ice_server_url = "turn:" + std::string(kTurnInternalAddress) +
                               ":" + std::to_string(kTurnInternalPort) +
                               "?transport=tcp";
  ice_server.urls.push_back(ice_server_url);
  ice_server.username = "test";
  ice_server.password = "test";
  PeerConnectionInterface::RTCConfiguration client_1_config;
  client_1_config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  client_1_config.servers.push_back(ice_server);
  client_1_config.type = PeerConnectionInterface::kRelay;
  PeerConnectionInterface::RTCConfiguration client_2_config;
  client_2_config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  client_2_config.servers.push_back(ice_server);
  client_2_config.type = PeerConnectionInterface::kRelay;
  ASSERT_TRUE(CreatePeerConnectionWrappers(client_1_config, client_2_config));

  SetupOneWayCall();
  RunTest("turn_over_tcp");
}

TEST_F(PeerConnectionRampUpTest, Bwe_After_TurnOverUDP) {
  CreateTurnServer(cricket::ProtocolType::PROTO_UDP);
  PeerConnectionInterface::IceServer ice_server;
  std::string ice_server_url = "turn:" + std::string(kTurnInternalAddress) +
                               ":" + std::to_string(kTurnInternalPort);

  ice_server.urls.push_back(ice_server_url);
  ice_server.username = "test";
  ice_server.password = "test";
  PeerConnectionInterface::RTCConfiguration client_1_config;
  client_1_config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  client_1_config.servers.push_back(ice_server);
  client_1_config.type = PeerConnectionInterface::kRelay;
  PeerConnectionInterface::RTCConfiguration client_2_config;
  client_2_config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  client_2_config.servers.push_back(ice_server);
  client_2_config.type = PeerConnectionInterface::kRelay;
  ASSERT_TRUE(CreatePeerConnectionWrappers(client_1_config, client_2_config));

  SetupOneWayCall();
  RunTest("turn_over_udp");
}

TEST_F(PeerConnectionRampUpTest, Bwe_After_TurnOverTLS) {
  CreateTurnServer(cricket::ProtocolType::PROTO_TLS, kTurnInternalAddress);
  PeerConnectionInterface::IceServer ice_server;
  std::string ice_server_url = "turns:" + std::string(kTurnInternalAddress) +
                               ":" + std::to_string(kTurnInternalPort) +
                               "?transport=tcp";
  ice_server.urls.push_back(ice_server_url);
  ice_server.username = "test";
  ice_server.password = "test";
  PeerConnectionInterface::RTCConfiguration client_1_config;
  client_1_config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  client_1_config.servers.push_back(ice_server);
  client_1_config.type = PeerConnectionInterface::kRelay;
  PeerConnectionInterface::RTCConfiguration client_2_config;
  client_2_config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  client_2_config.servers.push_back(ice_server);
  client_2_config.type = PeerConnectionInterface::kRelay;

  ASSERT_TRUE(CreatePeerConnectionWrappers(client_1_config, client_2_config));

  SetupOneWayCall();
  RunTest("turn_over_tls");
}

TEST_F(PeerConnectionRampUpTest, Bwe_After_UDPPeerToPeer) {
  PeerConnectionInterface::RTCConfiguration client_1_config;
  client_1_config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  client_1_config.tcp_candidate_policy =
      PeerConnection::kTcpCandidatePolicyDisabled;
  PeerConnectionInterface::RTCConfiguration client_2_config;
  client_2_config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  client_2_config.tcp_candidate_policy =
      PeerConnection::kTcpCandidatePolicyDisabled;
  ASSERT_TRUE(CreatePeerConnectionWrappers(client_1_config, client_2_config));

  SetupOneWayCall();
  RunTest("udp_peer_to_peer");
}

TEST_F(PeerConnectionRampUpTest, Bwe_After_TCPPeerToPeer) {
  firewall_socket_server()->set_udp_sockets_enabled(false);
  PeerConnectionInterface::RTCConfiguration config;
  config.sdp_semantics = SdpSemantics::kUnifiedPlan;
  ASSERT_TRUE(CreatePeerConnectionWrappers(config, config));

  SetupOneWayCall();
  RunTest("tcp_peer_to_peer");
}

}  // namespace webrtc
