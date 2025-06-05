/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_TEST_FAKE_PEER_CONNECTION_BASE_H_
#define PC_TEST_FAKE_PEER_CONNECTION_BASE_H_

#include <map>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/field_trials_view.h"
#include "api/sctp_transport_interface.h"
#include "pc/peer_connection_internal.h"
#include "test/scoped_key_value_config.h"

namespace webrtc {

// Customized PeerConnection fakes can be created by subclassing
// FakePeerConnectionBase then overriding the interesting methods. This class
// takes care of providing default implementations for all the pure virtual
// functions specified in the interfaces.
class FakePeerConnectionBase : public PeerConnectionInternal {
 public:
  // PeerConnectionInterface implementation.

  rtc::scoped_refptr<StreamCollectionInterface> local_streams() override {
    return nullptr;
  }

  rtc::scoped_refptr<StreamCollectionInterface> remote_streams() override {
    return nullptr;
  }

  bool AddStream(MediaStreamInterface* stream) override { return false; }

  void RemoveStream(MediaStreamInterface* stream) override {}

  RTCErrorOr<rtc::scoped_refptr<RtpSenderInterface>> AddTrack(
      rtc::scoped_refptr<MediaStreamTrackInterface> track,
      const std::vector<std::string>& stream_ids) override {
    return RTCError(RTCErrorType::UNSUPPORTED_OPERATION, "Not implemented");
  }

  RTCErrorOr<rtc::scoped_refptr<RtpSenderInterface>> AddTrack(
      rtc::scoped_refptr<MediaStreamTrackInterface> track,
      const std::vector<std::string>& stream_ids,
      const std::vector<RtpEncodingParameters>& init_send_encodings) override {
    return RTCError(RTCErrorType::UNSUPPORTED_OPERATION, "Not implemented");
  }

  RTCError RemoveTrackOrError(
      rtc::scoped_refptr<RtpSenderInterface> sender) override {
    return RTCError(RTCErrorType::UNSUPPORTED_OPERATION);
  }

  RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>> AddTransceiver(
      rtc::scoped_refptr<MediaStreamTrackInterface> track) override {
    return RTCError(RTCErrorType::UNSUPPORTED_OPERATION, "Not implemented");
  }

  RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>> AddTransceiver(
      rtc::scoped_refptr<MediaStreamTrackInterface> track,
      const RtpTransceiverInit& init) override {
    return RTCError(RTCErrorType::UNSUPPORTED_OPERATION, "Not implemented");
  }

  RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>> AddTransceiver(
      cricket::MediaType media_type) override {
    return RTCError(RTCErrorType::UNSUPPORTED_OPERATION, "Not implemented");
  }

  RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>> AddTransceiver(
      cricket::MediaType media_type,
      const RtpTransceiverInit& init) override {
    return RTCError(RTCErrorType::UNSUPPORTED_OPERATION, "Not implemented");
  }

  rtc::scoped_refptr<RtpSenderInterface> CreateSender(
      const std::string& kind,
      const std::string& stream_id) override {
    return nullptr;
  }

  std::vector<rtc::scoped_refptr<RtpSenderInterface>> GetSenders()
      const override {
    return {};
  }

  std::vector<rtc::scoped_refptr<RtpReceiverInterface>> GetReceivers()
      const override {
    return {};
  }

  std::vector<rtc::scoped_refptr<RtpTransceiverInterface>> GetTransceivers()
      const override {
    return {};
  }

  bool GetStats(StatsObserver* observer,
                MediaStreamTrackInterface* track,
                StatsOutputLevel level) override {
    return false;
  }

  void GetStats(RTCStatsCollectorCallback* callback) override {}
  void GetStats(
      rtc::scoped_refptr<RtpSenderInterface> selector,
      rtc::scoped_refptr<RTCStatsCollectorCallback> callback) override {}
  void GetStats(
      rtc::scoped_refptr<RtpReceiverInterface> selector,
      rtc::scoped_refptr<RTCStatsCollectorCallback> callback) override {}

  void ClearStatsCache() override {}

  rtc::scoped_refptr<SctpTransportInterface> GetSctpTransport() const {
    return nullptr;
  }

  RTCErrorOr<rtc::scoped_refptr<DataChannelInterface>> CreateDataChannelOrError(
      const std::string& label,
      const DataChannelInit* config) override {
    return RTCError(RTCErrorType::UNSUPPORTED_OPERATION,
                    "Fake function called");
  }

  const SessionDescriptionInterface* local_description() const override {
    return nullptr;
  }
  const SessionDescriptionInterface* remote_description() const override {
    return nullptr;
  }

  const SessionDescriptionInterface* current_local_description()
      const override {
    return nullptr;
  }
  const SessionDescriptionInterface* current_remote_description()
      const override {
    return nullptr;
  }

  const SessionDescriptionInterface* pending_local_description()
      const override {
    return nullptr;
  }
  const SessionDescriptionInterface* pending_remote_description()
      const override {
    return nullptr;
  }

  void RestartIce() override {}

  void CreateOffer(CreateSessionDescriptionObserver* observer,
                   const RTCOfferAnswerOptions& options) override {}

  void CreateAnswer(CreateSessionDescriptionObserver* observer,
                    const RTCOfferAnswerOptions& options) override {}

  void SetLocalDescription(SetSessionDescriptionObserver* observer,
                           SessionDescriptionInterface* desc) override {}

  void SetRemoteDescription(SetSessionDescriptionObserver* observer,
                            SessionDescriptionInterface* desc) override {}

  void SetRemoteDescription(
      std::unique_ptr<SessionDescriptionInterface> desc,
      rtc::scoped_refptr<SetRemoteDescriptionObserverInterface> observer)
      override {}

  RTCConfiguration GetConfiguration() override { return RTCConfiguration(); }

  RTCError SetConfiguration(
      const PeerConnectionInterface::RTCConfiguration& config) override {
    return RTCError();
  }

  bool AddIceCandidate(const IceCandidateInterface* candidate) override {
    return false;
  }

  bool RemoveIceCandidates(
      const std::vector<cricket::Candidate>& candidates) override {
    return false;
  }

  RTCError SetBitrate(const BitrateSettings& bitrate) override {
    return RTCError(RTCErrorType::UNSUPPORTED_OPERATION, "Not implemented");
  }

  void ReconfigureBandwidthEstimation(
      const BandwidthEstimationSettings& settings) override {}

  void SetAudioPlayout(bool playout) override {}

  void SetAudioRecording(bool recording) override {}

  rtc::scoped_refptr<DtlsTransportInterface> LookupDtlsTransportByMid(
      const std::string& mid) {
    return nullptr;
  }

  SignalingState signaling_state() override { return SignalingState::kStable; }

  IceConnectionState ice_connection_state() override {
    return IceConnectionState::kIceConnectionNew;
  }

  IceConnectionState standardized_ice_connection_state() override {
    return IceConnectionState::kIceConnectionNew;
  }

  PeerConnectionState peer_connection_state() override {
    return PeerConnectionState::kNew;
  }

  IceGatheringState ice_gathering_state() override {
    return IceGatheringState::kIceGatheringNew;
  }

  absl::optional<bool> can_trickle_ice_candidates() { return absl::nullopt; }

  bool StartRtcEventLog(std::unique_ptr<RtcEventLogOutput> output,
                        int64_t output_period_ms) override {
    return false;
  }

  bool StartRtcEventLog(std::unique_ptr<RtcEventLogOutput> output) override {
    return false;
  }

  void StopRtcEventLog() override {}

  void Close() override {}

  // PeerConnectionInternal implementation.

  rtc::Thread* network_thread() const override { return nullptr; }
  rtc::Thread* worker_thread() const override { return nullptr; }
  rtc::Thread* signaling_thread() const override { return nullptr; }

  std::string session_id() const override { return ""; }

  bool initial_offerer() const override { return false; }

  std::vector<
      rtc::scoped_refptr<RtpTransceiverProxyWithInternal<RtpTransceiver>>>
  GetTransceiversInternal() const override {
    return {};
  }

  absl::optional<std::string> sctp_transport_name() const override {
    return absl::nullopt;
  }

  absl::optional<std::string> sctp_mid() const override {
    return absl::nullopt;
  }

  std::map<std::string, cricket::TransportStats> GetTransportStatsByNames(
      const std::set<std::string>& transport_names) override {
    return {};
  }

  Call::Stats GetCallStats() override { return Call::Stats(); }

  absl::optional<AudioDeviceModule::Stats> GetAudioDeviceStats() override {
    return absl::nullopt;
  }

  bool GetLocalCertificate(
      const std::string& transport_name,
      rtc::scoped_refptr<rtc::RTCCertificate>* certificate) override {
    return false;
  }

  std::unique_ptr<rtc::SSLCertChain> GetRemoteSSLCertChain(
      const std::string& transport_name) override {
    return nullptr;
  }

  bool IceRestartPending(const std::string& content_name) const override {
    return false;
  }

  bool NeedsIceRestart(const std::string& content_name) const override {
    return false;
  }

  bool GetSslRole(const std::string& content_name,
                  rtc::SSLRole* role) override {
    return false;
  }
  const PeerConnectionInterface::RTCConfiguration* configuration()
      const override {
    return nullptr;
  }

  void ReportSdpBundleUsage(
      const SessionDescriptionInterface& remote_description) override {}

  PeerConnectionMessageHandler* message_handler() override { return nullptr; }
  RtpTransmissionManager* rtp_manager() override { return nullptr; }
  const RtpTransmissionManager* rtp_manager() const override { return nullptr; }
  bool dtls_enabled() const override { return false; }
  const PeerConnectionFactoryInterface::Options* options() const override {
    return nullptr;
  }

  CryptoOptions GetCryptoOptions() override { return CryptoOptions(); }
  JsepTransportController* transport_controller_s() override { return nullptr; }
  JsepTransportController* transport_controller_n() override { return nullptr; }
  DataChannelController* data_channel_controller() override { return nullptr; }
  cricket::PortAllocator* port_allocator() override { return nullptr; }
  LegacyStatsCollector* legacy_stats() override { return nullptr; }
  PeerConnectionObserver* Observer() const override { return nullptr; }
  absl::optional<rtc::SSLRole> GetSctpSslRole_n() override {
    return absl::nullopt;
  }
  PeerConnectionInterface::IceConnectionState ice_connection_state_internal()
      override {
    return PeerConnectionInterface::IceConnectionState::kIceConnectionNew;
  }
  void SetIceConnectionState(
      PeerConnectionInterface::IceConnectionState new_state) override {}
  void NoteUsageEvent(UsageEvent event) override {}
  bool IsClosed() const override { return false; }
  bool IsUnifiedPlan() const override { return true; }
  bool ValidateBundleSettings(
      const cricket::SessionDescription* desc,
      const std::map<std::string, const cricket::ContentGroup*>&
          bundle_groups_by_mid) override {
    return false;
  }

  RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>> AddTransceiver(
      cricket::MediaType media_type,
      rtc::scoped_refptr<MediaStreamTrackInterface> track,
      const RtpTransceiverInit& init,
      bool fire_callback = true) override {
    return RTCError(RTCErrorType::INTERNAL_ERROR, "");
  }
  void StartSctpTransport(int local_port,
                          int remote_port,
                          int max_message_size) override {}

  void AddRemoteCandidate(const std::string& mid,
                          const cricket::Candidate& candidate) override {}

  Call* call_ptr() override { return nullptr; }
  bool SrtpRequired() const override { return false; }
  bool CreateDataChannelTransport(absl::string_view mid) override {
    return false;
  }
  void DestroyDataChannelTransport(RTCError error) override {}

  const FieldTrialsView& trials() const override { return field_trials_; }

 protected:
  test::ScopedKeyValueConfig field_trials_;
};

}  // namespace webrtc

#endif  // PC_TEST_FAKE_PEER_CONNECTION_BASE_H_
