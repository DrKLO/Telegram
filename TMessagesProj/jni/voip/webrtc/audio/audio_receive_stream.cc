/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/audio_receive_stream.h"

#include <string>
#include <utility>

#include "absl/memory/memory.h"
#include "api/array_view.h"
#include "api/audio_codecs/audio_format.h"
#include "api/call/audio_sink.h"
#include "api/rtp_parameters.h"
#include "audio/audio_send_stream.h"
#include "audio/audio_state.h"
#include "audio/channel_receive.h"
#include "audio/conversion.h"
#include "call/rtp_config.h"
#include "call/rtp_stream_receiver_controller_interface.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

std::string AudioReceiveStream::Config::Rtp::ToString() const {
  char ss_buf[1024];
  rtc::SimpleStringBuilder ss(ss_buf);
  ss << "{remote_ssrc: " << remote_ssrc;
  ss << ", local_ssrc: " << local_ssrc;
  ss << ", transport_cc: " << (transport_cc ? "on" : "off");
  ss << ", nack: " << nack.ToString();
  ss << ", extensions: [";
  for (size_t i = 0; i < extensions.size(); ++i) {
    ss << extensions[i].ToString();
    if (i != extensions.size() - 1) {
      ss << ", ";
    }
  }
  ss << ']';
  ss << '}';
  return ss.str();
}

std::string AudioReceiveStream::Config::ToString() const {
  char ss_buf[1024];
  rtc::SimpleStringBuilder ss(ss_buf);
  ss << "{rtp: " << rtp.ToString();
  ss << ", rtcp_send_transport: "
     << (rtcp_send_transport ? "(Transport)" : "null");
  if (!sync_group.empty()) {
    ss << ", sync_group: " << sync_group;
  }
  ss << '}';
  return ss.str();
}

namespace internal {
namespace {
std::unique_ptr<voe::ChannelReceiveInterface> CreateChannelReceive(
    Clock* clock,
    webrtc::AudioState* audio_state,
    ProcessThread* module_process_thread,
    NetEqFactory* neteq_factory,
    const webrtc::AudioReceiveStream::Config& config,
    RtcEventLog* event_log) {
  RTC_DCHECK(audio_state);
  internal::AudioState* internal_audio_state =
      static_cast<internal::AudioState*>(audio_state);
  return voe::CreateChannelReceive(
      clock, module_process_thread, neteq_factory,
      internal_audio_state->audio_device_module(), config.rtcp_send_transport,
      event_log, config.rtp.local_ssrc, config.rtp.remote_ssrc,
      config.jitter_buffer_max_packets, config.jitter_buffer_fast_accelerate,
      config.jitter_buffer_min_delay_ms,
      config.jitter_buffer_enable_rtx_handling, config.decoder_factory,
      config.codec_pair_id, config.frame_decryptor, config.crypto_options,
      std::move(config.frame_transformer));
}
}  // namespace

AudioReceiveStream::AudioReceiveStream(
    Clock* clock,
    RtpStreamReceiverControllerInterface* receiver_controller,
    PacketRouter* packet_router,
    ProcessThread* module_process_thread,
    NetEqFactory* neteq_factory,
    const webrtc::AudioReceiveStream::Config& config,
    const rtc::scoped_refptr<webrtc::AudioState>& audio_state,
    webrtc::RtcEventLog* event_log)
    : AudioReceiveStream(clock,
                         receiver_controller,
                         packet_router,
                         config,
                         audio_state,
                         event_log,
                         CreateChannelReceive(clock,
                                              audio_state.get(),
                                              module_process_thread,
                                              neteq_factory,
                                              config,
                                              event_log)) {}

AudioReceiveStream::AudioReceiveStream(
    Clock* clock,
    RtpStreamReceiverControllerInterface* receiver_controller,
    PacketRouter* packet_router,
    const webrtc::AudioReceiveStream::Config& config,
    const rtc::scoped_refptr<webrtc::AudioState>& audio_state,
    webrtc::RtcEventLog* event_log,
    std::unique_ptr<voe::ChannelReceiveInterface> channel_receive)
    : audio_state_(audio_state),
      channel_receive_(std::move(channel_receive)),
      source_tracker_(clock) {
  RTC_LOG(LS_INFO) << "AudioReceiveStream: " << config.rtp.remote_ssrc;
  RTC_DCHECK(config.decoder_factory);
  RTC_DCHECK(config.rtcp_send_transport);
  RTC_DCHECK(audio_state_);
  RTC_DCHECK(channel_receive_);

  module_process_thread_checker_.Detach();

  RTC_DCHECK(receiver_controller);
  RTC_DCHECK(packet_router);
  // Configure bandwidth estimation.
  channel_receive_->RegisterReceiverCongestionControlObjects(packet_router);

  // Register with transport.
  rtp_stream_receiver_ = receiver_controller->CreateReceiver(
      config.rtp.remote_ssrc, channel_receive_.get());
  ConfigureStream(this, config, true);
}

AudioReceiveStream::~AudioReceiveStream() {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  RTC_LOG(LS_INFO) << "~AudioReceiveStream: " << config_.rtp.remote_ssrc;
  Stop();
  channel_receive_->SetAssociatedSendChannel(nullptr);
  channel_receive_->ResetReceiverCongestionControlObjects();
}

void AudioReceiveStream::Reconfigure(
    const webrtc::AudioReceiveStream::Config& config) {
  RTC_DCHECK(worker_thread_checker_.IsCurrent());
  ConfigureStream(this, config, false);
}

void AudioReceiveStream::Start() {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  if (playing_) {
    return;
  }
  channel_receive_->StartPlayout();
  playing_ = true;
  audio_state()->AddReceivingStream(this);
}

void AudioReceiveStream::Stop() {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  if (!playing_) {
    return;
  }
  channel_receive_->StopPlayout();
  playing_ = false;
  audio_state()->RemoveReceivingStream(this);
}

webrtc::AudioReceiveStream::Stats AudioReceiveStream::GetStats(
    bool get_and_clear_legacy_stats) const {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  webrtc::AudioReceiveStream::Stats stats;
  stats.remote_ssrc = config_.rtp.remote_ssrc;

  webrtc::CallReceiveStatistics call_stats =
      channel_receive_->GetRTCPStatistics();
  // TODO(solenberg): Don't return here if we can't get the codec - return the
  //                  stats we *can* get.
  auto receive_codec = channel_receive_->GetReceiveCodec();
  if (!receive_codec) {
    return stats;
  }

  stats.payload_bytes_rcvd = call_stats.payload_bytes_rcvd;
  stats.header_and_padding_bytes_rcvd =
      call_stats.header_and_padding_bytes_rcvd;
  stats.packets_rcvd = call_stats.packetsReceived;
  stats.packets_lost = call_stats.cumulativeLost;
  stats.capture_start_ntp_time_ms = call_stats.capture_start_ntp_time_ms_;
  stats.last_packet_received_timestamp_ms =
      call_stats.last_packet_received_timestamp_ms;
  stats.codec_name = receive_codec->second.name;
  stats.codec_payload_type = receive_codec->first;
  int clockrate_khz = receive_codec->second.clockrate_hz / 1000;
  if (clockrate_khz > 0) {
    stats.jitter_ms = call_stats.jitterSamples / clockrate_khz;
  }
  stats.delay_estimate_ms = channel_receive_->GetDelayEstimate();
  stats.audio_level = channel_receive_->GetSpeechOutputLevelFullRange();
  stats.total_output_energy = channel_receive_->GetTotalOutputEnergy();
  stats.total_output_duration = channel_receive_->GetTotalOutputDuration();
  stats.estimated_playout_ntp_timestamp_ms =
      channel_receive_->GetCurrentEstimatedPlayoutNtpTimestampMs(
          rtc::TimeMillis());

  // Get jitter buffer and total delay (alg + jitter + playout) stats.
  auto ns = channel_receive_->GetNetworkStatistics(get_and_clear_legacy_stats);
  stats.fec_packets_received = ns.fecPacketsReceived;
  stats.fec_packets_discarded = ns.fecPacketsDiscarded;
  stats.jitter_buffer_ms = ns.currentBufferSize;
  stats.jitter_buffer_preferred_ms = ns.preferredBufferSize;
  stats.total_samples_received = ns.totalSamplesReceived;
  stats.concealed_samples = ns.concealedSamples;
  stats.silent_concealed_samples = ns.silentConcealedSamples;
  stats.concealment_events = ns.concealmentEvents;
  stats.jitter_buffer_delay_seconds =
      static_cast<double>(ns.jitterBufferDelayMs) /
      static_cast<double>(rtc::kNumMillisecsPerSec);
  stats.jitter_buffer_emitted_count = ns.jitterBufferEmittedCount;
  stats.jitter_buffer_target_delay_seconds =
      static_cast<double>(ns.jitterBufferTargetDelayMs) /
      static_cast<double>(rtc::kNumMillisecsPerSec);
  stats.inserted_samples_for_deceleration = ns.insertedSamplesForDeceleration;
  stats.removed_samples_for_acceleration = ns.removedSamplesForAcceleration;
  stats.expand_rate = Q14ToFloat(ns.currentExpandRate);
  stats.speech_expand_rate = Q14ToFloat(ns.currentSpeechExpandRate);
  stats.secondary_decoded_rate = Q14ToFloat(ns.currentSecondaryDecodedRate);
  stats.secondary_discarded_rate = Q14ToFloat(ns.currentSecondaryDiscardedRate);
  stats.accelerate_rate = Q14ToFloat(ns.currentAccelerateRate);
  stats.preemptive_expand_rate = Q14ToFloat(ns.currentPreemptiveRate);
  stats.jitter_buffer_flushes = ns.packetBufferFlushes;
  stats.delayed_packet_outage_samples = ns.delayedPacketOutageSamples;
  stats.relative_packet_arrival_delay_seconds =
      static_cast<double>(ns.relativePacketArrivalDelayMs) /
      static_cast<double>(rtc::kNumMillisecsPerSec);
  stats.interruption_count = ns.interruptionCount;
  stats.total_interruption_duration_ms = ns.totalInterruptionDurationMs;

  auto ds = channel_receive_->GetDecodingCallStatistics();
  stats.decoding_calls_to_silence_generator = ds.calls_to_silence_generator;
  stats.decoding_calls_to_neteq = ds.calls_to_neteq;
  stats.decoding_normal = ds.decoded_normal;
  stats.decoding_plc = ds.decoded_neteq_plc;
  stats.decoding_codec_plc = ds.decoded_codec_plc;
  stats.decoding_cng = ds.decoded_cng;
  stats.decoding_plc_cng = ds.decoded_plc_cng;
  stats.decoding_muted_output = ds.decoded_muted_output;

  return stats;
}

void AudioReceiveStream::SetSink(AudioSinkInterface* sink) {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  channel_receive_->SetSink(sink);
}

void AudioReceiveStream::SetGain(float gain) {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  channel_receive_->SetChannelOutputVolumeScaling(gain);
}

bool AudioReceiveStream::SetBaseMinimumPlayoutDelayMs(int delay_ms) {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  return channel_receive_->SetBaseMinimumPlayoutDelayMs(delay_ms);
}

int AudioReceiveStream::GetBaseMinimumPlayoutDelayMs() const {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  return channel_receive_->GetBaseMinimumPlayoutDelayMs();
}

std::vector<RtpSource> AudioReceiveStream::GetSources() const {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  return source_tracker_.GetSources();
}

AudioMixer::Source::AudioFrameInfo AudioReceiveStream::GetAudioFrameWithInfo(
    int sample_rate_hz,
    AudioFrame* audio_frame) {
  AudioMixer::Source::AudioFrameInfo audio_frame_info =
      channel_receive_->GetAudioFrameWithInfo(sample_rate_hz, audio_frame);
  if (audio_frame_info != AudioMixer::Source::AudioFrameInfo::kError) {
    source_tracker_.OnFrameDelivered(audio_frame->packet_infos_);
  }
  return audio_frame_info;
}

int AudioReceiveStream::Ssrc() const {
  return config_.rtp.remote_ssrc;
}

int AudioReceiveStream::PreferredSampleRate() const {
  return channel_receive_->PreferredSampleRate();
}

uint32_t AudioReceiveStream::id() const {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  return config_.rtp.remote_ssrc;
}

absl::optional<Syncable::Info> AudioReceiveStream::GetInfo() const {
  RTC_DCHECK_RUN_ON(&module_process_thread_checker_);
  absl::optional<Syncable::Info> info = channel_receive_->GetSyncInfo();

  if (!info)
    return absl::nullopt;

  info->current_delay_ms = channel_receive_->GetDelayEstimate();
  return info;
}

bool AudioReceiveStream::GetPlayoutRtpTimestamp(uint32_t* rtp_timestamp,
                                                int64_t* time_ms) const {
  // Called on video capture thread.
  return channel_receive_->GetPlayoutRtpTimestamp(rtp_timestamp, time_ms);
}

void AudioReceiveStream::SetEstimatedPlayoutNtpTimestampMs(
    int64_t ntp_timestamp_ms,
    int64_t time_ms) {
  // Called on video capture thread.
  channel_receive_->SetEstimatedPlayoutNtpTimestampMs(ntp_timestamp_ms,
                                                      time_ms);
}

bool AudioReceiveStream::SetMinimumPlayoutDelay(int delay_ms) {
  RTC_DCHECK_RUN_ON(&module_process_thread_checker_);
  return channel_receive_->SetMinimumPlayoutDelay(delay_ms);
}

void AudioReceiveStream::AssociateSendStream(AudioSendStream* send_stream) {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  channel_receive_->SetAssociatedSendChannel(
      send_stream ? send_stream->GetChannel() : nullptr);
  associated_send_stream_ = send_stream;
}

void AudioReceiveStream::DeliverRtcp(const uint8_t* packet, size_t length) {
  // TODO(solenberg): Tests call this function on a network thread, libjingle
  // calls on the worker thread. We should move towards always using a network
  // thread. Then this check can be enabled.
  // RTC_DCHECK(!thread_checker_.IsCurrent());
  channel_receive_->ReceivedRTCPPacket(packet, length);
}

const webrtc::AudioReceiveStream::Config& AudioReceiveStream::config() const {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  return config_;
}

const AudioSendStream* AudioReceiveStream::GetAssociatedSendStreamForTesting()
    const {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  return associated_send_stream_;
}

internal::AudioState* AudioReceiveStream::audio_state() const {
  auto* audio_state = static_cast<internal::AudioState*>(audio_state_.get());
  RTC_DCHECK(audio_state);
  return audio_state;
}

void AudioReceiveStream::ConfigureStream(AudioReceiveStream* stream,
                                         const Config& new_config,
                                         bool first_time) {
  RTC_LOG(LS_INFO) << "AudioReceiveStream::ConfigureStream: "
                   << new_config.ToString();
  RTC_DCHECK(stream);
  const auto& channel_receive = stream->channel_receive_;
  const auto& old_config = stream->config_;

  // Configuration parameters which cannot be changed.
  RTC_DCHECK(first_time ||
             old_config.rtp.remote_ssrc == new_config.rtp.remote_ssrc);
  RTC_DCHECK(first_time ||
             old_config.rtcp_send_transport == new_config.rtcp_send_transport);
  // Decoder factory cannot be changed because it is configured at
  // voe::Channel construction time.
  RTC_DCHECK(first_time ||
             old_config.decoder_factory == new_config.decoder_factory);

  if (!first_time) {
    // SSRC can't be changed mid-stream.
    RTC_DCHECK_EQ(old_config.rtp.local_ssrc, new_config.rtp.local_ssrc);
    RTC_DCHECK_EQ(old_config.rtp.remote_ssrc, new_config.rtp.remote_ssrc);
  }

  // TODO(solenberg): Config NACK history window (which is a packet count),
  // using the actual packet size for the configured codec.
  if (first_time || old_config.rtp.nack.rtp_history_ms !=
                        new_config.rtp.nack.rtp_history_ms) {
    channel_receive->SetNACKStatus(new_config.rtp.nack.rtp_history_ms != 0,
                                   new_config.rtp.nack.rtp_history_ms / 20);
  }
  if (first_time || old_config.decoder_map != new_config.decoder_map) {
    channel_receive->SetReceiveCodecs(new_config.decoder_map);
  }

  if (first_time ||
      old_config.frame_transformer != new_config.frame_transformer) {
    channel_receive->SetDepacketizerToDecoderFrameTransformer(
        new_config.frame_transformer);
  }

  stream->config_ = new_config;
}
}  // namespace internal
}  // namespace webrtc
