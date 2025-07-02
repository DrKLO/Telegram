/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_TEST_PEER_CONNECTION_TEST_WRAPPER_H_
#define PC_TEST_PEER_CONNECTION_TEST_WRAPPER_H_

#include <memory>
#include <string>
#include <vector>

#include "api/audio_codecs/audio_decoder_factory.h"
#include "api/audio_codecs/audio_encoder_factory.h"
#include "api/audio_options.h"
#include "api/data_channel_interface.h"
#include "api/jsep.h"
#include "api/media_stream_interface.h"
#include "api/peer_connection_interface.h"
#include "api/rtc_error.h"
#include "api/rtp_parameters.h"
#include "api/rtp_receiver_interface.h"
#include "api/scoped_refptr.h"
#include "api/sequence_checker.h"
#include "api/video/resolution.h"
#include "pc/test/fake_audio_capture_module.h"
#include "pc/test/fake_periodic_video_source.h"
#include "pc/test/fake_periodic_video_track_source.h"
#include "pc/test/fake_video_track_renderer.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread.h"
#include "test/scoped_key_value_config.h"

class PeerConnectionTestWrapper
    : public webrtc::PeerConnectionObserver,
      public webrtc::CreateSessionDescriptionObserver,
      public sigslot::has_slots<> {
 public:
  static void Connect(PeerConnectionTestWrapper* caller,
                      PeerConnectionTestWrapper* callee);

  PeerConnectionTestWrapper(const std::string& name,
                            rtc::SocketServer* socket_server,
                            rtc::Thread* network_thread,
                            rtc::Thread* worker_thread);
  virtual ~PeerConnectionTestWrapper();

  bool CreatePc(
      const webrtc::PeerConnectionInterface::RTCConfiguration& config,
      rtc::scoped_refptr<webrtc::AudioEncoderFactory> audio_encoder_factory,
      rtc::scoped_refptr<webrtc::AudioDecoderFactory> audio_decoder_factory);

  rtc::scoped_refptr<webrtc::PeerConnectionFactoryInterface> pc_factory()
      const {
    return peer_connection_factory_;
  }
  webrtc::PeerConnectionInterface* pc() { return peer_connection_.get(); }

  rtc::scoped_refptr<webrtc::DataChannelInterface> CreateDataChannel(
      const std::string& label,
      const webrtc::DataChannelInit& init);

  absl::optional<webrtc::RtpCodecCapability> FindFirstSendCodecWithName(
      cricket::MediaType media_type,
      const std::string& name) const;

  void WaitForNegotiation();

  // Implements PeerConnectionObserver.
  void OnSignalingChange(
      webrtc::PeerConnectionInterface::SignalingState new_state) override;
  void OnAddTrack(
      rtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver,
      const std::vector<rtc::scoped_refptr<webrtc::MediaStreamInterface>>&
          streams) override;
  void OnDataChannel(
      rtc::scoped_refptr<webrtc::DataChannelInterface> data_channel) override;
  void OnRenegotiationNeeded() override {}
  void OnIceConnectionChange(
      webrtc::PeerConnectionInterface::IceConnectionState new_state) override {}
  void OnIceGatheringChange(
      webrtc::PeerConnectionInterface::IceGatheringState new_state) override {}
  void OnIceCandidate(const webrtc::IceCandidateInterface* candidate) override;

  // Implements CreateSessionDescriptionObserver.
  void OnSuccess(webrtc::SessionDescriptionInterface* desc) override;
  void OnFailure(webrtc::RTCError) override {}

  void CreateOffer(
      const webrtc::PeerConnectionInterface::RTCOfferAnswerOptions& options);
  void CreateAnswer(
      const webrtc::PeerConnectionInterface::RTCOfferAnswerOptions& options);
  void ReceiveOfferSdp(const std::string& sdp);
  void ReceiveAnswerSdp(const std::string& sdp);
  void AddIceCandidate(const std::string& sdp_mid,
                       int sdp_mline_index,
                       const std::string& candidate);
  void WaitForCallEstablished();
  void WaitForConnection();
  void WaitForAudio();
  void WaitForVideo();
  void GetAndAddUserMedia(bool audio,
                          const cricket::AudioOptions& audio_options,
                          bool video);

  // sigslots
  sigslot::signal3<const std::string&, int, const std::string&>
      SignalOnIceCandidateReady;
  sigslot::signal1<const std::string&> SignalOnSdpReady;
  sigslot::signal1<webrtc::DataChannelInterface*> SignalOnDataChannel;

  rtc::scoped_refptr<webrtc::MediaStreamInterface> GetUserMedia(
      bool audio,
      const cricket::AudioOptions& audio_options,
      bool video,
      webrtc::Resolution resolution = {
          .width = webrtc::FakePeriodicVideoSource::kDefaultWidth,
          .height = webrtc::FakePeriodicVideoSource::kDefaultHeight});
  void StopFakeVideoSources();

 private:
  void SetLocalDescription(webrtc::SdpType type, const std::string& sdp);
  void SetRemoteDescription(webrtc::SdpType type, const std::string& sdp);
  bool CheckForConnection();
  bool CheckForAudio();
  bool CheckForVideo();

  webrtc::test::ScopedKeyValueConfig field_trials_;
  std::string name_;
  rtc::SocketServer* const socket_server_;
  rtc::Thread* const network_thread_;
  rtc::Thread* const worker_thread_;
  webrtc::SequenceChecker pc_thread_checker_;
  rtc::scoped_refptr<webrtc::PeerConnectionInterface> peer_connection_;
  rtc::scoped_refptr<webrtc::PeerConnectionFactoryInterface>
      peer_connection_factory_;
  rtc::scoped_refptr<FakeAudioCaptureModule> fake_audio_capture_module_;
  std::unique_ptr<webrtc::FakeVideoTrackRenderer> renderer_;
  int num_get_user_media_calls_ = 0;
  bool pending_negotiation_;
  std::vector<rtc::scoped_refptr<webrtc::FakePeriodicVideoTrackSource>>
      fake_video_sources_;
};

#endif  // PC_TEST_PEER_CONNECTION_TEST_WRAPPER_H_
