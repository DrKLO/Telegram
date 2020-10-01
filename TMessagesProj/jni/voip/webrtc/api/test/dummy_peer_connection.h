/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_DUMMY_PEER_CONNECTION_H_
#define API_TEST_DUMMY_PEER_CONNECTION_H_

#include <memory>
#include <string>
#include <vector>

#include "api/peer_connection_interface.h"
#include "api/rtc_error.h"
#include "rtc_base/checks.h"
#include "rtc_base/ref_counted_object.h"

namespace webrtc {

// This class includes dummy implementations of all methods on the
// PeerconnectionInterface. Accessor/getter methods return empty or default
// values. State-changing methods with a return value return failure. Remaining
// methods (except Close())) will crash with FATAL if called.
class DummyPeerConnection : public PeerConnectionInterface {
  rtc::scoped_refptr<StreamCollectionInterface> local_streams() override {
    return nullptr;
  }
  rtc::scoped_refptr<StreamCollectionInterface> remote_streams() override {
    return nullptr;
  }

  bool AddStream(MediaStreamInterface* stream) override { return false; }
  void RemoveStream(MediaStreamInterface* stream) override {
    FATAL() << "Not implemented";
  }

  RTCErrorOr<rtc::scoped_refptr<RtpSenderInterface>> AddTrack(
      rtc::scoped_refptr<MediaStreamTrackInterface> track,
      const std::vector<std::string>& stream_ids) override {
    return RTCError(RTCErrorType::UNSUPPORTED_OPERATION, "Not implemented");
  }

  bool RemoveTrack(RtpSenderInterface* sender) override { return false; }

  RTCError RemoveTrackNew(
      rtc::scoped_refptr<RtpSenderInterface> sender) override {
    return RTCError(RTCErrorType::UNSUPPORTED_OPERATION, "Not implemented");
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
                MediaStreamTrackInterface* track,  // Optional
                StatsOutputLevel level) override {
    return false;
  }

  void GetStats(RTCStatsCollectorCallback* callback) override {
    FATAL() << "Not implemented";
  }
  void GetStats(
      rtc::scoped_refptr<RtpSenderInterface> selector,
      rtc::scoped_refptr<RTCStatsCollectorCallback> callback) override {
    FATAL() << "Not implemented";
  }
  void GetStats(
      rtc::scoped_refptr<RtpReceiverInterface> selector,
      rtc::scoped_refptr<RTCStatsCollectorCallback> callback) override {
    FATAL() << "Not implemented";
  }
  void ClearStatsCache() override {}

  rtc::scoped_refptr<DataChannelInterface> CreateDataChannel(
      const std::string& label,
      const DataChannelInit* config) override {
    return nullptr;
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

  void RestartIce() override { FATAL() << "Not implemented"; }

  // Create a new offer.
  // The CreateSessionDescriptionObserver callback will be called when done.
  void CreateOffer(CreateSessionDescriptionObserver* observer,
                   const RTCOfferAnswerOptions& options) override {
    FATAL() << "Not implemented";
  }

  void CreateAnswer(CreateSessionDescriptionObserver* observer,
                    const RTCOfferAnswerOptions& options) override {
    FATAL() << "Not implemented";
  }

  void SetLocalDescription(SetSessionDescriptionObserver* observer,
                           SessionDescriptionInterface* desc) override {
    FATAL() << "Not implemented";
  }
  void SetRemoteDescription(SetSessionDescriptionObserver* observer,
                            SessionDescriptionInterface* desc) override {
    FATAL() << "Not implemented";
  }
  void SetRemoteDescription(
      std::unique_ptr<SessionDescriptionInterface> desc,
      rtc::scoped_refptr<SetRemoteDescriptionObserverInterface> observer)
      override {
    FATAL() << "Not implemented";
  }

  PeerConnectionInterface::RTCConfiguration GetConfiguration() override {
    return RTCConfiguration();
  }
  RTCError SetConfiguration(
      const PeerConnectionInterface::RTCConfiguration& config) override {
    return RTCError(RTCErrorType::UNSUPPORTED_OPERATION, "Not implemented");
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

  void SetAudioPlayout(bool playout) override { FATAL() << "Not implemented"; }
  void SetAudioRecording(bool recording) override {
    FATAL() << "Not implemented";
  }

  rtc::scoped_refptr<DtlsTransportInterface> LookupDtlsTransportByMid(
      const std::string& mid) override {
    return nullptr;
  }
  rtc::scoped_refptr<SctpTransportInterface> GetSctpTransport() const override {
    return nullptr;
  }

  SignalingState signaling_state() override { return SignalingState(); }

  IceConnectionState ice_connection_state() override {
    return IceConnectionState();
  }

  IceConnectionState standardized_ice_connection_state() override {
    return IceConnectionState();
  }

  PeerConnectionState peer_connection_state() override {
    return PeerConnectionState();
  }

  IceGatheringState ice_gathering_state() override {
    return IceGatheringState();
  }

  absl::optional<bool> can_trickle_ice_candidates() { return absl::nullopt; }

  bool StartRtcEventLog(std::unique_ptr<RtcEventLogOutput> output,
                        int64_t output_period_ms) override {
    return false;
  }
  bool StartRtcEventLog(std::unique_ptr<RtcEventLogOutput> output) override {
    return false;
  }

  void StopRtcEventLog() { FATAL() << "Not implemented"; }

  void Close() override {}

  rtc::Thread* signaling_thread() const override {
    return rtc::Thread::Current();
  }
};

static_assert(
    !std::is_abstract<rtc::RefCountedObject<DummyPeerConnection>>::value,
    "");

}  // namespace webrtc

#endif  // API_TEST_DUMMY_PEER_CONNECTION_H_
