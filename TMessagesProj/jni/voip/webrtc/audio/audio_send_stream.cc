/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/audio_send_stream.h"

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "api/audio_codecs/audio_encoder.h"
#include "api/audio_codecs/audio_encoder_factory.h"
#include "api/audio_codecs/audio_format.h"
#include "api/call/transport.h"
#include "api/crypto/frame_encryptor_interface.h"
#include "api/function_view.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "api/task_queue/task_queue_base.h"
#include "audio/audio_state.h"
#include "audio/channel_send.h"
#include "audio/conversion.h"
#include "call/rtp_config.h"
#include "call/rtp_transport_controller_send_interface.h"
#include "common_audio/vad/include/vad.h"
#include "logging/rtc_event_log/events/rtc_event_audio_send_stream_config.h"
#include "logging/rtc_event_log/rtc_stream_config.h"
#include "modules/audio_coding/codecs/cng/audio_encoder_cng.h"
#include "modules/audio_coding/codecs/red/audio_encoder_copy_red.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/audio_format_to_string.h"
#include "rtc_base/trace_event.h"

namespace webrtc {
namespace {

void UpdateEventLogStreamConfig(RtcEventLog* event_log,
                                const AudioSendStream::Config& config,
                                const AudioSendStream::Config* old_config) {
  using SendCodecSpec = AudioSendStream::Config::SendCodecSpec;
  // Only update if any of the things we log have changed.
  auto payload_types_equal = [](const absl::optional<SendCodecSpec>& a,
                                const absl::optional<SendCodecSpec>& b) {
    if (a.has_value() && b.has_value()) {
      return a->format.name == b->format.name &&
             a->payload_type == b->payload_type;
    }
    return !a.has_value() && !b.has_value();
  };

  if (old_config && config.rtp.ssrc == old_config->rtp.ssrc &&
      config.rtp.extensions == old_config->rtp.extensions &&
      payload_types_equal(config.send_codec_spec,
                          old_config->send_codec_spec)) {
    return;
  }

  auto rtclog_config = std::make_unique<rtclog::StreamConfig>();
  rtclog_config->local_ssrc = config.rtp.ssrc;
  rtclog_config->rtp_extensions = config.rtp.extensions;
  if (config.send_codec_spec) {
    rtclog_config->codecs.emplace_back(config.send_codec_spec->format.name,
                                       config.send_codec_spec->payload_type, 0);
  }
  event_log->Log(std::make_unique<RtcEventAudioSendStreamConfig>(
      std::move(rtclog_config)));
}

}  // namespace

constexpr char AudioAllocationConfig::kKey[];

std::unique_ptr<StructParametersParser> AudioAllocationConfig::Parser() {
  return StructParametersParser::Create(       //
      "min", &min_bitrate,                     //
      "max", &max_bitrate,                     //
      "prio_rate", &priority_bitrate,          //
      "prio_rate_raw", &priority_bitrate_raw,  //
      "rate_prio", &bitrate_priority);
}

AudioAllocationConfig::AudioAllocationConfig(
    const FieldTrialsView& field_trials) {
  Parser()->Parse(field_trials.Lookup(kKey));
  if (priority_bitrate_raw && !priority_bitrate.IsZero()) {
    RTC_LOG(LS_WARNING) << "'priority_bitrate' and '_raw' are mutually "
                           "exclusive but both were configured.";
  }
}

namespace internal {
AudioSendStream::AudioSendStream(
    Clock* clock,
    const webrtc::AudioSendStream::Config& config,
    const rtc::scoped_refptr<webrtc::AudioState>& audio_state,
    TaskQueueFactory* task_queue_factory,
    RtpTransportControllerSendInterface* rtp_transport,
    BitrateAllocatorInterface* bitrate_allocator,
    RtcEventLog* event_log,
    RtcpRttStats* rtcp_rtt_stats,
    const absl::optional<RtpState>& suspended_rtp_state,
    const FieldTrialsView& field_trials)
    : AudioSendStream(
          clock,
          config,
          audio_state,
          task_queue_factory,
          rtp_transport,
          bitrate_allocator,
          event_log,
          suspended_rtp_state,
          voe::CreateChannelSend(clock,
                                 task_queue_factory,
                                 config.send_transport,
                                 rtcp_rtt_stats,
                                 event_log,
                                 config.frame_encryptor.get(),
                                 config.crypto_options,
                                 config.rtp.extmap_allow_mixed,
                                 config.rtcp_report_interval_ms,
                                 config.rtp.ssrc,
                                 config.frame_transformer,
                                 rtp_transport->transport_feedback_observer(),
                                 field_trials),
          field_trials) {}

AudioSendStream::AudioSendStream(
    Clock* clock,
    const webrtc::AudioSendStream::Config& config,
    const rtc::scoped_refptr<webrtc::AudioState>& audio_state,
    TaskQueueFactory* task_queue_factory,
    RtpTransportControllerSendInterface* rtp_transport,
    BitrateAllocatorInterface* bitrate_allocator,
    RtcEventLog* event_log,
    const absl::optional<RtpState>& suspended_rtp_state,
    std::unique_ptr<voe::ChannelSendInterface> channel_send,
    const FieldTrialsView& field_trials)
    : clock_(clock),
      field_trials_(field_trials),
      rtp_transport_queue_(rtp_transport->GetWorkerQueue()),
      allocate_audio_without_feedback_(
          field_trials_.IsEnabled("WebRTC-Audio-ABWENoTWCC")),
      enable_audio_alr_probing_(
          !field_trials_.IsDisabled("WebRTC-Audio-AlrProbing")),
      send_side_bwe_with_overhead_(
          !field_trials_.IsDisabled("WebRTC-SendSideBwe-WithOverhead")),
      allocation_settings_(field_trials_),
      config_(Config(/*send_transport=*/nullptr)),
      audio_state_(audio_state),
      channel_send_(std::move(channel_send)),
      event_log_(event_log),
      use_legacy_overhead_calculation_(
          field_trials_.IsEnabled("WebRTC-Audio-LegacyOverhead")),
      bitrate_allocator_(bitrate_allocator),
      rtp_transport_(rtp_transport),
      rtp_rtcp_module_(channel_send_->GetRtpRtcp()),
      suspended_rtp_state_(suspended_rtp_state) {
  RTC_LOG(LS_INFO) << "AudioSendStream: " << config.rtp.ssrc;
  RTC_DCHECK(rtp_transport_queue_);
  RTC_DCHECK(audio_state_);
  RTC_DCHECK(channel_send_);
  RTC_DCHECK(bitrate_allocator_);
  RTC_DCHECK(rtp_transport);

  RTC_DCHECK(rtp_rtcp_module_);

  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  ConfigureStream(config, true);
  UpdateCachedTargetAudioBitrateConstraints();
}

AudioSendStream::~AudioSendStream() {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  RTC_LOG(LS_INFO) << "~AudioSendStream: " << config_.rtp.ssrc;
  RTC_DCHECK(!sending_);
  channel_send_->ResetSenderCongestionControlObjects();

  // Blocking call to synchronize state with worker queue to ensure that there
  // are no pending tasks left that keeps references to audio.
  rtp_transport_queue_->RunSynchronous([] {});
}

const webrtc::AudioSendStream::Config& AudioSendStream::GetConfig() const {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  return config_;
}

void AudioSendStream::Reconfigure(
    const webrtc::AudioSendStream::Config& new_config) {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  ConfigureStream(new_config, false);
}

AudioSendStream::ExtensionIds AudioSendStream::FindExtensionIds(
    const std::vector<RtpExtension>& extensions) {
  ExtensionIds ids;
  for (const auto& extension : extensions) {
    if (extension.uri == RtpExtension::kAudioLevelUri) {
      ids.audio_level = extension.id;
    } else if (extension.uri == RtpExtension::kAbsSendTimeUri) {
      ids.abs_send_time = extension.id;
    } else if (extension.uri == RtpExtension::kTransportSequenceNumberUri) {
      ids.transport_sequence_number = extension.id;
    } else if (extension.uri == RtpExtension::kMidUri) {
      ids.mid = extension.id;
    } else if (extension.uri == RtpExtension::kRidUri) {
      ids.rid = extension.id;
    } else if (extension.uri == RtpExtension::kRepairedRidUri) {
      ids.repaired_rid = extension.id;
    } else if (extension.uri == RtpExtension::kAbsoluteCaptureTimeUri) {
      ids.abs_capture_time = extension.id;
    }
  }
  return ids;
}

int AudioSendStream::TransportSeqNumId(const AudioSendStream::Config& config) {
  return FindExtensionIds(config.rtp.extensions).transport_sequence_number;
}

void AudioSendStream::ConfigureStream(
    const webrtc::AudioSendStream::Config& new_config,
    bool first_time) {
  RTC_LOG(LS_INFO) << "AudioSendStream::ConfigureStream: "
                   << new_config.ToString();
  UpdateEventLogStreamConfig(event_log_, new_config,
                             first_time ? nullptr : &config_);

  const auto& old_config = config_;

  // Configuration parameters which cannot be changed.
  RTC_DCHECK(first_time ||
             old_config.send_transport == new_config.send_transport);
  RTC_DCHECK(first_time || old_config.rtp.ssrc == new_config.rtp.ssrc);
  if (suspended_rtp_state_ && first_time) {
    rtp_rtcp_module_->SetRtpState(*suspended_rtp_state_);
  }
  if (first_time || old_config.rtp.c_name != new_config.rtp.c_name) {
    channel_send_->SetRTCP_CNAME(new_config.rtp.c_name);
  }

  // Enable the frame encryptor if a new frame encryptor has been provided.
  if (first_time || new_config.frame_encryptor != old_config.frame_encryptor) {
    channel_send_->SetFrameEncryptor(new_config.frame_encryptor);
  }

  if (first_time ||
      new_config.frame_transformer != old_config.frame_transformer) {
    channel_send_->SetEncoderToPacketizerFrameTransformer(
        new_config.frame_transformer);
  }

  if (first_time ||
      new_config.rtp.extmap_allow_mixed != old_config.rtp.extmap_allow_mixed) {
    rtp_rtcp_module_->SetExtmapAllowMixed(new_config.rtp.extmap_allow_mixed);
  }

  const ExtensionIds old_ids = FindExtensionIds(old_config.rtp.extensions);
  const ExtensionIds new_ids = FindExtensionIds(new_config.rtp.extensions);

  // Audio level indication
  if (first_time || new_ids.audio_level != old_ids.audio_level) {
    channel_send_->SetSendAudioLevelIndicationStatus(new_ids.audio_level != 0,
                                                     new_ids.audio_level);
  }

  if (first_time || new_ids.abs_send_time != old_ids.abs_send_time) {
    absl::string_view uri = AbsoluteSendTime::Uri();
    rtp_rtcp_module_->DeregisterSendRtpHeaderExtension(uri);
    if (new_ids.abs_send_time) {
      rtp_rtcp_module_->RegisterRtpHeaderExtension(uri, new_ids.abs_send_time);
    }
  }

  bool transport_seq_num_id_changed =
      new_ids.transport_sequence_number != old_ids.transport_sequence_number;
  if (first_time ||
      (transport_seq_num_id_changed && !allocate_audio_without_feedback_)) {
    if (!first_time) {
      channel_send_->ResetSenderCongestionControlObjects();
    }

    RtcpBandwidthObserver* bandwidth_observer = nullptr;

    if (!allocate_audio_without_feedback_ &&
        new_ids.transport_sequence_number != 0) {
      rtp_rtcp_module_->RegisterRtpHeaderExtension(
          TransportSequenceNumber::Uri(), new_ids.transport_sequence_number);
      // Probing in application limited region is only used in combination with
      // send side congestion control, wich depends on feedback packets which
      // requires transport sequence numbers to be enabled.
      // Optionally request ALR probing but do not override any existing
      // request from other streams.
      if (enable_audio_alr_probing_) {
        rtp_transport_->EnablePeriodicAlrProbing(true);
      }
      bandwidth_observer = rtp_transport_->GetBandwidthObserver();
    }
    channel_send_->RegisterSenderCongestionControlObjects(rtp_transport_,
                                                          bandwidth_observer);
  }
  // MID RTP header extension.
  if ((first_time || new_ids.mid != old_ids.mid ||
       new_config.rtp.mid != old_config.rtp.mid) &&
      new_ids.mid != 0 && !new_config.rtp.mid.empty()) {
    rtp_rtcp_module_->RegisterRtpHeaderExtension(RtpMid::Uri(), new_ids.mid);
    rtp_rtcp_module_->SetMid(new_config.rtp.mid);
  }

  if (first_time || new_ids.abs_capture_time != old_ids.abs_capture_time) {
    absl::string_view uri = AbsoluteCaptureTimeExtension::Uri();
    rtp_rtcp_module_->DeregisterSendRtpHeaderExtension(uri);
    if (new_ids.abs_capture_time) {
      rtp_rtcp_module_->RegisterRtpHeaderExtension(uri,
                                                   new_ids.abs_capture_time);
    }
  }

  if (!ReconfigureSendCodec(new_config)) {
    RTC_LOG(LS_ERROR) << "Failed to set up send codec state.";
  }

  // Set currently known overhead (used in ANA, opus only).
  {
    MutexLock lock(&overhead_per_packet_lock_);
    UpdateOverheadForEncoder();
  }

  channel_send_->CallEncoder([this](AudioEncoder* encoder) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    if (!encoder) {
      return;
    }
    frame_length_range_ = encoder->GetFrameLengthRange();
    UpdateCachedTargetAudioBitrateConstraints();
  });

  if (sending_) {
    ReconfigureBitrateObserver(new_config);
  }

  config_ = new_config;
  if (!first_time) {
    UpdateCachedTargetAudioBitrateConstraints();
  }
}

void AudioSendStream::Start() {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  if (sending_) {
    return;
  }
  if (!config_.has_dscp && config_.min_bitrate_bps != -1 &&
      config_.max_bitrate_bps != -1 &&
      (allocate_audio_without_feedback_ || TransportSeqNumId(config_) != 0)) {
    rtp_transport_->AccountForAudioPacketsInPacedSender(true);
    if (send_side_bwe_with_overhead_)
      rtp_transport_->IncludeOverheadInPacedSender();
    rtp_rtcp_module_->SetAsPartOfAllocation(true);
    ConfigureBitrateObserver();
  } else {
    rtp_rtcp_module_->SetAsPartOfAllocation(false);
  }
  channel_send_->StartSend();
  sending_ = true;
  audio_state()->AddSendingStream(this, encoder_sample_rate_hz_,
                                  encoder_num_channels_);
}

void AudioSendStream::Stop() {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  if (!sending_) {
    return;
  }

  RemoveBitrateObserver();
  channel_send_->StopSend();
  sending_ = false;
  audio_state()->RemoveSendingStream(this);
}

void AudioSendStream::SendAudioData(std::unique_ptr<AudioFrame> audio_frame) {
  RTC_CHECK_RUNS_SERIALIZED(&audio_capture_race_checker_);
  RTC_DCHECK_GT(audio_frame->sample_rate_hz_, 0);
  TRACE_EVENT0("webrtc", "AudioSendStream::SendAudioData");
  double duration = static_cast<double>(audio_frame->samples_per_channel_) /
                    audio_frame->sample_rate_hz_;
  {
    // Note: SendAudioData() passes the frame further down the pipeline and it
    // may eventually get sent. But this method is invoked even if we are not
    // connected, as long as we have an AudioSendStream (created as a result of
    // an O/A exchange). This means that we are calculating audio levels whether
    // or not we are sending samples.
    // TODO(https://crbug.com/webrtc/10771): All "media-source" related stats
    // should move from send-streams to the local audio sources or tracks; a
    // send-stream should not be required to read the microphone audio levels.
    MutexLock lock(&audio_level_lock_);
    audio_level_.ComputeLevel(*audio_frame, duration);
  }
  channel_send_->ProcessAndEncodeAudio(std::move(audio_frame));
}

bool AudioSendStream::SendTelephoneEvent(int payload_type,
                                         int payload_frequency,
                                         int event,
                                         int duration_ms) {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  channel_send_->SetSendTelephoneEventPayloadType(payload_type,
                                                  payload_frequency);
  return channel_send_->SendTelephoneEventOutband(event, duration_ms);
}

void AudioSendStream::SetMuted(bool muted) {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  channel_send_->SetInputMute(muted);
}

webrtc::AudioSendStream::Stats AudioSendStream::GetStats() const {
  return GetStats(true);
}

webrtc::AudioSendStream::Stats AudioSendStream::GetStats(
    bool has_remote_tracks) const {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  webrtc::AudioSendStream::Stats stats;
  stats.local_ssrc = config_.rtp.ssrc;
  stats.target_bitrate_bps = channel_send_->GetTargetBitrate();

  webrtc::CallSendStatistics call_stats = channel_send_->GetRTCPStatistics();
  stats.payload_bytes_sent = call_stats.payload_bytes_sent;
  stats.header_and_padding_bytes_sent =
      call_stats.header_and_padding_bytes_sent;
  stats.retransmitted_bytes_sent = call_stats.retransmitted_bytes_sent;
  stats.packets_sent = call_stats.packetsSent;
  stats.total_packet_send_delay = call_stats.total_packet_send_delay;
  stats.retransmitted_packets_sent = call_stats.retransmitted_packets_sent;
  // RTT isn't known until a RTCP report is received. Until then, VoiceEngine
  // returns 0 to indicate an error value.
  if (call_stats.rttMs > 0) {
    stats.rtt_ms = call_stats.rttMs;
  }
  if (config_.send_codec_spec) {
    const auto& spec = *config_.send_codec_spec;
    stats.codec_name = spec.format.name;
    stats.codec_payload_type = spec.payload_type;

    // Get data from the last remote RTCP report.
    for (const auto& block : channel_send_->GetRemoteRTCPReportBlocks()) {
      // Lookup report for send ssrc only.
      if (block.source_SSRC == stats.local_ssrc) {
        stats.packets_lost = block.cumulative_num_packets_lost;
        stats.fraction_lost = Q8ToFloat(block.fraction_lost);
        // Convert timestamps to milliseconds.
        if (spec.format.clockrate_hz / 1000 > 0) {
          stats.jitter_ms =
              block.interarrival_jitter / (spec.format.clockrate_hz / 1000);
        }
        break;
      }
    }
  }

  {
    MutexLock lock(&audio_level_lock_);
    stats.audio_level = audio_level_.LevelFullRange();
    stats.total_input_energy = audio_level_.TotalEnergy();
    stats.total_input_duration = audio_level_.TotalDuration();
  }

  stats.ana_statistics = channel_send_->GetANAStatistics();

  AudioProcessing* ap = audio_state_->audio_processing();
  if (ap) {
    stats.apm_statistics = ap->GetStatistics(has_remote_tracks);
  }

  stats.report_block_datas = std::move(call_stats.report_block_datas);

  stats.nacks_rcvd = call_stats.nacks_rcvd;

  return stats;
}

void AudioSendStream::DeliverRtcp(const uint8_t* packet, size_t length) {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  channel_send_->ReceivedRTCPPacket(packet, length);

  {
    // Poll if overhead has changed, which it can do if ack triggers us to stop
    // sending mid/rid.
    MutexLock lock(&overhead_per_packet_lock_);
    UpdateOverheadForEncoder();
  }
  UpdateCachedTargetAudioBitrateConstraints();
}

uint32_t AudioSendStream::OnBitrateUpdated(BitrateAllocationUpdate update) {
  RTC_DCHECK_RUN_ON(rtp_transport_queue_);

  // Pick a target bitrate between the constraints. Overrules the allocator if
  // it 1) allocated a bitrate of zero to disable the stream or 2) allocated a
  // higher than max to allow for e.g. extra FEC.
  RTC_DCHECK(cached_constraints_.has_value());
  update.target_bitrate.Clamp(cached_constraints_->min,
                              cached_constraints_->max);
  update.stable_target_bitrate.Clamp(cached_constraints_->min,
                                     cached_constraints_->max);

  channel_send_->OnBitrateAllocation(update);

  // The amount of audio protection is not exposed by the encoder, hence
  // always returning 0.
  return 0;
}

void AudioSendStream::SetTransportOverhead(
    int transport_overhead_per_packet_bytes) {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  {
    MutexLock lock(&overhead_per_packet_lock_);
    transport_overhead_per_packet_bytes_ = transport_overhead_per_packet_bytes;
    UpdateOverheadForEncoder();
  }
  UpdateCachedTargetAudioBitrateConstraints();
}

void AudioSendStream::UpdateOverheadForEncoder() {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  size_t overhead_per_packet_bytes = GetPerPacketOverheadBytes();
  if (overhead_per_packet_ == overhead_per_packet_bytes) {
    return;
  }
  overhead_per_packet_ = overhead_per_packet_bytes;

  channel_send_->CallEncoder([&](AudioEncoder* encoder) {
    encoder->OnReceivedOverhead(overhead_per_packet_bytes);
  });
  if (total_packet_overhead_bytes_ != overhead_per_packet_bytes) {
    total_packet_overhead_bytes_ = overhead_per_packet_bytes;
    if (registered_with_allocator_) {
      ConfigureBitrateObserver();
    }
  }
}

size_t AudioSendStream::TestOnlyGetPerPacketOverheadBytes() const {
  MutexLock lock(&overhead_per_packet_lock_);
  return GetPerPacketOverheadBytes();
}

size_t AudioSendStream::GetPerPacketOverheadBytes() const {
  return transport_overhead_per_packet_bytes_ +
         rtp_rtcp_module_->ExpectedPerPacketOverhead();
}

RtpState AudioSendStream::GetRtpState() const {
  return rtp_rtcp_module_->GetRtpState();
}

const voe::ChannelSendInterface* AudioSendStream::GetChannel() const {
  return channel_send_.get();
}

internal::AudioState* AudioSendStream::audio_state() {
  internal::AudioState* audio_state =
      static_cast<internal::AudioState*>(audio_state_.get());
  RTC_DCHECK(audio_state);
  return audio_state;
}

const internal::AudioState* AudioSendStream::audio_state() const {
  internal::AudioState* audio_state =
      static_cast<internal::AudioState*>(audio_state_.get());
  RTC_DCHECK(audio_state);
  return audio_state;
}

void AudioSendStream::StoreEncoderProperties(int sample_rate_hz,
                                             size_t num_channels) {
  encoder_sample_rate_hz_ = sample_rate_hz;
  encoder_num_channels_ = num_channels;
  if (sending_) {
    // Update AudioState's information about the stream.
    audio_state()->AddSendingStream(this, sample_rate_hz, num_channels);
  }
}

// Apply current codec settings to a single voe::Channel used for sending.
bool AudioSendStream::SetupSendCodec(const Config& new_config) {
  RTC_DCHECK(new_config.send_codec_spec);
  const auto& spec = *new_config.send_codec_spec;

  RTC_DCHECK(new_config.encoder_factory);
  std::unique_ptr<AudioEncoder> encoder =
      new_config.encoder_factory->MakeAudioEncoder(
          spec.payload_type, spec.format, new_config.codec_pair_id);

  if (!encoder) {
    RTC_DLOG(LS_ERROR) << "Unable to create encoder for "
                       << rtc::ToString(spec.format);
    return false;
  }

  // If a bitrate has been specified for the codec, use it over the
  // codec's default.
  if (spec.target_bitrate_bps) {
    encoder->OnReceivedTargetAudioBitrate(*spec.target_bitrate_bps);
  }

  // Enable ANA if configured (currently only used by Opus).
  if (new_config.audio_network_adaptor_config) {
    if (encoder->EnableAudioNetworkAdaptor(
            *new_config.audio_network_adaptor_config, event_log_)) {
      RTC_LOG(LS_INFO) << "Audio network adaptor enabled on SSRC "
                       << new_config.rtp.ssrc;
    } else {
      RTC_LOG(LS_INFO) << "Failed to enable Audio network adaptor on SSRC "
                       << new_config.rtp.ssrc;
    }
  }

  // Wrap the encoder in an AudioEncoderCNG, if VAD is enabled.
  if (spec.cng_payload_type) {
    AudioEncoderCngConfig cng_config;
    cng_config.num_channels = encoder->NumChannels();
    cng_config.payload_type = *spec.cng_payload_type;
    cng_config.speech_encoder = std::move(encoder);
    cng_config.vad_mode = Vad::kVadNormal;
    encoder = CreateComfortNoiseEncoder(std::move(cng_config));

    RegisterCngPayloadType(*spec.cng_payload_type,
                           new_config.send_codec_spec->format.clockrate_hz);
  }

  // Wrap the encoder in a RED encoder, if RED is enabled.
  if (spec.red_payload_type) {
    AudioEncoderCopyRed::Config red_config;
    red_config.payload_type = *spec.red_payload_type;
    red_config.speech_encoder = std::move(encoder);
    encoder = std::make_unique<AudioEncoderCopyRed>(std::move(red_config),
                                                    field_trials_);
  }

  // Set currently known overhead (used in ANA, opus only).
  // If overhead changes later, it will be updated in UpdateOverheadForEncoder.
  {
    MutexLock lock(&overhead_per_packet_lock_);
    size_t overhead = GetPerPacketOverheadBytes();
    if (overhead > 0) {
      encoder->OnReceivedOverhead(overhead);
    }
  }

  StoreEncoderProperties(encoder->SampleRateHz(), encoder->NumChannels());
  channel_send_->SetEncoder(new_config.send_codec_spec->payload_type,
                            std::move(encoder));

  return true;
}

bool AudioSendStream::ReconfigureSendCodec(const Config& new_config) {
  const auto& old_config = config_;

  if (!new_config.send_codec_spec) {
    // We cannot de-configure a send codec. So we will do nothing.
    // By design, the send codec should have not been configured.
    RTC_DCHECK(!old_config.send_codec_spec);
    return true;
  }

  if (new_config.send_codec_spec == old_config.send_codec_spec &&
      new_config.audio_network_adaptor_config ==
          old_config.audio_network_adaptor_config) {
    return true;
  }

  // If we have no encoder, or the format or payload type's changed, create a
  // new encoder.
  if (!old_config.send_codec_spec ||
      new_config.send_codec_spec->format !=
          old_config.send_codec_spec->format ||
      new_config.send_codec_spec->payload_type !=
          old_config.send_codec_spec->payload_type ||
      new_config.send_codec_spec->red_payload_type !=
          old_config.send_codec_spec->red_payload_type) {
    return SetupSendCodec(new_config);
  }

  const absl::optional<int>& new_target_bitrate_bps =
      new_config.send_codec_spec->target_bitrate_bps;
  // If a bitrate has been specified for the codec, use it over the
  // codec's default.
  if (new_target_bitrate_bps &&
      new_target_bitrate_bps !=
          old_config.send_codec_spec->target_bitrate_bps) {
    channel_send_->CallEncoder([&](AudioEncoder* encoder) {
      encoder->OnReceivedTargetAudioBitrate(*new_target_bitrate_bps);
    });
  }

  ReconfigureANA(new_config);
  ReconfigureCNG(new_config);

  return true;
}

void AudioSendStream::ReconfigureANA(const Config& new_config) {
  if (new_config.audio_network_adaptor_config ==
      config_.audio_network_adaptor_config) {
    return;
  }
  if (new_config.audio_network_adaptor_config) {
    // This lock needs to be acquired before CallEncoder, since it aquires
    // another lock and we need to maintain the same order at all call sites to
    // avoid deadlock.
    MutexLock lock(&overhead_per_packet_lock_);
    size_t overhead = GetPerPacketOverheadBytes();
    channel_send_->CallEncoder([&](AudioEncoder* encoder) {
      if (encoder->EnableAudioNetworkAdaptor(
              *new_config.audio_network_adaptor_config, event_log_)) {
        RTC_LOG(LS_INFO) << "Audio network adaptor enabled on SSRC "
                         << new_config.rtp.ssrc;
        if (overhead > 0) {
          encoder->OnReceivedOverhead(overhead);
        }
      } else {
        RTC_LOG(LS_INFO) << "Failed to enable Audio network adaptor on SSRC "
                         << new_config.rtp.ssrc;
      }
    });
  } else {
    channel_send_->CallEncoder(
        [&](AudioEncoder* encoder) { encoder->DisableAudioNetworkAdaptor(); });
    RTC_LOG(LS_INFO) << "Audio network adaptor disabled on SSRC "
                     << new_config.rtp.ssrc;
  }
}

void AudioSendStream::ReconfigureCNG(const Config& new_config) {
  if (new_config.send_codec_spec->cng_payload_type ==
      config_.send_codec_spec->cng_payload_type) {
    return;
  }

  // Register the CNG payload type if it's been added, don't do anything if CNG
  // is removed. Payload types must not be redefined.
  if (new_config.send_codec_spec->cng_payload_type) {
    RegisterCngPayloadType(*new_config.send_codec_spec->cng_payload_type,
                           new_config.send_codec_spec->format.clockrate_hz);
  }

  // Wrap or unwrap the encoder in an AudioEncoderCNG.
  channel_send_->ModifyEncoder([&](std::unique_ptr<AudioEncoder>* encoder_ptr) {
    std::unique_ptr<AudioEncoder> old_encoder(std::move(*encoder_ptr));
    auto sub_encoders = old_encoder->ReclaimContainedEncoders();
    if (!sub_encoders.empty()) {
      // Replace enc with its sub encoder. We need to put the sub
      // encoder in a temporary first, since otherwise the old value
      // of enc would be destroyed before the new value got assigned,
      // which would be bad since the new value is a part of the old
      // value.
      auto tmp = std::move(sub_encoders[0]);
      old_encoder = std::move(tmp);
    }
    if (new_config.send_codec_spec->cng_payload_type) {
      AudioEncoderCngConfig config;
      config.speech_encoder = std::move(old_encoder);
      config.num_channels = config.speech_encoder->NumChannels();
      config.payload_type = *new_config.send_codec_spec->cng_payload_type;
      config.vad_mode = Vad::kVadNormal;
      *encoder_ptr = CreateComfortNoiseEncoder(std::move(config));
    } else {
      *encoder_ptr = std::move(old_encoder);
    }
  });
}

void AudioSendStream::ReconfigureBitrateObserver(
    const webrtc::AudioSendStream::Config& new_config) {
  // Since the Config's default is for both of these to be -1, this test will
  // allow us to configure the bitrate observer if the new config has bitrate
  // limits set, but would only have us call RemoveBitrateObserver if we were
  // previously configured with bitrate limits.
  if (config_.min_bitrate_bps == new_config.min_bitrate_bps &&
      config_.max_bitrate_bps == new_config.max_bitrate_bps &&
      config_.bitrate_priority == new_config.bitrate_priority &&
      TransportSeqNumId(config_) == TransportSeqNumId(new_config) &&
      config_.audio_network_adaptor_config ==
          new_config.audio_network_adaptor_config) {
    return;
  }

  if (!new_config.has_dscp && new_config.min_bitrate_bps != -1 &&
      new_config.max_bitrate_bps != -1 && TransportSeqNumId(new_config) != 0) {
    rtp_transport_->AccountForAudioPacketsInPacedSender(true);
    if (send_side_bwe_with_overhead_)
      rtp_transport_->IncludeOverheadInPacedSender();
    // We may get a callback immediately as the observer is registered, so
    // make sure the bitrate limits in config_ are up-to-date.
    config_.min_bitrate_bps = new_config.min_bitrate_bps;
    config_.max_bitrate_bps = new_config.max_bitrate_bps;

    config_.bitrate_priority = new_config.bitrate_priority;
    ConfigureBitrateObserver();
    rtp_rtcp_module_->SetAsPartOfAllocation(true);
  } else {
    rtp_transport_->AccountForAudioPacketsInPacedSender(false);
    RemoveBitrateObserver();
    rtp_rtcp_module_->SetAsPartOfAllocation(false);
  }
}

void AudioSendStream::ConfigureBitrateObserver() {
  // This either updates the current observer or adds a new observer.
  // TODO(srte): Add overhead compensation here.
  auto constraints = GetMinMaxBitrateConstraints();
  RTC_DCHECK(constraints.has_value());

  DataRate priority_bitrate = allocation_settings_.priority_bitrate;
  if (send_side_bwe_with_overhead_) {
    if (use_legacy_overhead_calculation_) {
      // OverheadPerPacket = Ipv4(20B) + UDP(8B) + SRTP(10B) + RTP(12)
      constexpr int kOverheadPerPacket = 20 + 8 + 10 + 12;
      const TimeDelta kMinPacketDuration = TimeDelta::Millis(20);
      DataRate max_overhead =
          DataSize::Bytes(kOverheadPerPacket) / kMinPacketDuration;
      priority_bitrate += max_overhead;
    } else {
      RTC_DCHECK(frame_length_range_);
      const DataSize overhead_per_packet =
          DataSize::Bytes(total_packet_overhead_bytes_);
      DataRate min_overhead = overhead_per_packet / frame_length_range_->second;
      priority_bitrate += min_overhead;
    }
  }
  if (allocation_settings_.priority_bitrate_raw)
    priority_bitrate = *allocation_settings_.priority_bitrate_raw;

  rtp_transport_queue_->RunOrPost([this, constraints, priority_bitrate,
                                   config_bitrate_priority =
                                       config_.bitrate_priority] {
    RTC_DCHECK_RUN_ON(rtp_transport_queue_);
    bitrate_allocator_->AddObserver(
        this,
        MediaStreamAllocationConfig{
            constraints->min.bps<uint32_t>(), constraints->max.bps<uint32_t>(),
            0, priority_bitrate.bps(), true,
            allocation_settings_.bitrate_priority.value_or(
                config_bitrate_priority)});
  });
  registered_with_allocator_ = true;
}

void AudioSendStream::RemoveBitrateObserver() {
  registered_with_allocator_ = false;
  rtp_transport_queue_->RunSynchronous([this] {
    RTC_DCHECK_RUN_ON(rtp_transport_queue_);
    bitrate_allocator_->RemoveObserver(this);
  });
}

absl::optional<AudioSendStream::TargetAudioBitrateConstraints>
AudioSendStream::GetMinMaxBitrateConstraints() const {
  if (config_.min_bitrate_bps < 0 || config_.max_bitrate_bps < 0) {
    RTC_LOG(LS_WARNING) << "Config is invalid: min_bitrate_bps="
                        << config_.min_bitrate_bps
                        << "; max_bitrate_bps=" << config_.max_bitrate_bps
                        << "; both expected greater or equal to 0";
    return absl::nullopt;
  }
  TargetAudioBitrateConstraints constraints{
      DataRate::BitsPerSec(config_.min_bitrate_bps),
      DataRate::BitsPerSec(config_.max_bitrate_bps)};

  // If bitrates were explicitly overriden via field trial, use those values.
  if (allocation_settings_.min_bitrate)
    constraints.min = *allocation_settings_.min_bitrate;
  if (allocation_settings_.max_bitrate)
    constraints.max = *allocation_settings_.max_bitrate;

  RTC_DCHECK_GE(constraints.min, DataRate::Zero());
  RTC_DCHECK_GE(constraints.max, DataRate::Zero());
  if (constraints.max < constraints.min) {
    RTC_LOG(LS_WARNING) << "TargetAudioBitrateConstraints::max is less than "
                        << "TargetAudioBitrateConstraints::min";
    return absl::nullopt;
  }
  if (send_side_bwe_with_overhead_) {
    if (use_legacy_overhead_calculation_) {
      // OverheadPerPacket = Ipv4(20B) + UDP(8B) + SRTP(10B) + RTP(12)
      const DataSize kOverheadPerPacket = DataSize::Bytes(20 + 8 + 10 + 12);
      const TimeDelta kMaxFrameLength =
          TimeDelta::Millis(60);  // Based on Opus spec
      const DataRate kMinOverhead = kOverheadPerPacket / kMaxFrameLength;
      constraints.min += kMinOverhead;
      constraints.max += kMinOverhead;
    } else {
      if (!frame_length_range_.has_value()) {
        RTC_LOG(LS_WARNING) << "frame_length_range_ is not set";
        return absl::nullopt;
      }
      const DataSize kOverheadPerPacket =
          DataSize::Bytes(total_packet_overhead_bytes_);
      constraints.min += kOverheadPerPacket / frame_length_range_->second;
      constraints.max += kOverheadPerPacket / frame_length_range_->first;
    }
  }
  return constraints;
}

void AudioSendStream::RegisterCngPayloadType(int payload_type,
                                             int clockrate_hz) {
  channel_send_->RegisterCngPayloadType(payload_type, clockrate_hz);
}

void AudioSendStream::UpdateCachedTargetAudioBitrateConstraints() {
  absl::optional<AudioSendStream::TargetAudioBitrateConstraints>
      new_constraints = GetMinMaxBitrateConstraints();
  if (!new_constraints.has_value()) {
    return;
  }
  rtp_transport_queue_->RunOrPost([this, new_constraints]() {
    RTC_DCHECK_RUN_ON(rtp_transport_queue_);
    cached_constraints_ = new_constraints;
  });
}

}  // namespace internal
}  // namespace webrtc
