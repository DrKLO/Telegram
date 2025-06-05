/*
 *  Copyright 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_TEST_INTEGRATION_TEST_HELPERS_H_
#define PC_TEST_INTEGRATION_TEST_HELPERS_H_

#include <limits.h>
#include <stdint.h>
#include <stdio.h>

#include <algorithm>
#include <functional>
#include <limits>
#include <list>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/memory/memory.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/audio_options.h"
#include "api/candidate.h"
#include "api/crypto/crypto_options.h"
#include "api/data_channel_interface.h"
#include "api/enable_media_with_defaults.h"
#include "api/field_trials_view.h"
#include "api/ice_transport_interface.h"
#include "api/jsep.h"
#include "api/media_stream_interface.h"
#include "api/media_types.h"
#include "api/peer_connection_interface.h"
#include "api/rtc_error.h"
#include "api/rtc_event_log/rtc_event_log_factory.h"
#include "api/rtc_event_log/rtc_event_log_factory_interface.h"
#include "api/rtc_event_log_output.h"
#include "api/rtp_receiver_interface.h"
#include "api/rtp_sender_interface.h"
#include "api/rtp_transceiver_interface.h"
#include "api/scoped_refptr.h"
#include "api/stats/rtc_stats.h"
#include "api/stats/rtc_stats_report.h"
#include "api/stats/rtcstats_objects.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "api/task_queue/task_queue_factory.h"
#include "api/test/mock_async_dns_resolver.h"
#include "api/transport/field_trial_based_config.h"
#include "api/uma_metrics.h"
#include "api/units/time_delta.h"
#include "api/video/video_rotation.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_decoder_factory.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "call/call.h"
#include "logging/rtc_event_log/fake_rtc_event_log_factory.h"
#include "media/base/media_engine.h"
#include "media/base/stream_params.h"
#include "media/engine/fake_webrtc_video_engine.h"
#include "modules/audio_device/include/audio_device.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "modules/audio_processing/test/audio_processing_builder_for_testing.h"
#include "p2p/base/fake_ice_transport.h"
#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/port.h"
#include "p2p/base/port_allocator.h"
#include "p2p/base/port_interface.h"
#include "p2p/base/test_stun_server.h"
#include "p2p/base/test_turn_customizer.h"
#include "p2p/base/test_turn_server.h"
#include "p2p/client/basic_port_allocator.h"
#include "pc/dtmf_sender.h"
#include "pc/local_audio_source.h"
#include "pc/media_session.h"
#include "pc/peer_connection.h"
#include "pc/peer_connection_factory.h"
#include "pc/peer_connection_proxy.h"
#include "pc/rtp_media_utils.h"
#include "pc/session_description.h"
#include "pc/test/fake_audio_capture_module.h"
#include "pc/test/fake_periodic_video_source.h"
#include "pc/test/fake_periodic_video_track_source.h"
#include "pc/test/fake_rtc_certificate_generator.h"
#include "pc/test/fake_video_track_renderer.h"
#include "pc/test/mock_peer_connection_observers.h"
#include "pc/video_track_source.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/fake_clock.h"
#include "rtc_base/fake_mdns_responder.h"
#include "rtc_base/fake_network.h"
#include "rtc_base/firewall_socket_server.h"
#include "rtc_base/gunit.h"
#include "rtc_base/helpers.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/logging.h"
#include "rtc_base/mdns_responder_interface.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/task_queue_for_test.h"
#include "rtc_base/task_utils/repeating_task.h"
#include "rtc_base/test_certificate_verifier.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/virtual_socket_server.h"
#include "system_wrappers/include/metrics.h"
#include "test/gmock.h"
#include "test/scoped_key_value_config.h"

namespace webrtc {

using ::cricket::ContentInfo;
using ::cricket::StreamParams;
using ::rtc::SocketAddress;
using ::testing::_;
using ::testing::Combine;
using ::testing::Contains;
using ::testing::DoAll;
using ::testing::ElementsAre;
using ::testing::InvokeArgument;
using ::testing::NiceMock;
using ::testing::Return;
using ::testing::SetArgPointee;
using ::testing::UnorderedElementsAreArray;
using ::testing::Values;
using RTCConfiguration = PeerConnectionInterface::RTCConfiguration;

static const int kDefaultTimeout = 10000;
static const int kMaxWaitForStatsMs = 3000;
static const int kMaxWaitForActivationMs = 5000;
static const int kMaxWaitForFramesMs = 10000;
// Default number of audio/video frames to wait for before considering a test
// successful.
static const int kDefaultExpectedAudioFrameCount = 3;
static const int kDefaultExpectedVideoFrameCount = 3;

static const char kDataChannelLabel[] = "data_channel";

// SRTP cipher name negotiated by the tests. This must be updated if the
// default changes.
static const int kDefaultSrtpCryptoSuite = rtc::kSrtpAes128CmSha1_80;
static const int kDefaultSrtpCryptoSuiteGcm = rtc::kSrtpAeadAes256Gcm;

static const SocketAddress kDefaultLocalAddress("192.168.1.1", 0);

// Helper function for constructing offer/answer options to initiate an ICE
// restart.
PeerConnectionInterface::RTCOfferAnswerOptions IceRestartOfferAnswerOptions();

// Remove all stream information (SSRCs, track IDs, etc.) and "msid-semantic"
// attribute from received SDP, simulating a legacy endpoint.
void RemoveSsrcsAndMsids(cricket::SessionDescription* desc);

// Removes all stream information besides the stream ids, simulating an
// endpoint that only signals a=msid lines to convey stream_ids.
void RemoveSsrcsAndKeepMsids(cricket::SessionDescription* desc);

// Replaces the stream's primary SSRC and updates the first SSRC of all
// ssrc-groups.
void ReplaceFirstSsrc(StreamParams& stream, uint32_t ssrc);

int FindFirstMediaStatsIndexByKind(
    const std::string& kind,
    const std::vector<const RTCInboundRtpStreamStats*>& inbound_rtps);

class TaskQueueMetronome : public Metronome {
 public:
  explicit TaskQueueMetronome(TimeDelta tick_period);
  ~TaskQueueMetronome() override;

  // Metronome implementation.
  void RequestCallOnNextTick(absl::AnyInvocable<void() &&> callback) override;
  TimeDelta TickPeriod() const override;

 private:
  const TimeDelta tick_period_;
  SequenceChecker sequence_checker_{SequenceChecker::kDetached};
  std::vector<absl::AnyInvocable<void() &&>> callbacks_;
  ScopedTaskSafetyDetached safety_;
};

class SignalingMessageReceiver {
 public:
  virtual void ReceiveSdpMessage(SdpType type, const std::string& msg) = 0;
  virtual void ReceiveIceMessage(const std::string& sdp_mid,
                                 int sdp_mline_index,
                                 const std::string& msg) = 0;

 protected:
  SignalingMessageReceiver() {}
  virtual ~SignalingMessageReceiver() {}
};

class MockRtpReceiverObserver : public RtpReceiverObserverInterface {
 public:
  explicit MockRtpReceiverObserver(cricket::MediaType media_type)
      : expected_media_type_(media_type) {}

  void OnFirstPacketReceived(cricket::MediaType media_type) override {
    ASSERT_EQ(expected_media_type_, media_type);
    first_packet_received_ = true;
  }

  bool first_packet_received() const { return first_packet_received_; }

  virtual ~MockRtpReceiverObserver() {}

 private:
  bool first_packet_received_ = false;
  cricket::MediaType expected_media_type_;
};

// Helper class that wraps a peer connection, observes it, and can accept
// signaling messages from another wrapper.
//
// Uses a fake network, fake A/V capture, and optionally fake
// encoders/decoders, though they aren't used by default since they don't
// advertise support of any codecs.
// TODO(steveanton): See how this could become a subclass of
// PeerConnectionWrapper defined in peerconnectionwrapper.h.
class PeerConnectionIntegrationWrapper : public PeerConnectionObserver,
                                         public SignalingMessageReceiver {
 public:
  PeerConnectionFactoryInterface* pc_factory() const {
    return peer_connection_factory_.get();
  }

  PeerConnectionInterface* pc() const { return peer_connection_.get(); }

  // If a signaling message receiver is set (via ConnectFakeSignaling), this
  // will set the whole offer/answer exchange in motion. Just need to wait for
  // the signaling state to reach "stable".
  void CreateAndSetAndSignalOffer() {
    auto offer = CreateOfferAndWait();
    ASSERT_NE(nullptr, offer);
    EXPECT_TRUE(SetLocalDescriptionAndSendSdpMessage(std::move(offer)));
  }

  // Sets the options to be used when CreateAndSetAndSignalOffer is called, or
  // when a remote offer is received (via fake signaling) and an answer is
  // generated. By default, uses default options.
  void SetOfferAnswerOptions(
      const PeerConnectionInterface::RTCOfferAnswerOptions& options) {
    offer_answer_options_ = options;
  }

  // Set a callback to be invoked when SDP is received via the fake signaling
  // channel, which provides an opportunity to munge (modify) the SDP. This is
  // used to test SDP being applied that a PeerConnection would normally not
  // generate, but a non-JSEP endpoint might.
  void SetReceivedSdpMunger(
      std::function<void(cricket::SessionDescription*)> munger) {
    received_sdp_munger_ = std::move(munger);
  }

  // Similar to the above, but this is run on SDP immediately after it's
  // generated.
  void SetGeneratedSdpMunger(
      std::function<void(cricket::SessionDescription*)> munger) {
    generated_sdp_munger_ = std::move(munger);
  }

  // Set a callback to be invoked when a remote offer is received via the fake
  // signaling channel. This provides an opportunity to change the
  // PeerConnection state before an answer is created and sent to the caller.
  void SetRemoteOfferHandler(std::function<void()> handler) {
    remote_offer_handler_ = std::move(handler);
  }

  void SetRemoteAsyncResolver(MockAsyncDnsResolver* resolver) {
    remote_async_dns_resolver_ = resolver;
  }

  // Every ICE connection state in order that has been seen by the observer.
  std::vector<PeerConnectionInterface::IceConnectionState>
  ice_connection_state_history() const {
    return ice_connection_state_history_;
  }
  void clear_ice_connection_state_history() {
    ice_connection_state_history_.clear();
  }

  // Every standardized ICE connection state in order that has been seen by the
  // observer.
  std::vector<PeerConnectionInterface::IceConnectionState>
  standardized_ice_connection_state_history() const {
    return standardized_ice_connection_state_history_;
  }

  // Every PeerConnection state in order that has been seen by the observer.
  std::vector<PeerConnectionInterface::PeerConnectionState>
  peer_connection_state_history() const {
    return peer_connection_state_history_;
  }

  // Every ICE gathering state in order that has been seen by the observer.
  std::vector<PeerConnectionInterface::IceGatheringState>
  ice_gathering_state_history() const {
    return ice_gathering_state_history_;
  }
  std::vector<cricket::CandidatePairChangeEvent>
  ice_candidate_pair_change_history() const {
    return ice_candidate_pair_change_history_;
  }

  // Every PeerConnection signaling state in order that has been seen by the
  // observer.
  std::vector<PeerConnectionInterface::SignalingState>
  peer_connection_signaling_state_history() const {
    return peer_connection_signaling_state_history_;
  }

  void AddAudioVideoTracks() {
    AddAudioTrack();
    AddVideoTrack();
  }

  rtc::scoped_refptr<RtpSenderInterface> AddAudioTrack() {
    return AddTrack(CreateLocalAudioTrack());
  }

  rtc::scoped_refptr<RtpSenderInterface> AddVideoTrack() {
    return AddTrack(CreateLocalVideoTrack());
  }

  rtc::scoped_refptr<AudioTrackInterface> CreateLocalAudioTrack() {
    cricket::AudioOptions options;
    // Disable highpass filter so that we can get all the test audio frames.
    options.highpass_filter = false;
    rtc::scoped_refptr<AudioSourceInterface> source =
        peer_connection_factory_->CreateAudioSource(options);
    // TODO(perkj): Test audio source when it is implemented. Currently audio
    // always use the default input.
    return peer_connection_factory_->CreateAudioTrack(rtc::CreateRandomUuid(),
                                                      source.get());
  }

  rtc::scoped_refptr<VideoTrackInterface> CreateLocalVideoTrack() {
    FakePeriodicVideoSource::Config config;
    config.timestamp_offset_ms = rtc::TimeMillis();
    return CreateLocalVideoTrackInternal(config);
  }

  rtc::scoped_refptr<VideoTrackInterface> CreateLocalVideoTrackWithConfig(
      FakePeriodicVideoSource::Config config) {
    return CreateLocalVideoTrackInternal(config);
  }

  rtc::scoped_refptr<VideoTrackInterface> CreateLocalVideoTrackWithRotation(
      VideoRotation rotation) {
    FakePeriodicVideoSource::Config config;
    config.rotation = rotation;
    config.timestamp_offset_ms = rtc::TimeMillis();
    return CreateLocalVideoTrackInternal(config);
  }

  rtc::scoped_refptr<RtpSenderInterface> AddTrack(
      rtc::scoped_refptr<MediaStreamTrackInterface> track,
      const std::vector<std::string>& stream_ids = {}) {
    EXPECT_TRUE(track);
    if (!track) {
      return nullptr;
    }
    auto result = pc()->AddTrack(track, stream_ids);
    EXPECT_EQ(RTCErrorType::NONE, result.error().type());
    if (result.ok()) {
      return result.MoveValue();
    } else {
      return nullptr;
    }
  }

  std::vector<rtc::scoped_refptr<RtpReceiverInterface>> GetReceiversOfType(
      cricket::MediaType media_type) {
    std::vector<rtc::scoped_refptr<RtpReceiverInterface>> receivers;
    for (const auto& receiver : pc()->GetReceivers()) {
      if (receiver->media_type() == media_type) {
        receivers.push_back(receiver);
      }
    }
    return receivers;
  }

  rtc::scoped_refptr<RtpTransceiverInterface> GetFirstTransceiverOfType(
      cricket::MediaType media_type) {
    for (auto transceiver : pc()->GetTransceivers()) {
      if (transceiver->receiver()->media_type() == media_type) {
        return transceiver;
      }
    }
    return nullptr;
  }

  bool SignalingStateStable() {
    return pc()->signaling_state() == PeerConnectionInterface::kStable;
  }

  bool IceGatheringStateComplete() {
    return pc()->ice_gathering_state() ==
           PeerConnectionInterface::kIceGatheringComplete;
  }

  void CreateDataChannel() { CreateDataChannel(nullptr); }

  void CreateDataChannel(const DataChannelInit* init) {
    CreateDataChannel(kDataChannelLabel, init);
  }

  void CreateDataChannel(const std::string& label,
                         const DataChannelInit* init) {
    auto data_channel_or_error = pc()->CreateDataChannelOrError(label, init);
    ASSERT_TRUE(data_channel_or_error.ok());
    data_channels_.push_back(data_channel_or_error.MoveValue());
    ASSERT_TRUE(data_channels_.back().get() != nullptr);
    data_observers_.push_back(
        std::make_unique<MockDataChannelObserver>(data_channels_.back().get()));
  }

  // Return the last observed data channel.
  DataChannelInterface* data_channel() {
    if (data_channels_.size() == 0) {
      return nullptr;
    }
    return data_channels_.back().get();
  }
  // Return all data channels.
  std::vector<rtc::scoped_refptr<DataChannelInterface>>& data_channels() {
    return data_channels_;
  }

  const MockDataChannelObserver* data_observer() const {
    if (data_observers_.size() == 0) {
      return nullptr;
    }
    return data_observers_.back().get();
  }

  std::vector<std::unique_ptr<MockDataChannelObserver>>& data_observers() {
    return data_observers_;
  }

  int audio_frames_received() const {
    return fake_audio_capture_module_->frames_received();
  }

  // Takes minimum of video frames received for each track.
  //
  // Can be used like:
  // EXPECT_GE(expected_frames, min_video_frames_received_per_track());
  //
  // To ensure that all video tracks received at least a certain number of
  // frames.
  int min_video_frames_received_per_track() const {
    int min_frames = INT_MAX;
    if (fake_video_renderers_.empty()) {
      return 0;
    }

    for (const auto& pair : fake_video_renderers_) {
      min_frames = std::min(min_frames, pair.second->num_rendered_frames());
    }
    return min_frames;
  }

  // Returns a MockStatsObserver in a state after stats gathering finished,
  // which can be used to access the gathered stats.
  rtc::scoped_refptr<MockStatsObserver> OldGetStatsForTrack(
      MediaStreamTrackInterface* track) {
    auto observer = rtc::make_ref_counted<MockStatsObserver>();
    EXPECT_TRUE(peer_connection_->GetStats(
        observer.get(), nullptr,
        PeerConnectionInterface::kStatsOutputLevelStandard));
    EXPECT_TRUE_WAIT(observer->called(), kDefaultTimeout);
    return observer;
  }

  // Version that doesn't take a track "filter", and gathers all stats.
  rtc::scoped_refptr<MockStatsObserver> OldGetStats() {
    return OldGetStatsForTrack(nullptr);
  }

  // Synchronously gets stats and returns them. If it times out, fails the test
  // and returns null.
  rtc::scoped_refptr<const RTCStatsReport> NewGetStats() {
    auto callback = rtc::make_ref_counted<MockRTCStatsCollectorCallback>();
    peer_connection_->GetStats(callback.get());
    EXPECT_TRUE_WAIT(callback->called(), kDefaultTimeout);
    return callback->report();
  }

  int rendered_width() {
    EXPECT_FALSE(fake_video_renderers_.empty());
    return fake_video_renderers_.empty()
               ? 0
               : fake_video_renderers_.begin()->second->width();
  }

  int rendered_height() {
    EXPECT_FALSE(fake_video_renderers_.empty());
    return fake_video_renderers_.empty()
               ? 0
               : fake_video_renderers_.begin()->second->height();
  }

  double rendered_aspect_ratio() {
    if (rendered_height() == 0) {
      return 0.0;
    }
    return static_cast<double>(rendered_width()) / rendered_height();
  }

  VideoRotation rendered_rotation() {
    EXPECT_FALSE(fake_video_renderers_.empty());
    return fake_video_renderers_.empty()
               ? kVideoRotation_0
               : fake_video_renderers_.begin()->second->rotation();
  }

  int local_rendered_width() {
    return local_video_renderer_ ? local_video_renderer_->width() : 0;
  }

  int local_rendered_height() {
    return local_video_renderer_ ? local_video_renderer_->height() : 0;
  }

  double local_rendered_aspect_ratio() {
    if (local_rendered_height() == 0) {
      return 0.0;
    }
    return static_cast<double>(local_rendered_width()) /
           local_rendered_height();
  }

  size_t number_of_remote_streams() {
    if (!pc()) {
      return 0;
    }
    return pc()->remote_streams()->count();
  }

  StreamCollectionInterface* remote_streams() const {
    if (!pc()) {
      ADD_FAILURE();
      return nullptr;
    }
    return pc()->remote_streams().get();
  }

  StreamCollectionInterface* local_streams() {
    if (!pc()) {
      ADD_FAILURE();
      return nullptr;
    }
    return pc()->local_streams().get();
  }

  PeerConnectionInterface::SignalingState signaling_state() {
    return pc()->signaling_state();
  }

  PeerConnectionInterface::IceConnectionState ice_connection_state() {
    return pc()->ice_connection_state();
  }

  PeerConnectionInterface::IceConnectionState
  standardized_ice_connection_state() {
    return pc()->standardized_ice_connection_state();
  }

  PeerConnectionInterface::IceGatheringState ice_gathering_state() {
    return pc()->ice_gathering_state();
  }

  // Returns a MockRtpReceiverObserver for each RtpReceiver returned by
  // GetReceivers. They're updated automatically when a remote offer/answer
  // from the fake signaling channel is applied, or when
  // ResetRtpReceiverObservers below is called.
  const std::vector<std::unique_ptr<MockRtpReceiverObserver>>&
  rtp_receiver_observers() {
    return rtp_receiver_observers_;
  }

  void ResetRtpReceiverObservers() {
    rtp_receiver_observers_.clear();
    for (const rtc::scoped_refptr<RtpReceiverInterface>& receiver :
         pc()->GetReceivers()) {
      std::unique_ptr<MockRtpReceiverObserver> observer(
          new MockRtpReceiverObserver(receiver->media_type()));
      receiver->SetObserver(observer.get());
      rtp_receiver_observers_.push_back(std::move(observer));
    }
  }

  rtc::FakeNetworkManager* network_manager() const {
    return fake_network_manager_.get();
  }
  cricket::PortAllocator* port_allocator() const { return port_allocator_; }

  FakeRtcEventLogFactory* event_log_factory() const {
    return event_log_factory_;
  }

  const cricket::Candidate& last_candidate_gathered() const {
    return last_candidate_gathered_;
  }
  const cricket::IceCandidateErrorEvent& error_event() const {
    return error_event_;
  }

  // Sets the mDNS responder for the owned fake network manager and keeps a
  // reference to the responder.
  void SetMdnsResponder(std::unique_ptr<FakeMdnsResponder> mdns_responder) {
    RTC_DCHECK(mdns_responder != nullptr);
    mdns_responder_ = mdns_responder.get();
    network_manager()->set_mdns_responder(std::move(mdns_responder));
  }

  // Returns null on failure.
  std::unique_ptr<SessionDescriptionInterface> CreateOfferAndWait() {
    auto observer =
        rtc::make_ref_counted<MockCreateSessionDescriptionObserver>();
    pc()->CreateOffer(observer.get(), offer_answer_options_);
    return WaitForDescriptionFromObserver(observer.get());
  }
  bool Rollback() {
    return SetRemoteDescription(
        CreateSessionDescription(SdpType::kRollback, ""));
  }

  // Functions for querying stats.
  void StartWatchingDelayStats() {
    // Get the baseline numbers for audio_packets and audio_delay.
    auto received_stats = NewGetStats();
    auto rtp_stats =
        received_stats->GetStatsOfType<RTCInboundRtpStreamStats>()[0];
    ASSERT_TRUE(rtp_stats->relative_packet_arrival_delay.has_value());
    ASSERT_TRUE(rtp_stats->packets_received.has_value());
    rtp_stats_id_ = rtp_stats->id();
    audio_packets_stat_ = *rtp_stats->packets_received;
    audio_delay_stat_ = *rtp_stats->relative_packet_arrival_delay;
    audio_samples_stat_ = *rtp_stats->total_samples_received;
    audio_concealed_stat_ = *rtp_stats->concealed_samples;
  }

  void UpdateDelayStats(std::string tag, int desc_size) {
    auto report = NewGetStats();
    auto rtp_stats = report->GetAs<RTCInboundRtpStreamStats>(rtp_stats_id_);
    ASSERT_TRUE(rtp_stats);
    auto delta_packets = *rtp_stats->packets_received - audio_packets_stat_;
    auto delta_rpad =
        *rtp_stats->relative_packet_arrival_delay - audio_delay_stat_;
    auto recent_delay = delta_packets > 0 ? delta_rpad / delta_packets : -1;
    // The purpose of these checks is to sound the alarm early if we introduce
    // serious regressions. The numbers are not acceptable for production, but
    // occur on slow bots.
    //
    // An average relative packet arrival delay over the renegotiation of
    // > 100 ms indicates that something is dramatically wrong, and will impact
    // quality for sure.
    // Worst bots:
    // linux_x86_dbg at 0.206
#if !defined(NDEBUG)
    EXPECT_GT(0.25, recent_delay) << tag << " size " << desc_size;
#else
    EXPECT_GT(0.1, recent_delay) << tag << " size " << desc_size;
#endif
    auto delta_samples =
        *rtp_stats->total_samples_received - audio_samples_stat_;
    auto delta_concealed =
        *rtp_stats->concealed_samples - audio_concealed_stat_;
    // These limits should be adjusted down as we improve:
    //
    // Concealing more than 4000 samples during a renegotiation is unacceptable.
    // But some bots are slow.

    // Worst bots:
    // linux_more_configs bot at conceal count 5184
    // android_arm_rel at conceal count 9241
    // linux_x86_dbg at 15174
#if !defined(NDEBUG)
    EXPECT_GT(18000U, delta_concealed) << "Concealed " << delta_concealed
                                       << " of " << delta_samples << " samples";
#else
    EXPECT_GT(15000U, delta_concealed) << "Concealed " << delta_concealed
                                       << " of " << delta_samples << " samples";
#endif
    // Concealing more than 20% of samples during a renegotiation is
    // unacceptable.
    // Worst bots:
    // Nondebug: Linux32 Release at conceal rate 0.606597 (CI run)
    // Debug: linux_x86_dbg bot at conceal rate 0.854
    //        internal bot at conceal rate 0.967 (b/294020344)
    // TODO(https://crbug.com/webrtc/15393): Improve audio quality during
    // renegotiation so that we can reduce these thresholds, 99% is not even
    // close to the 20% deemed unacceptable above or the 0% that would be ideal.
    if (delta_samples > 0) {
#if !defined(NDEBUG)
      EXPECT_LT(1.0 * delta_concealed / delta_samples, 0.99)
          << "Concealed " << delta_concealed << " of " << delta_samples
          << " samples";
#else
      EXPECT_LT(1.0 * delta_concealed / delta_samples, 0.7)
          << "Concealed " << delta_concealed << " of " << delta_samples
          << " samples";
#endif
    }
    // Increment trailing counters
    audio_packets_stat_ = *rtp_stats->packets_received;
    audio_delay_stat_ = *rtp_stats->relative_packet_arrival_delay;
    audio_samples_stat_ = *rtp_stats->total_samples_received;
    audio_concealed_stat_ = *rtp_stats->concealed_samples;
  }

  // Sets number of candidates expected
  void ExpectCandidates(int candidate_count) {
    candidates_expected_ = candidate_count;
  }

 private:
  // Constructor used by friend class PeerConnectionIntegrationBaseTest.
  explicit PeerConnectionIntegrationWrapper(const std::string& debug_name)
      : debug_name_(debug_name) {}

  bool Init(const PeerConnectionFactory::Options* options,
            const PeerConnectionInterface::RTCConfiguration* config,
            PeerConnectionDependencies dependencies,
            rtc::SocketServer* socket_server,
            rtc::Thread* network_thread,
            rtc::Thread* worker_thread,
            std::unique_ptr<FakeRtcEventLogFactory> event_log_factory,
            bool reset_encoder_factory,
            bool reset_decoder_factory,
            bool create_media_engine) {
    // There's an error in this test code if Init ends up being called twice.
    RTC_DCHECK(!peer_connection_);
    RTC_DCHECK(!peer_connection_factory_);

    fake_network_manager_.reset(new rtc::FakeNetworkManager());
    fake_network_manager_->AddInterface(kDefaultLocalAddress);

    socket_factory_.reset(new rtc::BasicPacketSocketFactory(socket_server));

    std::unique_ptr<cricket::PortAllocator> port_allocator(
        new cricket::BasicPortAllocator(fake_network_manager_.get(),
                                        socket_factory_.get()));
    port_allocator_ = port_allocator.get();
    fake_audio_capture_module_ = FakeAudioCaptureModule::Create();
    if (!fake_audio_capture_module_) {
      return false;
    }
    rtc::Thread* const signaling_thread = rtc::Thread::Current();

    PeerConnectionFactoryDependencies pc_factory_dependencies;
    pc_factory_dependencies.network_thread = network_thread;
    pc_factory_dependencies.worker_thread = worker_thread;
    pc_factory_dependencies.signaling_thread = signaling_thread;
    pc_factory_dependencies.task_queue_factory =
        CreateDefaultTaskQueueFactory();
    pc_factory_dependencies.trials = std::make_unique<FieldTrialBasedConfig>();
    pc_factory_dependencies.decode_metronome =
        std::make_unique<TaskQueueMetronome>(TimeDelta::Millis(8));

    pc_factory_dependencies.adm = fake_audio_capture_module_;
    if (create_media_engine) {
      EnableMediaWithDefaults(pc_factory_dependencies);
    }

    if (reset_encoder_factory) {
      pc_factory_dependencies.video_encoder_factory.reset();
    }
    if (reset_decoder_factory) {
      pc_factory_dependencies.video_decoder_factory.reset();
    }

    if (!pc_factory_dependencies.audio_processing) {
      // If the standard Creation method for APM returns a null pointer, instead
      // use the builder for testing to create an APM object.
      pc_factory_dependencies.audio_processing =
          AudioProcessingBuilderForTesting().Create();
    }

    if (event_log_factory) {
      event_log_factory_ = event_log_factory.get();
      pc_factory_dependencies.event_log_factory = std::move(event_log_factory);
    } else {
      pc_factory_dependencies.event_log_factory =
          std::make_unique<RtcEventLogFactory>();
    }
    peer_connection_factory_ =
        CreateModularPeerConnectionFactory(std::move(pc_factory_dependencies));

    if (!peer_connection_factory_) {
      return false;
    }
    if (options) {
      peer_connection_factory_->SetOptions(*options);
    }
    if (config) {
      sdp_semantics_ = config->sdp_semantics;
    }

    dependencies.allocator = std::move(port_allocator);
    peer_connection_ = CreatePeerConnection(config, std::move(dependencies));
    return peer_connection_.get() != nullptr;
  }

  rtc::scoped_refptr<PeerConnectionInterface> CreatePeerConnection(
      const PeerConnectionInterface::RTCConfiguration* config,
      PeerConnectionDependencies dependencies) {
    PeerConnectionInterface::RTCConfiguration modified_config;
    modified_config.sdp_semantics = sdp_semantics_;
    // If `config` is null, this will result in a default configuration being
    // used.
    if (config) {
      modified_config = *config;
    }
    // Disable resolution adaptation; we don't want it interfering with the
    // test results.
    // TODO(deadbeef): Do something more robust. Since we're testing for aspect
    // ratios and not specific resolutions, is this even necessary?
    modified_config.set_cpu_adaptation(false);

    dependencies.observer = this;
    auto peer_connection_or_error =
        peer_connection_factory_->CreatePeerConnectionOrError(
            modified_config, std::move(dependencies));
    return peer_connection_or_error.ok() ? peer_connection_or_error.MoveValue()
                                         : nullptr;
  }

  void set_signaling_message_receiver(
      SignalingMessageReceiver* signaling_message_receiver) {
    signaling_message_receiver_ = signaling_message_receiver;
  }

  void set_signaling_delay_ms(int delay_ms) { signaling_delay_ms_ = delay_ms; }

  void set_signal_ice_candidates(bool signal) {
    signal_ice_candidates_ = signal;
  }

  rtc::scoped_refptr<VideoTrackInterface> CreateLocalVideoTrackInternal(
      FakePeriodicVideoSource::Config config) {
    // Set max frame rate to 10fps to reduce the risk of test flakiness.
    // TODO(deadbeef): Do something more robust.
    config.frame_interval_ms = 100;

    video_track_sources_.emplace_back(
        rtc::make_ref_counted<FakePeriodicVideoTrackSource>(
            config, false /* remote */));
    rtc::scoped_refptr<VideoTrackInterface> track =
        peer_connection_factory_->CreateVideoTrack(video_track_sources_.back(),
                                                   rtc::CreateRandomUuid());
    if (!local_video_renderer_) {
      local_video_renderer_.reset(new FakeVideoTrackRenderer(track.get()));
    }
    return track;
  }

  void HandleIncomingOffer(const std::string& msg) {
    RTC_LOG(LS_INFO) << debug_name_ << ": HandleIncomingOffer";
    std::unique_ptr<SessionDescriptionInterface> desc =
        CreateSessionDescription(SdpType::kOffer, msg);
    if (received_sdp_munger_) {
      received_sdp_munger_(desc->description());
    }

    EXPECT_TRUE(SetRemoteDescription(std::move(desc)));
    // Setting a remote description may have changed the number of receivers,
    // so reset the receiver observers.
    ResetRtpReceiverObservers();
    if (remote_offer_handler_) {
      remote_offer_handler_();
    }
    auto answer = CreateAnswer();
    ASSERT_NE(nullptr, answer);
    EXPECT_TRUE(SetLocalDescriptionAndSendSdpMessage(std::move(answer)));
  }

  void HandleIncomingAnswer(const std::string& msg) {
    RTC_LOG(LS_INFO) << debug_name_ << ": HandleIncomingAnswer";
    std::unique_ptr<SessionDescriptionInterface> desc =
        CreateSessionDescription(SdpType::kAnswer, msg);
    if (received_sdp_munger_) {
      received_sdp_munger_(desc->description());
    }

    EXPECT_TRUE(SetRemoteDescription(std::move(desc)));
    // Set the RtpReceiverObserver after receivers are created.
    ResetRtpReceiverObservers();
  }

  // Returns null on failure.
  std::unique_ptr<SessionDescriptionInterface> CreateAnswer() {
    auto observer =
        rtc::make_ref_counted<MockCreateSessionDescriptionObserver>();
    pc()->CreateAnswer(observer.get(), offer_answer_options_);
    return WaitForDescriptionFromObserver(observer.get());
  }

  std::unique_ptr<SessionDescriptionInterface> WaitForDescriptionFromObserver(
      MockCreateSessionDescriptionObserver* observer) {
    EXPECT_EQ_WAIT(true, observer->called(), kDefaultTimeout);
    if (!observer->result()) {
      return nullptr;
    }
    auto description = observer->MoveDescription();
    if (generated_sdp_munger_) {
      generated_sdp_munger_(description->description());
    }
    return description;
  }

  // Setting the local description and sending the SDP message over the fake
  // signaling channel are combined into the same method because the SDP
  // message needs to be sent as soon as SetLocalDescription finishes, without
  // waiting for the observer to be called. This ensures that ICE candidates
  // don't outrace the description.
  bool SetLocalDescriptionAndSendSdpMessage(
      std::unique_ptr<SessionDescriptionInterface> desc) {
    auto observer = rtc::make_ref_counted<MockSetSessionDescriptionObserver>();
    RTC_LOG(LS_INFO) << debug_name_ << ": SetLocalDescriptionAndSendSdpMessage";
    SdpType type = desc->GetType();
    std::string sdp;
    EXPECT_TRUE(desc->ToString(&sdp));
    RTC_LOG(LS_INFO) << debug_name_ << ": local SDP contents=\n" << sdp;
    pc()->SetLocalDescription(observer.get(), desc.release());
    RemoveUnusedVideoRenderers();
    // As mentioned above, we need to send the message immediately after
    // SetLocalDescription.
    SendSdpMessage(type, sdp);
    EXPECT_TRUE_WAIT(observer->called(), kDefaultTimeout);
    return true;
  }

  bool SetRemoteDescription(std::unique_ptr<SessionDescriptionInterface> desc) {
    auto observer = rtc::make_ref_counted<MockSetSessionDescriptionObserver>();
    RTC_LOG(LS_INFO) << debug_name_ << ": SetRemoteDescription";
    pc()->SetRemoteDescription(observer.get(), desc.release());
    RemoveUnusedVideoRenderers();
    EXPECT_TRUE_WAIT(observer->called(), kDefaultTimeout);
    return observer->result();
  }

  // This is a work around to remove unused fake_video_renderers from
  // transceivers that have either stopped or are no longer receiving.
  void RemoveUnusedVideoRenderers() {
    if (sdp_semantics_ != SdpSemantics::kUnifiedPlan) {
      return;
    }
    auto transceivers = pc()->GetTransceivers();
    std::set<std::string> active_renderers;
    for (auto& transceiver : transceivers) {
      // Note - we don't check for direction here. This function is called
      // before direction is set, and in that case, we should not remove
      // the renderer.
      if (transceiver->receiver()->media_type() == cricket::MEDIA_TYPE_VIDEO) {
        active_renderers.insert(transceiver->receiver()->track()->id());
      }
    }
    for (auto it = fake_video_renderers_.begin();
         it != fake_video_renderers_.end();) {
      // Remove fake video renderers belonging to any non-active transceivers.
      if (!active_renderers.count(it->first)) {
        it = fake_video_renderers_.erase(it);
      } else {
        it++;
      }
    }
  }

  // Simulate sending a blob of SDP with delay `signaling_delay_ms_` (0 by
  // default).
  void SendSdpMessage(SdpType type, const std::string& msg) {
    if (signaling_delay_ms_ == 0) {
      RelaySdpMessageIfReceiverExists(type, msg);
    } else {
      rtc::Thread::Current()->PostDelayedTask(
          SafeTask(task_safety_.flag(),
                   [this, type, msg] {
                     RelaySdpMessageIfReceiverExists(type, msg);
                   }),
          TimeDelta::Millis(signaling_delay_ms_));
    }
  }

  void RelaySdpMessageIfReceiverExists(SdpType type, const std::string& msg) {
    if (signaling_message_receiver_) {
      signaling_message_receiver_->ReceiveSdpMessage(type, msg);
    }
  }

  // Simulate trickling an ICE candidate with delay `signaling_delay_ms_` (0 by
  // default).
  void SendIceMessage(const std::string& sdp_mid,
                      int sdp_mline_index,
                      const std::string& msg) {
    if (signaling_delay_ms_ == 0) {
      RelayIceMessageIfReceiverExists(sdp_mid, sdp_mline_index, msg);
    } else {
      rtc::Thread::Current()->PostDelayedTask(
          SafeTask(task_safety_.flag(),
                   [this, sdp_mid, sdp_mline_index, msg] {
                     RelayIceMessageIfReceiverExists(sdp_mid, sdp_mline_index,
                                                     msg);
                   }),
          TimeDelta::Millis(signaling_delay_ms_));
    }
  }

  void RelayIceMessageIfReceiverExists(const std::string& sdp_mid,
                                       int sdp_mline_index,
                                       const std::string& msg) {
    if (signaling_message_receiver_) {
      signaling_message_receiver_->ReceiveIceMessage(sdp_mid, sdp_mline_index,
                                                     msg);
    }
  }

  // SignalingMessageReceiver callbacks.
  void ReceiveSdpMessage(SdpType type, const std::string& msg) override {
    if (type == SdpType::kOffer) {
      HandleIncomingOffer(msg);
    } else {
      HandleIncomingAnswer(msg);
    }
  }

  void ReceiveIceMessage(const std::string& sdp_mid,
                         int sdp_mline_index,
                         const std::string& msg) override {
    RTC_LOG(LS_INFO) << debug_name_ << ": ReceiveIceMessage";
    absl::optional<RTCError> result;
    pc()->AddIceCandidate(absl::WrapUnique(CreateIceCandidate(
                              sdp_mid, sdp_mline_index, msg, nullptr)),
                          [&result](RTCError r) { result = r; });
    EXPECT_TRUE_WAIT(result.has_value(), kDefaultTimeout);
    EXPECT_TRUE(result.value().ok());
  }

  // PeerConnectionObserver callbacks.
  void OnSignalingChange(
      PeerConnectionInterface::SignalingState new_state) override {
    EXPECT_EQ(pc()->signaling_state(), new_state);
    peer_connection_signaling_state_history_.push_back(new_state);
  }
  void OnAddTrack(rtc::scoped_refptr<RtpReceiverInterface> receiver,
                  const std::vector<rtc::scoped_refptr<MediaStreamInterface>>&
                      streams) override {
    if (receiver->media_type() == cricket::MEDIA_TYPE_VIDEO) {
      rtc::scoped_refptr<VideoTrackInterface> video_track(
          static_cast<VideoTrackInterface*>(receiver->track().get()));
      ASSERT_TRUE(fake_video_renderers_.find(video_track->id()) ==
                  fake_video_renderers_.end());
      fake_video_renderers_[video_track->id()] =
          std::make_unique<FakeVideoTrackRenderer>(video_track.get());
    }
  }
  void OnRemoveTrack(
      rtc::scoped_refptr<RtpReceiverInterface> receiver) override {
    if (receiver->media_type() == cricket::MEDIA_TYPE_VIDEO) {
      auto it = fake_video_renderers_.find(receiver->track()->id());
      if (it != fake_video_renderers_.end()) {
        fake_video_renderers_.erase(it);
      } else {
        RTC_LOG(LS_ERROR) << "OnRemoveTrack called for non-active renderer";
      }
    }
  }
  void OnRenegotiationNeeded() override {}
  void OnIceConnectionChange(
      PeerConnectionInterface::IceConnectionState new_state) override {
    EXPECT_EQ(pc()->ice_connection_state(), new_state);
    ice_connection_state_history_.push_back(new_state);
  }
  void OnStandardizedIceConnectionChange(
      PeerConnectionInterface::IceConnectionState new_state) override {
    standardized_ice_connection_state_history_.push_back(new_state);
  }
  void OnConnectionChange(
      PeerConnectionInterface::PeerConnectionState new_state) override {
    peer_connection_state_history_.push_back(new_state);
  }

  void OnIceGatheringChange(
      PeerConnectionInterface::IceGatheringState new_state) override {
    EXPECT_EQ(pc()->ice_gathering_state(), new_state);
    ice_gathering_state_history_.push_back(new_state);
  }

  void OnIceSelectedCandidatePairChanged(
      const cricket::CandidatePairChangeEvent& event) {
    ice_candidate_pair_change_history_.push_back(event);
  }

  void OnIceCandidate(const IceCandidateInterface* candidate) override {
    RTC_LOG(LS_INFO) << debug_name_ << ": OnIceCandidate";

    if (remote_async_dns_resolver_) {
      const auto& local_candidate = candidate->candidate();
      if (local_candidate.address().IsUnresolvedIP()) {
        RTC_DCHECK(local_candidate.is_local());
        const auto resolved_ip = mdns_responder_->GetMappedAddressForName(
            local_candidate.address().hostname());
        RTC_DCHECK(!resolved_ip.IsNil());
        remote_async_dns_resolved_addr_ = local_candidate.address();
        remote_async_dns_resolved_addr_.SetResolvedIP(resolved_ip);
        EXPECT_CALL(*remote_async_dns_resolver_, Start(_, _))
            .WillOnce([](const rtc::SocketAddress& addr,
                         absl::AnyInvocable<void()> callback) { callback(); });
        EXPECT_CALL(*remote_async_dns_resolver_, result())
            .WillOnce(ReturnRef(remote_async_dns_resolver_result_));
        EXPECT_CALL(remote_async_dns_resolver_result_, GetResolvedAddress(_, _))
            .WillOnce(DoAll(SetArgPointee<1>(remote_async_dns_resolved_addr_),
                            Return(true)));
      }
    }

    // Check if we expected to have a candidate.
    EXPECT_GT(candidates_expected_, 1);
    candidates_expected_--;
    std::string ice_sdp;
    EXPECT_TRUE(candidate->ToString(&ice_sdp));
    if (signaling_message_receiver_ == nullptr || !signal_ice_candidates_) {
      // Remote party may be deleted.
      return;
    }
    SendIceMessage(candidate->sdp_mid(), candidate->sdp_mline_index(), ice_sdp);
    last_candidate_gathered_ = candidate->candidate();
  }
  void OnIceCandidateError(const std::string& address,
                           int port,
                           const std::string& url,
                           int error_code,
                           const std::string& error_text) override {
    error_event_ = cricket::IceCandidateErrorEvent(address, port, url,
                                                   error_code, error_text);
  }
  void OnDataChannel(
      rtc::scoped_refptr<DataChannelInterface> data_channel) override {
    RTC_LOG(LS_INFO) << debug_name_ << ": OnDataChannel";
    data_channels_.push_back(data_channel);
    data_observers_.push_back(
        std::make_unique<MockDataChannelObserver>(data_channel.get()));
  }

  std::string debug_name_;

  std::unique_ptr<rtc::FakeNetworkManager> fake_network_manager_;
  std::unique_ptr<rtc::BasicPacketSocketFactory> socket_factory_;
  // Reference to the mDNS responder owned by `fake_network_manager_` after set.
  FakeMdnsResponder* mdns_responder_ = nullptr;

  rtc::scoped_refptr<PeerConnectionInterface> peer_connection_;
  rtc::scoped_refptr<PeerConnectionFactoryInterface> peer_connection_factory_;

  cricket::PortAllocator* port_allocator_;
  // Needed to keep track of number of frames sent.
  rtc::scoped_refptr<FakeAudioCaptureModule> fake_audio_capture_module_;
  // Needed to keep track of number of frames received.
  std::map<std::string, std::unique_ptr<FakeVideoTrackRenderer>>
      fake_video_renderers_;
  // Needed to ensure frames aren't received for removed tracks.
  std::vector<std::unique_ptr<FakeVideoTrackRenderer>>
      removed_fake_video_renderers_;

  // For remote peer communication.
  SignalingMessageReceiver* signaling_message_receiver_ = nullptr;
  int signaling_delay_ms_ = 0;
  bool signal_ice_candidates_ = true;
  cricket::Candidate last_candidate_gathered_;
  cricket::IceCandidateErrorEvent error_event_;

  // Store references to the video sources we've created, so that we can stop
  // them, if required.
  std::vector<rtc::scoped_refptr<VideoTrackSource>> video_track_sources_;
  // `local_video_renderer_` attached to the first created local video track.
  std::unique_ptr<FakeVideoTrackRenderer> local_video_renderer_;

  SdpSemantics sdp_semantics_;
  PeerConnectionInterface::RTCOfferAnswerOptions offer_answer_options_;
  std::function<void(cricket::SessionDescription*)> received_sdp_munger_;
  std::function<void(cricket::SessionDescription*)> generated_sdp_munger_;
  std::function<void()> remote_offer_handler_;
  MockAsyncDnsResolver* remote_async_dns_resolver_ = nullptr;
  // Result variables for the mock DNS resolver
  NiceMock<MockAsyncDnsResolverResult> remote_async_dns_resolver_result_;
  rtc::SocketAddress remote_async_dns_resolved_addr_;

  // All data channels either created or observed on this peerconnection
  std::vector<rtc::scoped_refptr<DataChannelInterface>> data_channels_;
  std::vector<std::unique_ptr<MockDataChannelObserver>> data_observers_;

  std::vector<std::unique_ptr<MockRtpReceiverObserver>> rtp_receiver_observers_;

  std::vector<PeerConnectionInterface::IceConnectionState>
      ice_connection_state_history_;
  std::vector<PeerConnectionInterface::IceConnectionState>
      standardized_ice_connection_state_history_;
  std::vector<PeerConnectionInterface::PeerConnectionState>
      peer_connection_state_history_;
  std::vector<PeerConnectionInterface::IceGatheringState>
      ice_gathering_state_history_;
  std::vector<cricket::CandidatePairChangeEvent>
      ice_candidate_pair_change_history_;
  std::vector<PeerConnectionInterface::SignalingState>
      peer_connection_signaling_state_history_;
  FakeRtcEventLogFactory* event_log_factory_;

  // Number of ICE candidates expected. The default is no limit.
  int candidates_expected_ = std::numeric_limits<int>::max();

  // Variables for tracking delay stats on an audio track
  int audio_packets_stat_ = 0;
  double audio_delay_stat_ = 0.0;
  uint64_t audio_samples_stat_ = 0;
  uint64_t audio_concealed_stat_ = 0;
  std::string rtp_stats_id_;

  ScopedTaskSafety task_safety_;

  friend class PeerConnectionIntegrationBaseTest;
};

class MockRtcEventLogOutput : public RtcEventLogOutput {
 public:
  virtual ~MockRtcEventLogOutput() = default;
  MOCK_METHOD(bool, IsActive, (), (const, override));
  MOCK_METHOD(bool, Write, (absl::string_view), (override));
};

// This helper object is used for both specifying how many audio/video frames
// are expected to be received for a caller/callee. It provides helper functions
// to specify these expectations. The object initially starts in a state of no
// expectations.
class MediaExpectations {
 public:
  enum ExpectFrames {
    kExpectSomeFrames,
    kExpectNoFrames,
    kNoExpectation,
  };

  void ExpectBidirectionalAudioAndVideo() {
    ExpectBidirectionalAudio();
    ExpectBidirectionalVideo();
  }

  void ExpectBidirectionalAudio() {
    CallerExpectsSomeAudio();
    CalleeExpectsSomeAudio();
  }

  void ExpectNoAudio() {
    CallerExpectsNoAudio();
    CalleeExpectsNoAudio();
  }

  void ExpectBidirectionalVideo() {
    CallerExpectsSomeVideo();
    CalleeExpectsSomeVideo();
  }

  void ExpectNoVideo() {
    CallerExpectsNoVideo();
    CalleeExpectsNoVideo();
  }

  void CallerExpectsSomeAudioAndVideo() {
    CallerExpectsSomeAudio();
    CallerExpectsSomeVideo();
  }

  void CalleeExpectsSomeAudioAndVideo() {
    CalleeExpectsSomeAudio();
    CalleeExpectsSomeVideo();
  }

  // Caller's audio functions.
  void CallerExpectsSomeAudio(
      int expected_audio_frames = kDefaultExpectedAudioFrameCount) {
    caller_audio_expectation_ = kExpectSomeFrames;
    caller_audio_frames_expected_ = expected_audio_frames;
  }

  void CallerExpectsNoAudio() {
    caller_audio_expectation_ = kExpectNoFrames;
    caller_audio_frames_expected_ = 0;
  }

  // Caller's video functions.
  void CallerExpectsSomeVideo(
      int expected_video_frames = kDefaultExpectedVideoFrameCount) {
    caller_video_expectation_ = kExpectSomeFrames;
    caller_video_frames_expected_ = expected_video_frames;
  }

  void CallerExpectsNoVideo() {
    caller_video_expectation_ = kExpectNoFrames;
    caller_video_frames_expected_ = 0;
  }

  // Callee's audio functions.
  void CalleeExpectsSomeAudio(
      int expected_audio_frames = kDefaultExpectedAudioFrameCount) {
    callee_audio_expectation_ = kExpectSomeFrames;
    callee_audio_frames_expected_ = expected_audio_frames;
  }

  void CalleeExpectsNoAudio() {
    callee_audio_expectation_ = kExpectNoFrames;
    callee_audio_frames_expected_ = 0;
  }

  // Callee's video functions.
  void CalleeExpectsSomeVideo(
      int expected_video_frames = kDefaultExpectedVideoFrameCount) {
    callee_video_expectation_ = kExpectSomeFrames;
    callee_video_frames_expected_ = expected_video_frames;
  }

  void CalleeExpectsNoVideo() {
    callee_video_expectation_ = kExpectNoFrames;
    callee_video_frames_expected_ = 0;
  }

  ExpectFrames caller_audio_expectation_ = kNoExpectation;
  ExpectFrames caller_video_expectation_ = kNoExpectation;
  ExpectFrames callee_audio_expectation_ = kNoExpectation;
  ExpectFrames callee_video_expectation_ = kNoExpectation;
  int caller_audio_frames_expected_ = 0;
  int caller_video_frames_expected_ = 0;
  int callee_audio_frames_expected_ = 0;
  int callee_video_frames_expected_ = 0;
};

class MockIceTransport : public IceTransportInterface {
 public:
  MockIceTransport(const std::string& name, int component)
      : internal_(std::make_unique<cricket::FakeIceTransport>(
            name,
            component,
            nullptr /* network_thread */)) {}
  ~MockIceTransport() = default;
  cricket::IceTransportInternal* internal() { return internal_.get(); }

 private:
  std::unique_ptr<cricket::FakeIceTransport> internal_;
};

class MockIceTransportFactory : public IceTransportFactory {
 public:
  ~MockIceTransportFactory() override = default;
  rtc::scoped_refptr<IceTransportInterface> CreateIceTransport(
      const std::string& transport_name,
      int component,
      IceTransportInit init) {
    RecordIceTransportCreated();
    return rtc::make_ref_counted<MockIceTransport>(transport_name, component);
  }
  MOCK_METHOD(void, RecordIceTransportCreated, ());
};

// Tests two PeerConnections connecting to each other end-to-end, using a
// virtual network, fake A/V capture and fake encoder/decoders. The
// PeerConnections share the threads/socket servers, but use separate versions
// of everything else (including "PeerConnectionFactory"s).
class PeerConnectionIntegrationBaseTest : public ::testing::Test {
 public:
  PeerConnectionIntegrationBaseTest(
      SdpSemantics sdp_semantics,
      absl::optional<std::string> field_trials = absl::nullopt)
      : sdp_semantics_(sdp_semantics),
        ss_(new rtc::VirtualSocketServer()),
        fss_(new rtc::FirewallSocketServer(ss_.get())),
        network_thread_(new rtc::Thread(fss_.get())),
        worker_thread_(rtc::Thread::Create()),
        // TODO(bugs.webrtc.org/10335): Pass optional ScopedKeyValueConfig.
        field_trials_(new test::ScopedKeyValueConfig(
            field_trials.has_value() ? *field_trials : "")) {
    network_thread_->SetName("PCNetworkThread", this);
    worker_thread_->SetName("PCWorkerThread", this);
    RTC_CHECK(network_thread_->Start());
    RTC_CHECK(worker_thread_->Start());
    metrics::Reset();
  }

  ~PeerConnectionIntegrationBaseTest() {
    // The PeerConnections should be deleted before the TurnCustomizers.
    // A TurnPort is created with a raw pointer to a TurnCustomizer. The
    // TurnPort has the same lifetime as the PeerConnection, so it's expected
    // that the TurnCustomizer outlives the life of the PeerConnection or else
    // when Send() is called it will hit a seg fault.
    if (caller_) {
      caller_->set_signaling_message_receiver(nullptr);
      caller_->pc()->Close();
      delete SetCallerPcWrapperAndReturnCurrent(nullptr);
    }
    if (callee_) {
      callee_->set_signaling_message_receiver(nullptr);
      callee_->pc()->Close();
      delete SetCalleePcWrapperAndReturnCurrent(nullptr);
    }

    // If turn servers were created for the test they need to be destroyed on
    // the network thread.
    SendTask(network_thread(), [this] {
      turn_servers_.clear();
      turn_customizers_.clear();
    });
  }

  bool SignalingStateStable() {
    return caller_->SignalingStateStable() && callee_->SignalingStateStable();
  }

  bool DtlsConnected() {
    // TODO(deadbeef): kIceConnectionConnected currently means both ICE and DTLS
    // are connected. This is an important distinction. Once we have separate
    // ICE and DTLS state, this check needs to use the DTLS state.
    return (callee()->ice_connection_state() ==
                PeerConnectionInterface::kIceConnectionConnected ||
            callee()->ice_connection_state() ==
                PeerConnectionInterface::kIceConnectionCompleted) &&
           (caller()->ice_connection_state() ==
                PeerConnectionInterface::kIceConnectionConnected ||
            caller()->ice_connection_state() ==
                PeerConnectionInterface::kIceConnectionCompleted);
  }

  // When `event_log_factory` is null, the default implementation of the event
  // log factory will be used.
  std::unique_ptr<PeerConnectionIntegrationWrapper> CreatePeerConnectionWrapper(
      const std::string& debug_name,
      const PeerConnectionFactory::Options* options,
      const RTCConfiguration* config,
      PeerConnectionDependencies dependencies,
      std::unique_ptr<FakeRtcEventLogFactory> event_log_factory,
      bool reset_encoder_factory,
      bool reset_decoder_factory,
      bool create_media_engine = true) {
    RTCConfiguration modified_config;
    if (config) {
      modified_config = *config;
    }
    modified_config.sdp_semantics = sdp_semantics_;
    if (!dependencies.cert_generator) {
      dependencies.cert_generator =
          std::make_unique<FakeRTCCertificateGenerator>();
    }
    std::unique_ptr<PeerConnectionIntegrationWrapper> client(
        new PeerConnectionIntegrationWrapper(debug_name));

    if (!client->Init(options, &modified_config, std::move(dependencies),
                      fss_.get(), network_thread_.get(), worker_thread_.get(),
                      std::move(event_log_factory), reset_encoder_factory,
                      reset_decoder_factory, create_media_engine)) {
      return nullptr;
    }
    return client;
  }

  std::unique_ptr<PeerConnectionIntegrationWrapper>
  CreatePeerConnectionWrapperWithFakeRtcEventLog(
      const std::string& debug_name,
      const PeerConnectionFactory::Options* options,
      const RTCConfiguration* config,
      PeerConnectionDependencies dependencies) {
    return CreatePeerConnectionWrapper(
        debug_name, options, config, std::move(dependencies),
        std::make_unique<FakeRtcEventLogFactory>(),
        /*reset_encoder_factory=*/false,
        /*reset_decoder_factory=*/false);
  }

  bool CreatePeerConnectionWrappers() {
    return CreatePeerConnectionWrappersWithConfig(
        PeerConnectionInterface::RTCConfiguration(),
        PeerConnectionInterface::RTCConfiguration());
  }

  bool CreatePeerConnectionWrappersWithSdpSemantics(
      SdpSemantics caller_semantics,
      SdpSemantics callee_semantics) {
    // Can't specify the sdp_semantics in the passed-in configuration since it
    // will be overwritten by CreatePeerConnectionWrapper with whatever is
    // stored in sdp_semantics_. So get around this by modifying the instance
    // variable before calling CreatePeerConnectionWrapper for the caller and
    // callee PeerConnections.
    SdpSemantics original_semantics = sdp_semantics_;
    sdp_semantics_ = caller_semantics;
    caller_ = CreatePeerConnectionWrapper("Caller", nullptr, nullptr,
                                          PeerConnectionDependencies(nullptr),
                                          nullptr,
                                          /*reset_encoder_factory=*/false,
                                          /*reset_decoder_factory=*/false);
    sdp_semantics_ = callee_semantics;
    callee_ = CreatePeerConnectionWrapper("Callee", nullptr, nullptr,
                                          PeerConnectionDependencies(nullptr),
                                          nullptr,
                                          /*reset_encoder_factory=*/false,
                                          /*reset_decoder_factory=*/false);
    sdp_semantics_ = original_semantics;
    return caller_ && callee_;
  }

  bool CreatePeerConnectionWrappersWithConfig(
      const PeerConnectionInterface::RTCConfiguration& caller_config,
      const PeerConnectionInterface::RTCConfiguration& callee_config) {
    caller_ = CreatePeerConnectionWrapper("Caller", nullptr, &caller_config,
                                          PeerConnectionDependencies(nullptr),
                                          nullptr,
                                          /*reset_encoder_factory=*/false,
                                          /*reset_decoder_factory=*/false);
    callee_ = CreatePeerConnectionWrapper("Callee", nullptr, &callee_config,
                                          PeerConnectionDependencies(nullptr),
                                          nullptr,
                                          /*reset_encoder_factory=*/false,
                                          /*reset_decoder_factory=*/false);
    return caller_ && callee_;
  }

  bool CreatePeerConnectionWrappersWithConfigAndDeps(
      const PeerConnectionInterface::RTCConfiguration& caller_config,
      PeerConnectionDependencies caller_dependencies,
      const PeerConnectionInterface::RTCConfiguration& callee_config,
      PeerConnectionDependencies callee_dependencies) {
    caller_ =
        CreatePeerConnectionWrapper("Caller", nullptr, &caller_config,
                                    std::move(caller_dependencies), nullptr,
                                    /*reset_encoder_factory=*/false,
                                    /*reset_decoder_factory=*/false);
    callee_ =
        CreatePeerConnectionWrapper("Callee", nullptr, &callee_config,
                                    std::move(callee_dependencies), nullptr,
                                    /*reset_encoder_factory=*/false,
                                    /*reset_decoder_factory=*/false);
    return caller_ && callee_;
  }

  bool CreatePeerConnectionWrappersWithOptions(
      const PeerConnectionFactory::Options& caller_options,
      const PeerConnectionFactory::Options& callee_options) {
    caller_ = CreatePeerConnectionWrapper("Caller", &caller_options, nullptr,
                                          PeerConnectionDependencies(nullptr),
                                          nullptr,
                                          /*reset_encoder_factory=*/false,
                                          /*reset_decoder_factory=*/false);
    callee_ = CreatePeerConnectionWrapper("Callee", &callee_options, nullptr,
                                          PeerConnectionDependencies(nullptr),
                                          nullptr,
                                          /*reset_encoder_factory=*/false,
                                          /*reset_decoder_factory=*/false);
    return caller_ && callee_;
  }

  bool CreatePeerConnectionWrappersWithFakeRtcEventLog() {
    PeerConnectionInterface::RTCConfiguration default_config;
    caller_ = CreatePeerConnectionWrapperWithFakeRtcEventLog(
        "Caller", nullptr, &default_config,
        PeerConnectionDependencies(nullptr));
    callee_ = CreatePeerConnectionWrapperWithFakeRtcEventLog(
        "Callee", nullptr, &default_config,
        PeerConnectionDependencies(nullptr));
    return caller_ && callee_;
  }

  std::unique_ptr<PeerConnectionIntegrationWrapper>
  CreatePeerConnectionWrapperWithAlternateKey() {
    std::unique_ptr<FakeRTCCertificateGenerator> cert_generator(
        new FakeRTCCertificateGenerator());
    cert_generator->use_alternate_key();

    PeerConnectionDependencies dependencies(nullptr);
    dependencies.cert_generator = std::move(cert_generator);
    return CreatePeerConnectionWrapper("New Peer", nullptr, nullptr,
                                       std::move(dependencies), nullptr,
                                       /*reset_encoder_factory=*/false,
                                       /*reset_decoder_factory=*/false);
  }

  bool CreateOneDirectionalPeerConnectionWrappers(bool caller_to_callee) {
    caller_ = CreatePeerConnectionWrapper(
        "Caller", nullptr, nullptr, PeerConnectionDependencies(nullptr),
        nullptr,
        /*reset_encoder_factory=*/!caller_to_callee,
        /*reset_decoder_factory=*/caller_to_callee);
    callee_ = CreatePeerConnectionWrapper(
        "Callee", nullptr, nullptr, PeerConnectionDependencies(nullptr),
        nullptr,
        /*reset_encoder_factory=*/caller_to_callee,
        /*reset_decoder_factory=*/!caller_to_callee);
    return caller_ && callee_;
  }

  bool CreatePeerConnectionWrappersWithoutMediaEngine() {
    caller_ = CreatePeerConnectionWrapper("Caller", nullptr, nullptr,
                                          PeerConnectionDependencies(nullptr),
                                          nullptr,
                                          /*reset_encoder_factory=*/false,
                                          /*reset_decoder_factory=*/false,
                                          /*create_media_engine=*/false);
    callee_ = CreatePeerConnectionWrapper("Callee", nullptr, nullptr,
                                          PeerConnectionDependencies(nullptr),
                                          nullptr,
                                          /*reset_encoder_factory=*/false,
                                          /*reset_decoder_factory=*/false,
                                          /*create_media_engine=*/false);
    return caller_ && callee_;
  }

  cricket::TestTurnServer* CreateTurnServer(
      rtc::SocketAddress internal_address,
      rtc::SocketAddress external_address,
      cricket::ProtocolType type = cricket::ProtocolType::PROTO_UDP,
      const std::string& common_name = "test turn server") {
    rtc::Thread* thread = network_thread();
    rtc::SocketFactory* socket_factory = fss_.get();
    std::unique_ptr<cricket::TestTurnServer> turn_server;
    SendTask(network_thread(), [&] {
      turn_server = std::make_unique<cricket::TestTurnServer>(
          thread, socket_factory, internal_address, external_address, type,
          /*ignore_bad_certs=*/true, common_name);
    });
    turn_servers_.push_back(std::move(turn_server));
    // Interactions with the turn server should be done on the network thread.
    return turn_servers_.back().get();
  }

  cricket::TestTurnCustomizer* CreateTurnCustomizer() {
    std::unique_ptr<cricket::TestTurnCustomizer> turn_customizer;
    SendTask(network_thread(), [&] {
      turn_customizer = std::make_unique<cricket::TestTurnCustomizer>();
    });
    turn_customizers_.push_back(std::move(turn_customizer));
    // Interactions with the turn customizer should be done on the network
    // thread.
    return turn_customizers_.back().get();
  }

  // Checks that the function counters for a TestTurnCustomizer are greater than
  // 0.
  void ExpectTurnCustomizerCountersIncremented(
      cricket::TestTurnCustomizer* turn_customizer) {
    SendTask(network_thread(), [turn_customizer] {
      EXPECT_GT(turn_customizer->allow_channel_data_cnt_, 0u);
      EXPECT_GT(turn_customizer->modify_cnt_, 0u);
    });
  }

  // Once called, SDP blobs and ICE candidates will be automatically signaled
  // between PeerConnections.
  void ConnectFakeSignaling() {
    caller_->set_signaling_message_receiver(callee_.get());
    callee_->set_signaling_message_receiver(caller_.get());
  }

  // Once called, SDP blobs will be automatically signaled between
  // PeerConnections. Note that ICE candidates will not be signaled unless they
  // are in the exchanged SDP blobs.
  void ConnectFakeSignalingForSdpOnly() {
    ConnectFakeSignaling();
    SetSignalIceCandidates(false);
  }

  void SetSignalingDelayMs(int delay_ms) {
    caller_->set_signaling_delay_ms(delay_ms);
    callee_->set_signaling_delay_ms(delay_ms);
  }

  void SetSignalIceCandidates(bool signal) {
    caller_->set_signal_ice_candidates(signal);
    callee_->set_signal_ice_candidates(signal);
  }

  // Messages may get lost on the unreliable DataChannel, so we send multiple
  // times to avoid test flakiness.
  void SendRtpDataWithRetries(DataChannelInterface* dc,
                              const std::string& data,
                              int retries) {
    for (int i = 0; i < retries; ++i) {
      dc->Send(DataBuffer(data));
    }
  }

  rtc::Thread* network_thread() { return network_thread_.get(); }

  rtc::VirtualSocketServer* virtual_socket_server() { return ss_.get(); }

  PeerConnectionIntegrationWrapper* caller() { return caller_.get(); }

  // Destroy peerconnections.
  // This can be used to ensure that all pointers to on-stack mocks
  // get dropped before exit.
  void DestroyPeerConnections() {
    if (caller_) {
      caller_->pc()->Close();
    }
    if (callee_) {
      callee_->pc()->Close();
    }
    caller_.reset();
    callee_.reset();
  }

  // Set the `caller_` to the `wrapper` passed in and return the
  // original `caller_`.
  PeerConnectionIntegrationWrapper* SetCallerPcWrapperAndReturnCurrent(
      PeerConnectionIntegrationWrapper* wrapper) {
    PeerConnectionIntegrationWrapper* old = caller_.release();
    caller_.reset(wrapper);
    return old;
  }

  PeerConnectionIntegrationWrapper* callee() { return callee_.get(); }

  // Set the `callee_` to the `wrapper` passed in and return the
  // original `callee_`.
  PeerConnectionIntegrationWrapper* SetCalleePcWrapperAndReturnCurrent(
      PeerConnectionIntegrationWrapper* wrapper) {
    PeerConnectionIntegrationWrapper* old = callee_.release();
    callee_.reset(wrapper);
    return old;
  }

  void SetPortAllocatorFlags(uint32_t caller_flags, uint32_t callee_flags) {
    SendTask(network_thread(), [this, caller_flags] {
      caller()->port_allocator()->set_flags(caller_flags);
    });
    SendTask(network_thread(), [this, callee_flags] {
      callee()->port_allocator()->set_flags(callee_flags);
    });
  }

  rtc::FirewallSocketServer* firewall() const { return fss_.get(); }

  // Expects the provided number of new frames to be received within
  // kMaxWaitForFramesMs. The new expected frames are specified in
  // `media_expectations`. Returns false if any of the expectations were
  // not met.
  bool ExpectNewFrames(const MediaExpectations& media_expectations) {
    // Make sure there are no bogus tracks confusing the issue.
    caller()->RemoveUnusedVideoRenderers();
    callee()->RemoveUnusedVideoRenderers();
    // First initialize the expected frame counts based upon the current
    // frame count.
    int total_caller_audio_frames_expected = caller()->audio_frames_received();
    if (media_expectations.caller_audio_expectation_ ==
        MediaExpectations::kExpectSomeFrames) {
      total_caller_audio_frames_expected +=
          media_expectations.caller_audio_frames_expected_;
    }
    int total_caller_video_frames_expected =
        caller()->min_video_frames_received_per_track();
    if (media_expectations.caller_video_expectation_ ==
        MediaExpectations::kExpectSomeFrames) {
      total_caller_video_frames_expected +=
          media_expectations.caller_video_frames_expected_;
    }
    int total_callee_audio_frames_expected = callee()->audio_frames_received();
    if (media_expectations.callee_audio_expectation_ ==
        MediaExpectations::kExpectSomeFrames) {
      total_callee_audio_frames_expected +=
          media_expectations.callee_audio_frames_expected_;
    }
    int total_callee_video_frames_expected =
        callee()->min_video_frames_received_per_track();
    if (media_expectations.callee_video_expectation_ ==
        MediaExpectations::kExpectSomeFrames) {
      total_callee_video_frames_expected +=
          media_expectations.callee_video_frames_expected_;
    }

    // Wait for the expected frames.
    EXPECT_TRUE_WAIT(caller()->audio_frames_received() >=
                             total_caller_audio_frames_expected &&
                         caller()->min_video_frames_received_per_track() >=
                             total_caller_video_frames_expected &&
                         callee()->audio_frames_received() >=
                             total_callee_audio_frames_expected &&
                         callee()->min_video_frames_received_per_track() >=
                             total_callee_video_frames_expected,
                     kMaxWaitForFramesMs);
    bool expectations_correct =
        caller()->audio_frames_received() >=
            total_caller_audio_frames_expected &&
        caller()->min_video_frames_received_per_track() >=
            total_caller_video_frames_expected &&
        callee()->audio_frames_received() >=
            total_callee_audio_frames_expected &&
        callee()->min_video_frames_received_per_track() >=
            total_callee_video_frames_expected;

    // After the combined wait, print out a more detailed message upon
    // failure.
    EXPECT_GE(caller()->audio_frames_received(),
              total_caller_audio_frames_expected);
    EXPECT_GE(caller()->min_video_frames_received_per_track(),
              total_caller_video_frames_expected);
    EXPECT_GE(callee()->audio_frames_received(),
              total_callee_audio_frames_expected);
    EXPECT_GE(callee()->min_video_frames_received_per_track(),
              total_callee_video_frames_expected);

    // We want to make sure nothing unexpected was received.
    if (media_expectations.caller_audio_expectation_ ==
        MediaExpectations::kExpectNoFrames) {
      EXPECT_EQ(caller()->audio_frames_received(),
                total_caller_audio_frames_expected);
      if (caller()->audio_frames_received() !=
          total_caller_audio_frames_expected) {
        expectations_correct = false;
      }
    }
    if (media_expectations.caller_video_expectation_ ==
        MediaExpectations::kExpectNoFrames) {
      EXPECT_EQ(caller()->min_video_frames_received_per_track(),
                total_caller_video_frames_expected);
      if (caller()->min_video_frames_received_per_track() !=
          total_caller_video_frames_expected) {
        expectations_correct = false;
      }
    }
    if (media_expectations.callee_audio_expectation_ ==
        MediaExpectations::kExpectNoFrames) {
      EXPECT_EQ(callee()->audio_frames_received(),
                total_callee_audio_frames_expected);
      if (callee()->audio_frames_received() !=
          total_callee_audio_frames_expected) {
        expectations_correct = false;
      }
    }
    if (media_expectations.callee_video_expectation_ ==
        MediaExpectations::kExpectNoFrames) {
      EXPECT_EQ(callee()->min_video_frames_received_per_track(),
                total_callee_video_frames_expected);
      if (callee()->min_video_frames_received_per_track() !=
          total_callee_video_frames_expected) {
        expectations_correct = false;
      }
    }
    return expectations_correct;
  }

  void ClosePeerConnections() {
    if (caller())
      caller()->pc()->Close();
    if (callee())
      callee()->pc()->Close();
  }

  void TestNegotiatedCipherSuite(
      const PeerConnectionFactory::Options& caller_options,
      const PeerConnectionFactory::Options& callee_options,
      int expected_cipher_suite) {
    ASSERT_TRUE(CreatePeerConnectionWrappersWithOptions(caller_options,
                                                        callee_options));
    ConnectFakeSignaling();
    caller()->AddAudioVideoTracks();
    callee()->AddAudioVideoTracks();
    caller()->CreateAndSetAndSignalOffer();
    ASSERT_TRUE_WAIT(DtlsConnected(), kDefaultTimeout);
    EXPECT_EQ_WAIT(rtc::SrtpCryptoSuiteToName(expected_cipher_suite),
                   caller()->OldGetStats()->SrtpCipher(), kDefaultTimeout);
  }

  void TestGcmNegotiationUsesCipherSuite(bool local_gcm_enabled,
                                         bool remote_gcm_enabled,
                                         bool aes_ctr_enabled,
                                         int expected_cipher_suite) {
    PeerConnectionFactory::Options caller_options;
    caller_options.crypto_options.srtp.enable_gcm_crypto_suites =
        local_gcm_enabled;
    caller_options.crypto_options.srtp.enable_aes128_sha1_80_crypto_cipher =
        aes_ctr_enabled;
    PeerConnectionFactory::Options callee_options;
    callee_options.crypto_options.srtp.enable_gcm_crypto_suites =
        remote_gcm_enabled;
    callee_options.crypto_options.srtp.enable_aes128_sha1_80_crypto_cipher =
        aes_ctr_enabled;
    TestNegotiatedCipherSuite(caller_options, callee_options,
                              expected_cipher_suite);
  }

  const FieldTrialsView& trials() const { return *field_trials_.get(); }

 protected:
  SdpSemantics sdp_semantics_;

 private:
  rtc::AutoThread main_thread_;  // Used as the signal thread by most tests.
  // `ss_` is used by `network_thread_` so it must be destroyed later.
  std::unique_ptr<rtc::VirtualSocketServer> ss_;
  std::unique_ptr<rtc::FirewallSocketServer> fss_;
  // `network_thread_` and `worker_thread_` are used by both
  // `caller_` and `callee_` so they must be destroyed
  // later.
  std::unique_ptr<rtc::Thread> network_thread_;
  std::unique_ptr<rtc::Thread> worker_thread_;
  // The turn servers and turn customizers should be accessed & deleted on the
  // network thread to avoid a race with the socket read/write that occurs
  // on the network thread.
  std::vector<std::unique_ptr<cricket::TestTurnServer>> turn_servers_;
  std::vector<std::unique_ptr<cricket::TestTurnCustomizer>> turn_customizers_;
  std::unique_ptr<PeerConnectionIntegrationWrapper> caller_;
  std::unique_ptr<PeerConnectionIntegrationWrapper> callee_;
  std::unique_ptr<FieldTrialsView> field_trials_;
};

}  // namespace webrtc

#endif  // PC_TEST_INTEGRATION_TEST_HELPERS_H_
