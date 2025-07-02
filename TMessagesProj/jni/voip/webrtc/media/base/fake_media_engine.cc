/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/fake_media_engine.h"

#include <memory>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/strings/match.h"
#include "absl/types/optional.h"
#include "media/base/media_channel.h"
#include "rtc_base/checks.h"

namespace cricket {
using webrtc::TaskQueueBase;

FakeVoiceMediaReceiveChannel::DtmfInfo::DtmfInfo(uint32_t ssrc,
                                                 int event_code,
                                                 int duration)
    : ssrc(ssrc), event_code(event_code), duration(duration) {}

FakeVoiceMediaReceiveChannel::VoiceChannelAudioSink::VoiceChannelAudioSink(
    AudioSource* source)
    : source_(source) {
  source_->SetSink(this);
}
FakeVoiceMediaReceiveChannel::VoiceChannelAudioSink::~VoiceChannelAudioSink() {
  if (source_) {
    source_->SetSink(nullptr);
  }
}
void FakeVoiceMediaReceiveChannel::VoiceChannelAudioSink::OnData(
    const void* audio_data,
    int bits_per_sample,
    int sample_rate,
    size_t number_of_channels,
    size_t number_of_frames,
    absl::optional<int64_t> absolute_capture_timestamp_ms) {}
void FakeVoiceMediaReceiveChannel::VoiceChannelAudioSink::OnClose() {
  source_ = nullptr;
}
AudioSource* FakeVoiceMediaReceiveChannel::VoiceChannelAudioSink::source()
    const {
  return source_;
}

FakeVoiceMediaReceiveChannel::FakeVoiceMediaReceiveChannel(
    const AudioOptions& options,
    TaskQueueBase* network_thread)
    : RtpReceiveChannelHelper<VoiceMediaReceiveChannelInterface>(
          network_thread),
      max_bps_(-1) {
  output_scalings_[0] = 1.0;  // For default channel.
  SetOptions(options);
}
FakeVoiceMediaReceiveChannel::~FakeVoiceMediaReceiveChannel() = default;
const std::vector<AudioCodec>& FakeVoiceMediaReceiveChannel::recv_codecs()
    const {
  return recv_codecs_;
}
const std::vector<FakeVoiceMediaReceiveChannel::DtmfInfo>&
FakeVoiceMediaReceiveChannel::dtmf_info_queue() const {
  return dtmf_info_queue_;
}
const AudioOptions& FakeVoiceMediaReceiveChannel::options() const {
  return options_;
}
int FakeVoiceMediaReceiveChannel::max_bps() const {
  return max_bps_;
}
bool FakeVoiceMediaReceiveChannel::SetReceiverParameters(
    const AudioReceiverParameters& params) {
  set_recv_rtcp_parameters(params.rtcp);
  return (SetRecvCodecs(params.codecs) &&
          SetRecvRtpHeaderExtensions(params.extensions));
}
void FakeVoiceMediaReceiveChannel::SetPlayout(bool playout) {
  set_playout(playout);
}
bool FakeVoiceMediaReceiveChannel::HasSource(uint32_t ssrc) const {
  return local_sinks_.find(ssrc) != local_sinks_.end();
}
bool FakeVoiceMediaReceiveChannel::AddRecvStream(const StreamParams& sp) {
  if (!RtpReceiveChannelHelper<
          VoiceMediaReceiveChannelInterface>::AddRecvStream(sp))
    return false;
  output_scalings_[sp.first_ssrc()] = 1.0;
  output_delays_[sp.first_ssrc()] = 0;
  return true;
}
bool FakeVoiceMediaReceiveChannel::RemoveRecvStream(uint32_t ssrc) {
  if (!RtpReceiveChannelHelper<
          VoiceMediaReceiveChannelInterface>::RemoveRecvStream(ssrc))
    return false;
  output_scalings_.erase(ssrc);
  output_delays_.erase(ssrc);
  return true;
}
bool FakeVoiceMediaReceiveChannel::SetOutputVolume(uint32_t ssrc,
                                                   double volume) {
  if (output_scalings_.find(ssrc) != output_scalings_.end()) {
    output_scalings_[ssrc] = volume;
    return true;
  }
  return false;
}
bool FakeVoiceMediaReceiveChannel::SetDefaultOutputVolume(double volume) {
  for (auto& entry : output_scalings_) {
    entry.second = volume;
  }
  return true;
}
bool FakeVoiceMediaReceiveChannel::GetOutputVolume(uint32_t ssrc,
                                                   double* volume) {
  if (output_scalings_.find(ssrc) == output_scalings_.end())
    return false;
  *volume = output_scalings_[ssrc];
  return true;
}
bool FakeVoiceMediaReceiveChannel::SetBaseMinimumPlayoutDelayMs(uint32_t ssrc,
                                                                int delay_ms) {
  if (output_delays_.find(ssrc) == output_delays_.end()) {
    return false;
  } else {
    output_delays_[ssrc] = delay_ms;
    return true;
  }
}
absl::optional<int> FakeVoiceMediaReceiveChannel::GetBaseMinimumPlayoutDelayMs(
    uint32_t ssrc) const {
  const auto it = output_delays_.find(ssrc);
  if (it != output_delays_.end()) {
    return it->second;
  }
  return absl::nullopt;
}
bool FakeVoiceMediaReceiveChannel::GetStats(VoiceMediaReceiveInfo* info,
                                            bool get_and_clear_legacy_stats) {
  return false;
}
void FakeVoiceMediaReceiveChannel::SetRawAudioSink(
    uint32_t ssrc,
    std::unique_ptr<webrtc::AudioSinkInterface> sink) {
  sink_ = std::move(sink);
}
void FakeVoiceMediaReceiveChannel::SetDefaultRawAudioSink(
    std::unique_ptr<webrtc::AudioSinkInterface> sink) {
  sink_ = std::move(sink);
}
std::vector<webrtc::RtpSource> FakeVoiceMediaReceiveChannel::GetSources(
    uint32_t ssrc) const {
  return std::vector<webrtc::RtpSource>();
}
bool FakeVoiceMediaReceiveChannel::SetRecvCodecs(
    const std::vector<AudioCodec>& codecs) {
  if (fail_set_recv_codecs()) {
    // Fake the failure in SetRecvCodecs.
    return false;
  }
  recv_codecs_ = codecs;
  return true;
}
bool FakeVoiceMediaReceiveChannel::SetMaxSendBandwidth(int bps) {
  max_bps_ = bps;
  return true;
}
bool FakeVoiceMediaReceiveChannel::SetOptions(const AudioOptions& options) {
  // Does a "merge" of current options and set options.
  options_.SetAll(options);
  return true;
}

FakeVoiceMediaSendChannel::DtmfInfo::DtmfInfo(uint32_t ssrc,
                                              int event_code,
                                              int duration)
    : ssrc(ssrc), event_code(event_code), duration(duration) {}

FakeVoiceMediaSendChannel::VoiceChannelAudioSink::VoiceChannelAudioSink(
    AudioSource* source)
    : source_(source) {
  source_->SetSink(this);
}
FakeVoiceMediaSendChannel::VoiceChannelAudioSink::~VoiceChannelAudioSink() {
  if (source_) {
    source_->SetSink(nullptr);
  }
}
void FakeVoiceMediaSendChannel::VoiceChannelAudioSink::OnData(
    const void* audio_data,
    int bits_per_sample,
    int sample_rate,
    size_t number_of_channels,
    size_t number_of_frames,
    absl::optional<int64_t> absolute_capture_timestamp_ms) {}
void FakeVoiceMediaSendChannel::VoiceChannelAudioSink::OnClose() {
  source_ = nullptr;
}
AudioSource* FakeVoiceMediaSendChannel::VoiceChannelAudioSink::source() const {
  return source_;
}

FakeVoiceMediaSendChannel::FakeVoiceMediaSendChannel(
    const AudioOptions& options,
    TaskQueueBase* network_thread)
    : RtpSendChannelHelper<VoiceMediaSendChannelInterface>(network_thread),
      max_bps_(-1) {
  output_scalings_[0] = 1.0;  // For default channel.
  SetOptions(options);
}
FakeVoiceMediaSendChannel::~FakeVoiceMediaSendChannel() = default;
const std::vector<AudioCodec>& FakeVoiceMediaSendChannel::send_codecs() const {
  return send_codecs_;
}
absl::optional<Codec> FakeVoiceMediaSendChannel::GetSendCodec() const {
  if (!send_codecs_.empty()) {
    return send_codecs_.front();
  }
  return absl::nullopt;
}
const std::vector<FakeVoiceMediaSendChannel::DtmfInfo>&
FakeVoiceMediaSendChannel::dtmf_info_queue() const {
  return dtmf_info_queue_;
}
const AudioOptions& FakeVoiceMediaSendChannel::options() const {
  return options_;
}
int FakeVoiceMediaSendChannel::max_bps() const {
  return max_bps_;
}
bool FakeVoiceMediaSendChannel::SetSenderParameters(
    const AudioSenderParameter& params) {
  set_send_rtcp_parameters(params.rtcp);
  SetExtmapAllowMixed(params.extmap_allow_mixed);
  return (SetSendCodecs(params.codecs) &&
          SetSendRtpHeaderExtensions(params.extensions) &&
          SetMaxSendBandwidth(params.max_bandwidth_bps) &&
          SetOptions(params.options));
}
void FakeVoiceMediaSendChannel::SetSend(bool send) {
  set_sending(send);
}
bool FakeVoiceMediaSendChannel::SetAudioSend(uint32_t ssrc,
                                             bool enable,
                                             const AudioOptions* options,
                                             AudioSource* source) {
  if (!SetLocalSource(ssrc, source)) {
    return false;
  }
  if (!RtpSendChannelHelper<VoiceMediaSendChannelInterface>::MuteStream(
          ssrc, !enable)) {
    return false;
  }
  if (enable && options) {
    return SetOptions(*options);
  }
  return true;
}
bool FakeVoiceMediaSendChannel::HasSource(uint32_t ssrc) const {
  return local_sinks_.find(ssrc) != local_sinks_.end();
}
bool FakeVoiceMediaSendChannel::CanInsertDtmf() {
  for (std::vector<AudioCodec>::const_iterator it = send_codecs_.begin();
       it != send_codecs_.end(); ++it) {
    // Find the DTMF telephone event "codec".
    if (absl::EqualsIgnoreCase(it->name, "telephone-event")) {
      return true;
    }
  }
  return false;
}
bool FakeVoiceMediaSendChannel::InsertDtmf(uint32_t ssrc,
                                           int event_code,
                                           int duration) {
  dtmf_info_queue_.push_back(DtmfInfo(ssrc, event_code, duration));
  return true;
}
bool FakeVoiceMediaSendChannel::GetOutputVolume(uint32_t ssrc, double* volume) {
  if (output_scalings_.find(ssrc) == output_scalings_.end())
    return false;
  *volume = output_scalings_[ssrc];
  return true;
}
bool FakeVoiceMediaSendChannel::GetStats(VoiceMediaSendInfo* info) {
  return false;
}
bool FakeVoiceMediaSendChannel::SetSendCodecs(
    const std::vector<AudioCodec>& codecs) {
  if (fail_set_send_codecs()) {
    // Fake the failure in SetSendCodecs.
    return false;
  }
  send_codecs_ = codecs;
  return true;
}
bool FakeVoiceMediaSendChannel::SetMaxSendBandwidth(int bps) {
  max_bps_ = bps;
  return true;
}
bool FakeVoiceMediaSendChannel::SetOptions(const AudioOptions& options) {
  // Does a "merge" of current options and set options.
  options_.SetAll(options);
  return true;
}
bool FakeVoiceMediaSendChannel::SetLocalSource(uint32_t ssrc,
                                               AudioSource* source) {
  auto it = local_sinks_.find(ssrc);
  if (source) {
    if (it != local_sinks_.end()) {
      RTC_CHECK(it->second->source() == source);
    } else {
      local_sinks_.insert(std::make_pair(
          ssrc, std::make_unique<VoiceChannelAudioSink>(source)));
    }
  } else {
    if (it != local_sinks_.end()) {
      local_sinks_.erase(it);
    }
  }
  return true;
}

bool CompareDtmfInfo(const FakeVoiceMediaSendChannel::DtmfInfo& info,
                     uint32_t ssrc,
                     int event_code,
                     int duration) {
  return (info.duration == duration && info.event_code == event_code &&
          info.ssrc == ssrc);
}

FakeVideoMediaSendChannel::FakeVideoMediaSendChannel(
    const VideoOptions& options,
    TaskQueueBase* network_thread)
    : RtpSendChannelHelper<VideoMediaSendChannelInterface>(network_thread),
      max_bps_(-1) {
  SetOptions(options);
}
FakeVideoMediaSendChannel::~FakeVideoMediaSendChannel() = default;
const std::vector<VideoCodec>& FakeVideoMediaSendChannel::send_codecs() const {
  return send_codecs_;
}
const std::vector<VideoCodec>& FakeVideoMediaSendChannel::codecs() const {
  return send_codecs();
}
const VideoOptions& FakeVideoMediaSendChannel::options() const {
  return options_;
}
int FakeVideoMediaSendChannel::max_bps() const {
  return max_bps_;
}
bool FakeVideoMediaSendChannel::SetSenderParameters(
    const VideoSenderParameters& params) {
  set_send_rtcp_parameters(params.rtcp);
  SetExtmapAllowMixed(params.extmap_allow_mixed);
  return (SetSendCodecs(params.codecs) &&
          SetSendRtpHeaderExtensions(params.extensions) &&
          SetMaxSendBandwidth(params.max_bandwidth_bps));
}
absl::optional<Codec> FakeVideoMediaSendChannel::GetSendCodec() const {
  if (send_codecs_.empty()) {
    return absl::nullopt;
  }
  return send_codecs_[0];
}
bool FakeVideoMediaSendChannel::SetSend(bool send) {
  return set_sending(send);
}
bool FakeVideoMediaSendChannel::SetVideoSend(
    uint32_t ssrc,
    const VideoOptions* options,
    rtc::VideoSourceInterface<webrtc::VideoFrame>* source) {
  if (options) {
    if (!SetOptions(*options)) {
      return false;
    }
  }
  sources_[ssrc] = source;
  return true;
}
bool FakeVideoMediaSendChannel::HasSource(uint32_t ssrc) const {
  return sources_.find(ssrc) != sources_.end() && sources_.at(ssrc) != nullptr;
}
void FakeVideoMediaSendChannel::FillBitrateInfo(
    BandwidthEstimationInfo* bwe_info) {}
bool FakeVideoMediaSendChannel::GetStats(VideoMediaSendInfo* info) {
  return false;
}
bool FakeVideoMediaSendChannel::SetSendCodecs(
    const std::vector<VideoCodec>& codecs) {
  if (fail_set_send_codecs()) {
    // Fake the failure in SetSendCodecs.
    return false;
  }
  send_codecs_ = codecs;

  return true;
}
bool FakeVideoMediaSendChannel::SetOptions(const VideoOptions& options) {
  options_ = options;
  return true;
}

bool FakeVideoMediaSendChannel::SetMaxSendBandwidth(int bps) {
  max_bps_ = bps;
  return true;
}
void FakeVideoMediaSendChannel::GenerateSendKeyFrame(
    uint32_t ssrc,
    const std::vector<std::string>& rids) {}

FakeVideoMediaReceiveChannel::FakeVideoMediaReceiveChannel(
    const VideoOptions& options,
    TaskQueueBase* network_thread)
    : RtpReceiveChannelHelper<VideoMediaReceiveChannelInterface>(
          network_thread),
      max_bps_(-1) {
  SetOptions(options);
}
FakeVideoMediaReceiveChannel::~FakeVideoMediaReceiveChannel() = default;
const std::vector<VideoCodec>& FakeVideoMediaReceiveChannel::recv_codecs()
    const {
  return recv_codecs_;
}
bool FakeVideoMediaReceiveChannel::rendering() const {
  return playout();
}
const VideoOptions& FakeVideoMediaReceiveChannel::options() const {
  return options_;
}
const std::map<uint32_t, rtc::VideoSinkInterface<webrtc::VideoFrame>*>&
FakeVideoMediaReceiveChannel::sinks() const {
  return sinks_;
}
int FakeVideoMediaReceiveChannel::max_bps() const {
  return max_bps_;
}
bool FakeVideoMediaReceiveChannel::SetReceiverParameters(
    const VideoReceiverParameters& params) {
  set_recv_rtcp_parameters(params.rtcp);
  return (SetRecvCodecs(params.codecs) &&
          SetRecvRtpHeaderExtensions(params.extensions));
}
bool FakeVideoMediaReceiveChannel::SetSink(
    uint32_t ssrc,
    rtc::VideoSinkInterface<webrtc::VideoFrame>* sink) {
  auto it = sinks_.find(ssrc);
  if (it == sinks_.end()) {
    return false;
  }
  it->second = sink;
  return true;
}
void FakeVideoMediaReceiveChannel::SetDefaultSink(
    rtc::VideoSinkInterface<webrtc::VideoFrame>* sink) {}
bool FakeVideoMediaReceiveChannel::HasSink(uint32_t ssrc) const {
  return sinks_.find(ssrc) != sinks_.end() && sinks_.at(ssrc) != nullptr;
}
bool FakeVideoMediaReceiveChannel::HasSource(uint32_t ssrc) const {
  return sources_.find(ssrc) != sources_.end() && sources_.at(ssrc) != nullptr;
}
bool FakeVideoMediaReceiveChannel::AddRecvStream(const StreamParams& sp) {
  if (!RtpReceiveChannelHelper<
          VideoMediaReceiveChannelInterface>::AddRecvStream(sp))
    return false;
  sinks_[sp.first_ssrc()] = NULL;
  output_delays_[sp.first_ssrc()] = 0;
  return true;
}
bool FakeVideoMediaReceiveChannel::RemoveRecvStream(uint32_t ssrc) {
  if (!RtpReceiveChannelHelper<
          VideoMediaReceiveChannelInterface>::RemoveRecvStream(ssrc))
    return false;
  sinks_.erase(ssrc);
  output_delays_.erase(ssrc);
  return true;
}
std::vector<webrtc::RtpSource> FakeVideoMediaReceiveChannel::GetSources(
    uint32_t ssrc) const {
  return {};
}
bool FakeVideoMediaReceiveChannel::SetBaseMinimumPlayoutDelayMs(uint32_t ssrc,
                                                                int delay_ms) {
  if (output_delays_.find(ssrc) == output_delays_.end()) {
    return false;
  } else {
    output_delays_[ssrc] = delay_ms;
    return true;
  }
}
absl::optional<int> FakeVideoMediaReceiveChannel::GetBaseMinimumPlayoutDelayMs(
    uint32_t ssrc) const {
  const auto it = output_delays_.find(ssrc);
  if (it != output_delays_.end()) {
    return it->second;
  }
  return absl::nullopt;
}
bool FakeVideoMediaReceiveChannel::SetRecvCodecs(
    const std::vector<VideoCodec>& codecs) {
  if (fail_set_recv_codecs()) {
    // Fake the failure in SetRecvCodecs.
    return false;
  }
  recv_codecs_ = codecs;
  return true;
}
bool FakeVideoMediaReceiveChannel::SetOptions(const VideoOptions& options) {
  options_ = options;
  return true;
}

bool FakeVideoMediaReceiveChannel::SetMaxSendBandwidth(int bps) {
  max_bps_ = bps;
  return true;
}

void FakeVideoMediaReceiveChannel::SetRecordableEncodedFrameCallback(
    uint32_t ssrc,
    std::function<void(const webrtc::RecordableEncodedFrame&)> callback) {}

void FakeVideoMediaReceiveChannel::ClearRecordableEncodedFrameCallback(
    uint32_t ssrc) {}

void FakeVideoMediaReceiveChannel::RequestRecvKeyFrame(uint32_t ssrc) {}

bool FakeVideoMediaReceiveChannel::GetStats(VideoMediaReceiveInfo* info) {
  return false;
}

FakeVoiceEngine::FakeVoiceEngine() : fail_create_channel_(false) {
  // Add a fake audio codec. Note that the name must not be "" as there are
  // sanity checks against that.
  SetCodecs({cricket::CreateAudioCodec(101, "fake_audio_codec", 8000, 1)});
}
void FakeVoiceEngine::Init() {}
rtc::scoped_refptr<webrtc::AudioState> FakeVoiceEngine::GetAudioState() const {
  return rtc::scoped_refptr<webrtc::AudioState>();
}
std::unique_ptr<VoiceMediaSendChannelInterface>
FakeVoiceEngine::CreateSendChannel(webrtc::Call* call,
                                   const MediaConfig& config,
                                   const AudioOptions& options,
                                   const webrtc::CryptoOptions& crypto_options,
                                   webrtc::AudioCodecPairId codec_pair_id) {
  std::unique_ptr<FakeVoiceMediaSendChannel> ch =
      std::make_unique<FakeVoiceMediaSendChannel>(options,
                                                  call->network_thread());
  return ch;
}
std::unique_ptr<VoiceMediaReceiveChannelInterface>
FakeVoiceEngine::CreateReceiveChannel(
    webrtc::Call* call,
    const MediaConfig& config,
    const AudioOptions& options,
    const webrtc::CryptoOptions& crypto_options,
    webrtc::AudioCodecPairId codec_pair_id) {
  std::unique_ptr<FakeVoiceMediaReceiveChannel> ch =
      std::make_unique<FakeVoiceMediaReceiveChannel>(options,
                                                     call->network_thread());
  return ch;
}
const std::vector<AudioCodec>& FakeVoiceEngine::send_codecs() const {
  return send_codecs_;
}
const std::vector<AudioCodec>& FakeVoiceEngine::recv_codecs() const {
  return recv_codecs_;
}
void FakeVoiceEngine::SetCodecs(const std::vector<AudioCodec>& codecs) {
  send_codecs_ = codecs;
  recv_codecs_ = codecs;
}
void FakeVoiceEngine::SetRecvCodecs(const std::vector<AudioCodec>& codecs) {
  recv_codecs_ = codecs;
}
void FakeVoiceEngine::SetSendCodecs(const std::vector<AudioCodec>& codecs) {
  send_codecs_ = codecs;
}
int FakeVoiceEngine::GetInputLevel() {
  return 0;
}
bool FakeVoiceEngine::StartAecDump(webrtc::FileWrapper file,
                                   int64_t max_size_bytes) {
  return false;
}
absl::optional<webrtc::AudioDeviceModule::Stats>
FakeVoiceEngine::GetAudioDeviceStats() {
  return absl::nullopt;
}
void FakeVoiceEngine::StopAecDump() {}

std::vector<webrtc::RtpHeaderExtensionCapability>
FakeVoiceEngine::GetRtpHeaderExtensions() const {
  return header_extensions_;
}

void FakeVoiceEngine::SetRtpHeaderExtensions(
    std::vector<webrtc::RtpHeaderExtensionCapability> header_extensions) {
  header_extensions_ = std::move(header_extensions);
}

FakeVideoEngine::FakeVideoEngine()
    : capture_(false), fail_create_channel_(false) {
  // Add a fake video codec. Note that the name must not be "" as there are
  // sanity checks against that.
  send_codecs_.push_back(cricket::CreateVideoCodec(111, "fake_video_codec"));
  recv_codecs_.push_back(cricket::CreateVideoCodec(111, "fake_video_codec"));
}
bool FakeVideoEngine::SetOptions(const VideoOptions& options) {
  options_ = options;
  return true;
}
std::unique_ptr<VideoMediaSendChannelInterface>
FakeVideoEngine::CreateSendChannel(
    webrtc::Call* call,
    const MediaConfig& config,
    const VideoOptions& options,
    const webrtc::CryptoOptions& crypto_options,
    webrtc::VideoBitrateAllocatorFactory* video_bitrate_allocator_factory) {
  if (fail_create_channel_) {
    return nullptr;
  }

  std::unique_ptr<FakeVideoMediaSendChannel> ch =
      std::make_unique<FakeVideoMediaSendChannel>(options,
                                                  call->network_thread());
  return ch;
}
std::unique_ptr<VideoMediaReceiveChannelInterface>
FakeVideoEngine::CreateReceiveChannel(
    webrtc::Call* call,
    const MediaConfig& config,
    const VideoOptions& options,
    const webrtc::CryptoOptions& crypto_options) {
  if (fail_create_channel_) {
    return nullptr;
  }

  std::unique_ptr<FakeVideoMediaReceiveChannel> ch =
      std::make_unique<FakeVideoMediaReceiveChannel>(options,
                                                     call->network_thread());
  return ch;
}
std::vector<VideoCodec> FakeVideoEngine::send_codecs(bool use_rtx) const {
  return send_codecs_;
}

std::vector<VideoCodec> FakeVideoEngine::recv_codecs(bool use_rtx) const {
  return recv_codecs_;
}

void FakeVideoEngine::SetSendCodecs(const std::vector<VideoCodec>& codecs) {
  send_codecs_ = codecs;
}

void FakeVideoEngine::SetRecvCodecs(const std::vector<VideoCodec>& codecs) {
  recv_codecs_ = codecs;
}

bool FakeVideoEngine::SetCapture(bool capture) {
  capture_ = capture;
  return true;
}
std::vector<webrtc::RtpHeaderExtensionCapability>
FakeVideoEngine::GetRtpHeaderExtensions() const {
  return header_extensions_;
}
void FakeVideoEngine::SetRtpHeaderExtensions(
    std::vector<webrtc::RtpHeaderExtensionCapability> header_extensions) {
  header_extensions_ = std::move(header_extensions);
}

FakeMediaEngine::FakeMediaEngine()
    : CompositeMediaEngine(std::make_unique<FakeVoiceEngine>(),
                           std::make_unique<FakeVideoEngine>()),
      voice_(static_cast<FakeVoiceEngine*>(&voice())),
      video_(static_cast<FakeVideoEngine*>(&video())) {}
FakeMediaEngine::~FakeMediaEngine() {}
void FakeMediaEngine::SetAudioCodecs(const std::vector<AudioCodec>& codecs) {
  voice_->SetCodecs(codecs);
}
void FakeMediaEngine::SetAudioRecvCodecs(
    const std::vector<AudioCodec>& codecs) {
  voice_->SetRecvCodecs(codecs);
}
void FakeMediaEngine::SetAudioSendCodecs(
    const std::vector<AudioCodec>& codecs) {
  voice_->SetSendCodecs(codecs);
}
void FakeMediaEngine::SetVideoCodecs(const std::vector<VideoCodec>& codecs) {
  video_->SetSendCodecs(codecs);
  video_->SetRecvCodecs(codecs);
}
void FakeMediaEngine::set_fail_create_channel(bool fail) {
  voice_->fail_create_channel_ = fail;
  video_->fail_create_channel_ = fail;
}

}  // namespace cricket
