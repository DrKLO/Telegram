/*
 *  Copyright 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_PEER_CONNECTION_PROXY_H_
#define API_PEER_CONNECTION_PROXY_H_

#include <memory>
#include <string>
#include <vector>

#include "api/peer_connection_interface.h"
#include "api/proxy.h"

namespace webrtc {

// PeerConnection proxy objects will be constructed with two thread pointers,
// signaling and network. The proxy macros don't have 'network' specific macros
// and support for a secondary thread is provided via 'SECONDARY' macros.
// TODO(deadbeef): Move this to .cc file and out of api/. What threads methods
// are called on is an implementation detail.
BEGIN_PROXY_MAP(PeerConnection)
PROXY_PRIMARY_THREAD_DESTRUCTOR()
PROXY_METHOD0(rtc::scoped_refptr<StreamCollectionInterface>, local_streams)
PROXY_METHOD0(rtc::scoped_refptr<StreamCollectionInterface>, remote_streams)
PROXY_METHOD1(bool, AddStream, MediaStreamInterface*)
PROXY_METHOD1(void, RemoveStream, MediaStreamInterface*)
PROXY_METHOD2(RTCErrorOr<rtc::scoped_refptr<RtpSenderInterface>>,
              AddTrack,
              rtc::scoped_refptr<MediaStreamTrackInterface>,
              const std::vector<std::string>&)
PROXY_METHOD1(bool, RemoveTrack, RtpSenderInterface*)
PROXY_METHOD1(RTCError, RemoveTrackNew, rtc::scoped_refptr<RtpSenderInterface>)
PROXY_METHOD1(RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>>,
              AddTransceiver,
              rtc::scoped_refptr<MediaStreamTrackInterface>)
PROXY_METHOD2(RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>>,
              AddTransceiver,
              rtc::scoped_refptr<MediaStreamTrackInterface>,
              const RtpTransceiverInit&)
PROXY_METHOD1(RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>>,
              AddTransceiver,
              cricket::MediaType)
PROXY_METHOD2(RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>>,
              AddTransceiver,
              cricket::MediaType,
              const RtpTransceiverInit&)
PROXY_METHOD2(rtc::scoped_refptr<RtpSenderInterface>,
              CreateSender,
              const std::string&,
              const std::string&)
PROXY_CONSTMETHOD0(std::vector<rtc::scoped_refptr<RtpSenderInterface>>,
                   GetSenders)
PROXY_CONSTMETHOD0(std::vector<rtc::scoped_refptr<RtpReceiverInterface>>,
                   GetReceivers)
PROXY_CONSTMETHOD0(std::vector<rtc::scoped_refptr<RtpTransceiverInterface>>,
                   GetTransceivers)
PROXY_METHOD3(bool,
              GetStats,
              StatsObserver*,
              MediaStreamTrackInterface*,
              StatsOutputLevel)
PROXY_METHOD1(void, GetStats, RTCStatsCollectorCallback*)
PROXY_METHOD2(void,
              GetStats,
              rtc::scoped_refptr<RtpSenderInterface>,
              rtc::scoped_refptr<RTCStatsCollectorCallback>)
PROXY_METHOD2(void,
              GetStats,
              rtc::scoped_refptr<RtpReceiverInterface>,
              rtc::scoped_refptr<RTCStatsCollectorCallback>)
PROXY_METHOD0(void, ClearStatsCache)
PROXY_METHOD2(rtc::scoped_refptr<DataChannelInterface>,
              CreateDataChannel,
              const std::string&,
              const DataChannelInit*)
PROXY_CONSTMETHOD0(const SessionDescriptionInterface*, local_description)
PROXY_CONSTMETHOD0(const SessionDescriptionInterface*, remote_description)
PROXY_CONSTMETHOD0(const SessionDescriptionInterface*,
                   current_local_description)
PROXY_CONSTMETHOD0(const SessionDescriptionInterface*,
                   current_remote_description)
PROXY_CONSTMETHOD0(const SessionDescriptionInterface*,
                   pending_local_description)
PROXY_CONSTMETHOD0(const SessionDescriptionInterface*,
                   pending_remote_description)
PROXY_METHOD0(void, RestartIce)
PROXY_METHOD2(void,
              CreateOffer,
              CreateSessionDescriptionObserver*,
              const RTCOfferAnswerOptions&)
PROXY_METHOD2(void,
              CreateAnswer,
              CreateSessionDescriptionObserver*,
              const RTCOfferAnswerOptions&)
PROXY_METHOD2(void,
              SetLocalDescription,
              std::unique_ptr<SessionDescriptionInterface>,
              rtc::scoped_refptr<SetLocalDescriptionObserverInterface>)
PROXY_METHOD1(void,
              SetLocalDescription,
              rtc::scoped_refptr<SetLocalDescriptionObserverInterface>)
PROXY_METHOD2(void,
              SetLocalDescription,
              SetSessionDescriptionObserver*,
              SessionDescriptionInterface*)
PROXY_METHOD1(void, SetLocalDescription, SetSessionDescriptionObserver*)
PROXY_METHOD2(void,
              SetRemoteDescription,
              std::unique_ptr<SessionDescriptionInterface>,
              rtc::scoped_refptr<SetRemoteDescriptionObserverInterface>)
PROXY_METHOD2(void,
              SetRemoteDescription,
              SetSessionDescriptionObserver*,
              SessionDescriptionInterface*)
PROXY_METHOD1(bool, ShouldFireNegotiationNeededEvent, uint32_t)
PROXY_METHOD0(PeerConnectionInterface::RTCConfiguration, GetConfiguration)
PROXY_METHOD1(RTCError,
              SetConfiguration,
              const PeerConnectionInterface::RTCConfiguration&)
PROXY_METHOD1(bool, AddIceCandidate, const IceCandidateInterface*)
PROXY_METHOD2(void,
              AddIceCandidate,
              std::unique_ptr<IceCandidateInterface>,
              std::function<void(RTCError)>)
PROXY_METHOD1(bool, RemoveIceCandidates, const std::vector<cricket::Candidate>&)
PROXY_METHOD1(RTCError, SetBitrate, const BitrateSettings&)
PROXY_METHOD1(void, SetAudioPlayout, bool)
PROXY_METHOD1(void, SetAudioRecording, bool)
// This method will be invoked on the network thread. See
// PeerConnectionFactory::CreatePeerConnectionOrError for more details.
PROXY_SECONDARY_METHOD1(rtc::scoped_refptr<DtlsTransportInterface>,
                        LookupDtlsTransportByMid,
                        const std::string&)
// This method will be invoked on the network thread. See
// PeerConnectionFactory::CreatePeerConnectionOrError for more details.
PROXY_SECONDARY_CONSTMETHOD0(rtc::scoped_refptr<SctpTransportInterface>,
                             GetSctpTransport)
PROXY_METHOD0(SignalingState, signaling_state)
PROXY_METHOD0(IceConnectionState, ice_connection_state)
PROXY_METHOD0(IceConnectionState, standardized_ice_connection_state)
PROXY_METHOD0(PeerConnectionState, peer_connection_state)
PROXY_METHOD0(IceGatheringState, ice_gathering_state)
PROXY_METHOD0(absl::optional<bool>, can_trickle_ice_candidates)
PROXY_METHOD1(void, AddAdaptationResource, rtc::scoped_refptr<Resource>)
PROXY_METHOD2(bool,
              StartRtcEventLog,
              std::unique_ptr<RtcEventLogOutput>,
              int64_t)
PROXY_METHOD1(bool, StartRtcEventLog, std::unique_ptr<RtcEventLogOutput>)
PROXY_METHOD0(void, StopRtcEventLog)
PROXY_METHOD0(void, Close)
BYPASS_PROXY_CONSTMETHOD0(rtc::Thread*, signaling_thread)
END_PROXY_MAP()

}  // namespace webrtc

#endif  // API_PEER_CONNECTION_PROXY_H_
