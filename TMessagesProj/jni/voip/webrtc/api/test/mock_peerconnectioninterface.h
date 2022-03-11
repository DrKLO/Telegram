/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_MOCK_PEERCONNECTIONINTERFACE_H_
#define API_TEST_MOCK_PEERCONNECTIONINTERFACE_H_

#include <memory>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#include "api/peer_connection_interface.h"
#include "api/scoped_refptr.h"
#include "api/sctp_transport_interface.h"
#include "rtc_base/ref_counted_object.h"
#include "test/gmock.h"

namespace webrtc {

class MockPeerConnectionInterface
    : public rtc::RefCountedObject<webrtc::PeerConnectionInterface> {
 public:
  static rtc::scoped_refptr<MockPeerConnectionInterface> Create() {
    return rtc::make_ref_counted<MockPeerConnectionInterface>();
  }

  // PeerConnectionInterface
  MOCK_METHOD(rtc::scoped_refptr<StreamCollectionInterface>,
              local_streams,
              (),
              (override));
  MOCK_METHOD(rtc::scoped_refptr<StreamCollectionInterface>,
              remote_streams,
              (),
              (override));
  MOCK_METHOD(bool, AddStream, (MediaStreamInterface*), (override));
  MOCK_METHOD(void, RemoveStream, (MediaStreamInterface*), (override));
  MOCK_METHOD(RTCErrorOr<rtc::scoped_refptr<RtpSenderInterface>>,
              AddTrack,
              (rtc::scoped_refptr<MediaStreamTrackInterface>,
               const std::vector<std::string>&),
              (override));
  MOCK_METHOD(RTCError,
              RemoveTrackOrError,
              (rtc::scoped_refptr<RtpSenderInterface>),
              (override));
  MOCK_METHOD(RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>>,
              AddTransceiver,
              (rtc::scoped_refptr<MediaStreamTrackInterface>),
              (override));
  MOCK_METHOD(RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>>,
              AddTransceiver,
              (rtc::scoped_refptr<MediaStreamTrackInterface>,
               const RtpTransceiverInit&),
              (override));
  MOCK_METHOD(RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>>,
              AddTransceiver,
              (cricket::MediaType),
              (override));
  MOCK_METHOD(RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>>,
              AddTransceiver,
              (cricket::MediaType, const RtpTransceiverInit&),
              (override));
  MOCK_METHOD(rtc::scoped_refptr<RtpSenderInterface>,
              CreateSender,
              (const std::string&, const std::string&),
              (override));
  MOCK_METHOD(std::vector<rtc::scoped_refptr<RtpSenderInterface>>,
              GetSenders,
              (),
              (const, override));
  MOCK_METHOD(std::vector<rtc::scoped_refptr<RtpReceiverInterface>>,
              GetReceivers,
              (),
              (const, override));
  MOCK_METHOD(std::vector<rtc::scoped_refptr<RtpTransceiverInterface>>,
              GetTransceivers,
              (),
              (const, override));
  MOCK_METHOD(bool,
              GetStats,
              (StatsObserver*, MediaStreamTrackInterface*, StatsOutputLevel),
              (override));
  MOCK_METHOD(void, GetStats, (RTCStatsCollectorCallback*), (override));
  MOCK_METHOD(void,
              GetStats,
              (rtc::scoped_refptr<RtpSenderInterface>,
               rtc::scoped_refptr<RTCStatsCollectorCallback>),
              (override));
  MOCK_METHOD(void,
              GetStats,
              (rtc::scoped_refptr<RtpReceiverInterface>,
               rtc::scoped_refptr<RTCStatsCollectorCallback>),
              (override));
  MOCK_METHOD(void, ClearStatsCache, (), (override));
  MOCK_METHOD(rtc::scoped_refptr<SctpTransportInterface>,
              GetSctpTransport,
              (),
              (const, override));
  MOCK_METHOD(RTCErrorOr<rtc::scoped_refptr<DataChannelInterface>>,
              CreateDataChannelOrError,
              (const std::string&, const DataChannelInit*),
              (override));
  MOCK_METHOD(const SessionDescriptionInterface*,
              local_description,
              (),
              (const, override));
  MOCK_METHOD(const SessionDescriptionInterface*,
              remote_description,
              (),
              (const, override));
  MOCK_METHOD(const SessionDescriptionInterface*,
              current_local_description,
              (),
              (const, override));
  MOCK_METHOD(const SessionDescriptionInterface*,
              current_remote_description,
              (),
              (const, override));
  MOCK_METHOD(const SessionDescriptionInterface*,
              pending_local_description,
              (),
              (const, override));
  MOCK_METHOD(const SessionDescriptionInterface*,
              pending_remote_description,
              (),
              (const, override));
  MOCK_METHOD(void, RestartIce, (), (override));
  MOCK_METHOD(void,
              CreateOffer,
              (CreateSessionDescriptionObserver*, const RTCOfferAnswerOptions&),
              (override));
  MOCK_METHOD(void,
              CreateAnswer,
              (CreateSessionDescriptionObserver*, const RTCOfferAnswerOptions&),
              (override));
  MOCK_METHOD(void,
              SetLocalDescription,
              (SetSessionDescriptionObserver*, SessionDescriptionInterface*),
              (override));
  MOCK_METHOD(void,
              SetRemoteDescription,
              (SetSessionDescriptionObserver*, SessionDescriptionInterface*),
              (override));
  MOCK_METHOD(void,
              SetRemoteDescription,
              (std::unique_ptr<SessionDescriptionInterface>,
               rtc::scoped_refptr<SetRemoteDescriptionObserverInterface>),
              (override));
  MOCK_METHOD(PeerConnectionInterface::RTCConfiguration,
              GetConfiguration,
              (),
              (override));
  MOCK_METHOD(RTCError,
              SetConfiguration,
              (const PeerConnectionInterface::RTCConfiguration&),
              (override));
  MOCK_METHOD(bool,
              AddIceCandidate,
              (const IceCandidateInterface*),
              (override));
  MOCK_METHOD(bool,
              RemoveIceCandidates,
              (const std::vector<cricket::Candidate>&),
              (override));
  MOCK_METHOD(RTCError, SetBitrate, (const BitrateSettings&), (override));
  MOCK_METHOD(void, SetAudioPlayout, (bool), (override));
  MOCK_METHOD(void, SetAudioRecording, (bool), (override));
  MOCK_METHOD(rtc::scoped_refptr<DtlsTransportInterface>,
              LookupDtlsTransportByMid,
              (const std::string&),
              (override));
  MOCK_METHOD(SignalingState, signaling_state, (), (override));
  MOCK_METHOD(IceConnectionState, ice_connection_state, (), (override));
  MOCK_METHOD(IceConnectionState,
              standardized_ice_connection_state,
              (),
              (override));
  MOCK_METHOD(PeerConnectionState, peer_connection_state, (), (override));
  MOCK_METHOD(IceGatheringState, ice_gathering_state, (), (override));
  MOCK_METHOD(absl::optional<bool>, can_trickle_ice_candidates, (), (override));
  MOCK_METHOD(bool,
              StartRtcEventLog,
              (std::unique_ptr<RtcEventLogOutput>, int64_t),
              (override));
  MOCK_METHOD(bool,
              StartRtcEventLog,
              (std::unique_ptr<RtcEventLogOutput>),
              (override));
  MOCK_METHOD(void, StopRtcEventLog, (), (override));
  MOCK_METHOD(void, Close, (), (override));
};

static_assert(!std::is_abstract<MockPeerConnectionInterface>::value, "");

}  // namespace webrtc

#endif  // API_TEST_MOCK_PEERCONNECTIONINTERFACE_H_
