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
#include "rtc_base/checks.h"

namespace cricket {

FakeVoiceMediaChannel::DtmfInfo::DtmfInfo(uint32_t ssrc,
                                          int event_code,
                                          int duration)
    : ssrc(ssrc), event_code(event_code), duration(duration) {}

FakeVoiceMediaChannel::VoiceChannelAudioSink::VoiceChannelAudioSink(
    AudioSource* source)
    : source_(source) {
  source_->SetSink(this);
}
FakeVoiceMediaChannel::VoiceChannelAudioSink::~VoiceChannelAudioSink() {
  if (source_) {
    source_->SetSink(nullptr);
  }
}
void FakeVoiceMediaChannel::VoiceChannelAudioSink::OnData(
    const void* audio_data,
    int bits_per_sample,
    int sample_rate,
    size_t number_of_channels,
    size_t number_of_frames,
    absl::optional<int64_t> absolute_capture_timestamp_ms) {}
void FakeVoiceMediaChannel::VoiceChannelAudioSink::OnClose() {
  source_ = nullptr;
}
AudioSource* FakeVoiceMediaChannel::VoiceChannelAudioSink::source() const {
  return source_;
}

FakeVoiceMediaChannel::FakeVoiceMediaChannel(FakeVoiceEngine* engine,
                                             const AudioOptions& options)
    : engine_(engine), max_bps_(-1) {
  output_scalings_[0] = 1.0;  // For default channel.
  SetOptions(options);
}
FakeVoiceMediaChannel::~FakeVoiceMediaChannel() {
  if (engine_) {
    engine_->UnregisterChannel(this);
  }
}
const std::vector<AudioCodec>& FakeVoiceMediaChannel::recv_codecs() const {
  return recv_codecs_;
}
const std::vector<AudioCodec>& FakeVoiceMediaChannel::send_codecs() const {
  return send_codecs_;
}
const std::vector<AudioCodec>& FakeVoiceMediaChannel::codecs() const {
  return send_codecs();
}
const std::vector<FakeVoiceMediaChannel::DtmfInfo>&
FakeVoiceMediaChannel::dtmf_info_queue() const {
  return dtmf_info_queue_;
}
const AudioOptions& FakeVoiceMediaChannel::options() const {
  return options_;
}
int FakeVoiceMediaChannel::max_bps() const {
  return max_bps_;
}
bool FakeVoiceMediaChannel::SetSendParameters(
    const AudioSendParameters& params) {
  set_send_rtcp_parameters(params.rtcp);
  return (SetSendCodecs(params.codecs) &&
          SetSendExtmapAllowMixed(params.extmap_allow_mixed) &&
          SetSendRtpHeaderExtensions(params.extensions) &&
          SetMaxSendBandwidth(params.max_bandwidth_bps) &&
          SetOptions(params.options));
}
bool FakeVoiceMediaChannel::SetRecvParameters(
    const AudioRecvParameters& params) {
  set_recv_rtcp_parameters(params.rtcp);
  return (SetRecvCodecs(params.codecs) &&
          SetRecvRtpHeaderExtensions(params.extensions));
}
void FakeVoiceMediaChannel::SetPlayout(bool playout) {
  set_playout(playout);
}
void FakeVoiceMediaChannel::SetSend(bool send) {
  set_sending(send);
}
bool FakeVoiceMediaChannel::SetAudioSend(uint32_t ssrc,
                                         bool enable,
                                         const AudioOptions* options,
                                         AudioSource* source) {
  if (!SetLocalSource(ssrc, source)) {
    return false;
  }
  if (!RtpHelper<VoiceMediaChannel>::MuteStream(ssrc, !enable)) {
    return false;
  }
  if (enable && options) {
    return SetOptions(*options);
  }
  return true;
}
bool FakeVoiceMediaChannel::HasSource(uint32_t ssrc) const {
  return local_sinks_.find(ssrc) != local_sinks_.end();
}
bool FakeVoiceMediaChannel::AddRecvStream(const StreamParams& sp) {
  if (!RtpHelper<VoiceMediaChannel>::AddRecvStream(sp))
    return false;
  output_scalings_[sp.first_ssrc()] = 1.0;
  output_delays_[sp.first_ssrc()] = 0;
  return true;
}
bool FakeVoiceMediaChannel::RemoveRecvStream(uint32_t ssrc) {
  if (!RtpHelper<VoiceMediaChannel>::RemoveRecvStream(ssrc))
    return false;
  output_scalings_.erase(ssrc);
  output_delays_.erase(ssrc);
  return true;
}
bool FakeVoiceMediaChannel::CanInsertDtmf() {
  for (std::vector<AudioCodec>::const_iterator it = send_codecs_.begin();
       it != send_codecs_.end(); ++it) {
    // Find the DTMF telephone event "codec".
    if (absl::EqualsIgnoreCase(it->name, "telephone-event")) {
      return true;
    }
  }
  return false;
}
bool FakeVoiceMediaChannel::InsertDtmf(uint32_t ssrc,
                                       int event_code,
                                       int duration) {
  dtmf_info_queue_.push_back(DtmfInfo(ssrc, event_code, duration));
  return true;
}
bool FakeVoiceMediaChannel::SetOutputVolume(uint32_t ssrc, double volume) {
  if (output_scalings_.find(ssrc) != output_scalings_.end()) {
    output_scalings_[ssrc] = volume;
    return true;
  }
  return false;
}
bool FakeVoiceMediaChannel::SetDefaultOutputVolume(double volume) {
  for (auto& entry : output_scalings_) {
    entry.second = volume;
  }
  return true;
}
bool FakeVoiceMediaChannel::GetOutputVolume(uint32_t ssrc, double* volume) {
  if (output_scalings_.find(ssrc) == output_scalings_.end())
    return false;
  *volume = output_scalings_[ssrc];
  return true;
}
bool FakeVoiceMediaChannel::SetBaseMinimumPlayoutDelayMs(uint32_t ssrc,
                                                         int delay_ms) {
  if (output_delays_.find(ssrc) == output_delays_.end()) {
    return false;
  } else {
    output_delays_[ssrc] = delay_ms;
    return true;
  }
}
absl::optional<int> FakeVoiceMediaChannel::GetBaseMinimumPlayoutDelayMs(
    uint32_t ssrc) const {
  const auto it = output_delays_.find(ssrc);
  if (it != output_delays_.end()) {
    return it->second;
  }
  return absl::nullopt;
}
bool FakeVoiceMediaChannel::GetStats(VoiceMediaInfo* info,
                                     bool get_and_clear_legacy_stats) {
  return false;
}
void FakeVoiceMediaChannel::SetRawAudioSink(
    uint32_t ssrc,
    std::unique_ptr<webrtc::AudioSinkInterface> sink) {
  sink_ = std::move(sink);
}
void FakeVoiceMediaChannel::SetDefaultRawAudioSink(
    std::unique_ptr<webrtc::AudioSinkInterface> sink) {
  sink_ = std::move(sink);
}
std::vector<webrtc::RtpSource> FakeVoiceMediaChannel::GetSources(
    uint32_t ssrc) const {
  return std::vector<webrtc::RtpSource>();
}
bool FakeVoiceMediaChannel::SetRecvCodecs(
    const std::vector<AudioCodec>& codecs) {
  if (fail_set_recv_codecs()) {
    // Fake the failure in SetRecvCodecs.
    return false;
  }
  recv_codecs_ = codecs;
  return true;
}
bool FakeVoiceMediaChannel::SetSendCodecs(
    const std::vector<AudioCodec>& codecs) {
  if (fail_set_send_codecs()) {
    // Fake the failure in SetSendCodecs.
    return false;
  }
  send_codecs_ = codecs;
  return true;
}
bool FakeVoiceMediaChannel::SetMaxSendBandwidth(int bps) {
  max_bps_ = bps;
  return true;
}
bool FakeVoiceMediaChannel::SetOptions(const AudioOptions& options) {
  // Does a "merge" of current options and set options.
  options_.SetAll(options);
  return true;
}
bool FakeVoiceMediaChannel::SetLocalSource(uint32_t ssrc, AudioSource* source) {
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

bool CompareDtmfInfo(const FakeVoiceMediaChannel::DtmfInfo& info,
                     uint32_t ssrc,
                     int event_code,
                     int duration) {
  return (info.duration == duration && info.event_code == event_code &&
          info.ssrc == ssrc);
}

FakeVideoMediaChannel::FakeVideoMediaChannel(FakeVideoEngine* engine,
                                             const VideoOptions& options)
    : engine_(engine), max_bps_(-1) {
  SetOptions(options);
}
FakeVideoMediaChannel::~FakeVideoMediaChannel() {
  if (engine_) {
    engine_->UnregisterChannel(this);
  }
}
const std::vector<VideoCodec>& FakeVideoMediaChannel::recv_codecs() const {
  return recv_codecs_;
}
const std::vector<VideoCodec>& FakeVideoMediaChannel::send_codecs() const {
  return send_codecs_;
}
const std::vector<VideoCodec>& FakeVideoMediaChannel::codecs() const {
  return send_codecs();
}
bool FakeVideoMediaChannel::rendering() const {
  return playout();
}
const VideoOptions& FakeVideoMediaChannel::options() const {
  return options_;
}
const std::map<uint32_t, rtc::VideoSinkInterface<webrtc::VideoFrame>*>&
FakeVideoMediaChannel::sinks() const {
  return sinks_;
}
int FakeVideoMediaChannel::max_bps() const {
  return max_bps_;
}
bool FakeVideoMediaChannel::SetSendParameters(
    const VideoSendParameters& params) {
  set_send_rtcp_parameters(params.rtcp);
  return (SetSendCodecs(params.codecs) &&
          SetSendExtmapAllowMixed(params.extmap_allow_mixed) &&
          SetSendRtpHeaderExtensions(params.extensions) &&
          SetMaxSendBandwidth(params.max_bandwidth_bps));
}
bool FakeVideoMediaChannel::SetRecvParameters(
    const VideoRecvParameters& params) {
  set_recv_rtcp_parameters(params.rtcp);
  return (SetRecvCodecs(params.codecs) &&
          SetRecvRtpHeaderExtensions(params.extensions));
}
bool FakeVideoMediaChannel::AddSendStream(const StreamParams& sp) {
  return RtpHelper<VideoMediaChannel>::AddSendStream(sp);
}
bool FakeVideoMediaChannel::RemoveSendStream(uint32_t ssrc) {
  return RtpHelper<VideoMediaChannel>::RemoveSendStream(ssrc);
}
bool FakeVideoMediaChannel::GetSendCodec(VideoCodec* send_codec) {
  if (send_codecs_.empty()) {
    return false;
  }
  *send_codec = send_codecs_[0];
  return true;
}
bool FakeVideoMediaChannel::SetSink(
    uint32_t ssrc,
    rtc::VideoSinkInterface<webrtc::VideoFrame>* sink) {
  auto it = sinks_.find(ssrc);
  if (it == sinks_.end()) {
    return false;
  }
  it->second = sink;
  return true;
}
void FakeVideoMediaChannel::SetDefaultSink(
    rtc::VideoSinkInterface<webrtc::VideoFrame>* sink) {}
bool FakeVideoMediaChannel::HasSink(uint32_t ssrc) const {
  return sinks_.find(ssrc) != sinks_.end() && sinks_.at(ssrc) != nullptr;
}
bool FakeVideoMediaChannel::SetSend(bool send) {
  return set_sending(send);
}
bool FakeVideoMediaChannel::SetVideoSend(
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
bool FakeVideoMediaChannel::HasSource(uint32_t ssrc) const {
  return sources_.find(ssrc) != sources_.end() && sources_.at(ssrc) != nullptr;
}
bool FakeVideoMediaChannel::AddRecvStream(const StreamParams& sp) {
  if (!RtpHelper<VideoMediaChannel>::AddRecvStream(sp))
    return false;
  sinks_[sp.first_ssrc()] = NULL;
  output_delays_[sp.first_ssrc()] = 0;
  return true;
}
bool FakeVideoMediaChannel::RemoveRecvStream(uint32_t ssrc) {
  if (!RtpHelper<VideoMediaChannel>::RemoveRecvStream(ssrc))
    return false;
  sinks_.erase(ssrc);
  output_delays_.erase(ssrc);
  return true;
}
void FakeVideoMediaChannel::FillBitrateInfo(BandwidthEstimationInfo* bwe_info) {
}
bool FakeVideoMediaChannel::GetStats(VideoMediaInfo* info) {
  return false;
}
std::vector<webrtc::RtpSource> FakeVideoMediaChannel::GetSources(
    uint32_t ssrc) const {
  return {};
}
bool FakeVideoMediaChannel::SetBaseMinimumPlayoutDelayMs(uint32_t ssrc,
                                                         int delay_ms) {
  if (output_delays_.find(ssrc) == output_delays_.end()) {
    return false;
  } else {
    output_delays_[ssrc] = delay_ms;
    return true;
  }
}
absl::optional<int> FakeVideoMediaChannel::GetBaseMinimumPlayoutDelayMs(
    uint32_t ssrc) const {
  const auto it = output_delays_.find(ssrc);
  if (it != output_delays_.end()) {
    return it->second;
  }
  return absl::nullopt;
}
bool FakeVideoMediaChannel::SetRecvCodecs(
    const std::vector<VideoCodec>& codecs) {
  if (fail_set_recv_codecs()) {
    // Fake the failure in SetRecvCodecs.
    return false;
  }
  recv_codecs_ = codecs;
  return true;
}
bool FakeVideoMediaChannel::SetSendCodecs(
    const std::vector<VideoCodec>& codecs) {
  if (fail_set_send_codecs()) {
    // Fake the failure in SetSendCodecs.
    return false;
  }
  send_codecs_ = codecs;

  return true;
}
bool FakeVideoMediaChannel::SetOptions(const VideoOptions& options) {
  options_ = options;
  return true;
}

bool FakeVideoMediaChannel::SetMaxSendBandwidth(int bps) {
  max_bps_ = bps;
  return true;
}

void FakeVideoMediaChannel::SetRecordableEncodedFrameCallback(
    uint32_t ssrc,
    std::function<void(const webrtc::RecordableEncodedFrame&)> callback) {}

void FakeVideoMediaChannel::ClearRecordableEncodedFrameCallback(uint32_t ssrc) {
}

void FakeVideoMediaChannel::GenerateKeyFrame(uint32_t ssrc) {}

FakeDataMediaChannel::FakeDataMediaChannel(void* unused,
                                           const DataOptions& options)
    : send_blocked_(false), max_bps_(-1) {}
FakeDataMediaChannel::~FakeDataMediaChannel() {}
const std::vector<DataCodec>& FakeDataMediaChannel::recv_codecs() const {
  return recv_codecs_;
}
const std::vector<DataCodec>& FakeDataMediaChannel::send_codecs() const {
  return send_codecs_;
}
const std::vector<DataCodec>& FakeDataMediaChannel::codecs() const {
  return send_codecs();
}
int FakeDataMediaChannel::max_bps() const {
  return max_bps_;
}
bool FakeDataMediaChannel::SetSendParameters(const DataSendParameters& params) {
  set_send_rtcp_parameters(params.rtcp);
  return (SetSendCodecs(params.codecs) &&
          SetMaxSendBandwidth(params.max_bandwidth_bps));
}
bool FakeDataMediaChannel::SetRecvParameters(const DataRecvParameters& params) {
  set_recv_rtcp_parameters(params.rtcp);
  return SetRecvCodecs(params.codecs);
}
bool FakeDataMediaChannel::SetSend(bool send) {
  return set_sending(send);
}
bool FakeDataMediaChannel::SetReceive(bool receive) {
  set_playout(receive);
  return true;
}
bool FakeDataMediaChannel::AddRecvStream(const StreamParams& sp) {
  if (!RtpHelper<DataMediaChannel>::AddRecvStream(sp))
    return false;
  return true;
}
bool FakeDataMediaChannel::RemoveRecvStream(uint32_t ssrc) {
  if (!RtpHelper<DataMediaChannel>::RemoveRecvStream(ssrc))
    return false;
  return true;
}
bool FakeDataMediaChannel::SendData(const SendDataParams& params,
                                    const rtc::CopyOnWriteBuffer& payload,
                                    SendDataResult* result) {
  if (send_blocked_) {
    *result = SDR_BLOCK;
    return false;
  } else {
    last_sent_data_params_ = params;
    last_sent_data_ = std::string(payload.data<char>(), payload.size());
    return true;
  }
}
SendDataParams FakeDataMediaChannel::last_sent_data_params() {
  return last_sent_data_params_;
}
std::string FakeDataMediaChannel::last_sent_data() {
  return last_sent_data_;
}
bool FakeDataMediaChannel::is_send_blocked() {
  return send_blocked_;
}
void FakeDataMediaChannel::set_send_blocked(bool blocked) {
  send_blocked_ = blocked;
}
bool FakeDataMediaChannel::SetRecvCodecs(const std::vector<DataCodec>& codecs) {
  if (fail_set_recv_codecs()) {
    // Fake the failure in SetRecvCodecs.
    return false;
  }
  recv_codecs_ = codecs;
  return true;
}
bool FakeDataMediaChannel::SetSendCodecs(const std::vector<DataCodec>& codecs) {
  if (fail_set_send_codecs()) {
    // Fake the failure in SetSendCodecs.
    return false;
  }
  send_codecs_ = codecs;
  return true;
}
bool FakeDataMediaChannel::SetMaxSendBandwidth(int bps) {
  max_bps_ = bps;
  return true;
}

FakeVoiceEngine::FakeVoiceEngine() : fail_create_channel_(false) {
  // Add a fake audio codec. Note that the name must not be "" as there are
  // sanity checks against that.
  SetCodecs({AudioCodec(101, "fake_audio_codec", 0, 0, 1)});
}
void FakeVoiceEngine::Init() {}
rtc::scoped_refptr<webrtc::AudioState> FakeVoiceEngine::GetAudioState() const {
  return rtc::scoped_refptr<webrtc::AudioState>();
}
VoiceMediaChannel* FakeVoiceEngine::CreateMediaChannel(
    webrtc::Call* call,
    const MediaConfig& config,
    const AudioOptions& options,
    const webrtc::CryptoOptions& crypto_options) {
  if (fail_create_channel_) {
    return nullptr;
  }

  FakeVoiceMediaChannel* ch = new FakeVoiceMediaChannel(this, options);
  channels_.push_back(ch);
  return ch;
}
FakeVoiceMediaChannel* FakeVoiceEngine::GetChannel(size_t index) {
  return (channels_.size() > index) ? channels_[index] : NULL;
}
void FakeVoiceEngine::UnregisterChannel(VoiceMediaChannel* channel) {
  channels_.erase(absl::c_find(channels_, channel));
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
  send_codecs_.push_back(VideoCodec(0, "fake_video_codec"));
  recv_codecs_.push_back(VideoCodec(0, "fake_video_codec"));
}
bool FakeVideoEngine::SetOptions(const VideoOptions& options) {
  options_ = options;
  return true;
}
VideoMediaChannel* FakeVideoEngine::CreateMediaChannel(
    webrtc::Call* call,
    const MediaConfig& config,
    const VideoOptions& options,
    const webrtc::CryptoOptions& crypto_options,
    webrtc::VideoBitrateAllocatorFactory* video_bitrate_allocator_factory) {
  if (fail_create_channel_) {
    return nullptr;
  }

  FakeVideoMediaChannel* ch = new FakeVideoMediaChannel(this, options);
  channels_.emplace_back(ch);
  return ch;
}
FakeVideoMediaChannel* FakeVideoEngine::GetChannel(size_t index) {
  return (channels_.size() > index) ? channels_[index] : nullptr;
}
void FakeVideoEngine::UnregisterChannel(VideoMediaChannel* channel) {
  auto it = absl::c_find(channels_, channel);
  RTC_DCHECK(it != channels_.end());
  channels_.erase(it);
}
std::vector<VideoCodec> FakeVideoEngine::send_codecs() const {
  return send_codecs_;
}

std::vector<VideoCodec> FakeVideoEngine::recv_codecs() const {
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

FakeVoiceMediaChannel* FakeMediaEngine::GetVoiceChannel(size_t index) {
  return voice_->GetChannel(index);
}
FakeVideoMediaChannel* FakeMediaEngine::GetVideoChannel(size_t index) {
  return video_->GetChannel(index);
}

void FakeMediaEngine::set_fail_create_channel(bool fail) {
  voice_->fail_create_channel_ = fail;
  video_->fail_create_channel_ = fail;
}

DataMediaChannel* FakeDataEngine::CreateChannel(const MediaConfig& config) {
  FakeDataMediaChannel* ch = new FakeDataMediaChannel(this, DataOptions());
  channels_.push_back(ch);
  return ch;
}
FakeDataMediaChannel* FakeDataEngine::GetChannel(size_t index) {
  return (channels_.size() > index) ? channels_[index] : NULL;
}
void FakeDataEngine::UnregisterChannel(DataMediaChannel* channel) {
  channels_.erase(absl::c_find(channels_, channel));
}
void FakeDataEngine::SetDataCodecs(const std::vector<DataCodec>& data_codecs) {
  data_codecs_ = data_codecs;
}
const std::vector<DataCodec>& FakeDataEngine::data_codecs() {
  return data_codecs_;
}

}  // namespace cricket
