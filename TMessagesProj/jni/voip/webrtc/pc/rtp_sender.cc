/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/rtp_sender.h"

#include <algorithm>
#include <atomic>
#include <string>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "api/audio_options.h"
#include "api/media_stream_interface.h"
#include "api/priority.h"
#include "media/base/media_engine.h"
#include "pc/legacy_stats_collector_interface.h"
#include "rtc_base/checks.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

namespace {

// This function is only expected to be called on the signaling thread.
// On the other hand, some test or even production setups may use
// several signaling threads.
int GenerateUniqueId() {
  static std::atomic<int> g_unique_id{0};

  return ++g_unique_id;
}

// Returns true if a "per-sender" encoding parameter contains a value that isn't
// its default. Currently max_bitrate_bps and bitrate_priority both are
// implemented "per-sender," meaning that these encoding parameters
// are used for the RtpSender as a whole, not for a specific encoding layer.
// This is done by setting these encoding parameters at index 0 of
// RtpParameters.encodings. This function can be used to check if these
// parameters are set at any index other than 0 of RtpParameters.encodings,
// because they are currently unimplemented to be used for a specific encoding
// layer.
bool PerSenderRtpEncodingParameterHasValue(
    const RtpEncodingParameters& encoding_params) {
  if (encoding_params.bitrate_priority != kDefaultBitratePriority ||
      encoding_params.network_priority != Priority::kLow) {
    return true;
  }
  return false;
}

void RemoveEncodingLayers(const std::vector<std::string>& rids,
                          std::vector<RtpEncodingParameters>* encodings) {
  RTC_DCHECK(encodings);
  encodings->erase(
      std::remove_if(encodings->begin(), encodings->end(),
                     [&rids](const RtpEncodingParameters& encoding) {
                       return absl::c_linear_search(rids, encoding.rid);
                     }),
      encodings->end());
}

RtpParameters RestoreEncodingLayers(
    const RtpParameters& parameters,
    const std::vector<std::string>& removed_rids,
    const std::vector<RtpEncodingParameters>& all_layers) {
  RTC_CHECK_EQ(parameters.encodings.size() + removed_rids.size(),
               all_layers.size());
  RtpParameters result(parameters);
  result.encodings.clear();
  size_t index = 0;
  for (const RtpEncodingParameters& encoding : all_layers) {
    if (absl::c_linear_search(removed_rids, encoding.rid)) {
      result.encodings.push_back(encoding);
      continue;
    }
    result.encodings.push_back(parameters.encodings[index++]);
  }
  return result;
}

}  // namespace

// Returns true if any RtpParameters member that isn't implemented contains a
// value.
bool UnimplementedRtpParameterHasValue(const RtpParameters& parameters) {
  if (!parameters.mid.empty()) {
    return true;
  }
  for (size_t i = 0; i < parameters.encodings.size(); ++i) {
    // Encoding parameters that are per-sender should only contain value at
    // index 0.
    if (i != 0 &&
        PerSenderRtpEncodingParameterHasValue(parameters.encodings[i])) {
      return true;
    }
  }
  return false;
}

RtpSenderBase::RtpSenderBase(rtc::Thread* worker_thread,
                             const std::string& id,
                             SetStreamsObserver* set_streams_observer)
    : signaling_thread_(rtc::Thread::Current()),
      worker_thread_(worker_thread),
      id_(id),
      set_streams_observer_(set_streams_observer) {
  RTC_DCHECK(worker_thread);
  init_parameters_.encodings.emplace_back();
}

void RtpSenderBase::SetFrameEncryptor(
    rtc::scoped_refptr<FrameEncryptorInterface> frame_encryptor) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  frame_encryptor_ = std::move(frame_encryptor);
  // Special Case: Set the frame encryptor to any value on any existing channel.
  if (media_channel_ && ssrc_ && !stopped_) {
    worker_thread_->BlockingCall(
        [&] { media_channel_->SetFrameEncryptor(ssrc_, frame_encryptor_); });
  }
}

void RtpSenderBase::SetEncoderSelector(
    std::unique_ptr<VideoEncoderFactory::EncoderSelectorInterface>
        encoder_selector) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  encoder_selector_ = std::move(encoder_selector);
  SetEncoderSelectorOnChannel();
}

void RtpSenderBase::SetEncoderSelectorOnChannel() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (media_channel_ && ssrc_ && !stopped_) {
    worker_thread_->BlockingCall([&] {
      media_channel_->SetEncoderSelector(ssrc_, encoder_selector_.get());
    });
  }
}

void RtpSenderBase::SetMediaChannel(cricket::MediaChannel* media_channel) {
  RTC_DCHECK(media_channel == nullptr ||
             media_channel->media_type() == media_type());
  media_channel_ = media_channel;
}

RtpParameters RtpSenderBase::GetParametersInternal() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (stopped_) {
    return RtpParameters();
  }
  if (!media_channel_ || !ssrc_) {
    return init_parameters_;
  }
  return worker_thread_->BlockingCall([&] {
    RtpParameters result = media_channel_->GetRtpSendParameters(ssrc_);
    RemoveEncodingLayers(disabled_rids_, &result.encodings);
    return result;
  });
}

RtpParameters RtpSenderBase::GetParametersInternalWithAllLayers() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (stopped_) {
    return RtpParameters();
  }
  if (!media_channel_ || !ssrc_) {
    return init_parameters_;
  }
  return worker_thread_->BlockingCall([&] {
    RtpParameters result = media_channel_->GetRtpSendParameters(ssrc_);
    return result;
  });
}

RtpParameters RtpSenderBase::GetParameters() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RtpParameters result = GetParametersInternal();
  last_transaction_id_ = rtc::CreateRandomUuid();
  result.transaction_id = last_transaction_id_.value();
  return result;
}

RTCError RtpSenderBase::SetParametersInternal(const RtpParameters& parameters) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_DCHECK(!stopped_);

  if (UnimplementedRtpParameterHasValue(parameters)) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::UNSUPPORTED_PARAMETER,
        "Attempted to set an unimplemented parameter of RtpParameters.");
  }
  if (!media_channel_ || !ssrc_) {
    auto result = cricket::CheckRtpParametersInvalidModificationAndValues(
        init_parameters_, parameters, video_codec_preferences_);
    if (result.ok()) {
      init_parameters_ = parameters;
    }
    return result;
  }
  return worker_thread_->BlockingCall([&] {
    RtpParameters rtp_parameters = parameters;
    RtpParameters old_parameters = media_channel_->GetRtpSendParameters(ssrc_);
    if (!disabled_rids_.empty()) {
      // Need to add the inactive layers.
      rtp_parameters = RestoreEncodingLayers(parameters, disabled_rids_,
                                             old_parameters.encodings);
    }

    auto result = cricket::CheckRtpParametersInvalidModificationAndValues(
        old_parameters, rtp_parameters);
    if (!result.ok())
      return result;

    result = CheckSVCParameters(rtp_parameters);
    if (!result.ok())
      return result;

    return media_channel_->SetRtpSendParameters(ssrc_, rtp_parameters);
  });
}

RTCError RtpSenderBase::SetParametersInternalWithAllLayers(
    const RtpParameters& parameters) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_DCHECK(!stopped_);

  if (UnimplementedRtpParameterHasValue(parameters)) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::UNSUPPORTED_PARAMETER,
        "Attempted to set an unimplemented parameter of RtpParameters.");
  }
  if (!media_channel_ || !ssrc_) {
    auto result = cricket::CheckRtpParametersInvalidModificationAndValues(
        init_parameters_, parameters, video_codec_preferences_);
    if (result.ok()) {
      init_parameters_ = parameters;
    }
    return result;
  }
  return worker_thread_->BlockingCall([&] {
    RtpParameters rtp_parameters = parameters;
    return media_channel_->SetRtpSendParameters(ssrc_, rtp_parameters);
  });
}

RTCError RtpSenderBase::SetParameters(const RtpParameters& parameters) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  TRACE_EVENT0("webrtc", "RtpSenderBase::SetParameters");
  if (is_transceiver_stopped_) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::INVALID_STATE,
        "Cannot set parameters on sender of a stopped transceiver.");
  }
  if (stopped_) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_STATE,
                         "Cannot set parameters on a stopped sender.");
  }
  if (stopped_) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_STATE,
                         "Cannot set parameters on a stopped sender.");
  }
  if (!last_transaction_id_) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::INVALID_STATE,
        "Failed to set parameters since getParameters() has never been called"
        " on this sender");
  }
  if (last_transaction_id_ != parameters.transaction_id) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::INVALID_MODIFICATION,
        "Failed to set parameters since the transaction_id doesn't match"
        " the last value returned from getParameters()");
  }

  RTCError result = SetParametersInternal(parameters);
  last_transaction_id_.reset();
  return result;
}

void RtpSenderBase::SetStreams(const std::vector<std::string>& stream_ids) {
  set_stream_ids(stream_ids);
  if (set_streams_observer_)
    set_streams_observer_->OnSetStreams();
}

bool RtpSenderBase::SetTrack(MediaStreamTrackInterface* track) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  TRACE_EVENT0("webrtc", "RtpSenderBase::SetTrack");
  if (stopped_) {
    RTC_LOG(LS_ERROR) << "SetTrack can't be called on a stopped RtpSender.";
    return false;
  }
  if (track && track->kind() != track_kind()) {
    RTC_LOG(LS_ERROR) << "SetTrack with " << track->kind()
                      << " called on RtpSender with " << track_kind()
                      << " track.";
    return false;
  }

  // Detach from old track.
  if (track_) {
    DetachTrack();
    track_->UnregisterObserver(this);
    RemoveTrackFromStats();
  }

  // Attach to new track.
  bool prev_can_send_track = can_send_track();
  // Keep a reference to the old track to keep it alive until we call SetSend.
  rtc::scoped_refptr<MediaStreamTrackInterface> old_track = track_;
  track_ = track;
  if (track_) {
    track_->RegisterObserver(this);
    AttachTrack();
  }

  // Update channel.
  if (can_send_track()) {
    SetSend();
    AddTrackToStats();
  } else if (prev_can_send_track) {
    ClearSend();
  }
  attachment_id_ = (track_ ? GenerateUniqueId() : 0);
  return true;
}

void RtpSenderBase::SetSsrc(uint32_t ssrc) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  TRACE_EVENT0("webrtc", "RtpSenderBase::SetSsrc");
  if (stopped_ || ssrc == ssrc_) {
    return;
  }
  // If we are already sending with a particular SSRC, stop sending.
  if (can_send_track()) {
    ClearSend();
    RemoveTrackFromStats();
  }
  ssrc_ = ssrc;
  if (can_send_track()) {
    SetSend();
    AddTrackToStats();
  }
  if (!init_parameters_.encodings.empty() ||
      init_parameters_.degradation_preference.has_value()) {
    worker_thread_->BlockingCall([&] {
      RTC_DCHECK(media_channel_);
      // Get the current parameters, which are constructed from the SDP.
      // The number of layers in the SDP is currently authoritative to support
      // SDP munging for Plan-B simulcast with "a=ssrc-group:SIM <ssrc-id>..."
      // lines as described in RFC 5576.
      // All fields should be default constructed and the SSRC field set, which
      // we need to copy.
      RtpParameters current_parameters =
          media_channel_->GetRtpSendParameters(ssrc_);
      RTC_CHECK_GE(current_parameters.encodings.size(),
                   init_parameters_.encodings.size());
      for (size_t i = 0; i < init_parameters_.encodings.size(); ++i) {
        init_parameters_.encodings[i].ssrc =
            current_parameters.encodings[i].ssrc;
        init_parameters_.encodings[i].rid = current_parameters.encodings[i].rid;
        current_parameters.encodings[i] = init_parameters_.encodings[i];
      }
      current_parameters.degradation_preference =
          init_parameters_.degradation_preference;
      media_channel_->SetRtpSendParameters(ssrc_, current_parameters);
      init_parameters_.encodings.clear();
      init_parameters_.degradation_preference = absl::nullopt;
    });
  }
  // Attempt to attach the frame decryptor to the current media channel.
  if (frame_encryptor_) {
    SetFrameEncryptor(frame_encryptor_);
  }
  if (frame_transformer_) {
    SetEncoderToPacketizerFrameTransformer(frame_transformer_);
  }
  if (encoder_selector_) {
    SetEncoderSelectorOnChannel();
  }
}

void RtpSenderBase::Stop() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  TRACE_EVENT0("webrtc", "RtpSenderBase::Stop");
  // TODO(deadbeef): Need to do more here to fully stop sending packets.
  if (stopped_) {
    return;
  }
  if (track_) {
    DetachTrack();
    track_->UnregisterObserver(this);
  }
  if (can_send_track()) {
    ClearSend();
    RemoveTrackFromStats();
  }
  media_channel_ = nullptr;
  set_streams_observer_ = nullptr;
  stopped_ = true;
}

RTCError RtpSenderBase::DisableEncodingLayers(
    const std::vector<std::string>& rids) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (stopped_) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_STATE,
                         "Cannot disable encodings on a stopped sender.");
  }

  if (rids.empty()) {
    return RTCError::OK();
  }

  // Check that all the specified layers exist and disable them in the channel.
  RtpParameters parameters = GetParametersInternalWithAllLayers();
  for (const std::string& rid : rids) {
    if (absl::c_none_of(parameters.encodings,
                        [&rid](const RtpEncodingParameters& encoding) {
                          return encoding.rid == rid;
                        })) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           "RID: " + rid + " does not refer to a valid layer.");
    }
  }

  if (!media_channel_ || !ssrc_) {
    RemoveEncodingLayers(rids, &init_parameters_.encodings);
    // Invalidate any transaction upon success.
    last_transaction_id_.reset();
    return RTCError::OK();
  }

  for (RtpEncodingParameters& encoding : parameters.encodings) {
    // Remain active if not in the disable list.
    encoding.active &= absl::c_none_of(
        rids,
        [&encoding](const std::string& rid) { return encoding.rid == rid; });
  }

  RTCError result = SetParametersInternalWithAllLayers(parameters);
  if (result.ok()) {
    disabled_rids_.insert(disabled_rids_.end(), rids.begin(), rids.end());
    // Invalidate any transaction upon success.
    last_transaction_id_.reset();
  }
  return result;
}

void RtpSenderBase::SetEncoderToPacketizerFrameTransformer(
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  frame_transformer_ = std::move(frame_transformer);
  if (media_channel_ && ssrc_ && !stopped_) {
    worker_thread_->BlockingCall([&] {
      media_channel_->SetEncoderToPacketizerFrameTransformer(
          ssrc_, frame_transformer_);
    });
  }
}

LocalAudioSinkAdapter::LocalAudioSinkAdapter() : sink_(nullptr) {}

LocalAudioSinkAdapter::~LocalAudioSinkAdapter() {
  MutexLock lock(&lock_);
  if (sink_)
    sink_->OnClose();
}

void LocalAudioSinkAdapter::OnData(
    const void* audio_data,
    int bits_per_sample,
    int sample_rate,
    size_t number_of_channels,
    size_t number_of_frames,
    absl::optional<int64_t> absolute_capture_timestamp_ms) {
  TRACE_EVENT2("webrtc", "LocalAudioSinkAdapter::OnData", "sample_rate",
               sample_rate, "number_of_frames", number_of_frames);
  MutexLock lock(&lock_);
  if (sink_) {
    sink_->OnData(audio_data, bits_per_sample, sample_rate, number_of_channels,
                  number_of_frames, absolute_capture_timestamp_ms);
    num_preferred_channels_ = sink_->NumPreferredChannels();
  }
}

void LocalAudioSinkAdapter::SetSink(cricket::AudioSource::Sink* sink) {
  MutexLock lock(&lock_);
  RTC_DCHECK(!sink || !sink_);
  sink_ = sink;
}

rtc::scoped_refptr<AudioRtpSender> AudioRtpSender::Create(
    rtc::Thread* worker_thread,
    const std::string& id,
    LegacyStatsCollectorInterface* stats,
    SetStreamsObserver* set_streams_observer) {
  return rtc::make_ref_counted<AudioRtpSender>(worker_thread, id, stats,
                                               set_streams_observer);
}

AudioRtpSender::AudioRtpSender(rtc::Thread* worker_thread,
                               const std::string& id,
                               LegacyStatsCollectorInterface* legacy_stats,
                               SetStreamsObserver* set_streams_observer)
    : RtpSenderBase(worker_thread, id, set_streams_observer),
      legacy_stats_(legacy_stats),
      dtmf_sender_(DtmfSender::Create(rtc::Thread::Current(), this)),
      dtmf_sender_proxy_(
          DtmfSenderProxy::Create(rtc::Thread::Current(), dtmf_sender_)),
      sink_adapter_(new LocalAudioSinkAdapter()) {}

AudioRtpSender::~AudioRtpSender() {
  dtmf_sender_->OnDtmfProviderDestroyed();
  Stop();
}

bool AudioRtpSender::CanInsertDtmf() {
  if (!media_channel_) {
    RTC_LOG(LS_ERROR) << "CanInsertDtmf: No audio channel exists.";
    return false;
  }
  // Check that this RTP sender is active (description has been applied that
  // matches an SSRC to its ID).
  if (!ssrc_) {
    RTC_LOG(LS_ERROR) << "CanInsertDtmf: Sender does not have SSRC.";
    return false;
  }
  return worker_thread_->BlockingCall(
      [&] { return voice_media_channel()->CanInsertDtmf(); });
}

bool AudioRtpSender::InsertDtmf(int code, int duration) {
  if (!media_channel_) {
    RTC_LOG(LS_ERROR) << "InsertDtmf: No audio channel exists.";
    return false;
  }
  if (!ssrc_) {
    RTC_LOG(LS_ERROR) << "InsertDtmf: Sender does not have SSRC.";
    return false;
  }
  bool success = worker_thread_->BlockingCall(
      [&] { return voice_media_channel()->InsertDtmf(ssrc_, code, duration); });
  if (!success) {
    RTC_LOG(LS_ERROR) << "Failed to insert DTMF to channel.";
  }
  return success;
}

void AudioRtpSender::OnChanged() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  TRACE_EVENT0("webrtc", "AudioRtpSender::OnChanged");
  RTC_DCHECK(!stopped_);
  if (cached_track_enabled_ != track_->enabled()) {
    cached_track_enabled_ = track_->enabled();
    if (can_send_track()) {
      SetSend();
    }
  }
}

void AudioRtpSender::DetachTrack() {
  RTC_DCHECK(track_);
  audio_track()->RemoveSink(sink_adapter_.get());
}

void AudioRtpSender::AttachTrack() {
  RTC_DCHECK(track_);
  cached_track_enabled_ = track_->enabled();
  audio_track()->AddSink(sink_adapter_.get());
}

void AudioRtpSender::AddTrackToStats() {
  if (can_send_track() && legacy_stats_) {
    legacy_stats_->AddLocalAudioTrack(audio_track().get(), ssrc_);
  }
}

void AudioRtpSender::RemoveTrackFromStats() {
  if (can_send_track() && legacy_stats_) {
    legacy_stats_->RemoveLocalAudioTrack(audio_track().get(), ssrc_);
  }
}

rtc::scoped_refptr<DtmfSenderInterface> AudioRtpSender::GetDtmfSender() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return dtmf_sender_proxy_;
}

RTCError AudioRtpSender::GenerateKeyFrame() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_DLOG(LS_ERROR) << "Tried to get generate a key frame for audio.";
  return RTCError(RTCErrorType::UNSUPPORTED_OPERATION,
                  "Generating key frames for audio is not supported.");
}

void AudioRtpSender::SetSend() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_DCHECK(!stopped_);
  RTC_DCHECK(can_send_track());
  if (!media_channel_) {
    RTC_LOG(LS_ERROR) << "SetAudioSend: No audio channel exists.";
    return;
  }
  cricket::AudioOptions options;
#if !defined(WEBRTC_CHROMIUM_BUILD) && !defined(WEBRTC_WEBKIT_BUILD)
  // TODO(tommi): Remove this hack when we move CreateAudioSource out of
  // PeerConnection.  This is a bit of a strange way to apply local audio
  // options since it is also applied to all streams/channels, local or remote.
  if (track_->enabled() && audio_track()->GetSource() &&
      !audio_track()->GetSource()->remote()) {
    options = audio_track()->GetSource()->options();
  }
#endif

  // `track_->enabled()` hops to the signaling thread, so call it before we hop
  // to the worker thread or else it will deadlock.
  bool track_enabled = track_->enabled();
  bool success = worker_thread_->BlockingCall([&] {
    return voice_media_channel()->SetAudioSend(ssrc_, track_enabled, &options,
                                               sink_adapter_.get());
  });
  if (!success) {
    RTC_LOG(LS_ERROR) << "SetAudioSend: ssrc is incorrect: " << ssrc_;
  }
}

void AudioRtpSender::ClearSend() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_DCHECK(ssrc_ != 0);
  RTC_DCHECK(!stopped_);
  if (!media_channel_) {
    RTC_LOG(LS_WARNING) << "ClearAudioSend: No audio channel exists.";
    return;
  }
  cricket::AudioOptions options;
  bool success = worker_thread_->BlockingCall([&] {
    return voice_media_channel()->SetAudioSend(ssrc_, false, &options, nullptr);
  });
  if (!success) {
    RTC_LOG(LS_WARNING) << "ClearAudioSend: ssrc is incorrect: " << ssrc_;
  }
}

rtc::scoped_refptr<VideoRtpSender> VideoRtpSender::Create(
    rtc::Thread* worker_thread,
    const std::string& id,
    SetStreamsObserver* set_streams_observer) {
  return rtc::make_ref_counted<VideoRtpSender>(worker_thread, id,
                                               set_streams_observer);
}

VideoRtpSender::VideoRtpSender(rtc::Thread* worker_thread,
                               const std::string& id,
                               SetStreamsObserver* set_streams_observer)
    : RtpSenderBase(worker_thread, id, set_streams_observer) {}

VideoRtpSender::~VideoRtpSender() {
  Stop();
}

void VideoRtpSender::OnChanged() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  TRACE_EVENT0("webrtc", "VideoRtpSender::OnChanged");
  RTC_DCHECK(!stopped_);

  auto content_hint = video_track()->content_hint();
  if (cached_track_content_hint_ != content_hint) {
    cached_track_content_hint_ = content_hint;
    if (can_send_track()) {
      SetSend();
    }
  }
}

void VideoRtpSender::AttachTrack() {
  RTC_DCHECK(track_);
  cached_track_content_hint_ = video_track()->content_hint();
}

rtc::scoped_refptr<DtmfSenderInterface> VideoRtpSender::GetDtmfSender() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_DLOG(LS_ERROR) << "Tried to get DTMF sender from video sender.";
  return nullptr;
}

RTCError VideoRtpSender::GenerateKeyFrame() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (video_media_channel() && ssrc_ && !stopped_) {
    worker_thread_->PostTask(
        [&] { video_media_channel()->GenerateSendKeyFrame(ssrc_); });
  } else {
    RTC_LOG(LS_WARNING) << "Tried to generate key frame for sender that is "
                           "stopped or has no media channel.";
  }
  return RTCError::OK();
}

void VideoRtpSender::SetSend() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_DCHECK(!stopped_);
  RTC_DCHECK(can_send_track());
  if (!media_channel_) {
    RTC_LOG(LS_ERROR) << "SetVideoSend: No video channel exists.";
    return;
  }
  cricket::VideoOptions options;
  VideoTrackSourceInterface* source = video_track()->GetSource();
  if (source) {
    options.is_screencast = source->is_screencast();
    options.video_noise_reduction = source->needs_denoising();
  }
  options.content_hint = cached_track_content_hint_;
  switch (cached_track_content_hint_) {
    case VideoTrackInterface::ContentHint::kNone:
      break;
    case VideoTrackInterface::ContentHint::kFluid:
      options.is_screencast = false;
      break;
    case VideoTrackInterface::ContentHint::kDetailed:
    case VideoTrackInterface::ContentHint::kText:
      options.is_screencast = true;
      break;
  }
  bool success = worker_thread_->BlockingCall([&] {
    return video_media_channel()->SetVideoSend(ssrc_, &options,
                                               video_track().get());
  });
  RTC_DCHECK(success);
}

void VideoRtpSender::ClearSend() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_DCHECK(ssrc_ != 0);
  RTC_DCHECK(!stopped_);
  if (!media_channel_) {
    RTC_LOG(LS_WARNING) << "SetVideoSend: No video channel exists.";
    return;
  }
  // Allow SetVideoSend to fail since `enable` is false and `source` is null.
  // This the normal case when the underlying media channel has already been
  // deleted.
  worker_thread_->BlockingCall(
      [&] { video_media_channel()->SetVideoSend(ssrc_, nullptr, nullptr); });
}

RTCError VideoRtpSender::CheckSVCParameters(const RtpParameters& parameters) {
  cricket::VideoCodec codec;
  video_media_channel()->GetSendCodec(&codec);

  // Match the currently used codec against the codec preferences to gather
  // the SVC capabilities.
  std::vector<cricket::VideoCodec> codecs;
  for (const auto& codec_preference : video_codec_preferences_) {
    if (codec.Matches(codec_preference)) {
      codecs.push_back(codec_preference);
    }
  }

  return cricket::CheckScalabilityModeValues(parameters, codecs);
}

}  // namespace webrtc
