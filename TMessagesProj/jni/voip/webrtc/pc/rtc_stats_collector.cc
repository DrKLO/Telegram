/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/rtc_stats_collector.h"

#include <stdint.h>
#include <stdio.h>

#include <cstdint>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#include "absl/functional/bind_front.h"
#include "absl/strings/string_view.h"
#include "api/array_view.h"
#include "api/candidate.h"
#include "api/dtls_transport_interface.h"
#include "api/media_stream_interface.h"
#include "api/media_types.h"
#include "api/rtp_parameters.h"
#include "api/sequence_checker.h"
#include "api/stats/rtc_stats.h"
#include "api/stats/rtcstats_objects.h"
#include "api/units/time_delta.h"
#include "api/video/video_content_type.h"
#include "api/video_codecs/scalability_mode.h"
#include "common_video/include/quality_limitation_reason.h"
#include "media/base/media_channel.h"
#include "media/base/media_channel_impl.h"
#include "modules/audio_device/include/audio_device.h"
#include "modules/audio_processing/include/audio_processing_statistics.h"
#include "modules/rtp_rtcp/include/report_block_data.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "p2p/base/connection_info.h"
#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/port.h"
#include "pc/channel_interface.h"
#include "pc/data_channel_utils.h"
#include "pc/rtc_stats_traversal.h"
#include "pc/rtp_receiver_proxy.h"
#include "pc/rtp_sender_proxy.h"
#include "pc/webrtc_sdp.h"
#include "rtc_base/checks.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/logging.h"
#include "rtc_base/network_constants.h"
#include "rtc_base/rtc_certificate.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

namespace {

const char kDirectionInbound = 'I';
const char kDirectionOutbound = 'O';

static constexpr char kAudioPlayoutSingletonId[] = "AP";

// TODO(https://crbug.com/webrtc/10656): Consider making IDs less predictable.
std::string RTCCertificateIDFromFingerprint(const std::string& fingerprint) {
  return "CF" + fingerprint;
}

// `direction` is either kDirectionInbound or kDirectionOutbound.
std::string RTCCodecStatsIDFromTransportAndCodecParameters(
    const char direction,
    const std::string& transport_id,
    const RtpCodecParameters& codec_params) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << 'C' << direction << transport_id << '_' << codec_params.payload_type;
  // TODO(https://crbug.com/webrtc/14420): If we stop supporting different FMTP
  // lines for the same PT and transport, which should be illegal SDP, then we
  // wouldn't need `fmtp` to be part of the ID here.
  rtc::StringBuilder fmtp;
  if (WriteFmtpParameters(codec_params.parameters, &fmtp)) {
    sb << '_' << fmtp.Release();
  }
  return sb.str();
}

std::string RTCIceCandidatePairStatsIDFromConnectionInfo(
    const cricket::ConnectionInfo& info) {
  char buf[4096];
  rtc::SimpleStringBuilder sb(buf);
  sb << "CP" << info.local_candidate.id() << "_" << info.remote_candidate.id();
  return sb.str();
}

std::string RTCTransportStatsIDFromTransportChannel(
    const std::string& transport_name,
    int channel_component) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << 'T' << transport_name << channel_component;
  return sb.str();
}

std::string RTCInboundRtpStreamStatsIDFromSSRC(const std::string& transport_id,
                                               cricket::MediaType media_type,
                                               uint32_t ssrc) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << 'I' << transport_id
     << (media_type == cricket::MEDIA_TYPE_AUDIO ? 'A' : 'V') << ssrc;
  return sb.str();
}

std::string RTCOutboundRtpStreamStatsIDFromSSRC(const std::string& transport_id,
                                                cricket::MediaType media_type,
                                                uint32_t ssrc) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << 'O' << transport_id
     << (media_type == cricket::MEDIA_TYPE_AUDIO ? 'A' : 'V') << ssrc;
  return sb.str();
}

std::string RTCRemoteInboundRtpStreamStatsIdFromSourceSsrc(
    cricket::MediaType media_type,
    uint32_t source_ssrc) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << "RI" << (media_type == cricket::MEDIA_TYPE_AUDIO ? 'A' : 'V')
     << source_ssrc;
  return sb.str();
}

std::string RTCRemoteOutboundRTPStreamStatsIDFromSSRC(
    cricket::MediaType media_type,
    uint32_t source_ssrc) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << "RO" << (media_type == cricket::MEDIA_TYPE_AUDIO ? 'A' : 'V')
     << source_ssrc;
  return sb.str();
}

std::string RTCMediaSourceStatsIDFromKindAndAttachment(
    cricket::MediaType media_type,
    int attachment_id) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << 'S' << (media_type == cricket::MEDIA_TYPE_AUDIO ? 'A' : 'V')
     << attachment_id;
  return sb.str();
}

const char* DataStateToRTCDataChannelState(
    DataChannelInterface::DataState state) {
  switch (state) {
    case DataChannelInterface::kConnecting:
      return "connecting";
    case DataChannelInterface::kOpen:
      return "open";
    case DataChannelInterface::kClosing:
      return "closing";
    case DataChannelInterface::kClosed:
      return "closed";
    default:
      RTC_DCHECK_NOTREACHED();
      return nullptr;
  }
}

const char* IceCandidatePairStateToRTCStatsIceCandidatePairState(
    cricket::IceCandidatePairState state) {
  switch (state) {
    case cricket::IceCandidatePairState::WAITING:
      return "waiting";
    case cricket::IceCandidatePairState::IN_PROGRESS:
      return "in-progress";
    case cricket::IceCandidatePairState::SUCCEEDED:
      return "succeeded";
    case cricket::IceCandidatePairState::FAILED:
      return "failed";
    default:
      RTC_DCHECK_NOTREACHED();
      return nullptr;
  }
}

const char* IceRoleToRTCIceRole(cricket::IceRole role) {
  switch (role) {
    case cricket::IceRole::ICEROLE_UNKNOWN:
      return "unknown";
    case cricket::IceRole::ICEROLE_CONTROLLED:
      return "controlled";
    case cricket::IceRole::ICEROLE_CONTROLLING:
      return "controlling";
    default:
      RTC_DCHECK_NOTREACHED();
      return nullptr;
  }
}

const char* DtlsTransportStateToRTCDtlsTransportState(
    DtlsTransportState state) {
  switch (state) {
    case DtlsTransportState::kNew:
      return "new";
    case DtlsTransportState::kConnecting:
      return "connecting";
    case DtlsTransportState::kConnected:
      return "connected";
    case DtlsTransportState::kClosed:
      return "closed";
    case DtlsTransportState::kFailed:
      return "failed";
    default:
      RTC_CHECK_NOTREACHED();
      return nullptr;
  }
}

const char* IceTransportStateToRTCIceTransportState(IceTransportState state) {
  switch (state) {
    case IceTransportState::kNew:
      return "new";
    case IceTransportState::kChecking:
      return "checking";
    case IceTransportState::kConnected:
      return "connected";
    case IceTransportState::kCompleted:
      return "completed";
    case IceTransportState::kFailed:
      return "failed";
    case IceTransportState::kDisconnected:
      return "disconnected";
    case IceTransportState::kClosed:
      return "closed";
    default:
      RTC_CHECK_NOTREACHED();
      return nullptr;
  }
}

const char* NetworkTypeToStatsType(rtc::AdapterType type) {
  switch (type) {
    case rtc::ADAPTER_TYPE_CELLULAR:
    case rtc::ADAPTER_TYPE_CELLULAR_2G:
    case rtc::ADAPTER_TYPE_CELLULAR_3G:
    case rtc::ADAPTER_TYPE_CELLULAR_4G:
    case rtc::ADAPTER_TYPE_CELLULAR_5G:
      return "cellular";
    case rtc::ADAPTER_TYPE_ETHERNET:
      return "ethernet";
    case rtc::ADAPTER_TYPE_WIFI:
      return "wifi";
    case rtc::ADAPTER_TYPE_VPN:
      return "vpn";
    case rtc::ADAPTER_TYPE_UNKNOWN:
    case rtc::ADAPTER_TYPE_LOOPBACK:
    case rtc::ADAPTER_TYPE_ANY:
      return "unknown";
  }
  RTC_DCHECK_NOTREACHED();
  return nullptr;
}

absl::string_view NetworkTypeToStatsNetworkAdapterType(rtc::AdapterType type) {
  switch (type) {
    case rtc::ADAPTER_TYPE_CELLULAR:
      return "cellular";
    case rtc::ADAPTER_TYPE_CELLULAR_2G:
      return "cellular2g";
    case rtc::ADAPTER_TYPE_CELLULAR_3G:
      return "cellular3g";
    case rtc::ADAPTER_TYPE_CELLULAR_4G:
      return "cellular4g";
    case rtc::ADAPTER_TYPE_CELLULAR_5G:
      return "cellular5g";
    case rtc::ADAPTER_TYPE_ETHERNET:
      return "ethernet";
    case rtc::ADAPTER_TYPE_WIFI:
      return "wifi";
    case rtc::ADAPTER_TYPE_UNKNOWN:
      return "unknown";
    case rtc::ADAPTER_TYPE_LOOPBACK:
      return "loopback";
    case rtc::ADAPTER_TYPE_ANY:
      return "any";
    case rtc::ADAPTER_TYPE_VPN:
      /* should not be handled here. Vpn is modelled as a bool */
      break;
  }
  RTC_DCHECK_NOTREACHED();
  return {};
}

const char* QualityLimitationReasonToRTCQualityLimitationReason(
    QualityLimitationReason reason) {
  switch (reason) {
    case QualityLimitationReason::kNone:
      return "none";
    case QualityLimitationReason::kCpu:
      return "cpu";
    case QualityLimitationReason::kBandwidth:
      return "bandwidth";
    case QualityLimitationReason::kOther:
      return "other";
  }
  RTC_CHECK_NOTREACHED();
}

std::map<std::string, double>
QualityLimitationDurationToRTCQualityLimitationDuration(
    std::map<QualityLimitationReason, int64_t> durations_ms) {
  std::map<std::string, double> result;
  // The internal duration is defined in milliseconds while the spec defines
  // the value in seconds:
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-qualitylimitationdurations
  for (const auto& elem : durations_ms) {
    result[QualityLimitationReasonToRTCQualityLimitationReason(elem.first)] =
        elem.second / static_cast<double>(rtc::kNumMillisecsPerSec);
  }
  return result;
}

double DoubleAudioLevelFromIntAudioLevel(int audio_level) {
  RTC_DCHECK_GE(audio_level, 0);
  RTC_DCHECK_LE(audio_level, 32767);
  return audio_level / 32767.0;
}

// Gets the `codecId` identified by `transport_id` and `codec_params`. If no
// such `RTCCodecStats` exist yet, create it and add it to `report`.
std::string GetCodecIdAndMaybeCreateCodecStats(
    Timestamp timestamp,
    const char direction,
    const std::string& transport_id,
    const RtpCodecParameters& codec_params,
    RTCStatsReport* report) {
  RTC_DCHECK_GE(codec_params.payload_type, 0);
  RTC_DCHECK_LE(codec_params.payload_type, 127);
  RTC_DCHECK(codec_params.clock_rate);
  uint32_t payload_type = static_cast<uint32_t>(codec_params.payload_type);
  std::string codec_id = RTCCodecStatsIDFromTransportAndCodecParameters(
      direction, transport_id, codec_params);
  if (report->Get(codec_id) != nullptr) {
    // The `RTCCodecStats` already exists.
    return codec_id;
  }
  // Create the `RTCCodecStats` that we want to reference.
  auto codec_stats = std::make_unique<RTCCodecStats>(codec_id, timestamp);
  codec_stats->payload_type = payload_type;
  codec_stats->mime_type = codec_params.mime_type();
  if (codec_params.clock_rate.has_value()) {
    codec_stats->clock_rate = static_cast<uint32_t>(*codec_params.clock_rate);
  }
  if (codec_params.num_channels) {
    codec_stats->channels = *codec_params.num_channels;
  }

  rtc::StringBuilder fmtp;
  if (WriteFmtpParameters(codec_params.parameters, &fmtp)) {
    codec_stats->sdp_fmtp_line = fmtp.Release();
  }
  codec_stats->transport_id = transport_id;
  report->AddStats(std::move(codec_stats));
  return codec_id;
}

// Provides the media independent counters (both audio and video).
void SetInboundRTPStreamStatsFromMediaReceiverInfo(
    const cricket::MediaReceiverInfo& media_receiver_info,
    RTCInboundRtpStreamStats* inbound_stats) {
  RTC_DCHECK(inbound_stats);
  inbound_stats->ssrc = media_receiver_info.ssrc();
  inbound_stats->packets_received =
      static_cast<uint32_t>(media_receiver_info.packets_received);
  inbound_stats->bytes_received =
      static_cast<uint64_t>(media_receiver_info.payload_bytes_received);
  inbound_stats->header_bytes_received = static_cast<uint64_t>(
      media_receiver_info.header_and_padding_bytes_received);
  if (media_receiver_info.retransmitted_bytes_received.has_value()) {
    inbound_stats->retransmitted_bytes_received =
        *media_receiver_info.retransmitted_bytes_received;
  }
  if (media_receiver_info.retransmitted_packets_received.has_value()) {
    inbound_stats->retransmitted_packets_received =
        *media_receiver_info.retransmitted_packets_received;
  }
  inbound_stats->packets_lost =
      static_cast<int32_t>(media_receiver_info.packets_lost);
  inbound_stats->jitter_buffer_delay =
      media_receiver_info.jitter_buffer_delay_seconds;
  inbound_stats->jitter_buffer_target_delay =
      media_receiver_info.jitter_buffer_target_delay_seconds;
  inbound_stats->jitter_buffer_minimum_delay =
      media_receiver_info.jitter_buffer_minimum_delay_seconds;
  inbound_stats->jitter_buffer_emitted_count =
      media_receiver_info.jitter_buffer_emitted_count;
  if (media_receiver_info.nacks_sent.has_value()) {
    inbound_stats->nack_count = *media_receiver_info.nacks_sent;
  }
  if (media_receiver_info.fec_packets_received.has_value()) {
    inbound_stats->fec_packets_received =
        *media_receiver_info.fec_packets_received;
  }
  if (media_receiver_info.fec_packets_discarded.has_value()) {
    inbound_stats->fec_packets_discarded =
        *media_receiver_info.fec_packets_discarded;
  }
  if (media_receiver_info.fec_bytes_received.has_value()) {
    inbound_stats->fec_bytes_received = *media_receiver_info.fec_bytes_received;
  }
}

std::unique_ptr<RTCInboundRtpStreamStats> CreateInboundAudioStreamStats(
    const cricket::VoiceMediaInfo& voice_media_info,
    const cricket::VoiceReceiverInfo& voice_receiver_info,
    const std::string& transport_id,
    const std::string& mid,
    Timestamp timestamp,
    RTCStatsReport* report) {
  auto inbound_audio = std::make_unique<RTCInboundRtpStreamStats>(
      /*id=*/RTCInboundRtpStreamStatsIDFromSSRC(
          transport_id, cricket::MEDIA_TYPE_AUDIO, voice_receiver_info.ssrc()),
      timestamp);
  SetInboundRTPStreamStatsFromMediaReceiverInfo(voice_receiver_info,
                                                inbound_audio.get());
  inbound_audio->transport_id = transport_id;
  inbound_audio->mid = mid;
  inbound_audio->kind = "audio";
  if (voice_receiver_info.codec_payload_type.has_value()) {
    auto codec_param_it = voice_media_info.receive_codecs.find(
        *voice_receiver_info.codec_payload_type);
    RTC_DCHECK(codec_param_it != voice_media_info.receive_codecs.end());
    if (codec_param_it != voice_media_info.receive_codecs.end()) {
      inbound_audio->codec_id = GetCodecIdAndMaybeCreateCodecStats(
          inbound_audio->timestamp(), kDirectionInbound, transport_id,
          codec_param_it->second, report);
    }
  }
  inbound_audio->jitter = static_cast<double>(voice_receiver_info.jitter_ms) /
                          rtc::kNumMillisecsPerSec;
  inbound_audio->total_samples_received =
      voice_receiver_info.total_samples_received;
  inbound_audio->concealed_samples = voice_receiver_info.concealed_samples;
  inbound_audio->silent_concealed_samples =
      voice_receiver_info.silent_concealed_samples;
  inbound_audio->concealment_events = voice_receiver_info.concealment_events;
  inbound_audio->inserted_samples_for_deceleration =
      voice_receiver_info.inserted_samples_for_deceleration;
  inbound_audio->removed_samples_for_acceleration =
      voice_receiver_info.removed_samples_for_acceleration;
  if (voice_receiver_info.audio_level >= 0) {
    inbound_audio->audio_level =
        DoubleAudioLevelFromIntAudioLevel(voice_receiver_info.audio_level);
  }
  inbound_audio->total_audio_energy = voice_receiver_info.total_output_energy;
  inbound_audio->total_samples_duration =
      voice_receiver_info.total_output_duration;
  // `fir_count` and `pli_count` are only valid for video and are
  // purposefully left undefined for audio.
  if (voice_receiver_info.last_packet_received.has_value()) {
    inbound_audio->last_packet_received_timestamp =
        voice_receiver_info.last_packet_received->ms<double>();
  }
  if (voice_receiver_info.estimated_playout_ntp_timestamp_ms.has_value()) {
    // TODO(bugs.webrtc.org/10529): Fix time origin.
    inbound_audio->estimated_playout_timestamp = static_cast<double>(
        *voice_receiver_info.estimated_playout_ntp_timestamp_ms);
  }
  inbound_audio->packets_discarded = voice_receiver_info.packets_discarded;
  inbound_audio->jitter_buffer_flushes =
      voice_receiver_info.jitter_buffer_flushes;
  inbound_audio->delayed_packet_outage_samples =
      voice_receiver_info.delayed_packet_outage_samples;
  inbound_audio->relative_packet_arrival_delay =
      voice_receiver_info.relative_packet_arrival_delay_seconds;
  inbound_audio->interruption_count =
      voice_receiver_info.interruption_count >= 0
          ? voice_receiver_info.interruption_count
          : 0;
  inbound_audio->total_interruption_duration =
      static_cast<double>(voice_receiver_info.total_interruption_duration_ms) /
      rtc::kNumMillisecsPerSec;
  return inbound_audio;
}

std::unique_ptr<RTCAudioPlayoutStats> CreateAudioPlayoutStats(
    const AudioDeviceModule::Stats& audio_device_stats,
    Timestamp timestamp) {
  auto stats = std::make_unique<RTCAudioPlayoutStats>(
      /*id=*/kAudioPlayoutSingletonId, timestamp);
  stats->synthesized_samples_duration =
      audio_device_stats.synthesized_samples_duration_s;
  stats->synthesized_samples_events =
      audio_device_stats.synthesized_samples_events;
  stats->total_samples_count = audio_device_stats.total_samples_count;
  stats->total_samples_duration = audio_device_stats.total_samples_duration_s;
  stats->total_playout_delay = audio_device_stats.total_playout_delay_s;
  return stats;
}

std::unique_ptr<RTCRemoteOutboundRtpStreamStats>
CreateRemoteOutboundAudioStreamStats(
    const cricket::VoiceReceiverInfo& voice_receiver_info,
    const std::string& mid,
    const RTCInboundRtpStreamStats& inbound_audio_stats,
    const std::string& transport_id) {
  if (!voice_receiver_info.last_sender_report_timestamp_ms.has_value()) {
    // Cannot create `RTCRemoteOutboundRtpStreamStats` when the RTCP SR arrival
    // timestamp is not available - i.e., until the first sender report is
    // received.
    return nullptr;
  }
  RTC_DCHECK_GT(voice_receiver_info.sender_reports_reports_count, 0);

  // Create.
  auto stats = std::make_unique<RTCRemoteOutboundRtpStreamStats>(
      /*id=*/RTCRemoteOutboundRTPStreamStatsIDFromSSRC(
          cricket::MEDIA_TYPE_AUDIO, voice_receiver_info.ssrc()),
      Timestamp::Millis(*voice_receiver_info.last_sender_report_timestamp_ms));

  // Populate.
  // - RTCRtpStreamStats.
  stats->ssrc = voice_receiver_info.ssrc();
  stats->kind = "audio";
  stats->transport_id = transport_id;
  if (inbound_audio_stats.codec_id.has_value()) {
    stats->codec_id = *inbound_audio_stats.codec_id;
  }
  // - RTCSentRtpStreamStats.
  stats->packets_sent = voice_receiver_info.sender_reports_packets_sent;
  stats->bytes_sent = voice_receiver_info.sender_reports_bytes_sent;
  // - RTCRemoteOutboundRtpStreamStats.
  stats->local_id = inbound_audio_stats.id();
  // last_sender_report_remote_timestamp_ms is set together with
  // last_sender_report_timestamp_ms.
  RTC_DCHECK(
      voice_receiver_info.last_sender_report_remote_timestamp_ms.has_value());
  stats->remote_timestamp = static_cast<double>(
      *voice_receiver_info.last_sender_report_remote_timestamp_ms);
  stats->reports_sent = voice_receiver_info.sender_reports_reports_count;
  if (voice_receiver_info.round_trip_time.has_value()) {
    stats->round_trip_time =
        voice_receiver_info.round_trip_time->seconds<double>();
  }
  stats->round_trip_time_measurements =
      voice_receiver_info.round_trip_time_measurements;
  stats->total_round_trip_time =
      voice_receiver_info.total_round_trip_time.seconds<double>();

  return stats;
}

std::unique_ptr<RTCInboundRtpStreamStats>
CreateInboundRTPStreamStatsFromVideoReceiverInfo(
    const std::string& transport_id,
    const std::string& mid,
    const cricket::VideoMediaInfo& video_media_info,
    const cricket::VideoReceiverInfo& video_receiver_info,
    Timestamp timestamp,
    RTCStatsReport* report) {
  auto inbound_video = std::make_unique<RTCInboundRtpStreamStats>(
      RTCInboundRtpStreamStatsIDFromSSRC(
          transport_id, cricket::MEDIA_TYPE_VIDEO, video_receiver_info.ssrc()),
      timestamp);
  SetInboundRTPStreamStatsFromMediaReceiverInfo(video_receiver_info,
                                                inbound_video.get());
  inbound_video->transport_id = transport_id;
  inbound_video->mid = mid;
  inbound_video->kind = "video";
  if (video_receiver_info.codec_payload_type.has_value()) {
    auto codec_param_it = video_media_info.receive_codecs.find(
        *video_receiver_info.codec_payload_type);
    RTC_DCHECK(codec_param_it != video_media_info.receive_codecs.end());
    if (codec_param_it != video_media_info.receive_codecs.end()) {
      inbound_video->codec_id = GetCodecIdAndMaybeCreateCodecStats(
          inbound_video->timestamp(), kDirectionInbound, transport_id,
          codec_param_it->second, report);
    }
  }
  inbound_video->jitter = static_cast<double>(video_receiver_info.jitter_ms) /
                          rtc::kNumMillisecsPerSec;
  inbound_video->fir_count =
      static_cast<uint32_t>(video_receiver_info.firs_sent);
  inbound_video->pli_count =
      static_cast<uint32_t>(video_receiver_info.plis_sent);
  inbound_video->frames_received = video_receiver_info.frames_received;
  inbound_video->frames_decoded = video_receiver_info.frames_decoded;
  inbound_video->frames_dropped = video_receiver_info.frames_dropped;
  inbound_video->key_frames_decoded = video_receiver_info.key_frames_decoded;
  if (video_receiver_info.frame_width > 0) {
    inbound_video->frame_width =
        static_cast<uint32_t>(video_receiver_info.frame_width);
  }
  if (video_receiver_info.frame_height > 0) {
    inbound_video->frame_height =
        static_cast<uint32_t>(video_receiver_info.frame_height);
  }
  if (video_receiver_info.framerate_decoded > 0) {
    inbound_video->frames_per_second = video_receiver_info.framerate_decoded;
  }
  if (video_receiver_info.qp_sum.has_value()) {
    inbound_video->qp_sum = *video_receiver_info.qp_sum;
  }
  if (video_receiver_info.timing_frame_info.has_value()) {
    inbound_video->goog_timing_frame_info =
        video_receiver_info.timing_frame_info->ToString();
  }
  inbound_video->total_decode_time =
      video_receiver_info.total_decode_time.seconds<double>();
  inbound_video->total_processing_delay =
      video_receiver_info.total_processing_delay.seconds<double>();
  inbound_video->total_assembly_time =
      video_receiver_info.total_assembly_time.seconds<double>();
  inbound_video->frames_assembled_from_multiple_packets =
      video_receiver_info.frames_assembled_from_multiple_packets;
  inbound_video->total_inter_frame_delay =
      video_receiver_info.total_inter_frame_delay;
  inbound_video->total_squared_inter_frame_delay =
      video_receiver_info.total_squared_inter_frame_delay;
  inbound_video->pause_count = video_receiver_info.pause_count;
  inbound_video->total_pauses_duration =
      static_cast<double>(video_receiver_info.total_pauses_duration_ms) /
      rtc::kNumMillisecsPerSec;
  inbound_video->freeze_count = video_receiver_info.freeze_count;
  inbound_video->total_freezes_duration =
      static_cast<double>(video_receiver_info.total_freezes_duration_ms) /
      rtc::kNumMillisecsPerSec;
  inbound_video->min_playout_delay =
      static_cast<double>(video_receiver_info.min_playout_delay_ms) /
      rtc::kNumMillisecsPerSec;
  if (video_receiver_info.last_packet_received.has_value()) {
    inbound_video->last_packet_received_timestamp =
        video_receiver_info.last_packet_received->ms<double>();
  }
  if (video_receiver_info.estimated_playout_ntp_timestamp_ms.has_value()) {
    // TODO(bugs.webrtc.org/10529): Fix time origin if needed.
    inbound_video->estimated_playout_timestamp = static_cast<double>(
        *video_receiver_info.estimated_playout_ntp_timestamp_ms);
  }
  // TODO(bugs.webrtc.org/10529): When info's `content_info` is optional
  // support the "unspecified" value.
  if (videocontenttypehelpers::IsScreenshare(video_receiver_info.content_type))
    inbound_video->content_type = "screenshare";
  if (video_receiver_info.decoder_implementation_name.has_value()) {
    inbound_video->decoder_implementation =
        *video_receiver_info.decoder_implementation_name;
  }
  if (video_receiver_info.power_efficient_decoder.has_value()) {
    inbound_video->power_efficient_decoder =
        *video_receiver_info.power_efficient_decoder;
  }
  for (const auto& ssrc_group : video_receiver_info.ssrc_groups) {
    if (ssrc_group.semantics == cricket::kFidSsrcGroupSemantics &&
        ssrc_group.ssrcs.size() == 2) {
      inbound_video->rtx_ssrc = ssrc_group.ssrcs[1];
    } else if (ssrc_group.semantics == cricket::kFecFrSsrcGroupSemantics &&
               ssrc_group.ssrcs.size() == 2) {
      // TODO(bugs.webrtc.org/15002): the ssrc-group might be >= 2 with
      // multistream support.
      inbound_video->fec_ssrc = ssrc_group.ssrcs[1];
    }
  }

  return inbound_video;
}

// Provides the media independent counters and information (both audio and
// video).
void SetOutboundRTPStreamStatsFromMediaSenderInfo(
    const cricket::MediaSenderInfo& media_sender_info,
    RTCOutboundRtpStreamStats* outbound_stats) {
  RTC_DCHECK(outbound_stats);
  outbound_stats->ssrc = media_sender_info.ssrc();
  outbound_stats->packets_sent =
      static_cast<uint32_t>(media_sender_info.packets_sent);
  outbound_stats->total_packet_send_delay =
      media_sender_info.total_packet_send_delay.seconds<double>();
  outbound_stats->retransmitted_packets_sent =
      media_sender_info.retransmitted_packets_sent;
  outbound_stats->bytes_sent =
      static_cast<uint64_t>(media_sender_info.payload_bytes_sent);
  outbound_stats->header_bytes_sent =
      static_cast<uint64_t>(media_sender_info.header_and_padding_bytes_sent);
  outbound_stats->retransmitted_bytes_sent =
      media_sender_info.retransmitted_bytes_sent;
  outbound_stats->nack_count = media_sender_info.nacks_received;
  if (media_sender_info.active.has_value()) {
    outbound_stats->active = *media_sender_info.active;
  }
}

std::unique_ptr<RTCOutboundRtpStreamStats>
CreateOutboundRTPStreamStatsFromVoiceSenderInfo(
    const std::string& transport_id,
    const std::string& mid,
    const cricket::VoiceMediaInfo& voice_media_info,
    const cricket::VoiceSenderInfo& voice_sender_info,
    Timestamp timestamp,
    RTCStatsReport* report) {
  auto outbound_audio = std::make_unique<RTCOutboundRtpStreamStats>(
      RTCOutboundRtpStreamStatsIDFromSSRC(
          transport_id, cricket::MEDIA_TYPE_AUDIO, voice_sender_info.ssrc()),
      timestamp);
  SetOutboundRTPStreamStatsFromMediaSenderInfo(voice_sender_info,
                                               outbound_audio.get());
  outbound_audio->transport_id = transport_id;
  outbound_audio->mid = mid;
  outbound_audio->kind = "audio";
  if (voice_sender_info.target_bitrate.has_value() &&
      *voice_sender_info.target_bitrate > 0) {
    outbound_audio->target_bitrate = *voice_sender_info.target_bitrate;
  }
  if (voice_sender_info.codec_payload_type.has_value()) {
    auto codec_param_it = voice_media_info.send_codecs.find(
        *voice_sender_info.codec_payload_type);
    RTC_DCHECK(codec_param_it != voice_media_info.send_codecs.end());
    if (codec_param_it != voice_media_info.send_codecs.end()) {
      outbound_audio->codec_id = GetCodecIdAndMaybeCreateCodecStats(
          outbound_audio->timestamp(), kDirectionOutbound, transport_id,
          codec_param_it->second, report);
    }
  }
  // `fir_count` and `pli_count` are only valid for video and are
  // purposefully left undefined for audio.
  return outbound_audio;
}

std::unique_ptr<RTCOutboundRtpStreamStats>
CreateOutboundRTPStreamStatsFromVideoSenderInfo(
    const std::string& transport_id,
    const std::string& mid,
    const cricket::VideoMediaInfo& video_media_info,
    const cricket::VideoSenderInfo& video_sender_info,
    Timestamp timestamp,
    RTCStatsReport* report) {
  auto outbound_video = std::make_unique<RTCOutboundRtpStreamStats>(
      RTCOutboundRtpStreamStatsIDFromSSRC(
          transport_id, cricket::MEDIA_TYPE_VIDEO, video_sender_info.ssrc()),
      timestamp);
  SetOutboundRTPStreamStatsFromMediaSenderInfo(video_sender_info,
                                               outbound_video.get());
  outbound_video->transport_id = transport_id;
  outbound_video->mid = mid;
  outbound_video->kind = "video";
  if (video_sender_info.codec_payload_type.has_value()) {
    auto codec_param_it = video_media_info.send_codecs.find(
        *video_sender_info.codec_payload_type);
    RTC_DCHECK(codec_param_it != video_media_info.send_codecs.end());
    if (codec_param_it != video_media_info.send_codecs.end()) {
      outbound_video->codec_id = GetCodecIdAndMaybeCreateCodecStats(
          outbound_video->timestamp(), kDirectionOutbound, transport_id,
          codec_param_it->second, report);
    }
  }
  outbound_video->fir_count =
      static_cast<uint32_t>(video_sender_info.firs_received);
  outbound_video->pli_count =
      static_cast<uint32_t>(video_sender_info.plis_received);
  if (video_sender_info.qp_sum.has_value())
    outbound_video->qp_sum = *video_sender_info.qp_sum;
  if (video_sender_info.target_bitrate.has_value() &&
      *video_sender_info.target_bitrate > 0) {
    outbound_video->target_bitrate = *video_sender_info.target_bitrate;
  }
  outbound_video->frames_encoded = video_sender_info.frames_encoded;
  outbound_video->key_frames_encoded = video_sender_info.key_frames_encoded;
  outbound_video->total_encode_time =
      static_cast<double>(video_sender_info.total_encode_time_ms) /
      rtc::kNumMillisecsPerSec;
  outbound_video->total_encoded_bytes_target =
      video_sender_info.total_encoded_bytes_target;
  if (video_sender_info.send_frame_width > 0) {
    outbound_video->frame_width =
        static_cast<uint32_t>(video_sender_info.send_frame_width);
  }
  if (video_sender_info.send_frame_height > 0) {
    outbound_video->frame_height =
        static_cast<uint32_t>(video_sender_info.send_frame_height);
  }
  if (video_sender_info.framerate_sent > 0) {
    outbound_video->frames_per_second = video_sender_info.framerate_sent;
  }
  outbound_video->frames_sent = video_sender_info.frames_sent;
  outbound_video->huge_frames_sent = video_sender_info.huge_frames_sent;
  outbound_video->quality_limitation_reason =
      QualityLimitationReasonToRTCQualityLimitationReason(
          video_sender_info.quality_limitation_reason);
  outbound_video->quality_limitation_durations =
      QualityLimitationDurationToRTCQualityLimitationDuration(
          video_sender_info.quality_limitation_durations_ms);
  outbound_video->quality_limitation_resolution_changes =
      video_sender_info.quality_limitation_resolution_changes;
  // TODO(https://crbug.com/webrtc/10529): When info's `content_info` is
  // optional, support the "unspecified" value.
  if (videocontenttypehelpers::IsScreenshare(video_sender_info.content_type))
    outbound_video->content_type = "screenshare";
  if (video_sender_info.encoder_implementation_name.has_value()) {
    outbound_video->encoder_implementation =
        *video_sender_info.encoder_implementation_name;
  }
  if (video_sender_info.rid.has_value()) {
    outbound_video->rid = *video_sender_info.rid;
  }
  if (video_sender_info.power_efficient_encoder.has_value()) {
    outbound_video->power_efficient_encoder =
        *video_sender_info.power_efficient_encoder;
  }
  if (video_sender_info.scalability_mode) {
    outbound_video->scalability_mode = std::string(
        ScalabilityModeToString(*video_sender_info.scalability_mode));
  }
  for (const auto& ssrc_group : video_sender_info.ssrc_groups) {
    if (ssrc_group.semantics == cricket::kFidSsrcGroupSemantics &&
        ssrc_group.ssrcs.size() == 2 &&
        video_sender_info.ssrc() == ssrc_group.ssrcs[0]) {
      outbound_video->rtx_ssrc = ssrc_group.ssrcs[1];
    }
  }
  return outbound_video;
}

std::unique_ptr<RTCRemoteInboundRtpStreamStats>
ProduceRemoteInboundRtpStreamStatsFromReportBlockData(
    const std::string& transport_id,
    const ReportBlockData& report_block,
    cricket::MediaType media_type,
    const std::map<std::string, RTCOutboundRtpStreamStats*>& outbound_rtps,
    const RTCStatsReport& report) {
  // RTCStats' timestamp generally refers to when the metric was sampled, but
  // for "remote-[outbound/inbound]-rtp" it refers to the local time when the
  // Report Block was received.
  auto remote_inbound = std::make_unique<RTCRemoteInboundRtpStreamStats>(
      RTCRemoteInboundRtpStreamStatsIdFromSourceSsrc(
          media_type, report_block.source_ssrc()),
      report_block.report_block_timestamp_utc());
  remote_inbound->ssrc = report_block.source_ssrc();
  remote_inbound->kind =
      media_type == cricket::MEDIA_TYPE_AUDIO ? "audio" : "video";
  remote_inbound->packets_lost = report_block.cumulative_lost();
  remote_inbound->fraction_lost = report_block.fraction_lost();
  if (report_block.num_rtts() > 0) {
    remote_inbound->round_trip_time = report_block.last_rtt().seconds<double>();
  }
  remote_inbound->total_round_trip_time =
      report_block.sum_rtts().seconds<double>();
  remote_inbound->round_trip_time_measurements = report_block.num_rtts();

  std::string local_id = RTCOutboundRtpStreamStatsIDFromSSRC(
      transport_id, media_type, report_block.source_ssrc());
  // Look up local stat from `outbound_rtps` where the pointers are non-const.
  auto local_id_it = outbound_rtps.find(local_id);
  if (local_id_it != outbound_rtps.end()) {
    remote_inbound->local_id = local_id;
    auto& outbound_rtp = *local_id_it->second;
    outbound_rtp.remote_id = remote_inbound->id();
    // The RTP/RTCP transport is obtained from the
    // RTCOutboundRtpStreamStats's transport.
    const auto* transport_from_id = report.Get(transport_id);
    if (transport_from_id) {
      const auto& transport = transport_from_id->cast_to<RTCTransportStats>();
      // If RTP and RTCP are not multiplexed, there is a separate RTCP
      // transport paired with the RTP transport, otherwise the same
      // transport is used for RTCP and RTP.
      remote_inbound->transport_id =
          transport.rtcp_transport_stats_id.has_value()
              ? *transport.rtcp_transport_stats_id
              : *outbound_rtp.transport_id;
    }
    // We're assuming the same codec is used on both ends. However if the
    // codec is switched out on the fly we may have received a Report Block
    // based on the previous codec and there is no way to tell which point in
    // time the codec changed for the remote end.
    const auto* codec_from_id = outbound_rtp.codec_id.has_value()
                                    ? report.Get(*outbound_rtp.codec_id)
                                    : nullptr;
    if (codec_from_id) {
      remote_inbound->codec_id = *outbound_rtp.codec_id;
      const auto& codec = codec_from_id->cast_to<RTCCodecStats>();
      if (codec.clock_rate.has_value()) {
        remote_inbound->jitter =
            report_block.jitter(*codec.clock_rate).seconds<double>();
      }
    }
  }
  return remote_inbound;
}

void ProduceCertificateStatsFromSSLCertificateStats(
    Timestamp timestamp,
    const rtc::SSLCertificateStats& certificate_stats,
    RTCStatsReport* report) {
  RTCCertificateStats* prev_certificate_stats = nullptr;
  for (const rtc::SSLCertificateStats* s = &certificate_stats; s;
       s = s->issuer.get()) {
    std::string certificate_stats_id =
        RTCCertificateIDFromFingerprint(s->fingerprint);
    // It is possible for the same certificate to show up multiple times, e.g.
    // if local and remote side use the same certificate in a loopback call.
    // If the report already contains stats for this certificate, skip it.
    if (report->Get(certificate_stats_id)) {
      RTC_DCHECK_EQ(s, &certificate_stats);
      break;
    }
    RTCCertificateStats* certificate_stats =
        new RTCCertificateStats(certificate_stats_id, timestamp);
    certificate_stats->fingerprint = s->fingerprint;
    certificate_stats->fingerprint_algorithm = s->fingerprint_algorithm;
    certificate_stats->base64_certificate = s->base64_certificate;
    if (prev_certificate_stats)
      prev_certificate_stats->issuer_certificate_id = certificate_stats->id();
    report->AddStats(std::unique_ptr<RTCCertificateStats>(certificate_stats));
    prev_certificate_stats = certificate_stats;
  }
}

const std::string& ProduceIceCandidateStats(Timestamp timestamp,
                                            const cricket::Candidate& candidate,
                                            bool is_local,
                                            const std::string& transport_id,
                                            RTCStatsReport* report) {
  std::string id = "I" + candidate.id();
  const RTCStats* stats = report->Get(id);
  if (!stats) {
    std::unique_ptr<RTCIceCandidateStats> candidate_stats;
    if (is_local) {
      candidate_stats =
          std::make_unique<RTCLocalIceCandidateStats>(std::move(id), timestamp);
    } else {
      candidate_stats = std::make_unique<RTCRemoteIceCandidateStats>(
          std::move(id), timestamp);
    }
    candidate_stats->transport_id = transport_id;
    if (is_local) {
      candidate_stats->network_type =
          NetworkTypeToStatsType(candidate.network_type());
      const std::string& relay_protocol = candidate.relay_protocol();
      const std::string& url = candidate.url();
      if (candidate.is_relay() ||
          (candidate.is_prflx() && !relay_protocol.empty())) {
        RTC_DCHECK(relay_protocol.compare("udp") == 0 ||
                   relay_protocol.compare("tcp") == 0 ||
                   relay_protocol.compare("tls") == 0);
        candidate_stats->relay_protocol = relay_protocol;
        if (!url.empty()) {
          candidate_stats->url = url;
        }
      } else if (candidate.is_stun()) {
        if (!url.empty()) {
          candidate_stats->url = url;
        }
      }
      if (candidate.network_type() == rtc::ADAPTER_TYPE_VPN) {
        candidate_stats->vpn = true;
        candidate_stats->network_adapter_type =
            std::string(NetworkTypeToStatsNetworkAdapterType(
                candidate.underlying_type_for_vpn()));
      } else {
        candidate_stats->vpn = false;
        candidate_stats->network_adapter_type = std::string(
            NetworkTypeToStatsNetworkAdapterType(candidate.network_type()));
      }
    } else {
      // We don't expect to know the adapter type of remote candidates.
      RTC_DCHECK_EQ(rtc::ADAPTER_TYPE_UNKNOWN, candidate.network_type());
      RTC_DCHECK_EQ(0, candidate.relay_protocol().compare(""));
      RTC_DCHECK_EQ(rtc::ADAPTER_TYPE_UNKNOWN,
                    candidate.underlying_type_for_vpn());
    }
    candidate_stats->ip = candidate.address().ipaddr().ToString();
    candidate_stats->address = candidate.address().ipaddr().ToString();
    candidate_stats->port = static_cast<int32_t>(candidate.address().port());
    candidate_stats->protocol = candidate.protocol();
    candidate_stats->candidate_type = candidate.type_name();
    candidate_stats->priority = static_cast<int32_t>(candidate.priority());
    candidate_stats->foundation = candidate.foundation();
    auto related_address = candidate.related_address();
    if (related_address.port() != 0) {
      candidate_stats->related_address = related_address.ipaddr().ToString();
      candidate_stats->related_port =
          static_cast<int32_t>(related_address.port());
    }
    candidate_stats->username_fragment = candidate.username();
    if (candidate.protocol() == "tcp") {
      candidate_stats->tcp_type = candidate.tcptype();
    }

    stats = candidate_stats.get();
    report->AddStats(std::move(candidate_stats));
  }
  RTC_DCHECK_EQ(stats->type(), is_local ? RTCLocalIceCandidateStats::kType
                                        : RTCRemoteIceCandidateStats::kType);
  return stats->id();
}

template <typename StatsType>
void SetAudioProcessingStats(StatsType* stats,
                             const AudioProcessingStats& apm_stats) {
  if (apm_stats.echo_return_loss.has_value()) {
    stats->echo_return_loss = *apm_stats.echo_return_loss;
  }
  if (apm_stats.echo_return_loss_enhancement.has_value()) {
    stats->echo_return_loss_enhancement =
        *apm_stats.echo_return_loss_enhancement;
  }
}

}  // namespace

rtc::scoped_refptr<RTCStatsReport>
RTCStatsCollector::CreateReportFilteredBySelector(
    bool filter_by_sender_selector,
    rtc::scoped_refptr<const RTCStatsReport> report,
    rtc::scoped_refptr<RtpSenderInternal> sender_selector,
    rtc::scoped_refptr<RtpReceiverInternal> receiver_selector) {
  std::vector<std::string> rtpstream_ids;
  if (filter_by_sender_selector) {
    // Filter mode: RTCStatsCollector::RequestInfo::kSenderSelector
    if (sender_selector) {
      // Find outbound-rtp(s) of the sender using ssrc lookup.
      auto encodings = sender_selector->GetParametersInternal().encodings;
      for (const auto* outbound_rtp :
           report->GetStatsOfType<RTCOutboundRtpStreamStats>()) {
        RTC_DCHECK(outbound_rtp->ssrc.has_value());
        auto it = std::find_if(encodings.begin(), encodings.end(),
                               [ssrc = *outbound_rtp->ssrc](
                                   const RtpEncodingParameters& encoding) {
                                 return encoding.ssrc == ssrc;
                               });
        if (it != encodings.end()) {
          rtpstream_ids.push_back(outbound_rtp->id());
        }
      }
    }
  } else {
    // Filter mode: RTCStatsCollector::RequestInfo::kReceiverSelector
    if (receiver_selector) {
      // Find the inbound-rtp of the receiver using ssrc lookup.
      absl::optional<uint32_t> ssrc;
      worker_thread_->BlockingCall([&] { ssrc = receiver_selector->ssrc(); });
      if (ssrc.has_value()) {
        for (const auto* inbound_rtp :
             report->GetStatsOfType<RTCInboundRtpStreamStats>()) {
          RTC_DCHECK(inbound_rtp->ssrc.has_value());
          if (*inbound_rtp->ssrc == *ssrc) {
            rtpstream_ids.push_back(inbound_rtp->id());
          }
        }
      }
    }
  }
  if (rtpstream_ids.empty())
    return RTCStatsReport::Create(report->timestamp());
  return TakeReferencedStats(report->Copy(), rtpstream_ids);
}

RTCStatsCollector::CertificateStatsPair
RTCStatsCollector::CertificateStatsPair::Copy() const {
  CertificateStatsPair copy;
  copy.local = local ? local->Copy() : nullptr;
  copy.remote = remote ? remote->Copy() : nullptr;
  return copy;
}

RTCStatsCollector::RequestInfo::RequestInfo(
    rtc::scoped_refptr<RTCStatsCollectorCallback> callback)
    : RequestInfo(FilterMode::kAll, std::move(callback), nullptr, nullptr) {}

RTCStatsCollector::RequestInfo::RequestInfo(
    rtc::scoped_refptr<RtpSenderInternal> selector,
    rtc::scoped_refptr<RTCStatsCollectorCallback> callback)
    : RequestInfo(FilterMode::kSenderSelector,
                  std::move(callback),
                  std::move(selector),
                  nullptr) {}

RTCStatsCollector::RequestInfo::RequestInfo(
    rtc::scoped_refptr<RtpReceiverInternal> selector,
    rtc::scoped_refptr<RTCStatsCollectorCallback> callback)
    : RequestInfo(FilterMode::kReceiverSelector,
                  std::move(callback),
                  nullptr,
                  std::move(selector)) {}

RTCStatsCollector::RequestInfo::RequestInfo(
    RTCStatsCollector::RequestInfo::FilterMode filter_mode,
    rtc::scoped_refptr<RTCStatsCollectorCallback> callback,
    rtc::scoped_refptr<RtpSenderInternal> sender_selector,
    rtc::scoped_refptr<RtpReceiverInternal> receiver_selector)
    : filter_mode_(filter_mode),
      callback_(std::move(callback)),
      sender_selector_(std::move(sender_selector)),
      receiver_selector_(std::move(receiver_selector)) {
  RTC_DCHECK(callback_);
  RTC_DCHECK(!sender_selector_ || !receiver_selector_);
}

rtc::scoped_refptr<RTCStatsCollector> RTCStatsCollector::Create(
    PeerConnectionInternal* pc,
    int64_t cache_lifetime_us) {
  return rtc::make_ref_counted<RTCStatsCollector>(pc, cache_lifetime_us);
}

RTCStatsCollector::RTCStatsCollector(PeerConnectionInternal* pc,
                                     int64_t cache_lifetime_us)
    : pc_(pc),
      signaling_thread_(pc->signaling_thread()),
      worker_thread_(pc->worker_thread()),
      network_thread_(pc->network_thread()),
      num_pending_partial_reports_(0),
      partial_report_timestamp_us_(0),
      network_report_event_(true /* manual_reset */,
                            true /* initially_signaled */),
      cache_timestamp_us_(0),
      cache_lifetime_us_(cache_lifetime_us) {
  RTC_DCHECK(pc_);
  RTC_DCHECK(signaling_thread_);
  RTC_DCHECK(worker_thread_);
  RTC_DCHECK(network_thread_);
  RTC_DCHECK_GE(cache_lifetime_us_, 0);
}

RTCStatsCollector::~RTCStatsCollector() {
  RTC_DCHECK_EQ(num_pending_partial_reports_, 0);
}

void RTCStatsCollector::GetStatsReport(
    rtc::scoped_refptr<RTCStatsCollectorCallback> callback) {
  GetStatsReportInternal(RequestInfo(std::move(callback)));
}

void RTCStatsCollector::GetStatsReport(
    rtc::scoped_refptr<RtpSenderInternal> selector,
    rtc::scoped_refptr<RTCStatsCollectorCallback> callback) {
  GetStatsReportInternal(RequestInfo(std::move(selector), std::move(callback)));
}

void RTCStatsCollector::GetStatsReport(
    rtc::scoped_refptr<RtpReceiverInternal> selector,
    rtc::scoped_refptr<RTCStatsCollectorCallback> callback) {
  GetStatsReportInternal(RequestInfo(std::move(selector), std::move(callback)));
}

void RTCStatsCollector::GetStatsReportInternal(
    RTCStatsCollector::RequestInfo request) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  requests_.push_back(std::move(request));

  // "Now" using a monotonically increasing timer.
  int64_t cache_now_us = rtc::TimeMicros();
  if (cached_report_ &&
      cache_now_us - cache_timestamp_us_ <= cache_lifetime_us_) {
    // We have a fresh cached report to deliver. Deliver asynchronously, since
    // the caller may not be expecting a synchronous callback, and it avoids
    // reentrancy problems.
    signaling_thread_->PostTask(
        absl::bind_front(&RTCStatsCollector::DeliverCachedReport,
                         rtc::scoped_refptr<RTCStatsCollector>(this),
                         cached_report_, std::move(requests_)));
  } else if (!num_pending_partial_reports_) {
    // Only start gathering stats if we're not already gathering stats. In the
    // case of already gathering stats, `callback_` will be invoked when there
    // are no more pending partial reports.

    // "Now" using a system clock, relative to the UNIX epoch (Jan 1, 1970,
    // UTC), in microseconds. The system clock could be modified and is not
    // necessarily monotonically increasing.
    Timestamp timestamp = Timestamp::Micros(rtc::TimeUTCMicros());

    num_pending_partial_reports_ = 2;
    partial_report_timestamp_us_ = cache_now_us;

    // Prepare `transceiver_stats_infos_` and `call_stats_` for use in
    // `ProducePartialResultsOnNetworkThread` and
    // `ProducePartialResultsOnSignalingThread`.
    PrepareTransceiverStatsInfosAndCallStats_s_w_n();
    // Don't touch `network_report_` on the signaling thread until
    // ProducePartialResultsOnNetworkThread() has signaled the
    // `network_report_event_`.
    network_report_event_.Reset();
    rtc::scoped_refptr<RTCStatsCollector> collector(this);
    network_thread_->PostTask([collector,
                               sctp_transport_name = pc_->sctp_transport_name(),
                               timestamp]() mutable {
      collector->ProducePartialResultsOnNetworkThread(
          timestamp, std::move(sctp_transport_name));
    });
    ProducePartialResultsOnSignalingThread(timestamp);
  }
}

void RTCStatsCollector::ClearCachedStatsReport() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  cached_report_ = nullptr;
  MutexLock lock(&cached_certificates_mutex_);
  cached_certificates_by_transport_.clear();
}

void RTCStatsCollector::WaitForPendingRequest() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  // If a request is pending, blocks until the `network_report_event_` is
  // signaled and then delivers the result. Otherwise this is a NO-OP.
  MergeNetworkReport_s();
}

void RTCStatsCollector::ProducePartialResultsOnSignalingThread(
    Timestamp timestamp) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  partial_report_ = RTCStatsReport::Create(timestamp);

  ProducePartialResultsOnSignalingThreadImpl(timestamp, partial_report_.get());

  // ProducePartialResultsOnSignalingThread() is running synchronously on the
  // signaling thread, so it is always the first partial result delivered on the
  // signaling thread. The request is not complete until MergeNetworkReport_s()
  // happens; we don't have to do anything here.
  RTC_DCHECK_GT(num_pending_partial_reports_, 1);
  --num_pending_partial_reports_;
}

void RTCStatsCollector::ProducePartialResultsOnSignalingThreadImpl(
    Timestamp timestamp,
    RTCStatsReport* partial_report) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  ProduceMediaSourceStats_s(timestamp, partial_report);
  ProducePeerConnectionStats_s(timestamp, partial_report);
  ProduceAudioPlayoutStats_s(timestamp, partial_report);
}

void RTCStatsCollector::ProducePartialResultsOnNetworkThread(
    Timestamp timestamp,
    absl::optional<std::string> sctp_transport_name) {
  TRACE_EVENT0("webrtc",
               "RTCStatsCollector::ProducePartialResultsOnNetworkThread");
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  // Touching `network_report_` on this thread is safe by this method because
  // `network_report_event_` is reset before this method is invoked.
  network_report_ = RTCStatsReport::Create(timestamp);

  ProduceDataChannelStats_n(timestamp, network_report_.get());

  std::set<std::string> transport_names;
  if (sctp_transport_name) {
    transport_names.emplace(std::move(*sctp_transport_name));
  }

  for (const auto& info : transceiver_stats_infos_) {
    if (info.transport_name)
      transport_names.insert(*info.transport_name);
  }

  std::map<std::string, cricket::TransportStats> transport_stats_by_name =
      pc_->GetTransportStatsByNames(transport_names);
  std::map<std::string, CertificateStatsPair> transport_cert_stats =
      PrepareTransportCertificateStats_n(transport_stats_by_name);

  ProducePartialResultsOnNetworkThreadImpl(timestamp, transport_stats_by_name,
                                           transport_cert_stats,
                                           network_report_.get());

  // Signal that it is now safe to touch `network_report_` on the signaling
  // thread, and post a task to merge it into the final results.
  network_report_event_.Set();
  rtc::scoped_refptr<RTCStatsCollector> collector(this);
  signaling_thread_->PostTask(
      [collector] { collector->MergeNetworkReport_s(); });
}

void RTCStatsCollector::ProducePartialResultsOnNetworkThreadImpl(
    Timestamp timestamp,
    const std::map<std::string, cricket::TransportStats>&
        transport_stats_by_name,
    const std::map<std::string, CertificateStatsPair>& transport_cert_stats,
    RTCStatsReport* partial_report) {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  ProduceCertificateStats_n(timestamp, transport_cert_stats, partial_report);
  ProduceIceCandidateAndPairStats_n(timestamp, transport_stats_by_name,
                                    call_stats_, partial_report);
  ProduceTransportStats_n(timestamp, transport_stats_by_name,
                          transport_cert_stats, partial_report);
  ProduceRTPStreamStats_n(timestamp, transceiver_stats_infos_, partial_report);
}

void RTCStatsCollector::MergeNetworkReport_s() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  // The `network_report_event_` must be signaled for it to be safe to touch
  // `network_report_`. This is normally not blocking, but if
  // WaitForPendingRequest() is called while a request is pending, we might have
  // to wait until the network thread is done touching `network_report_`.
  network_report_event_.Wait(rtc::Event::kForever);
  if (!network_report_) {
    // Normally, MergeNetworkReport_s() is executed because it is posted from
    // the network thread. But if WaitForPendingRequest() is called while a
    // request is pending, an early call to MergeNetworkReport_s() is made,
    // merging the report and setting `network_report_` to null. If so, when the
    // previously posted MergeNetworkReport_s() is later executed, the report is
    // already null and nothing needs to be done here.
    return;
  }
  RTC_DCHECK_GT(num_pending_partial_reports_, 0);
  RTC_DCHECK(partial_report_);
  partial_report_->TakeMembersFrom(network_report_);
  network_report_ = nullptr;
  --num_pending_partial_reports_;
  // `network_report_` is currently the only partial report collected
  // asynchronously, so `num_pending_partial_reports_` must now be 0 and we are
  // ready to deliver the result.
  RTC_DCHECK_EQ(num_pending_partial_reports_, 0);
  cache_timestamp_us_ = partial_report_timestamp_us_;
  cached_report_ = partial_report_;
  partial_report_ = nullptr;
  transceiver_stats_infos_.clear();
  // Trace WebRTC Stats when getStats is called on Javascript.
  // This allows access to WebRTC stats from trace logs. To enable them,
  // select the "webrtc_stats" category when recording traces.
  TRACE_EVENT_INSTANT1("webrtc_stats", "webrtc_stats", "report",
                       cached_report_->ToJson());

  // Deliver report and clear `requests_`.
  std::vector<RequestInfo> requests;
  requests.swap(requests_);
  DeliverCachedReport(cached_report_, std::move(requests));
}

void RTCStatsCollector::DeliverCachedReport(
    rtc::scoped_refptr<const RTCStatsReport> cached_report,
    std::vector<RTCStatsCollector::RequestInfo> requests) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_DCHECK(!requests.empty());
  RTC_DCHECK(cached_report);

  for (const RequestInfo& request : requests) {
    if (request.filter_mode() == RequestInfo::FilterMode::kAll) {
      request.callback()->OnStatsDelivered(cached_report);
    } else {
      bool filter_by_sender_selector;
      rtc::scoped_refptr<RtpSenderInternal> sender_selector;
      rtc::scoped_refptr<RtpReceiverInternal> receiver_selector;
      if (request.filter_mode() == RequestInfo::FilterMode::kSenderSelector) {
        filter_by_sender_selector = true;
        sender_selector = request.sender_selector();
      } else {
        RTC_DCHECK(request.filter_mode() ==
                   RequestInfo::FilterMode::kReceiverSelector);
        filter_by_sender_selector = false;
        receiver_selector = request.receiver_selector();
      }
      request.callback()->OnStatsDelivered(CreateReportFilteredBySelector(
          filter_by_sender_selector, cached_report, sender_selector,
          receiver_selector));
    }
  }
}

void RTCStatsCollector::ProduceCertificateStats_n(
    Timestamp timestamp,
    const std::map<std::string, CertificateStatsPair>& transport_cert_stats,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  for (const auto& transport_cert_stats_pair : transport_cert_stats) {
    if (transport_cert_stats_pair.second.local) {
      ProduceCertificateStatsFromSSLCertificateStats(
          timestamp, *transport_cert_stats_pair.second.local.get(), report);
    }
    if (transport_cert_stats_pair.second.remote) {
      ProduceCertificateStatsFromSSLCertificateStats(
          timestamp, *transport_cert_stats_pair.second.remote.get(), report);
    }
  }
}

void RTCStatsCollector::ProduceDataChannelStats_n(
    Timestamp timestamp,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;
  std::vector<DataChannelStats> data_stats = pc_->GetDataChannelStats();
  for (const auto& stats : data_stats) {
    auto data_channel_stats = std::make_unique<RTCDataChannelStats>(
        "D" + rtc::ToString(stats.internal_id), timestamp);
    data_channel_stats->label = std::move(stats.label);
    data_channel_stats->protocol = std::move(stats.protocol);
    if (stats.id >= 0) {
      // Do not set this value before the DTLS handshake is finished
      // and filter out the magic value -1.
      data_channel_stats->data_channel_identifier = stats.id;
    }
    data_channel_stats->state = DataStateToRTCDataChannelState(stats.state);
    data_channel_stats->messages_sent = stats.messages_sent;
    data_channel_stats->bytes_sent = stats.bytes_sent;
    data_channel_stats->messages_received = stats.messages_received;
    data_channel_stats->bytes_received = stats.bytes_received;
    report->AddStats(std::move(data_channel_stats));
  }
}

void RTCStatsCollector::ProduceIceCandidateAndPairStats_n(
    Timestamp timestamp,
    const std::map<std::string, cricket::TransportStats>&
        transport_stats_by_name,
    const Call::Stats& call_stats,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  for (const auto& entry : transport_stats_by_name) {
    const std::string& transport_name = entry.first;
    const cricket::TransportStats& transport_stats = entry.second;
    for (const auto& channel_stats : transport_stats.channel_stats) {
      std::string transport_id = RTCTransportStatsIDFromTransportChannel(
          transport_name, channel_stats.component);
      for (const auto& info :
           channel_stats.ice_transport_stats.connection_infos) {
        auto candidate_pair_stats = std::make_unique<RTCIceCandidatePairStats>(
            RTCIceCandidatePairStatsIDFromConnectionInfo(info), timestamp);

        candidate_pair_stats->transport_id = transport_id;
        candidate_pair_stats->local_candidate_id = ProduceIceCandidateStats(
            timestamp, info.local_candidate, true, transport_id, report);
        candidate_pair_stats->remote_candidate_id = ProduceIceCandidateStats(
            timestamp, info.remote_candidate, false, transport_id, report);
        candidate_pair_stats->state =
            IceCandidatePairStateToRTCStatsIceCandidatePairState(info.state);
        candidate_pair_stats->priority = info.priority;
        candidate_pair_stats->nominated = info.nominated;
        // TODO(hbos): This writable is different than the spec. It goes to
        // false after a certain amount of time without a response passes.
        // https://crbug.com/633550
        candidate_pair_stats->writable = info.writable;
        // Note that sent_total_packets includes discarded packets but
        // sent_total_bytes does not.
        candidate_pair_stats->packets_sent = static_cast<uint64_t>(
            info.sent_total_packets - info.sent_discarded_packets);
        candidate_pair_stats->packets_discarded_on_send =
            static_cast<uint64_t>(info.sent_discarded_packets);
        candidate_pair_stats->packets_received =
            static_cast<uint64_t>(info.packets_received);
        candidate_pair_stats->bytes_sent =
            static_cast<uint64_t>(info.sent_total_bytes);
        candidate_pair_stats->bytes_discarded_on_send =
            static_cast<uint64_t>(info.sent_discarded_bytes);
        candidate_pair_stats->bytes_received =
            static_cast<uint64_t>(info.recv_total_bytes);
        candidate_pair_stats->total_round_trip_time =
            static_cast<double>(info.total_round_trip_time_ms) /
            rtc::kNumMillisecsPerSec;
        if (info.current_round_trip_time_ms.has_value()) {
          candidate_pair_stats->current_round_trip_time =
              static_cast<double>(*info.current_round_trip_time_ms) /
              rtc::kNumMillisecsPerSec;
        }
        if (info.best_connection) {
          // The bandwidth estimations we have are for the selected candidate
          // pair ("info.best_connection").
          RTC_DCHECK_GE(call_stats.send_bandwidth_bps, 0);
          RTC_DCHECK_GE(call_stats.recv_bandwidth_bps, 0);
          if (call_stats.send_bandwidth_bps > 0) {
            candidate_pair_stats->available_outgoing_bitrate =
                static_cast<double>(call_stats.send_bandwidth_bps);
          }
          if (call_stats.recv_bandwidth_bps > 0) {
            candidate_pair_stats->available_incoming_bitrate =
                static_cast<double>(call_stats.recv_bandwidth_bps);
          }
        }
        candidate_pair_stats->requests_received =
            static_cast<uint64_t>(info.recv_ping_requests);
        candidate_pair_stats->requests_sent =
            static_cast<uint64_t>(info.sent_ping_requests_total);
        candidate_pair_stats->responses_received =
            static_cast<uint64_t>(info.recv_ping_responses);
        candidate_pair_stats->responses_sent =
            static_cast<uint64_t>(info.sent_ping_responses);
        RTC_DCHECK_GE(info.sent_ping_requests_total,
                      info.sent_ping_requests_before_first_response);
        candidate_pair_stats->consent_requests_sent = static_cast<uint64_t>(
            info.sent_ping_requests_total -
            info.sent_ping_requests_before_first_response);

        if (info.last_data_received.has_value()) {
          candidate_pair_stats->last_packet_received_timestamp =
              static_cast<double>(info.last_data_received->ms());
        }
        if (info.last_data_sent) {
          candidate_pair_stats->last_packet_sent_timestamp =
              static_cast<double>(info.last_data_sent->ms());
        }

        report->AddStats(std::move(candidate_pair_stats));
      }

      // Produce local candidate stats. If a transport exists these will already
      // have been produced.
      for (const auto& candidate_stats :
           channel_stats.ice_transport_stats.candidate_stats_list) {
        const auto& candidate = candidate_stats.candidate();
        ProduceIceCandidateStats(timestamp, candidate, true, transport_id,
                                 report);
      }
    }
  }
}

void RTCStatsCollector::ProduceMediaSourceStats_s(
    Timestamp timestamp,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  for (const RtpTransceiverStatsInfo& transceiver_stats_info :
       transceiver_stats_infos_) {
    const auto& track_media_info_map =
        transceiver_stats_info.track_media_info_map;
    for (const auto& sender : transceiver_stats_info.transceiver->senders()) {
      const auto& sender_internal = sender->internal();
      const auto& track = sender_internal->track();
      if (!track)
        continue;
      // TODO(https://crbug.com/webrtc/10771): The same track could be attached
      // to multiple senders which should result in multiple senders referencing
      // the same media-source stats. When all media source related metrics are
      // moved to the track's source (e.g. input frame rate is moved from
      // cricket::VideoSenderInfo to VideoTrackSourceInterface::Stats and audio
      // levels are moved to the corresponding audio track/source object), don't
      // create separate media source stats objects on a per-attachment basis.
      std::unique_ptr<RTCMediaSourceStats> media_source_stats;
      if (track->kind() == MediaStreamTrackInterface::kAudioKind) {
        AudioTrackInterface* audio_track =
            static_cast<AudioTrackInterface*>(track.get());
        auto audio_source_stats = std::make_unique<RTCAudioSourceStats>(
            RTCMediaSourceStatsIDFromKindAndAttachment(
                cricket::MEDIA_TYPE_AUDIO, sender_internal->AttachmentId()),
            timestamp);
        // TODO(https://crbug.com/webrtc/10771): We shouldn't need to have an
        // SSRC assigned (there shouldn't need to exist a send-stream, created
        // by an O/A exchange) in order to read audio media-source stats.
        // TODO(https://crbug.com/webrtc/8694): SSRC 0 shouldn't be a magic
        // value indicating no SSRC.
        if (sender_internal->ssrc() != 0) {
          auto* voice_sender_info =
              track_media_info_map.GetVoiceSenderInfoBySsrc(
                  sender_internal->ssrc());
          if (voice_sender_info) {
            audio_source_stats->audio_level = DoubleAudioLevelFromIntAudioLevel(
                voice_sender_info->audio_level);
            audio_source_stats->total_audio_energy =
                voice_sender_info->total_input_energy;
            audio_source_stats->total_samples_duration =
                voice_sender_info->total_input_duration;
            SetAudioProcessingStats(audio_source_stats.get(),
                                    voice_sender_info->apm_statistics);
          }
        }
        // Audio processor may be attached to either the track or the send
        // stream, so look in both places.
        auto audio_processor(audio_track->GetAudioProcessor());
        if (audio_processor.get()) {
          // The `has_remote_tracks` argument is obsolete; makes no difference
          // if it's set to true or false.
          AudioProcessorInterface::AudioProcessorStatistics ap_stats =
              audio_processor->GetStats(/*has_remote_tracks=*/false);
          SetAudioProcessingStats(audio_source_stats.get(),
                                  ap_stats.apm_statistics);
        }
        media_source_stats = std::move(audio_source_stats);
      } else {
        RTC_DCHECK_EQ(MediaStreamTrackInterface::kVideoKind, track->kind());
        auto video_source_stats = std::make_unique<RTCVideoSourceStats>(
            RTCMediaSourceStatsIDFromKindAndAttachment(
                cricket::MEDIA_TYPE_VIDEO, sender_internal->AttachmentId()),
            timestamp);
        auto* video_track = static_cast<VideoTrackInterface*>(track.get());
        auto* video_source = video_track->GetSource();
        VideoTrackSourceInterface::Stats source_stats;
        if (video_source && video_source->GetStats(&source_stats)) {
          video_source_stats->width = source_stats.input_width;
          video_source_stats->height = source_stats.input_height;
        }
        // TODO(https://crbug.com/webrtc/10771): We shouldn't need to have an
        // SSRC assigned (there shouldn't need to exist a send-stream, created
        // by an O/A exchange) in order to get framesPerSecond.
        // TODO(https://crbug.com/webrtc/8694): SSRC 0 shouldn't be a magic
        // value indicating no SSRC.
        if (sender_internal->ssrc() != 0) {
          auto* video_sender_info =
              track_media_info_map.GetVideoSenderInfoBySsrc(
                  sender_internal->ssrc());
          if (video_sender_info) {
            video_source_stats->frames_per_second =
                video_sender_info->framerate_input;
            video_source_stats->frames = video_sender_info->frames;
          }
        }
        media_source_stats = std::move(video_source_stats);
      }
      media_source_stats->track_identifier = track->id();
      media_source_stats->kind = track->kind();
      report->AddStats(std::move(media_source_stats));
    }
  }
}

void RTCStatsCollector::ProducePeerConnectionStats_s(
    Timestamp timestamp,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  auto stats(std::make_unique<RTCPeerConnectionStats>("P", timestamp));
  stats->data_channels_opened = internal_record_.data_channels_opened;
  stats->data_channels_closed = internal_record_.data_channels_closed;
  report->AddStats(std::move(stats));
}

void RTCStatsCollector::ProduceAudioPlayoutStats_s(
    Timestamp timestamp,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  if (audio_device_stats_) {
    report->AddStats(CreateAudioPlayoutStats(*audio_device_stats_, timestamp));
  }
}

void RTCStatsCollector::ProduceRTPStreamStats_n(
    Timestamp timestamp,
    const std::vector<RtpTransceiverStatsInfo>& transceiver_stats_infos,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  for (const RtpTransceiverStatsInfo& stats : transceiver_stats_infos) {
    if (stats.media_type == cricket::MEDIA_TYPE_AUDIO) {
      ProduceAudioRTPStreamStats_n(timestamp, stats, report);
    } else if (stats.media_type == cricket::MEDIA_TYPE_VIDEO) {
      ProduceVideoRTPStreamStats_n(timestamp, stats, report);
    } else {
      RTC_DCHECK_NOTREACHED();
    }
  }
}

void RTCStatsCollector::ProduceAudioRTPStreamStats_n(
    Timestamp timestamp,
    const RtpTransceiverStatsInfo& stats,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  if (!stats.mid || !stats.transport_name) {
    return;
  }
  RTC_DCHECK(stats.track_media_info_map.voice_media_info().has_value());
  std::string mid = *stats.mid;
  std::string transport_id = RTCTransportStatsIDFromTransportChannel(
      *stats.transport_name, cricket::ICE_CANDIDATE_COMPONENT_RTP);
  // Inbound and remote-outbound.
  // The remote-outbound stats are based on RTCP sender reports sent from the
  // remote endpoint providing metrics about the remote outbound streams.
  for (const cricket::VoiceReceiverInfo& voice_receiver_info :
       stats.track_media_info_map.voice_media_info()->receivers) {
    if (!voice_receiver_info.connected())
      continue;
    // Inbound.
    auto inbound_audio = CreateInboundAudioStreamStats(
        *stats.track_media_info_map.voice_media_info(), voice_receiver_info,
        transport_id, mid, timestamp, report);
    // TODO(hta): This lookup should look for the sender, not the track.
    rtc::scoped_refptr<AudioTrackInterface> audio_track =
        stats.track_media_info_map.GetAudioTrack(voice_receiver_info);
    if (audio_track) {
      inbound_audio->track_identifier = audio_track->id();
    }
    if (audio_device_stats_ && stats.media_type == cricket::MEDIA_TYPE_AUDIO &&
        stats.current_direction &&
        (*stats.current_direction == RtpTransceiverDirection::kSendRecv ||
         *stats.current_direction == RtpTransceiverDirection::kRecvOnly)) {
      inbound_audio->playout_id = kAudioPlayoutSingletonId;
    }
    auto* inbound_audio_ptr = report->TryAddStats(std::move(inbound_audio));
    if (!inbound_audio_ptr) {
      RTC_LOG(LS_ERROR)
          << "Unable to add audio 'inbound-rtp' to report, ID is not unique.";
      continue;
    }
    // Remote-outbound.
    auto remote_outbound_audio = CreateRemoteOutboundAudioStreamStats(
        voice_receiver_info, mid, *inbound_audio_ptr, transport_id);
    // Add stats.
    if (remote_outbound_audio) {
      // When the remote outbound stats are available, the remote ID for the
      // local inbound stats is set.
      auto* remote_outbound_audio_ptr =
          report->TryAddStats(std::move(remote_outbound_audio));
      if (remote_outbound_audio_ptr) {
        inbound_audio_ptr->remote_id = remote_outbound_audio_ptr->id();
      } else {
        RTC_LOG(LS_ERROR) << "Unable to add audio 'remote-outbound-rtp' to "
                          << "report, ID is not unique.";
      }
    }
  }
  // Outbound.
  std::map<std::string, RTCOutboundRtpStreamStats*> audio_outbound_rtps;
  for (const cricket::VoiceSenderInfo& voice_sender_info :
       stats.track_media_info_map.voice_media_info()->senders) {
    if (!voice_sender_info.connected())
      continue;
    auto outbound_audio = CreateOutboundRTPStreamStatsFromVoiceSenderInfo(
        transport_id, mid, *stats.track_media_info_map.voice_media_info(),
        voice_sender_info, timestamp, report);
    rtc::scoped_refptr<AudioTrackInterface> audio_track =
        stats.track_media_info_map.GetAudioTrack(voice_sender_info);
    if (audio_track) {
      int attachment_id =
          stats.track_media_info_map.GetAttachmentIdByTrack(audio_track.get())
              .value();
      outbound_audio->media_source_id =
          RTCMediaSourceStatsIDFromKindAndAttachment(cricket::MEDIA_TYPE_AUDIO,
                                                     attachment_id);
    }
    auto audio_outbound_pair =
        std::make_pair(outbound_audio->id(), outbound_audio.get());
    if (report->TryAddStats(std::move(outbound_audio))) {
      audio_outbound_rtps.insert(std::move(audio_outbound_pair));
    } else {
      RTC_LOG(LS_ERROR)
          << "Unable to add audio 'outbound-rtp' to report, ID is not unique.";
    }
  }
  // Remote-inbound.
  // These are Report Block-based, information sent from the remote endpoint,
  // providing metrics about our Outbound streams. We take advantage of the fact
  // that RTCOutboundRtpStreamStats, RTCCodecStats and RTCTransport have already
  // been added to the report.
  for (const cricket::VoiceSenderInfo& voice_sender_info :
       stats.track_media_info_map.voice_media_info()->senders) {
    for (const auto& report_block_data : voice_sender_info.report_block_datas) {
      report->AddStats(ProduceRemoteInboundRtpStreamStatsFromReportBlockData(
          transport_id, report_block_data, cricket::MEDIA_TYPE_AUDIO,
          audio_outbound_rtps, *report));
    }
  }
}

void RTCStatsCollector::ProduceVideoRTPStreamStats_n(
    Timestamp timestamp,
    const RtpTransceiverStatsInfo& stats,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  if (!stats.mid || !stats.transport_name) {
    return;
  }
  RTC_DCHECK(stats.track_media_info_map.video_media_info().has_value());
  std::string mid = *stats.mid;
  std::string transport_id = RTCTransportStatsIDFromTransportChannel(
      *stats.transport_name, cricket::ICE_CANDIDATE_COMPONENT_RTP);
  // Inbound
  for (const cricket::VideoReceiverInfo& video_receiver_info :
       stats.track_media_info_map.video_media_info()->receivers) {
    if (!video_receiver_info.connected())
      continue;
    auto inbound_video = CreateInboundRTPStreamStatsFromVideoReceiverInfo(
        transport_id, mid, *stats.track_media_info_map.video_media_info(),
        video_receiver_info, timestamp, report);
    rtc::scoped_refptr<VideoTrackInterface> video_track =
        stats.track_media_info_map.GetVideoTrack(video_receiver_info);
    if (video_track) {
      inbound_video->track_identifier = video_track->id();
    }
    if (!report->TryAddStats(std::move(inbound_video))) {
      RTC_LOG(LS_ERROR)
          << "Unable to add video 'inbound-rtp' to report, ID is not unique.";
    }
  }
  // Outbound
  std::map<std::string, RTCOutboundRtpStreamStats*> video_outbound_rtps;
  for (const cricket::VideoSenderInfo& video_sender_info :
       stats.track_media_info_map.video_media_info()->senders) {
    if (!video_sender_info.connected())
      continue;
    auto outbound_video = CreateOutboundRTPStreamStatsFromVideoSenderInfo(
        transport_id, mid, *stats.track_media_info_map.video_media_info(),
        video_sender_info, timestamp, report);
    rtc::scoped_refptr<VideoTrackInterface> video_track =
        stats.track_media_info_map.GetVideoTrack(video_sender_info);
    if (video_track) {
      int attachment_id =
          stats.track_media_info_map.GetAttachmentIdByTrack(video_track.get())
              .value();
      outbound_video->media_source_id =
          RTCMediaSourceStatsIDFromKindAndAttachment(cricket::MEDIA_TYPE_VIDEO,
                                                     attachment_id);
    }
    auto video_outbound_pair =
        std::make_pair(outbound_video->id(), outbound_video.get());
    if (report->TryAddStats(std::move(outbound_video))) {
      video_outbound_rtps.insert(std::move(video_outbound_pair));
    } else {
      RTC_LOG(LS_ERROR)
          << "Unable to add video 'outbound-rtp' to report, ID is not unique.";
    }
  }
  // Remote-inbound
  // These are Report Block-based, information sent from the remote endpoint,
  // providing metrics about our Outbound streams. We take advantage of the fact
  // that RTCOutboundRtpStreamStats, RTCCodecStats and RTCTransport have already
  // been added to the report.
  for (const cricket::VideoSenderInfo& video_sender_info :
       stats.track_media_info_map.video_media_info()->senders) {
    for (const auto& report_block_data : video_sender_info.report_block_datas) {
      report->AddStats(ProduceRemoteInboundRtpStreamStatsFromReportBlockData(
          transport_id, report_block_data, cricket::MEDIA_TYPE_VIDEO,
          video_outbound_rtps, *report));
    }
  }
}

void RTCStatsCollector::ProduceTransportStats_n(
    Timestamp timestamp,
    const std::map<std::string, cricket::TransportStats>&
        transport_stats_by_name,
    const std::map<std::string, CertificateStatsPair>& transport_cert_stats,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  for (const auto& entry : transport_stats_by_name) {
    const std::string& transport_name = entry.first;
    const cricket::TransportStats& transport_stats = entry.second;

    // Get reference to RTCP channel, if it exists.
    std::string rtcp_transport_stats_id;
    for (const cricket::TransportChannelStats& channel_stats :
         transport_stats.channel_stats) {
      if (channel_stats.component == cricket::ICE_CANDIDATE_COMPONENT_RTCP) {
        rtcp_transport_stats_id = RTCTransportStatsIDFromTransportChannel(
            transport_name, channel_stats.component);
        break;
      }
    }

    // Get reference to local and remote certificates of this transport, if they
    // exist.
    const auto& certificate_stats_it =
        transport_cert_stats.find(transport_name);
    std::string local_certificate_id, remote_certificate_id;
    RTC_DCHECK(certificate_stats_it != transport_cert_stats.cend());
    if (certificate_stats_it != transport_cert_stats.cend()) {
      if (certificate_stats_it->second.local) {
        local_certificate_id = RTCCertificateIDFromFingerprint(
            certificate_stats_it->second.local->fingerprint);
      }
      if (certificate_stats_it->second.remote) {
        remote_certificate_id = RTCCertificateIDFromFingerprint(
            certificate_stats_it->second.remote->fingerprint);
      }
    }

    // There is one transport stats for each channel.
    for (const cricket::TransportChannelStats& channel_stats :
         transport_stats.channel_stats) {
      auto transport_stats = std::make_unique<RTCTransportStats>(
          RTCTransportStatsIDFromTransportChannel(transport_name,
                                                  channel_stats.component),
          timestamp);
      transport_stats->packets_sent =
          channel_stats.ice_transport_stats.packets_sent;
      transport_stats->packets_received =
          channel_stats.ice_transport_stats.packets_received;
      transport_stats->bytes_sent =
          channel_stats.ice_transport_stats.bytes_sent;
      transport_stats->bytes_received =
          channel_stats.ice_transport_stats.bytes_received;
      transport_stats->dtls_state =
          DtlsTransportStateToRTCDtlsTransportState(channel_stats.dtls_state);
      transport_stats->selected_candidate_pair_changes =
          channel_stats.ice_transport_stats.selected_candidate_pair_changes;
      transport_stats->ice_role =
          IceRoleToRTCIceRole(channel_stats.ice_transport_stats.ice_role);
      transport_stats->ice_local_username_fragment =
          channel_stats.ice_transport_stats.ice_local_username_fragment;
      transport_stats->ice_state = IceTransportStateToRTCIceTransportState(
          channel_stats.ice_transport_stats.ice_state);
      for (const cricket::ConnectionInfo& info :
           channel_stats.ice_transport_stats.connection_infos) {
        if (info.best_connection) {
          transport_stats->selected_candidate_pair_id =
              RTCIceCandidatePairStatsIDFromConnectionInfo(info);
        }
      }
      if (channel_stats.component != cricket::ICE_CANDIDATE_COMPONENT_RTCP &&
          !rtcp_transport_stats_id.empty()) {
        transport_stats->rtcp_transport_stats_id = rtcp_transport_stats_id;
      }
      if (!local_certificate_id.empty())
        transport_stats->local_certificate_id = local_certificate_id;
      if (!remote_certificate_id.empty())
        transport_stats->remote_certificate_id = remote_certificate_id;
      // Crypto information
      if (channel_stats.ssl_version_bytes) {
        char bytes[5];
        snprintf(bytes, sizeof(bytes), "%04X", channel_stats.ssl_version_bytes);
        transport_stats->tls_version = bytes;
      }

      if (channel_stats.dtls_role) {
        transport_stats->dtls_role =
            *channel_stats.dtls_role == rtc::SSL_CLIENT ? "client" : "server";
      } else {
        transport_stats->dtls_role = "unknown";
      }

      if (channel_stats.ssl_cipher_suite != rtc::kTlsNullWithNullNull &&
          rtc::SSLStreamAdapter::SslCipherSuiteToName(
              channel_stats.ssl_cipher_suite)
              .length()) {
        transport_stats->dtls_cipher =
            rtc::SSLStreamAdapter::SslCipherSuiteToName(
                channel_stats.ssl_cipher_suite);
      }
      if (channel_stats.srtp_crypto_suite != rtc::kSrtpInvalidCryptoSuite &&
          rtc::SrtpCryptoSuiteToName(channel_stats.srtp_crypto_suite)
              .length()) {
        transport_stats->srtp_cipher =
            rtc::SrtpCryptoSuiteToName(channel_stats.srtp_crypto_suite);
      }
      report->AddStats(std::move(transport_stats));
    }
  }
}

std::map<std::string, RTCStatsCollector::CertificateStatsPair>
RTCStatsCollector::PrepareTransportCertificateStats_n(
    const std::map<std::string, cricket::TransportStats>&
        transport_stats_by_name) {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  std::map<std::string, CertificateStatsPair> transport_cert_stats;
  {
    MutexLock lock(&cached_certificates_mutex_);
    // Copy the certificate info from the cache, avoiding expensive
    // rtc::SSLCertChain::GetStats() calls.
    for (const auto& pair : cached_certificates_by_transport_) {
      transport_cert_stats.insert(
          std::make_pair(pair.first, pair.second.Copy()));
    }
  }
  if (transport_cert_stats.empty()) {
    // Collect certificate info.
    for (const auto& entry : transport_stats_by_name) {
      const std::string& transport_name = entry.first;

      CertificateStatsPair certificate_stats_pair;
      rtc::scoped_refptr<rtc::RTCCertificate> local_certificate;
      if (pc_->GetLocalCertificate(transport_name, &local_certificate)) {
        certificate_stats_pair.local =
            local_certificate->GetSSLCertificateChain().GetStats();
      }

      auto remote_cert_chain = pc_->GetRemoteSSLCertChain(transport_name);
      if (remote_cert_chain) {
        certificate_stats_pair.remote = remote_cert_chain->GetStats();
      }

      transport_cert_stats.insert(
          std::make_pair(transport_name, std::move(certificate_stats_pair)));
    }
    // Copy the result into the certificate cache for future reference.
    MutexLock lock(&cached_certificates_mutex_);
    for (const auto& pair : transport_cert_stats) {
      cached_certificates_by_transport_.insert(
          std::make_pair(pair.first, pair.second.Copy()));
    }
  }
  return transport_cert_stats;
}

void RTCStatsCollector::PrepareTransceiverStatsInfosAndCallStats_s_w_n() {
  RTC_DCHECK_RUN_ON(signaling_thread_);

  transceiver_stats_infos_.clear();
  // These are used to invoke GetStats for all the media channels together in
  // one worker thread hop.
  std::map<cricket::VoiceMediaSendChannelInterface*,
           cricket::VoiceMediaSendInfo>
      voice_send_stats;
  std::map<cricket::VideoMediaSendChannelInterface*,
           cricket::VideoMediaSendInfo>
      video_send_stats;
  std::map<cricket::VoiceMediaReceiveChannelInterface*,
           cricket::VoiceMediaReceiveInfo>
      voice_receive_stats;
  std::map<cricket::VideoMediaReceiveChannelInterface*,
           cricket::VideoMediaReceiveInfo>
      video_receive_stats;

  auto transceivers = pc_->GetTransceiversInternal();

  // TODO(tommi): See if we can avoid synchronously blocking the signaling
  // thread while we do this (or avoid the BlockingCall at all).
  network_thread_->BlockingCall([&] {
    rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

    for (const auto& transceiver_proxy : transceivers) {
      RtpTransceiver* transceiver = transceiver_proxy->internal();
      cricket::MediaType media_type = transceiver->media_type();

      // Prepare stats entry. The TrackMediaInfoMap will be filled in after the
      // stats have been fetched on the worker thread.
      transceiver_stats_infos_.emplace_back();
      RtpTransceiverStatsInfo& stats = transceiver_stats_infos_.back();
      stats.transceiver = transceiver;
      stats.media_type = media_type;

      cricket::ChannelInterface* channel = transceiver->channel();
      if (!channel) {
        // The remaining fields require a BaseChannel.
        continue;
      }

      stats.mid = channel->mid();
      stats.transport_name = std::string(channel->transport_name());

      if (media_type == cricket::MEDIA_TYPE_AUDIO) {
        auto voice_send_channel = channel->voice_media_send_channel();
        RTC_DCHECK(voice_send_stats.find(voice_send_channel) ==
                   voice_send_stats.end());
        voice_send_stats.insert(
            std::make_pair(voice_send_channel, cricket::VoiceMediaSendInfo()));

        auto voice_receive_channel = channel->voice_media_receive_channel();
        RTC_DCHECK(voice_receive_stats.find(voice_receive_channel) ==
                   voice_receive_stats.end());
        voice_receive_stats.insert(std::make_pair(
            voice_receive_channel, cricket::VoiceMediaReceiveInfo()));
      } else if (media_type == cricket::MEDIA_TYPE_VIDEO) {
        auto video_send_channel = channel->video_media_send_channel();
        RTC_DCHECK(video_send_stats.find(video_send_channel) ==
                   video_send_stats.end());
        video_send_stats.insert(
            std::make_pair(video_send_channel, cricket::VideoMediaSendInfo()));
        auto video_receive_channel = channel->video_media_receive_channel();
        RTC_DCHECK(video_receive_stats.find(video_receive_channel) ==
                   video_receive_stats.end());
        video_receive_stats.insert(std::make_pair(
            video_receive_channel, cricket::VideoMediaReceiveInfo()));
      } else {
        RTC_DCHECK_NOTREACHED();
      }
    }
  });

  // We jump to the worker thread and call GetStats() on each media channel as
  // well as GetCallStats(). At the same time we construct the
  // TrackMediaInfoMaps, which also needs info from the worker thread. This
  // minimizes the number of thread jumps.
  worker_thread_->BlockingCall([&] {
    rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

    for (auto& pair : voice_send_stats) {
      if (!pair.first->GetStats(&pair.second)) {
        RTC_LOG(LS_WARNING) << "Failed to get voice send stats.";
      }
    }
    for (auto& pair : voice_receive_stats) {
      if (!pair.first->GetStats(&pair.second,
                                /*get_and_clear_legacy_stats=*/false)) {
        RTC_LOG(LS_WARNING) << "Failed to get voice receive stats.";
      }
    }
    for (auto& pair : video_send_stats) {
      if (!pair.first->GetStats(&pair.second)) {
        RTC_LOG(LS_WARNING) << "Failed to get video send stats.";
      }
    }
    for (auto& pair : video_receive_stats) {
      if (!pair.first->GetStats(&pair.second)) {
        RTC_LOG(LS_WARNING) << "Failed to get video receive stats.";
      }
    }

    // Create the TrackMediaInfoMap for each transceiver stats object
    // and keep track of whether we have at least one audio receiver.
    bool has_audio_receiver = false;
    for (auto& stats : transceiver_stats_infos_) {
      auto transceiver = stats.transceiver;
      absl::optional<cricket::VoiceMediaInfo> voice_media_info;
      absl::optional<cricket::VideoMediaInfo> video_media_info;
      auto channel = transceiver->channel();
      if (channel) {
        cricket::MediaType media_type = transceiver->media_type();
        if (media_type == cricket::MEDIA_TYPE_AUDIO) {
          auto voice_send_channel = channel->voice_media_send_channel();
          auto voice_receive_channel = channel->voice_media_receive_channel();
          voice_media_info = cricket::VoiceMediaInfo(
              std::move(voice_send_stats[voice_send_channel]),
              std::move(voice_receive_stats[voice_receive_channel]));
        } else if (media_type == cricket::MEDIA_TYPE_VIDEO) {
          auto video_send_channel = channel->video_media_send_channel();
          auto video_receive_channel = channel->video_media_receive_channel();
          video_media_info = cricket::VideoMediaInfo(
              std::move(video_send_stats[video_send_channel]),
              std::move(video_receive_stats[video_receive_channel]));
        }
      }
      std::vector<rtc::scoped_refptr<RtpSenderInternal>> senders;
      for (const auto& sender : transceiver->senders()) {
        senders.push_back(
            rtc::scoped_refptr<RtpSenderInternal>(sender->internal()));
      }
      std::vector<rtc::scoped_refptr<RtpReceiverInternal>> receivers;
      for (const auto& receiver : transceiver->receivers()) {
        receivers.push_back(
            rtc::scoped_refptr<RtpReceiverInternal>(receiver->internal()));
      }
      stats.track_media_info_map.Initialize(std::move(voice_media_info),
                                            std::move(video_media_info),
                                            senders, receivers);
      if (transceiver->media_type() == cricket::MEDIA_TYPE_AUDIO) {
        has_audio_receiver |= !receivers.empty();
      }
    }

    call_stats_ = pc_->GetCallStats();
    audio_device_stats_ =
        has_audio_receiver ? pc_->GetAudioDeviceStats() : absl::nullopt;
  });

  for (auto& stats : transceiver_stats_infos_) {
    stats.current_direction = stats.transceiver->current_direction();
  }
}

void RTCStatsCollector::OnSctpDataChannelStateChanged(
    int channel_id,
    DataChannelInterface::DataState state) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (state == DataChannelInterface::DataState::kOpen) {
    bool result =
        internal_record_.opened_data_channels.insert(channel_id).second;
    RTC_DCHECK(result);
    ++internal_record_.data_channels_opened;
  } else if (state == DataChannelInterface::DataState::kClosed) {
    // Only channels that have been fully opened (and have increased the
    // `data_channels_opened_` counter) increase the closed counter.
    if (internal_record_.opened_data_channels.erase(channel_id)) {
      ++internal_record_.data_channels_closed;
    }
  }
}

}  // namespace webrtc
