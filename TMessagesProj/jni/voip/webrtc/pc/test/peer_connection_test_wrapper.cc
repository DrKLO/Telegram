/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/test/peer_connection_test_wrapper.h"

#include <stddef.h>

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/match.h"
#include "absl/types/optional.h"
#include "api/audio/audio_mixer.h"
#include "api/create_peerconnection_factory.h"
#include "api/media_types.h"
#include "api/sequence_checker.h"
#include "api/video_codecs/video_decoder_factory.h"
#include "api/video_codecs/video_decoder_factory_template.h"
#include "api/video_codecs/video_decoder_factory_template_dav1d_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_libvpx_vp8_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_libvpx_vp9_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_open_h264_adapter.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "api/video_codecs/video_encoder_factory_template.h"
#include "api/video_codecs/video_encoder_factory_template_libaom_av1_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_libvpx_vp8_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_libvpx_vp9_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_open_h264_adapter.h"
#include "media/engine/simulcast_encoder_adapter.h"
#include "modules/audio_device/include/audio_device.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "p2p/base/fake_port_allocator.h"
#include "p2p/base/port_allocator.h"
#include "pc/test/fake_periodic_video_source.h"
#include "pc/test/fake_rtc_certificate_generator.h"
#include "pc/test/mock_peer_connection_observers.h"
#include "rtc_base/gunit.h"
#include "rtc_base/logging.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/time_utils.h"
#include "test/gtest.h"

using webrtc::FakeVideoTrackRenderer;
using webrtc::IceCandidateInterface;
using webrtc::MediaStreamInterface;
using webrtc::MediaStreamTrackInterface;
using webrtc::MockSetSessionDescriptionObserver;
using webrtc::PeerConnectionInterface;
using webrtc::RtpReceiverInterface;
using webrtc::SdpType;
using webrtc::SessionDescriptionInterface;
using webrtc::VideoTrackInterface;

namespace {
const char kStreamIdBase[] = "stream_id";
const char kVideoTrackLabelBase[] = "video_track";
const char kAudioTrackLabelBase[] = "audio_track";
constexpr int kMaxWait = 10000;
constexpr int kTestAudioFrameCount = 3;
constexpr int kTestVideoFrameCount = 3;

class FuzzyMatchedVideoEncoderFactory : public webrtc::VideoEncoderFactory {
 public:
  std::vector<webrtc::SdpVideoFormat> GetSupportedFormats() const override {
    return factory_.GetSupportedFormats();
  }

  std::unique_ptr<webrtc::VideoEncoder> CreateVideoEncoder(
      const webrtc::SdpVideoFormat& format) override {
    if (absl::optional<webrtc::SdpVideoFormat> original_format =
            webrtc::FuzzyMatchSdpVideoFormat(factory_.GetSupportedFormats(),
                                             format)) {
      return std::make_unique<webrtc::SimulcastEncoderAdapter>(
          &factory_, *original_format);
    }

    return nullptr;
  }

  CodecSupport QueryCodecSupport(
      const webrtc::SdpVideoFormat& format,
      absl::optional<std::string> scalability_mode) const override {
    return factory_.QueryCodecSupport(format, scalability_mode);
  }

 private:
  webrtc::VideoEncoderFactoryTemplate<webrtc::LibvpxVp8EncoderTemplateAdapter,
                                      webrtc::LibvpxVp9EncoderTemplateAdapter,
                                      webrtc::OpenH264EncoderTemplateAdapter,
                                      webrtc::LibaomAv1EncoderTemplateAdapter>
      factory_;
};
}  // namespace

void PeerConnectionTestWrapper::Connect(PeerConnectionTestWrapper* caller,
                                        PeerConnectionTestWrapper* callee) {
  caller->SignalOnIceCandidateReady.connect(
      callee, &PeerConnectionTestWrapper::AddIceCandidate);
  callee->SignalOnIceCandidateReady.connect(
      caller, &PeerConnectionTestWrapper::AddIceCandidate);

  caller->SignalOnSdpReady.connect(callee,
                                   &PeerConnectionTestWrapper::ReceiveOfferSdp);
  callee->SignalOnSdpReady.connect(
      caller, &PeerConnectionTestWrapper::ReceiveAnswerSdp);
}

PeerConnectionTestWrapper::PeerConnectionTestWrapper(
    const std::string& name,
    rtc::SocketServer* socket_server,
    rtc::Thread* network_thread,
    rtc::Thread* worker_thread)
    : name_(name),
      socket_server_(socket_server),
      network_thread_(network_thread),
      worker_thread_(worker_thread),
      pending_negotiation_(false) {
  pc_thread_checker_.Detach();
}

PeerConnectionTestWrapper::~PeerConnectionTestWrapper() {
  RTC_DCHECK_RUN_ON(&pc_thread_checker_);
  // To avoid flaky bot failures, make sure fake sources are stopped prior to
  // closing the peer connections. See https://crbug.com/webrtc/15018.
  StopFakeVideoSources();
  // Either network_thread or worker_thread might be active at this point.
  // Relying on ~PeerConnection to properly wait for them doesn't work,
  // as a vptr race might occur (before we enter the destruction body).
  // See: bugs.webrtc.org/9847
  if (pc()) {
    pc()->Close();
  }
}

bool PeerConnectionTestWrapper::CreatePc(
    const webrtc::PeerConnectionInterface::RTCConfiguration& config,
    rtc::scoped_refptr<webrtc::AudioEncoderFactory> audio_encoder_factory,
    rtc::scoped_refptr<webrtc::AudioDecoderFactory> audio_decoder_factory) {
  std::unique_ptr<cricket::PortAllocator> port_allocator(
      new cricket::FakePortAllocator(
          network_thread_,
          std::make_unique<rtc::BasicPacketSocketFactory>(socket_server_),
          &field_trials_));

  RTC_DCHECK_RUN_ON(&pc_thread_checker_);

  fake_audio_capture_module_ = FakeAudioCaptureModule::Create();
  if (fake_audio_capture_module_ == nullptr) {
    return false;
  }

  peer_connection_factory_ = webrtc::CreatePeerConnectionFactory(
      network_thread_, worker_thread_, rtc::Thread::Current(),
      rtc::scoped_refptr<webrtc::AudioDeviceModule>(fake_audio_capture_module_),
      audio_encoder_factory, audio_decoder_factory,
      std::make_unique<FuzzyMatchedVideoEncoderFactory>(),
      std::make_unique<webrtc::VideoDecoderFactoryTemplate<
          webrtc::LibvpxVp8DecoderTemplateAdapter,
          webrtc::LibvpxVp9DecoderTemplateAdapter,
          webrtc::OpenH264DecoderTemplateAdapter,
          webrtc::Dav1dDecoderTemplateAdapter>>(),
      nullptr /* audio_mixer */, nullptr /* audio_processing */);
  if (!peer_connection_factory_) {
    return false;
  }

  std::unique_ptr<rtc::RTCCertificateGeneratorInterface> cert_generator(
      new FakeRTCCertificateGenerator());
  webrtc::PeerConnectionDependencies deps(this);
  deps.allocator = std::move(port_allocator);
  deps.cert_generator = std::move(cert_generator);
  auto result = peer_connection_factory_->CreatePeerConnectionOrError(
      config, std::move(deps));
  if (result.ok()) {
    peer_connection_ = result.MoveValue();
    return true;
  } else {
    return false;
  }
}

rtc::scoped_refptr<webrtc::DataChannelInterface>
PeerConnectionTestWrapper::CreateDataChannel(
    const std::string& label,
    const webrtc::DataChannelInit& init) {
  auto result = peer_connection_->CreateDataChannelOrError(label, &init);
  if (!result.ok()) {
    RTC_LOG(LS_ERROR) << "CreateDataChannel failed: "
                      << ToString(result.error().type()) << " "
                      << result.error().message();
    return nullptr;
  }
  return result.MoveValue();
}

absl::optional<webrtc::RtpCodecCapability>
PeerConnectionTestWrapper::FindFirstSendCodecWithName(
    cricket::MediaType media_type,
    const std::string& name) const {
  std::vector<webrtc::RtpCodecCapability> codecs =
      peer_connection_factory_->GetRtpSenderCapabilities(media_type).codecs;
  for (const auto& codec : codecs) {
    if (absl::EqualsIgnoreCase(codec.name, name)) {
      return codec;
    }
  }
  return absl::nullopt;
}

void PeerConnectionTestWrapper::WaitForNegotiation() {
  EXPECT_TRUE_WAIT(!pending_negotiation_, kMaxWait);
}

void PeerConnectionTestWrapper::OnSignalingChange(
    webrtc::PeerConnectionInterface::SignalingState new_state) {
  if (new_state == webrtc::PeerConnectionInterface::SignalingState::kStable) {
    pending_negotiation_ = false;
  }
}

void PeerConnectionTestWrapper::OnAddTrack(
    rtc::scoped_refptr<RtpReceiverInterface> receiver,
    const std::vector<rtc::scoped_refptr<MediaStreamInterface>>& streams) {
  RTC_LOG(LS_INFO) << "PeerConnectionTestWrapper " << name_ << ": OnAddTrack";
  if (receiver->track()->kind() == MediaStreamTrackInterface::kVideoKind) {
    auto* video_track =
        static_cast<VideoTrackInterface*>(receiver->track().get());
    renderer_ = std::make_unique<FakeVideoTrackRenderer>(video_track);
  }
}

void PeerConnectionTestWrapper::OnIceCandidate(
    const IceCandidateInterface* candidate) {
  std::string sdp;
  EXPECT_TRUE(candidate->ToString(&sdp));
  SignalOnIceCandidateReady(candidate->sdp_mid(), candidate->sdp_mline_index(),
                            sdp);
}

void PeerConnectionTestWrapper::OnDataChannel(
    rtc::scoped_refptr<webrtc::DataChannelInterface> data_channel) {
  SignalOnDataChannel(data_channel.get());
}

void PeerConnectionTestWrapper::OnSuccess(SessionDescriptionInterface* desc) {
  // This callback should take the ownership of `desc`.
  std::unique_ptr<SessionDescriptionInterface> owned_desc(desc);
  std::string sdp;
  EXPECT_TRUE(desc->ToString(&sdp));

  RTC_LOG(LS_INFO) << "PeerConnectionTestWrapper " << name_ << ": "
                   << webrtc::SdpTypeToString(desc->GetType())
                   << " sdp created: " << sdp;

  SetLocalDescription(desc->GetType(), sdp);

  SignalOnSdpReady(sdp);
}

void PeerConnectionTestWrapper::CreateOffer(
    const webrtc::PeerConnectionInterface::RTCOfferAnswerOptions& options) {
  RTC_LOG(LS_INFO) << "PeerConnectionTestWrapper " << name_ << ": CreateOffer.";
  pending_negotiation_ = true;
  peer_connection_->CreateOffer(this, options);
}

void PeerConnectionTestWrapper::CreateAnswer(
    const webrtc::PeerConnectionInterface::RTCOfferAnswerOptions& options) {
  RTC_LOG(LS_INFO) << "PeerConnectionTestWrapper " << name_
                   << ": CreateAnswer.";
  pending_negotiation_ = true;
  peer_connection_->CreateAnswer(this, options);
}

void PeerConnectionTestWrapper::ReceiveOfferSdp(const std::string& sdp) {
  SetRemoteDescription(SdpType::kOffer, sdp);
  CreateAnswer(webrtc::PeerConnectionInterface::RTCOfferAnswerOptions());
}

void PeerConnectionTestWrapper::ReceiveAnswerSdp(const std::string& sdp) {
  SetRemoteDescription(SdpType::kAnswer, sdp);
}

void PeerConnectionTestWrapper::SetLocalDescription(SdpType type,
                                                    const std::string& sdp) {
  RTC_LOG(LS_INFO) << "PeerConnectionTestWrapper " << name_
                   << ": SetLocalDescription " << webrtc::SdpTypeToString(type)
                   << " " << sdp;

  auto observer = rtc::make_ref_counted<MockSetSessionDescriptionObserver>();
  peer_connection_->SetLocalDescription(
      observer.get(), webrtc::CreateSessionDescription(type, sdp).release());
}

void PeerConnectionTestWrapper::SetRemoteDescription(SdpType type,
                                                     const std::string& sdp) {
  RTC_LOG(LS_INFO) << "PeerConnectionTestWrapper " << name_
                   << ": SetRemoteDescription " << webrtc::SdpTypeToString(type)
                   << " " << sdp;

  auto observer = rtc::make_ref_counted<MockSetSessionDescriptionObserver>();
  peer_connection_->SetRemoteDescription(
      observer.get(), webrtc::CreateSessionDescription(type, sdp).release());
}

void PeerConnectionTestWrapper::AddIceCandidate(const std::string& sdp_mid,
                                                int sdp_mline_index,
                                                const std::string& candidate) {
  std::unique_ptr<webrtc::IceCandidateInterface> owned_candidate(
      webrtc::CreateIceCandidate(sdp_mid, sdp_mline_index, candidate, NULL));
  EXPECT_TRUE(peer_connection_->AddIceCandidate(owned_candidate.get()));
}

void PeerConnectionTestWrapper::WaitForCallEstablished() {
  WaitForConnection();
  WaitForAudio();
  WaitForVideo();
}

void PeerConnectionTestWrapper::WaitForConnection() {
  EXPECT_TRUE_WAIT(CheckForConnection(), kMaxWait);
  RTC_LOG(LS_INFO) << "PeerConnectionTestWrapper " << name_ << ": Connected.";
}

bool PeerConnectionTestWrapper::CheckForConnection() {
  return (peer_connection_->ice_connection_state() ==
          PeerConnectionInterface::kIceConnectionConnected) ||
         (peer_connection_->ice_connection_state() ==
          PeerConnectionInterface::kIceConnectionCompleted);
}

void PeerConnectionTestWrapper::WaitForAudio() {
  EXPECT_TRUE_WAIT(CheckForAudio(), kMaxWait);
  RTC_LOG(LS_INFO) << "PeerConnectionTestWrapper " << name_
                   << ": Got enough audio frames.";
}

bool PeerConnectionTestWrapper::CheckForAudio() {
  return (fake_audio_capture_module_->frames_received() >=
          kTestAudioFrameCount);
}

void PeerConnectionTestWrapper::WaitForVideo() {
  EXPECT_TRUE_WAIT(CheckForVideo(), kMaxWait);
  RTC_LOG(LS_INFO) << "PeerConnectionTestWrapper " << name_
                   << ": Got enough video frames.";
}

bool PeerConnectionTestWrapper::CheckForVideo() {
  if (!renderer_) {
    return false;
  }
  return (renderer_->num_rendered_frames() >= kTestVideoFrameCount);
}

void PeerConnectionTestWrapper::GetAndAddUserMedia(
    bool audio,
    const cricket::AudioOptions& audio_options,
    bool video) {
  rtc::scoped_refptr<webrtc::MediaStreamInterface> stream =
      GetUserMedia(audio, audio_options, video);
  for (const auto& audio_track : stream->GetAudioTracks()) {
    EXPECT_TRUE(peer_connection_->AddTrack(audio_track, {stream->id()}).ok());
  }
  for (const auto& video_track : stream->GetVideoTracks()) {
    EXPECT_TRUE(peer_connection_->AddTrack(video_track, {stream->id()}).ok());
  }
}

rtc::scoped_refptr<webrtc::MediaStreamInterface>
PeerConnectionTestWrapper::GetUserMedia(
    bool audio,
    const cricket::AudioOptions& audio_options,
    bool video,
    webrtc::Resolution resolution) {
  std::string stream_id =
      kStreamIdBase + rtc::ToString(num_get_user_media_calls_++);
  rtc::scoped_refptr<webrtc::MediaStreamInterface> stream =
      peer_connection_factory_->CreateLocalMediaStream(stream_id);

  if (audio) {
    cricket::AudioOptions options = audio_options;
    // Disable highpass filter so that we can get all the test audio frames.
    options.highpass_filter = false;
    rtc::scoped_refptr<webrtc::AudioSourceInterface> source =
        peer_connection_factory_->CreateAudioSource(options);
    rtc::scoped_refptr<webrtc::AudioTrackInterface> audio_track(
        peer_connection_factory_->CreateAudioTrack(kAudioTrackLabelBase,
                                                   source.get()));
    stream->AddTrack(audio_track);
  }

  if (video) {
    // Set max frame rate to 10fps to reduce the risk of the tests to be flaky.
    webrtc::FakePeriodicVideoSource::Config config;
    config.frame_interval_ms = 100;
    config.timestamp_offset_ms = rtc::TimeMillis();
    config.width = resolution.width;
    config.height = resolution.height;

    auto source = rtc::make_ref_counted<webrtc::FakePeriodicVideoTrackSource>(
        config, /* remote */ false);
    fake_video_sources_.push_back(source);

    std::string videotrack_label = stream_id + kVideoTrackLabelBase;
    rtc::scoped_refptr<webrtc::VideoTrackInterface> video_track(
        peer_connection_factory_->CreateVideoTrack(source, videotrack_label));

    stream->AddTrack(video_track);
  }
  return stream;
}

void PeerConnectionTestWrapper::StopFakeVideoSources() {
  for (const auto& fake_video_source : fake_video_sources_) {
    fake_video_source->fake_periodic_source().Stop();
  }
  fake_video_sources_.clear();
}
