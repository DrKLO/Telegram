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

#include <stdio.h>

#include <algorithm>
#include <cstdint>
#include <map>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "api/array_view.h"
#include "api/candidate.h"
#include "api/media_stream_interface.h"
#include "api/rtp_parameters.h"
#include "api/rtp_receiver_interface.h"
#include "api/rtp_sender_interface.h"
#include "api/sequence_checker.h"
#include "api/stats/rtc_stats.h"
#include "api/stats/rtcstats_objects.h"
#include "api/task_queue/queued_task.h"
#include "api/video/video_content_type.h"
#include "common_video/include/quality_limitation_reason.h"
#include "media/base/media_channel.h"
#include "modules/audio_processing/include/audio_processing_statistics.h"
#include "modules/rtp_rtcp/include/report_block_data.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "p2p/base/connection_info.h"
#include "p2p/base/dtls_transport_internal.h"
#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/port.h"
#include "pc/channel.h"
#include "pc/channel_interface.h"
#include "pc/data_channel_utils.h"
#include "pc/rtc_stats_traversal.h"
#include "pc/webrtc_sdp.h"
#include "rtc_base/checks.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/network_constants.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/rtc_certificate.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

namespace {

// TODO(https://crbug.com/webrtc/10656): Consider making IDs less predictable.
std::string RTCCertificateIDFromFingerprint(const std::string& fingerprint) {
  return "RTCCertificate_" + fingerprint;
}

std::string RTCCodecStatsIDFromMidDirectionAndPayload(const std::string& mid,
                                                      bool inbound,
                                                      uint32_t payload_type) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << "RTCCodec_" << mid << (inbound ? "_Inbound_" : "_Outbound_")
     << payload_type;
  return sb.str();
}

std::string RTCIceCandidatePairStatsIDFromConnectionInfo(
    const cricket::ConnectionInfo& info) {
  char buf[4096];
  rtc::SimpleStringBuilder sb(buf);
  sb << "RTCIceCandidatePair_" << info.local_candidate.id() << "_"
     << info.remote_candidate.id();
  return sb.str();
}

const char kSender[] = "sender";
const char kReceiver[] = "receiver";

std::string RTCMediaStreamTrackStatsIDFromDirectionAndAttachment(
    const char* direction,
    int attachment_id) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << "RTCMediaStreamTrack_" << direction << "_" << attachment_id;
  return sb.str();
}

std::string RTCTransportStatsIDFromTransportChannel(
    const std::string& transport_name,
    int channel_component) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << "RTCTransport_" << transport_name << "_" << channel_component;
  return sb.str();
}

std::string RTCInboundRTPStreamStatsIDFromSSRC(cricket::MediaType media_type,
                                               uint32_t ssrc) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << "RTCInboundRTP"
     << (media_type == cricket::MEDIA_TYPE_AUDIO ? "Audio" : "Video")
     << "Stream_" << ssrc;
  return sb.str();
}

std::string RTCOutboundRTPStreamStatsIDFromSSRC(cricket::MediaType media_type,
                                                uint32_t ssrc) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << "RTCOutboundRTP"
     << (media_type == cricket::MEDIA_TYPE_AUDIO ? "Audio" : "Video")
     << "Stream_" << ssrc;
  return sb.str();
}

std::string RTCRemoteInboundRtpStreamStatsIdFromSourceSsrc(
    cricket::MediaType media_type,
    uint32_t source_ssrc) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << "RTCRemoteInboundRtp"
     << (media_type == cricket::MEDIA_TYPE_AUDIO ? "Audio" : "Video")
     << "Stream_" << source_ssrc;
  return sb.str();
}

std::string RTCRemoteOutboundRTPStreamStatsIDFromSSRC(
    cricket::MediaType media_type,
    uint32_t source_ssrc) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << "RTCRemoteOutboundRTP"
     << (media_type == cricket::MEDIA_TYPE_AUDIO ? "Audio" : "Video")
     << "Stream_" << source_ssrc;
  return sb.str();
}

std::string RTCMediaSourceStatsIDFromKindAndAttachment(
    cricket::MediaType media_type,
    int attachment_id) {
  char buf[1024];
  rtc::SimpleStringBuilder sb(buf);
  sb << "RTC" << (media_type == cricket::MEDIA_TYPE_AUDIO ? "Audio" : "Video")
     << "Source_" << attachment_id;
  return sb.str();
}

const char* CandidateTypeToRTCIceCandidateType(const std::string& type) {
  if (type == cricket::LOCAL_PORT_TYPE)
    return RTCIceCandidateType::kHost;
  if (type == cricket::STUN_PORT_TYPE)
    return RTCIceCandidateType::kSrflx;
  if (type == cricket::PRFLX_PORT_TYPE)
    return RTCIceCandidateType::kPrflx;
  if (type == cricket::RELAY_PORT_TYPE)
    return RTCIceCandidateType::kRelay;
  RTC_NOTREACHED();
  return nullptr;
}

const char* DataStateToRTCDataChannelState(
    DataChannelInterface::DataState state) {
  switch (state) {
    case DataChannelInterface::kConnecting:
      return RTCDataChannelState::kConnecting;
    case DataChannelInterface::kOpen:
      return RTCDataChannelState::kOpen;
    case DataChannelInterface::kClosing:
      return RTCDataChannelState::kClosing;
    case DataChannelInterface::kClosed:
      return RTCDataChannelState::kClosed;
    default:
      RTC_NOTREACHED();
      return nullptr;
  }
}

const char* IceCandidatePairStateToRTCStatsIceCandidatePairState(
    cricket::IceCandidatePairState state) {
  switch (state) {
    case cricket::IceCandidatePairState::WAITING:
      return RTCStatsIceCandidatePairState::kWaiting;
    case cricket::IceCandidatePairState::IN_PROGRESS:
      return RTCStatsIceCandidatePairState::kInProgress;
    case cricket::IceCandidatePairState::SUCCEEDED:
      return RTCStatsIceCandidatePairState::kSucceeded;
    case cricket::IceCandidatePairState::FAILED:
      return RTCStatsIceCandidatePairState::kFailed;
    default:
      RTC_NOTREACHED();
      return nullptr;
  }
}

const char* DtlsTransportStateToRTCDtlsTransportState(
    cricket::DtlsTransportState state) {
  switch (state) {
    case cricket::DTLS_TRANSPORT_NEW:
      return RTCDtlsTransportState::kNew;
    case cricket::DTLS_TRANSPORT_CONNECTING:
      return RTCDtlsTransportState::kConnecting;
    case cricket::DTLS_TRANSPORT_CONNECTED:
      return RTCDtlsTransportState::kConnected;
    case cricket::DTLS_TRANSPORT_CLOSED:
      return RTCDtlsTransportState::kClosed;
    case cricket::DTLS_TRANSPORT_FAILED:
      return RTCDtlsTransportState::kFailed;
    default:
      RTC_NOTREACHED();
      return nullptr;
  }
}

const char* NetworkAdapterTypeToStatsType(rtc::AdapterType type) {
  switch (type) {
    case rtc::ADAPTER_TYPE_CELLULAR:
    case rtc::ADAPTER_TYPE_CELLULAR_2G:
    case rtc::ADAPTER_TYPE_CELLULAR_3G:
    case rtc::ADAPTER_TYPE_CELLULAR_4G:
    case rtc::ADAPTER_TYPE_CELLULAR_5G:
      return RTCNetworkType::kCellular;
    case rtc::ADAPTER_TYPE_ETHERNET:
      return RTCNetworkType::kEthernet;
    case rtc::ADAPTER_TYPE_WIFI:
      return RTCNetworkType::kWifi;
    case rtc::ADAPTER_TYPE_VPN:
      return RTCNetworkType::kVpn;
    case rtc::ADAPTER_TYPE_UNKNOWN:
    case rtc::ADAPTER_TYPE_LOOPBACK:
    case rtc::ADAPTER_TYPE_ANY:
      return RTCNetworkType::kUnknown;
  }
  RTC_NOTREACHED();
  return nullptr;
}

const char* QualityLimitationReasonToRTCQualityLimitationReason(
    QualityLimitationReason reason) {
  switch (reason) {
    case QualityLimitationReason::kNone:
      return RTCQualityLimitationReason::kNone;
    case QualityLimitationReason::kCpu:
      return RTCQualityLimitationReason::kCpu;
    case QualityLimitationReason::kBandwidth:
      return RTCQualityLimitationReason::kBandwidth;
    case QualityLimitationReason::kOther:
      return RTCQualityLimitationReason::kOther;
  }
  RTC_CHECK_NOTREACHED();
}

double DoubleAudioLevelFromIntAudioLevel(int audio_level) {
  RTC_DCHECK_GE(audio_level, 0);
  RTC_DCHECK_LE(audio_level, 32767);
  return audio_level / 32767.0;
}

std::unique_ptr<RTCCodecStats> CodecStatsFromRtpCodecParameters(
    uint64_t timestamp_us,
    const std::string& mid,
    const std::string& transport_id,
    bool inbound,
    const RtpCodecParameters& codec_params) {
  RTC_DCHECK_GE(codec_params.payload_type, 0);
  RTC_DCHECK_LE(codec_params.payload_type, 127);
  RTC_DCHECK(codec_params.clock_rate);
  uint32_t payload_type = static_cast<uint32_t>(codec_params.payload_type);
  std::unique_ptr<RTCCodecStats> codec_stats(new RTCCodecStats(
      RTCCodecStatsIDFromMidDirectionAndPayload(mid, inbound, payload_type),
      timestamp_us));
  codec_stats->payload_type = payload_type;
  codec_stats->mime_type = codec_params.mime_type();
  if (codec_params.clock_rate) {
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
  return codec_stats;
}

void SetMediaStreamTrackStatsFromMediaStreamTrackInterface(
    const MediaStreamTrackInterface& track,
    RTCMediaStreamTrackStats* track_stats) {
  track_stats->track_identifier = track.id();
  track_stats->ended = (track.state() == MediaStreamTrackInterface::kEnded);
}

// Provides the media independent counters (both audio and video).
void SetInboundRTPStreamStatsFromMediaReceiverInfo(
    const cricket::MediaReceiverInfo& media_receiver_info,
    RTCInboundRTPStreamStats* inbound_stats) {
  RTC_DCHECK(inbound_stats);
  inbound_stats->ssrc = media_receiver_info.ssrc();
  inbound_stats->packets_received =
      static_cast<uint32_t>(media_receiver_info.packets_rcvd);
  inbound_stats->bytes_received =
      static_cast<uint64_t>(media_receiver_info.payload_bytes_rcvd);
  inbound_stats->header_bytes_received =
      static_cast<uint64_t>(media_receiver_info.header_and_padding_bytes_rcvd);
  inbound_stats->packets_lost =
      static_cast<int32_t>(media_receiver_info.packets_lost);
}

std::unique_ptr<RTCInboundRTPStreamStats> CreateInboundAudioStreamStats(
    const cricket::VoiceReceiverInfo& voice_receiver_info,
    const std::string& mid,
    int64_t timestamp_us) {
  auto inbound_audio = std::make_unique<RTCInboundRTPStreamStats>(
      /*id=*/RTCInboundRTPStreamStatsIDFromSSRC(cricket::MEDIA_TYPE_AUDIO,
                                                voice_receiver_info.ssrc()),
      timestamp_us);
  SetInboundRTPStreamStatsFromMediaReceiverInfo(voice_receiver_info,
                                                inbound_audio.get());
  inbound_audio->media_type = "audio";
  inbound_audio->kind = "audio";
  if (voice_receiver_info.codec_payload_type) {
    inbound_audio->codec_id = RTCCodecStatsIDFromMidDirectionAndPayload(
        mid, /*inbound=*/true, *voice_receiver_info.codec_payload_type);
  }
  inbound_audio->jitter = static_cast<double>(voice_receiver_info.jitter_ms) /
                          rtc::kNumMillisecsPerSec;
  inbound_audio->jitter_buffer_delay =
      voice_receiver_info.jitter_buffer_delay_seconds;
  inbound_audio->jitter_buffer_emitted_count =
      voice_receiver_info.jitter_buffer_emitted_count;
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
  // |fir_count|, |pli_count| and |sli_count| are only valid for video and are
  // purposefully left undefined for audio.
  if (voice_receiver_info.last_packet_received_timestamp_ms) {
    inbound_audio->last_packet_received_timestamp = static_cast<double>(
        *voice_receiver_info.last_packet_received_timestamp_ms);
  }
  if (voice_receiver_info.estimated_playout_ntp_timestamp_ms) {
    // TODO(bugs.webrtc.org/10529): Fix time origin.
    inbound_audio->estimated_playout_timestamp = static_cast<double>(
        *voice_receiver_info.estimated_playout_ntp_timestamp_ms);
  }
  inbound_audio->fec_packets_received =
      voice_receiver_info.fec_packets_received;
  inbound_audio->fec_packets_discarded =
      voice_receiver_info.fec_packets_discarded;
  return inbound_audio;
}

std::unique_ptr<RTCRemoteOutboundRtpStreamStats>
CreateRemoteOutboundAudioStreamStats(
    const cricket::VoiceReceiverInfo& voice_receiver_info,
    const std::string& mid,
    const std::string& inbound_audio_id,
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
      /*timestamp_us=*/rtc::kNumMicrosecsPerMillisec *
          voice_receiver_info.last_sender_report_timestamp_ms.value());

  // Populate.
  // - RTCRtpStreamStats.
  stats->ssrc = voice_receiver_info.ssrc();
  stats->kind = "audio";
  stats->transport_id = transport_id;
  stats->codec_id = RTCCodecStatsIDFromMidDirectionAndPayload(
      mid,
      /*inbound=*/true,  // Remote-outbound same as local-inbound.
      *voice_receiver_info.codec_payload_type);
  // - RTCSentRtpStreamStats.
  stats->packets_sent = voice_receiver_info.sender_reports_packets_sent;
  stats->bytes_sent = voice_receiver_info.sender_reports_bytes_sent;
  // - RTCRemoteOutboundRtpStreamStats.
  stats->local_id = inbound_audio_id;
  RTC_DCHECK(
      voice_receiver_info.last_sender_report_remote_timestamp_ms.has_value());
  stats->remote_timestamp = static_cast<double>(
      voice_receiver_info.last_sender_report_remote_timestamp_ms.value());
  stats->reports_sent = voice_receiver_info.sender_reports_reports_count;

  return stats;
}

void SetInboundRTPStreamStatsFromVideoReceiverInfo(
    const std::string& mid,
    const cricket::VideoReceiverInfo& video_receiver_info,
    RTCInboundRTPStreamStats* inbound_video) {
  SetInboundRTPStreamStatsFromMediaReceiverInfo(video_receiver_info,
                                                inbound_video);
  inbound_video->media_type = "video";
  inbound_video->kind = "video";
  if (video_receiver_info.codec_payload_type) {
    inbound_video->codec_id = RTCCodecStatsIDFromMidDirectionAndPayload(
        mid, /*inbound=*/true, *video_receiver_info.codec_payload_type);
  }
  inbound_video->jitter = static_cast<double>(video_receiver_info.jitter_ms) /
                          rtc::kNumMillisecsPerSec;
  inbound_video->fir_count =
      static_cast<uint32_t>(video_receiver_info.firs_sent);
  inbound_video->pli_count =
      static_cast<uint32_t>(video_receiver_info.plis_sent);
  inbound_video->nack_count =
      static_cast<uint32_t>(video_receiver_info.nacks_sent);
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
  if (video_receiver_info.framerate_rcvd > 0) {
    inbound_video->frames_per_second = video_receiver_info.framerate_rcvd;
  }
  if (video_receiver_info.qp_sum)
    inbound_video->qp_sum = *video_receiver_info.qp_sum;
  inbound_video->total_decode_time =
      static_cast<double>(video_receiver_info.total_decode_time_ms) /
      rtc::kNumMillisecsPerSec;
  inbound_video->total_inter_frame_delay =
      video_receiver_info.total_inter_frame_delay;
  inbound_video->total_squared_inter_frame_delay =
      video_receiver_info.total_squared_inter_frame_delay;
  if (video_receiver_info.last_packet_received_timestamp_ms) {
    inbound_video->last_packet_received_timestamp = static_cast<double>(
        *video_receiver_info.last_packet_received_timestamp_ms);
  }
  if (video_receiver_info.estimated_playout_ntp_timestamp_ms) {
    // TODO(bugs.webrtc.org/10529): Fix time origin if needed.
    inbound_video->estimated_playout_timestamp = static_cast<double>(
        *video_receiver_info.estimated_playout_ntp_timestamp_ms);
  }
  // TODO(bugs.webrtc.org/10529): When info's |content_info| is optional
  // support the "unspecified" value.
  if (video_receiver_info.content_type == VideoContentType::SCREENSHARE)
    inbound_video->content_type = RTCContentType::kScreenshare;
  if (!video_receiver_info.decoder_implementation_name.empty()) {
    inbound_video->decoder_implementation =
        video_receiver_info.decoder_implementation_name;
  }
}

// Provides the media independent counters (both audio and video).
void SetOutboundRTPStreamStatsFromMediaSenderInfo(
    const cricket::MediaSenderInfo& media_sender_info,
    RTCOutboundRTPStreamStats* outbound_stats) {
  RTC_DCHECK(outbound_stats);
  outbound_stats->ssrc = media_sender_info.ssrc();
  outbound_stats->packets_sent =
      static_cast<uint32_t>(media_sender_info.packets_sent);
  outbound_stats->retransmitted_packets_sent =
      media_sender_info.retransmitted_packets_sent;
  outbound_stats->bytes_sent =
      static_cast<uint64_t>(media_sender_info.payload_bytes_sent);
  outbound_stats->header_bytes_sent =
      static_cast<uint64_t>(media_sender_info.header_and_padding_bytes_sent);
  outbound_stats->retransmitted_bytes_sent =
      media_sender_info.retransmitted_bytes_sent;
}

void SetOutboundRTPStreamStatsFromVoiceSenderInfo(
    const std::string& mid,
    const cricket::VoiceSenderInfo& voice_sender_info,
    RTCOutboundRTPStreamStats* outbound_audio) {
  SetOutboundRTPStreamStatsFromMediaSenderInfo(voice_sender_info,
                                               outbound_audio);
  outbound_audio->media_type = "audio";
  outbound_audio->kind = "audio";
  if (voice_sender_info.codec_payload_type) {
    outbound_audio->codec_id = RTCCodecStatsIDFromMidDirectionAndPayload(
        mid, /*inbound=*/false, *voice_sender_info.codec_payload_type);
  }
  // |fir_count|, |pli_count| and |sli_count| are only valid for video and are
  // purposefully left undefined for audio.
}

void SetOutboundRTPStreamStatsFromVideoSenderInfo(
    const std::string& mid,
    const cricket::VideoSenderInfo& video_sender_info,
    RTCOutboundRTPStreamStats* outbound_video) {
  SetOutboundRTPStreamStatsFromMediaSenderInfo(video_sender_info,
                                               outbound_video);
  outbound_video->media_type = "video";
  outbound_video->kind = "video";
  if (video_sender_info.codec_payload_type) {
    outbound_video->codec_id = RTCCodecStatsIDFromMidDirectionAndPayload(
        mid, /*inbound=*/false, *video_sender_info.codec_payload_type);
  }
  outbound_video->fir_count =
      static_cast<uint32_t>(video_sender_info.firs_rcvd);
  outbound_video->pli_count =
      static_cast<uint32_t>(video_sender_info.plis_rcvd);
  outbound_video->nack_count =
      static_cast<uint32_t>(video_sender_info.nacks_rcvd);
  if (video_sender_info.qp_sum)
    outbound_video->qp_sum = *video_sender_info.qp_sum;
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
  outbound_video->total_packet_send_delay =
      static_cast<double>(video_sender_info.total_packet_send_delay_ms) /
      rtc::kNumMillisecsPerSec;
  outbound_video->quality_limitation_reason =
      QualityLimitationReasonToRTCQualityLimitationReason(
          video_sender_info.quality_limitation_reason);
  outbound_video->quality_limitation_resolution_changes =
      video_sender_info.quality_limitation_resolution_changes;
  // TODO(https://crbug.com/webrtc/10529): When info's |content_info| is
  // optional, support the "unspecified" value.
  if (video_sender_info.content_type == VideoContentType::SCREENSHARE)
    outbound_video->content_type = RTCContentType::kScreenshare;
  if (!video_sender_info.encoder_implementation_name.empty()) {
    outbound_video->encoder_implementation =
        video_sender_info.encoder_implementation_name;
  }
  if (video_sender_info.rid) {
    outbound_video->rid = *video_sender_info.rid;
  }
}

std::unique_ptr<RTCRemoteInboundRtpStreamStats>
ProduceRemoteInboundRtpStreamStatsFromReportBlockData(
    const ReportBlockData& report_block_data,
    cricket::MediaType media_type,
    const std::map<std::string, RTCOutboundRTPStreamStats*>& outbound_rtps,
    const RTCStatsReport& report) {
  const auto& report_block = report_block_data.report_block();
  // RTCStats' timestamp generally refers to when the metric was sampled, but
  // for "remote-[outbound/inbound]-rtp" it refers to the local time when the
  // Report Block was received.
  auto remote_inbound = std::make_unique<RTCRemoteInboundRtpStreamStats>(
      RTCRemoteInboundRtpStreamStatsIdFromSourceSsrc(media_type,
                                                     report_block.source_ssrc),
      /*timestamp=*/report_block_data.report_block_timestamp_utc_us());
  remote_inbound->ssrc = report_block.source_ssrc;
  remote_inbound->kind =
      media_type == cricket::MEDIA_TYPE_AUDIO ? "audio" : "video";
  remote_inbound->packets_lost = report_block.packets_lost;
  remote_inbound->fraction_lost =
      static_cast<double>(report_block.fraction_lost) / (1 << 8);
  remote_inbound->round_trip_time =
      static_cast<double>(report_block_data.last_rtt_ms()) /
      rtc::kNumMillisecsPerSec;
  remote_inbound->total_round_trip_time =
      static_cast<double>(report_block_data.sum_rtt_ms()) /
      rtc::kNumMillisecsPerSec;
  remote_inbound->round_trip_time_measurements =
      report_block_data.num_rtts();

  std::string local_id =
      RTCOutboundRTPStreamStatsIDFromSSRC(media_type, report_block.source_ssrc);
  // Look up local stat from |outbound_rtps| where the pointers are non-const.
  auto local_id_it = outbound_rtps.find(local_id);
  if (local_id_it != outbound_rtps.end()) {
    remote_inbound->local_id = local_id;
    auto& outbound_rtp = *local_id_it->second;
    outbound_rtp.remote_id = remote_inbound->id();
    // The RTP/RTCP transport is obtained from the
    // RTCOutboundRtpStreamStats's transport.
    const auto* transport_from_id = outbound_rtp.transport_id.is_defined()
                                        ? report.Get(*outbound_rtp.transport_id)
                                        : nullptr;
    if (transport_from_id) {
      const auto& transport = transport_from_id->cast_to<RTCTransportStats>();
      // If RTP and RTCP are not multiplexed, there is a separate RTCP
      // transport paired with the RTP transport, otherwise the same
      // transport is used for RTCP and RTP.
      remote_inbound->transport_id =
          transport.rtcp_transport_stats_id.is_defined()
              ? *transport.rtcp_transport_stats_id
              : *outbound_rtp.transport_id;
    }
    // We're assuming the same codec is used on both ends. However if the
    // codec is switched out on the fly we may have received a Report Block
    // based on the previous codec and there is no way to tell which point in
    // time the codec changed for the remote end.
    const auto* codec_from_id = outbound_rtp.codec_id.is_defined()
                                    ? report.Get(*outbound_rtp.codec_id)
                                    : nullptr;
    if (codec_from_id) {
      remote_inbound->codec_id = *outbound_rtp.codec_id;
      const auto& codec = codec_from_id->cast_to<RTCCodecStats>();
      if (codec.clock_rate.is_defined()) {
        // The Report Block jitter is expressed in RTP timestamp units
        // (https://tools.ietf.org/html/rfc3550#section-6.4.1). To convert this
        // to seconds we divide by the codec's clock rate.
        remote_inbound->jitter =
            static_cast<double>(report_block.jitter) / *codec.clock_rate;
      }
    }
  }
  return remote_inbound;
}

void ProduceCertificateStatsFromSSLCertificateStats(
    int64_t timestamp_us,
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
        new RTCCertificateStats(certificate_stats_id, timestamp_us);
    certificate_stats->fingerprint = s->fingerprint;
    certificate_stats->fingerprint_algorithm = s->fingerprint_algorithm;
    certificate_stats->base64_certificate = s->base64_certificate;
    if (prev_certificate_stats)
      prev_certificate_stats->issuer_certificate_id = certificate_stats->id();
    report->AddStats(std::unique_ptr<RTCCertificateStats>(certificate_stats));
    prev_certificate_stats = certificate_stats;
  }
}

const std::string& ProduceIceCandidateStats(int64_t timestamp_us,
                                            const cricket::Candidate& candidate,
                                            bool is_local,
                                            const std::string& transport_id,
                                            RTCStatsReport* report) {
  const std::string& id = "RTCIceCandidate_" + candidate.id();
  const RTCStats* stats = report->Get(id);
  if (!stats) {
    std::unique_ptr<RTCIceCandidateStats> candidate_stats;
    if (is_local)
      candidate_stats.reset(new RTCLocalIceCandidateStats(id, timestamp_us));
    else
      candidate_stats.reset(new RTCRemoteIceCandidateStats(id, timestamp_us));
    candidate_stats->transport_id = transport_id;
    if (is_local) {
      candidate_stats->network_type =
          NetworkAdapterTypeToStatsType(candidate.network_type());
      if (candidate.type() == cricket::RELAY_PORT_TYPE) {
        std::string relay_protocol = candidate.relay_protocol();
        RTC_DCHECK(relay_protocol.compare("udp") == 0 ||
                   relay_protocol.compare("tcp") == 0 ||
                   relay_protocol.compare("tls") == 0);
        candidate_stats->relay_protocol = relay_protocol;
      }
    } else {
      // We don't expect to know the adapter type of remote candidates.
      RTC_DCHECK_EQ(rtc::ADAPTER_TYPE_UNKNOWN, candidate.network_type());
    }
    candidate_stats->ip = candidate.address().ipaddr().ToString();
    candidate_stats->address = candidate.address().ipaddr().ToString();
    candidate_stats->port = static_cast<int32_t>(candidate.address().port());
    candidate_stats->protocol = candidate.protocol();
    candidate_stats->candidate_type =
        CandidateTypeToRTCIceCandidateType(candidate.type());
    candidate_stats->priority = static_cast<int32_t>(candidate.priority());

    stats = candidate_stats.get();
    report->AddStats(std::move(candidate_stats));
  }
  RTC_DCHECK_EQ(stats->type(), is_local ? RTCLocalIceCandidateStats::kType
                                        : RTCRemoteIceCandidateStats::kType);
  return stats->id();
}

std::unique_ptr<RTCMediaStreamTrackStats>
ProduceMediaStreamTrackStatsFromVoiceSenderInfo(
    int64_t timestamp_us,
    const AudioTrackInterface& audio_track,
    const cricket::VoiceSenderInfo& voice_sender_info,
    int attachment_id) {
  std::unique_ptr<RTCMediaStreamTrackStats> audio_track_stats(
      new RTCMediaStreamTrackStats(
          RTCMediaStreamTrackStatsIDFromDirectionAndAttachment(kSender,
                                                               attachment_id),
          timestamp_us, RTCMediaStreamTrackKind::kAudio));
  SetMediaStreamTrackStatsFromMediaStreamTrackInterface(
      audio_track, audio_track_stats.get());
  audio_track_stats->media_source_id =
      RTCMediaSourceStatsIDFromKindAndAttachment(cricket::MEDIA_TYPE_AUDIO,
                                                 attachment_id);
  audio_track_stats->remote_source = false;
  audio_track_stats->detached = false;
  if (voice_sender_info.apm_statistics.echo_return_loss) {
    audio_track_stats->echo_return_loss =
        *voice_sender_info.apm_statistics.echo_return_loss;
  }
  if (voice_sender_info.apm_statistics.echo_return_loss_enhancement) {
    audio_track_stats->echo_return_loss_enhancement =
        *voice_sender_info.apm_statistics.echo_return_loss_enhancement;
  }
  return audio_track_stats;
}

std::unique_ptr<RTCMediaStreamTrackStats>
ProduceMediaStreamTrackStatsFromVoiceReceiverInfo(
    int64_t timestamp_us,
    const AudioTrackInterface& audio_track,
    const cricket::VoiceReceiverInfo& voice_receiver_info,
    int attachment_id) {
  // Since receiver tracks can't be reattached, we use the SSRC as
  // an attachment identifier.
  std::unique_ptr<RTCMediaStreamTrackStats> audio_track_stats(
      new RTCMediaStreamTrackStats(
          RTCMediaStreamTrackStatsIDFromDirectionAndAttachment(kReceiver,
                                                               attachment_id),
          timestamp_us, RTCMediaStreamTrackKind::kAudio));
  SetMediaStreamTrackStatsFromMediaStreamTrackInterface(
      audio_track, audio_track_stats.get());
  audio_track_stats->remote_source = true;
  audio_track_stats->detached = false;
  if (voice_receiver_info.audio_level >= 0) {
    audio_track_stats->audio_level =
        DoubleAudioLevelFromIntAudioLevel(voice_receiver_info.audio_level);
  }
  audio_track_stats->jitter_buffer_delay =
      voice_receiver_info.jitter_buffer_delay_seconds;
  audio_track_stats->jitter_buffer_emitted_count =
      voice_receiver_info.jitter_buffer_emitted_count;
  audio_track_stats->inserted_samples_for_deceleration =
      voice_receiver_info.inserted_samples_for_deceleration;
  audio_track_stats->removed_samples_for_acceleration =
      voice_receiver_info.removed_samples_for_acceleration;
  audio_track_stats->total_audio_energy =
      voice_receiver_info.total_output_energy;
  audio_track_stats->total_samples_received =
      voice_receiver_info.total_samples_received;
  audio_track_stats->total_samples_duration =
      voice_receiver_info.total_output_duration;
  audio_track_stats->concealed_samples = voice_receiver_info.concealed_samples;
  audio_track_stats->silent_concealed_samples =
      voice_receiver_info.silent_concealed_samples;
  audio_track_stats->concealment_events =
      voice_receiver_info.concealment_events;
  audio_track_stats->jitter_buffer_flushes =
      voice_receiver_info.jitter_buffer_flushes;
  audio_track_stats->delayed_packet_outage_samples =
      voice_receiver_info.delayed_packet_outage_samples;
  audio_track_stats->relative_packet_arrival_delay =
      voice_receiver_info.relative_packet_arrival_delay_seconds;
  audio_track_stats->jitter_buffer_target_delay =
      voice_receiver_info.jitter_buffer_target_delay_seconds;
  audio_track_stats->interruption_count =
      voice_receiver_info.interruption_count >= 0
          ? voice_receiver_info.interruption_count
          : 0;
  audio_track_stats->total_interruption_duration =
      static_cast<double>(voice_receiver_info.total_interruption_duration_ms) /
      rtc::kNumMillisecsPerSec;
  return audio_track_stats;
}

std::unique_ptr<RTCMediaStreamTrackStats>
ProduceMediaStreamTrackStatsFromVideoSenderInfo(
    int64_t timestamp_us,
    const VideoTrackInterface& video_track,
    const cricket::VideoSenderInfo& video_sender_info,
    int attachment_id) {
  std::unique_ptr<RTCMediaStreamTrackStats> video_track_stats(
      new RTCMediaStreamTrackStats(
          RTCMediaStreamTrackStatsIDFromDirectionAndAttachment(kSender,
                                                               attachment_id),
          timestamp_us, RTCMediaStreamTrackKind::kVideo));
  SetMediaStreamTrackStatsFromMediaStreamTrackInterface(
      video_track, video_track_stats.get());
  video_track_stats->media_source_id =
      RTCMediaSourceStatsIDFromKindAndAttachment(cricket::MEDIA_TYPE_VIDEO,
                                                 attachment_id);
  video_track_stats->remote_source = false;
  video_track_stats->detached = false;
  video_track_stats->frame_width =
      static_cast<uint32_t>(video_sender_info.send_frame_width);
  video_track_stats->frame_height =
      static_cast<uint32_t>(video_sender_info.send_frame_height);
  // TODO(hbos): Will reduce this by frames dropped due to congestion control
  // when available. https://crbug.com/659137
  video_track_stats->frames_sent = video_sender_info.frames_encoded;
  video_track_stats->huge_frames_sent = video_sender_info.huge_frames_sent;
  return video_track_stats;
}

std::unique_ptr<RTCMediaStreamTrackStats>
ProduceMediaStreamTrackStatsFromVideoReceiverInfo(
    int64_t timestamp_us,
    const VideoTrackInterface& video_track,
    const cricket::VideoReceiverInfo& video_receiver_info,
    int attachment_id) {
  std::unique_ptr<RTCMediaStreamTrackStats> video_track_stats(
      new RTCMediaStreamTrackStats(
          RTCMediaStreamTrackStatsIDFromDirectionAndAttachment(kReceiver,

                                                               attachment_id),
          timestamp_us, RTCMediaStreamTrackKind::kVideo));
  SetMediaStreamTrackStatsFromMediaStreamTrackInterface(
      video_track, video_track_stats.get());
  video_track_stats->remote_source = true;
  video_track_stats->detached = false;
  if (video_receiver_info.frame_width > 0 &&
      video_receiver_info.frame_height > 0) {
    video_track_stats->frame_width =
        static_cast<uint32_t>(video_receiver_info.frame_width);
    video_track_stats->frame_height =
        static_cast<uint32_t>(video_receiver_info.frame_height);
  }
  video_track_stats->jitter_buffer_delay =
      video_receiver_info.jitter_buffer_delay_seconds;
  video_track_stats->jitter_buffer_emitted_count =
      video_receiver_info.jitter_buffer_emitted_count;
  video_track_stats->frames_received = video_receiver_info.frames_received;
  // TODO(hbos): When we support receiving simulcast, this should be the total
  // number of frames correctly decoded, independent of which SSRC it was
  // received from. Since we don't support that, this is correct and is the same
  // value as "RTCInboundRTPStreamStats.framesDecoded". https://crbug.com/659137
  video_track_stats->frames_decoded = video_receiver_info.frames_decoded;
  video_track_stats->frames_dropped = video_receiver_info.frames_dropped;
  video_track_stats->freeze_count = video_receiver_info.freeze_count;
  video_track_stats->pause_count = video_receiver_info.pause_count;
  video_track_stats->total_freezes_duration =
      static_cast<double>(video_receiver_info.total_freezes_duration_ms) /
      rtc::kNumMillisecsPerSec;
  video_track_stats->total_pauses_duration =
      static_cast<double>(video_receiver_info.total_pauses_duration_ms) /
      rtc::kNumMillisecsPerSec;
  video_track_stats->total_frames_duration =
      static_cast<double>(video_receiver_info.total_frames_duration_ms) /
      rtc::kNumMillisecsPerSec;
  video_track_stats->sum_squared_frame_durations =
      video_receiver_info.sum_squared_frame_durations;

  return video_track_stats;
}

void ProduceSenderMediaTrackStats(
    int64_t timestamp_us,
    const TrackMediaInfoMap& track_media_info_map,
    std::vector<rtc::scoped_refptr<RtpSenderInternal>> senders,
    RTCStatsReport* report) {
  // This function iterates over the senders to generate outgoing track stats.

  // TODO(hbos): Return stats of detached tracks. We have to perform stats
  // gathering at the time of detachment to get accurate stats and timestamps.
  // https://crbug.com/659137
  for (const auto& sender : senders) {
    if (sender->media_type() == cricket::MEDIA_TYPE_AUDIO) {
      AudioTrackInterface* track =
          static_cast<AudioTrackInterface*>(sender->track().get());
      if (!track)
        continue;
      cricket::VoiceSenderInfo null_sender_info;
      const cricket::VoiceSenderInfo* voice_sender_info = &null_sender_info;
      // TODO(hta): Checking on ssrc is not proper. There should be a way
      // to see from a sender whether it's connected or not.
      // Related to https://crbug.com/8694 (using ssrc 0 to indicate "none")
      if (sender->ssrc() != 0) {
        // When pc.close is called, sender info is discarded, so
        // we generate zeroes instead. Bug: It should be retained.
        // https://crbug.com/807174
        const cricket::VoiceSenderInfo* sender_info =
            track_media_info_map.GetVoiceSenderInfoBySsrc(sender->ssrc());
        if (sender_info) {
          voice_sender_info = sender_info;
        } else {
          RTC_LOG(LS_INFO)
              << "RTCStatsCollector: No voice sender info for sender with ssrc "
              << sender->ssrc();
        }
      }
      std::unique_ptr<RTCMediaStreamTrackStats> audio_track_stats =
          ProduceMediaStreamTrackStatsFromVoiceSenderInfo(
              timestamp_us, *track, *voice_sender_info, sender->AttachmentId());
      report->AddStats(std::move(audio_track_stats));
    } else if (sender->media_type() == cricket::MEDIA_TYPE_VIDEO) {
      VideoTrackInterface* track =
          static_cast<VideoTrackInterface*>(sender->track().get());
      if (!track)
        continue;
      cricket::VideoSenderInfo null_sender_info;
      const cricket::VideoSenderInfo* video_sender_info = &null_sender_info;
      // TODO(hta): Check on state not ssrc when state is available
      // Related to https://bugs.webrtc.org/8694 (using ssrc 0 to indicate
      // "none")
      if (sender->ssrc() != 0) {
        // When pc.close is called, sender info is discarded, so
        // we generate zeroes instead. Bug: It should be retained.
        // https://crbug.com/807174
        const cricket::VideoSenderInfo* sender_info =
            track_media_info_map.GetVideoSenderInfoBySsrc(sender->ssrc());
        if (sender_info) {
          video_sender_info = sender_info;
        } else {
          RTC_LOG(LS_INFO) << "No video sender info for sender with ssrc "
                           << sender->ssrc();
        }
      }
      std::unique_ptr<RTCMediaStreamTrackStats> video_track_stats =
          ProduceMediaStreamTrackStatsFromVideoSenderInfo(
              timestamp_us, *track, *video_sender_info, sender->AttachmentId());
      report->AddStats(std::move(video_track_stats));
    } else {
      RTC_NOTREACHED();
    }
  }
}

void ProduceReceiverMediaTrackStats(
    int64_t timestamp_us,
    const TrackMediaInfoMap& track_media_info_map,
    std::vector<rtc::scoped_refptr<RtpReceiverInternal>> receivers,
    RTCStatsReport* report) {
  // This function iterates over the receivers to find the remote tracks.
  for (const auto& receiver : receivers) {
    if (receiver->media_type() == cricket::MEDIA_TYPE_AUDIO) {
      AudioTrackInterface* track =
          static_cast<AudioTrackInterface*>(receiver->track().get());
      const cricket::VoiceReceiverInfo* voice_receiver_info =
          track_media_info_map.GetVoiceReceiverInfo(*track);
      if (!voice_receiver_info) {
        continue;
      }
      std::unique_ptr<RTCMediaStreamTrackStats> audio_track_stats =
          ProduceMediaStreamTrackStatsFromVoiceReceiverInfo(
              timestamp_us, *track, *voice_receiver_info,
              receiver->AttachmentId());
      report->AddStats(std::move(audio_track_stats));
    } else if (receiver->media_type() == cricket::MEDIA_TYPE_VIDEO) {
      VideoTrackInterface* track =
          static_cast<VideoTrackInterface*>(receiver->track().get());
      const cricket::VideoReceiverInfo* video_receiver_info =
          track_media_info_map.GetVideoReceiverInfo(*track);
      if (!video_receiver_info) {
        continue;
      }
      std::unique_ptr<RTCMediaStreamTrackStats> video_track_stats =
          ProduceMediaStreamTrackStatsFromVideoReceiverInfo(
              timestamp_us, *track, *video_receiver_info,
              receiver->AttachmentId());
      report->AddStats(std::move(video_track_stats));
    } else {
      RTC_NOTREACHED();
    }
  }
}

rtc::scoped_refptr<RTCStatsReport> CreateReportFilteredBySelector(
    bool filter_by_sender_selector,
    rtc::scoped_refptr<const RTCStatsReport> report,
    rtc::scoped_refptr<RtpSenderInternal> sender_selector,
    rtc::scoped_refptr<RtpReceiverInternal> receiver_selector) {
  std::vector<std::string> rtpstream_ids;
  if (filter_by_sender_selector) {
    // Filter mode: RTCStatsCollector::RequestInfo::kSenderSelector
    if (sender_selector) {
      // Find outbound-rtp(s) of the sender, i.e. the outbound-rtp(s) that
      // reference the sender stats.
      // Because we do not implement sender stats, we look at outbound-rtp(s)
      // that reference the track attachment stats for the sender instead.
      std::string track_id =
          RTCMediaStreamTrackStatsIDFromDirectionAndAttachment(
              kSender, sender_selector->AttachmentId());
      for (const auto& stats : *report) {
        if (stats.type() != RTCOutboundRTPStreamStats::kType)
          continue;
        const auto& outbound_rtp = stats.cast_to<RTCOutboundRTPStreamStats>();
        if (outbound_rtp.track_id.is_defined() &&
            *outbound_rtp.track_id == track_id) {
          rtpstream_ids.push_back(outbound_rtp.id());
        }
      }
    }
  } else {
    // Filter mode: RTCStatsCollector::RequestInfo::kReceiverSelector
    if (receiver_selector) {
      // Find inbound-rtp(s) of the receiver, i.e. the inbound-rtp(s) that
      // reference the receiver stats.
      // Because we do not implement receiver stats, we look at inbound-rtp(s)
      // that reference the track attachment stats for the receiver instead.
      std::string track_id =
          RTCMediaStreamTrackStatsIDFromDirectionAndAttachment(
              kReceiver, receiver_selector->AttachmentId());
      for (const auto& stats : *report) {
        if (stats.type() != RTCInboundRTPStreamStats::kType)
          continue;
        const auto& inbound_rtp = stats.cast_to<RTCInboundRTPStreamStats>();
        if (inbound_rtp.track_id.is_defined() &&
            *inbound_rtp.track_id == track_id) {
          rtpstream_ids.push_back(inbound_rtp.id());
        }
      }
    }
  }
  if (rtpstream_ids.empty())
    return RTCStatsReport::Create(report->timestamp_us());
  return TakeReferencedStats(report->Copy(), rtpstream_ids);
}

}  // namespace

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
  pc_->SignalSctpDataChannelCreated().connect(
      this, &RTCStatsCollector::OnSctpDataChannelCreated);
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
    std::vector<RequestInfo> requests;
    requests.swap(requests_);

    // Task subclass to take ownership of the requests.
    // TODO(nisse): Delete when we can use C++14, and do lambda capture with
    // std::move.
    class DeliveryTask : public QueuedTask {
     public:
      DeliveryTask(rtc::scoped_refptr<RTCStatsCollector> collector,
                   rtc::scoped_refptr<const RTCStatsReport> cached_report,
                   std::vector<RequestInfo> requests)
          : collector_(collector),
            cached_report_(cached_report),
            requests_(std::move(requests)) {}
      bool Run() override {
        collector_->DeliverCachedReport(cached_report_, std::move(requests_));
        return true;
      }

     private:
      rtc::scoped_refptr<RTCStatsCollector> collector_;
      rtc::scoped_refptr<const RTCStatsReport> cached_report_;
      std::vector<RequestInfo> requests_;
    };
    signaling_thread_->PostTask(std::make_unique<DeliveryTask>(
        this, cached_report_, std::move(requests)));
  } else if (!num_pending_partial_reports_) {
    // Only start gathering stats if we're not already gathering stats. In the
    // case of already gathering stats, |callback_| will be invoked when there
    // are no more pending partial reports.

    // "Now" using a system clock, relative to the UNIX epoch (Jan 1, 1970,
    // UTC), in microseconds. The system clock could be modified and is not
    // necessarily monotonically increasing.
    int64_t timestamp_us = rtc::TimeUTCMicros();

    num_pending_partial_reports_ = 2;
    partial_report_timestamp_us_ = cache_now_us;

    // Prepare |transceiver_stats_infos_| and |call_stats_| for use in
    // |ProducePartialResultsOnNetworkThread| and
    // |ProducePartialResultsOnSignalingThread|.
    PrepareTransceiverStatsInfosAndCallStats_s_w_n();
    // Don't touch |network_report_| on the signaling thread until
    // ProducePartialResultsOnNetworkThread() has signaled the
    // |network_report_event_|.
    network_report_event_.Reset();
    rtc::scoped_refptr<RTCStatsCollector> collector(this);
    network_thread_->PostTask(
        RTC_FROM_HERE,
        [collector, sctp_transport_name = pc_->sctp_transport_name(),
         timestamp_us]() mutable {
          collector->ProducePartialResultsOnNetworkThread(
              timestamp_us, std::move(sctp_transport_name));
        });
    ProducePartialResultsOnSignalingThread(timestamp_us);
  }
}

void RTCStatsCollector::ClearCachedStatsReport() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  cached_report_ = nullptr;
}

void RTCStatsCollector::WaitForPendingRequest() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  // If a request is pending, blocks until the |network_report_event_| is
  // signaled and then delivers the result. Otherwise this is a NO-OP.
  MergeNetworkReport_s();
}

void RTCStatsCollector::ProducePartialResultsOnSignalingThread(
    int64_t timestamp_us) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  partial_report_ = RTCStatsReport::Create(timestamp_us);

  ProducePartialResultsOnSignalingThreadImpl(timestamp_us,
                                             partial_report_.get());

  // ProducePartialResultsOnSignalingThread() is running synchronously on the
  // signaling thread, so it is always the first partial result delivered on the
  // signaling thread. The request is not complete until MergeNetworkReport_s()
  // happens; we don't have to do anything here.
  RTC_DCHECK_GT(num_pending_partial_reports_, 1);
  --num_pending_partial_reports_;
}

void RTCStatsCollector::ProducePartialResultsOnSignalingThreadImpl(
    int64_t timestamp_us,
    RTCStatsReport* partial_report) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  ProduceDataChannelStats_s(timestamp_us, partial_report);
  ProduceMediaStreamStats_s(timestamp_us, partial_report);
  ProduceMediaStreamTrackStats_s(timestamp_us, partial_report);
  ProduceMediaSourceStats_s(timestamp_us, partial_report);
  ProducePeerConnectionStats_s(timestamp_us, partial_report);
}

void RTCStatsCollector::ProducePartialResultsOnNetworkThread(
    int64_t timestamp_us,
    absl::optional<std::string> sctp_transport_name) {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  // Touching |network_report_| on this thread is safe by this method because
  // |network_report_event_| is reset before this method is invoked.
  network_report_ = RTCStatsReport::Create(timestamp_us);

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

  ProducePartialResultsOnNetworkThreadImpl(
      timestamp_us, transport_stats_by_name, transport_cert_stats,
      network_report_.get());

  // Signal that it is now safe to touch |network_report_| on the signaling
  // thread, and post a task to merge it into the final results.
  network_report_event_.Set();
  rtc::scoped_refptr<RTCStatsCollector> collector(this);
  signaling_thread_->PostTask(
      RTC_FROM_HERE, [collector] { collector->MergeNetworkReport_s(); });
}

void RTCStatsCollector::ProducePartialResultsOnNetworkThreadImpl(
    int64_t timestamp_us,
    const std::map<std::string, cricket::TransportStats>&
        transport_stats_by_name,
    const std::map<std::string, CertificateStatsPair>& transport_cert_stats,
    RTCStatsReport* partial_report) {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  ProduceCertificateStats_n(timestamp_us, transport_cert_stats, partial_report);
  ProduceCodecStats_n(timestamp_us, transceiver_stats_infos_, partial_report);
  ProduceIceCandidateAndPairStats_n(timestamp_us, transport_stats_by_name,
                                    call_stats_, partial_report);
  ProduceTransportStats_n(timestamp_us, transport_stats_by_name,
                          transport_cert_stats, partial_report);
  ProduceRTPStreamStats_n(timestamp_us, transceiver_stats_infos_,
                          partial_report);
}

void RTCStatsCollector::MergeNetworkReport_s() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  // The |network_report_event_| must be signaled for it to be safe to touch
  // |network_report_|. This is normally not blocking, but if
  // WaitForPendingRequest() is called while a request is pending, we might have
  // to wait until the network thread is done touching |network_report_|.
  network_report_event_.Wait(rtc::Event::kForever);
  if (!network_report_) {
    // Normally, MergeNetworkReport_s() is executed because it is posted from
    // the network thread. But if WaitForPendingRequest() is called while a
    // request is pending, an early call to MergeNetworkReport_s() is made,
    // merging the report and setting |network_report_| to null. If so, when the
    // previously posted MergeNetworkReport_s() is later executed, the report is
    // already null and nothing needs to be done here.
    return;
  }
  RTC_DCHECK_GT(num_pending_partial_reports_, 0);
  RTC_DCHECK(partial_report_);
  partial_report_->TakeMembersFrom(network_report_);
  network_report_ = nullptr;
  --num_pending_partial_reports_;
  // |network_report_| is currently the only partial report collected
  // asynchronously, so |num_pending_partial_reports_| must now be 0 and we are
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

  // Deliver report and clear |requests_|.
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
    int64_t timestamp_us,
    const std::map<std::string, CertificateStatsPair>& transport_cert_stats,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  for (const auto& transport_cert_stats_pair : transport_cert_stats) {
    if (transport_cert_stats_pair.second.local) {
      ProduceCertificateStatsFromSSLCertificateStats(
          timestamp_us, *transport_cert_stats_pair.second.local.get(), report);
    }
    if (transport_cert_stats_pair.second.remote) {
      ProduceCertificateStatsFromSSLCertificateStats(
          timestamp_us, *transport_cert_stats_pair.second.remote.get(), report);
    }
  }
}

void RTCStatsCollector::ProduceCodecStats_n(
    int64_t timestamp_us,
    const std::vector<RtpTransceiverStatsInfo>& transceiver_stats_infos,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  for (const auto& stats : transceiver_stats_infos) {
    if (!stats.mid) {
      continue;
    }
    std::string transport_id = RTCTransportStatsIDFromTransportChannel(
        *stats.transport_name, cricket::ICE_CANDIDATE_COMPONENT_RTP);

    const cricket::VoiceMediaInfo* voice_media_info =
        stats.track_media_info_map->voice_media_info();
    const cricket::VideoMediaInfo* video_media_info =
        stats.track_media_info_map->video_media_info();
    // Audio
    if (voice_media_info) {
      // Inbound
      for (const auto& pair : voice_media_info->receive_codecs) {
        report->AddStats(CodecStatsFromRtpCodecParameters(
            timestamp_us, *stats.mid, transport_id, true, pair.second));
      }
      // Outbound
      for (const auto& pair : voice_media_info->send_codecs) {
        report->AddStats(CodecStatsFromRtpCodecParameters(
            timestamp_us, *stats.mid, transport_id, false, pair.second));
      }
    }
    // Video
    if (video_media_info) {
      // Inbound
      for (const auto& pair : video_media_info->receive_codecs) {
        report->AddStats(CodecStatsFromRtpCodecParameters(
            timestamp_us, *stats.mid, transport_id, true, pair.second));
      }
      // Outbound
      for (const auto& pair : video_media_info->send_codecs) {
        report->AddStats(CodecStatsFromRtpCodecParameters(
            timestamp_us, *stats.mid, transport_id, false, pair.second));
      }
    }
  }
}

void RTCStatsCollector::ProduceDataChannelStats_s(
    int64_t timestamp_us,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;
  std::vector<DataChannelStats> data_stats = pc_->GetDataChannelStats();
  for (const auto& stats : data_stats) {
    std::unique_ptr<RTCDataChannelStats> data_channel_stats(
        new RTCDataChannelStats(
            "RTCDataChannel_" + rtc::ToString(stats.internal_id),
            timestamp_us));
    data_channel_stats->label = std::move(stats.label);
    data_channel_stats->protocol = std::move(stats.protocol);
    data_channel_stats->data_channel_identifier = stats.id;
    data_channel_stats->state = DataStateToRTCDataChannelState(stats.state);
    data_channel_stats->messages_sent = stats.messages_sent;
    data_channel_stats->bytes_sent = stats.bytes_sent;
    data_channel_stats->messages_received = stats.messages_received;
    data_channel_stats->bytes_received = stats.bytes_received;
    report->AddStats(std::move(data_channel_stats));
  }
}

void RTCStatsCollector::ProduceIceCandidateAndPairStats_n(
    int64_t timestamp_us,
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
      for (const cricket::ConnectionInfo& info :
           channel_stats.ice_transport_stats.connection_infos) {
        std::unique_ptr<RTCIceCandidatePairStats> candidate_pair_stats(
            new RTCIceCandidatePairStats(
                RTCIceCandidatePairStatsIDFromConnectionInfo(info),
                timestamp_us));

        candidate_pair_stats->transport_id = transport_id;
        // TODO(hbos): There could be other candidates that are not paired with
        // anything. We don't have a complete list. Local candidates come from
        // Port objects, and prflx candidates (both local and remote) are only
        // stored in candidate pairs. https://crbug.com/632723
        candidate_pair_stats->local_candidate_id = ProduceIceCandidateStats(
            timestamp_us, info.local_candidate, true, transport_id, report);
        candidate_pair_stats->remote_candidate_id = ProduceIceCandidateStats(
            timestamp_us, info.remote_candidate, false, transport_id, report);
        candidate_pair_stats->state =
            IceCandidatePairStateToRTCStatsIceCandidatePairState(info.state);
        candidate_pair_stats->priority = info.priority;
        candidate_pair_stats->nominated = info.nominated;
        // TODO(hbos): This writable is different than the spec. It goes to
        // false after a certain amount of time without a response passes.
        // https://crbug.com/633550
        candidate_pair_stats->writable = info.writable;
        candidate_pair_stats->bytes_sent =
            static_cast<uint64_t>(info.sent_total_bytes);
        candidate_pair_stats->bytes_received =
            static_cast<uint64_t>(info.recv_total_bytes);
        candidate_pair_stats->total_round_trip_time =
            static_cast<double>(info.total_round_trip_time_ms) /
            rtc::kNumMillisecsPerSec;
        if (info.current_round_trip_time_ms) {
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
        candidate_pair_stats->requests_sent = static_cast<uint64_t>(
            info.sent_ping_requests_before_first_response);
        candidate_pair_stats->responses_received =
            static_cast<uint64_t>(info.recv_ping_responses);
        candidate_pair_stats->responses_sent =
            static_cast<uint64_t>(info.sent_ping_responses);
        RTC_DCHECK_GE(info.sent_ping_requests_total,
                      info.sent_ping_requests_before_first_response);
        candidate_pair_stats->consent_requests_sent = static_cast<uint64_t>(
            info.sent_ping_requests_total -
            info.sent_ping_requests_before_first_response);

        report->AddStats(std::move(candidate_pair_stats));
      }
    }
  }
}

void RTCStatsCollector::ProduceMediaStreamStats_s(
    int64_t timestamp_us,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  std::map<std::string, std::vector<std::string>> track_ids;

  for (const auto& stats : transceiver_stats_infos_) {
    for (const auto& sender : stats.transceiver->senders()) {
      std::string track_id =
          RTCMediaStreamTrackStatsIDFromDirectionAndAttachment(
              kSender, sender->internal()->AttachmentId());
      for (auto& stream_id : sender->stream_ids()) {
        track_ids[stream_id].push_back(track_id);
      }
    }
    for (const auto& receiver : stats.transceiver->receivers()) {
      std::string track_id =
          RTCMediaStreamTrackStatsIDFromDirectionAndAttachment(
              kReceiver, receiver->internal()->AttachmentId());
      for (auto& stream : receiver->streams()) {
        track_ids[stream->id()].push_back(track_id);
      }
    }
  }

  // Build stats for each stream ID known.
  for (auto& it : track_ids) {
    std::unique_ptr<RTCMediaStreamStats> stream_stats(
        new RTCMediaStreamStats("RTCMediaStream_" + it.first, timestamp_us));
    stream_stats->stream_identifier = it.first;
    stream_stats->track_ids = it.second;
    report->AddStats(std::move(stream_stats));
  }
}

void RTCStatsCollector::ProduceMediaStreamTrackStats_s(
    int64_t timestamp_us,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  for (const RtpTransceiverStatsInfo& stats : transceiver_stats_infos_) {
    std::vector<rtc::scoped_refptr<RtpSenderInternal>> senders;
    for (const auto& sender : stats.transceiver->senders()) {
      senders.push_back(sender->internal());
    }
    ProduceSenderMediaTrackStats(timestamp_us, *stats.track_media_info_map,
                                 senders, report);

    std::vector<rtc::scoped_refptr<RtpReceiverInternal>> receivers;
    for (const auto& receiver : stats.transceiver->receivers()) {
      receivers.push_back(receiver->internal());
    }
    ProduceReceiverMediaTrackStats(timestamp_us, *stats.track_media_info_map,
                                   receivers, report);
  }
}

void RTCStatsCollector::ProduceMediaSourceStats_s(
    int64_t timestamp_us,
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
        auto audio_source_stats = std::make_unique<RTCAudioSourceStats>(
            RTCMediaSourceStatsIDFromKindAndAttachment(
                cricket::MEDIA_TYPE_AUDIO, sender_internal->AttachmentId()),
            timestamp_us);
        // TODO(https://crbug.com/webrtc/10771): We shouldn't need to have an
        // SSRC assigned (there shouldn't need to exist a send-stream, created
        // by an O/A exchange) in order to read audio media-source stats.
        // TODO(https://crbug.com/webrtc/8694): SSRC 0 shouldn't be a magic
        // value indicating no SSRC.
        if (sender_internal->ssrc() != 0) {
          auto* voice_sender_info =
              track_media_info_map->GetVoiceSenderInfoBySsrc(
                  sender_internal->ssrc());
          if (voice_sender_info) {
            audio_source_stats->audio_level = DoubleAudioLevelFromIntAudioLevel(
                voice_sender_info->audio_level);
            audio_source_stats->total_audio_energy =
                voice_sender_info->total_input_energy;
            audio_source_stats->total_samples_duration =
                voice_sender_info->total_input_duration;
          }
        }
        media_source_stats = std::move(audio_source_stats);
      } else {
        RTC_DCHECK_EQ(MediaStreamTrackInterface::kVideoKind, track->kind());
        auto video_source_stats = std::make_unique<RTCVideoSourceStats>(
            RTCMediaSourceStatsIDFromKindAndAttachment(
                cricket::MEDIA_TYPE_VIDEO, sender_internal->AttachmentId()),
            timestamp_us);
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
              track_media_info_map->GetVideoSenderInfoBySsrc(
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
    int64_t timestamp_us,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  std::unique_ptr<RTCPeerConnectionStats> stats(
      new RTCPeerConnectionStats("RTCPeerConnection", timestamp_us));
  stats->data_channels_opened = internal_record_.data_channels_opened;
  stats->data_channels_closed = internal_record_.data_channels_closed;
  report->AddStats(std::move(stats));
}

void RTCStatsCollector::ProduceRTPStreamStats_n(
    int64_t timestamp_us,
    const std::vector<RtpTransceiverStatsInfo>& transceiver_stats_infos,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  for (const RtpTransceiverStatsInfo& stats : transceiver_stats_infos) {
    if (stats.media_type == cricket::MEDIA_TYPE_AUDIO) {
      ProduceAudioRTPStreamStats_n(timestamp_us, stats, report);
    } else if (stats.media_type == cricket::MEDIA_TYPE_VIDEO) {
      ProduceVideoRTPStreamStats_n(timestamp_us, stats, report);
    } else {
      RTC_NOTREACHED();
    }
  }
}

void RTCStatsCollector::ProduceAudioRTPStreamStats_n(
    int64_t timestamp_us,
    const RtpTransceiverStatsInfo& stats,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  if (!stats.mid || !stats.transport_name) {
    return;
  }
  RTC_DCHECK(stats.track_media_info_map);
  const TrackMediaInfoMap& track_media_info_map = *stats.track_media_info_map;
  RTC_DCHECK(track_media_info_map.voice_media_info());
  std::string mid = *stats.mid;
  std::string transport_id = RTCTransportStatsIDFromTransportChannel(
      *stats.transport_name, cricket::ICE_CANDIDATE_COMPONENT_RTP);
  // Inbound and remote-outbound.
  // The remote-outbound stats are based on RTCP sender reports sent from the
  // remote endpoint providing metrics about the remote outbound streams.
  for (const cricket::VoiceReceiverInfo& voice_receiver_info :
       track_media_info_map.voice_media_info()->receivers) {
    if (!voice_receiver_info.connected())
      continue;
    // Inbound.
    auto inbound_audio =
        CreateInboundAudioStreamStats(voice_receiver_info, mid, timestamp_us);
    // TODO(hta): This lookup should look for the sender, not the track.
    rtc::scoped_refptr<AudioTrackInterface> audio_track =
        track_media_info_map.GetAudioTrack(voice_receiver_info);
    if (audio_track) {
      inbound_audio->track_id =
          RTCMediaStreamTrackStatsIDFromDirectionAndAttachment(
              kReceiver,
              track_media_info_map.GetAttachmentIdByTrack(audio_track).value());
    }
    inbound_audio->transport_id = transport_id;
    // Remote-outbound.
    auto remote_outbound_audio = CreateRemoteOutboundAudioStreamStats(
        voice_receiver_info, mid, inbound_audio->id(), transport_id);
    // Add stats.
    if (remote_outbound_audio) {
      // When the remote outbound stats are available, the remote ID for the
      // local inbound stats is set.
      inbound_audio->remote_id = remote_outbound_audio->id();
      report->AddStats(std::move(remote_outbound_audio));
    }
    report->AddStats(std::move(inbound_audio));
  }
  // Outbound.
  std::map<std::string, RTCOutboundRTPStreamStats*> audio_outbound_rtps;
  for (const cricket::VoiceSenderInfo& voice_sender_info :
       track_media_info_map.voice_media_info()->senders) {
    if (!voice_sender_info.connected())
      continue;
    auto outbound_audio = std::make_unique<RTCOutboundRTPStreamStats>(
        RTCOutboundRTPStreamStatsIDFromSSRC(cricket::MEDIA_TYPE_AUDIO,
                                            voice_sender_info.ssrc()),
        timestamp_us);
    SetOutboundRTPStreamStatsFromVoiceSenderInfo(mid, voice_sender_info,
                                                 outbound_audio.get());
    rtc::scoped_refptr<AudioTrackInterface> audio_track =
        track_media_info_map.GetAudioTrack(voice_sender_info);
    if (audio_track) {
      int attachment_id =
          track_media_info_map.GetAttachmentIdByTrack(audio_track).value();
      outbound_audio->track_id =
          RTCMediaStreamTrackStatsIDFromDirectionAndAttachment(kSender,
                                                               attachment_id);
      outbound_audio->media_source_id =
          RTCMediaSourceStatsIDFromKindAndAttachment(cricket::MEDIA_TYPE_AUDIO,
                                                     attachment_id);
    }
    outbound_audio->transport_id = transport_id;
    audio_outbound_rtps.insert(
        std::make_pair(outbound_audio->id(), outbound_audio.get()));
    report->AddStats(std::move(outbound_audio));
  }
  // Remote-inbound.
  // These are Report Block-based, information sent from the remote endpoint,
  // providing metrics about our Outbound streams. We take advantage of the fact
  // that RTCOutboundRtpStreamStats, RTCCodecStats and RTCTransport have already
  // been added to the report.
  for (const cricket::VoiceSenderInfo& voice_sender_info :
       track_media_info_map.voice_media_info()->senders) {
    for (const auto& report_block_data : voice_sender_info.report_block_datas) {
      report->AddStats(ProduceRemoteInboundRtpStreamStatsFromReportBlockData(
          report_block_data, cricket::MEDIA_TYPE_AUDIO, audio_outbound_rtps,
          *report));
    }
  }
}

void RTCStatsCollector::ProduceVideoRTPStreamStats_n(
    int64_t timestamp_us,
    const RtpTransceiverStatsInfo& stats,
    RTCStatsReport* report) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  if (!stats.mid || !stats.transport_name) {
    return;
  }
  RTC_DCHECK(stats.track_media_info_map);
  const TrackMediaInfoMap& track_media_info_map = *stats.track_media_info_map;
  RTC_DCHECK(track_media_info_map.video_media_info());
  std::string mid = *stats.mid;
  std::string transport_id = RTCTransportStatsIDFromTransportChannel(
      *stats.transport_name, cricket::ICE_CANDIDATE_COMPONENT_RTP);
  // Inbound
  for (const cricket::VideoReceiverInfo& video_receiver_info :
       track_media_info_map.video_media_info()->receivers) {
    if (!video_receiver_info.connected())
      continue;
    auto inbound_video = std::make_unique<RTCInboundRTPStreamStats>(
        RTCInboundRTPStreamStatsIDFromSSRC(cricket::MEDIA_TYPE_VIDEO,
                                           video_receiver_info.ssrc()),
        timestamp_us);
    SetInboundRTPStreamStatsFromVideoReceiverInfo(mid, video_receiver_info,
                                                  inbound_video.get());
    rtc::scoped_refptr<VideoTrackInterface> video_track =
        track_media_info_map.GetVideoTrack(video_receiver_info);
    if (video_track) {
      inbound_video->track_id =
          RTCMediaStreamTrackStatsIDFromDirectionAndAttachment(
              kReceiver,
              track_media_info_map.GetAttachmentIdByTrack(video_track).value());
    }
    inbound_video->transport_id = transport_id;
    report->AddStats(std::move(inbound_video));
    // TODO(crbug.com/webrtc/12529): Add remote-outbound stats.
  }
  // Outbound
  std::map<std::string, RTCOutboundRTPStreamStats*> video_outbound_rtps;
  for (const cricket::VideoSenderInfo& video_sender_info :
       track_media_info_map.video_media_info()->senders) {
    if (!video_sender_info.connected())
      continue;
    auto outbound_video = std::make_unique<RTCOutboundRTPStreamStats>(
        RTCOutboundRTPStreamStatsIDFromSSRC(cricket::MEDIA_TYPE_VIDEO,
                                            video_sender_info.ssrc()),
        timestamp_us);
    SetOutboundRTPStreamStatsFromVideoSenderInfo(mid, video_sender_info,
                                                 outbound_video.get());
    rtc::scoped_refptr<VideoTrackInterface> video_track =
        track_media_info_map.GetVideoTrack(video_sender_info);
    if (video_track) {
      int attachment_id =
          track_media_info_map.GetAttachmentIdByTrack(video_track).value();
      outbound_video->track_id =
          RTCMediaStreamTrackStatsIDFromDirectionAndAttachment(kSender,
                                                               attachment_id);
      outbound_video->media_source_id =
          RTCMediaSourceStatsIDFromKindAndAttachment(cricket::MEDIA_TYPE_VIDEO,
                                                     attachment_id);
    }
    outbound_video->transport_id = transport_id;
    video_outbound_rtps.insert(
        std::make_pair(outbound_video->id(), outbound_video.get()));
    report->AddStats(std::move(outbound_video));
  }
  // Remote-inbound
  // These are Report Block-based, information sent from the remote endpoint,
  // providing metrics about our Outbound streams. We take advantage of the fact
  // that RTCOutboundRtpStreamStats, RTCCodecStats and RTCTransport have already
  // been added to the report.
  for (const cricket::VideoSenderInfo& video_sender_info :
       track_media_info_map.video_media_info()->senders) {
    for (const auto& report_block_data : video_sender_info.report_block_datas) {
      report->AddStats(ProduceRemoteInboundRtpStreamStatsFromReportBlockData(
          report_block_data, cricket::MEDIA_TYPE_VIDEO, video_outbound_rtps,
          *report));
    }
  }
}

void RTCStatsCollector::ProduceTransportStats_n(
    int64_t timestamp_us,
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
    RTC_DCHECK(certificate_stats_it != transport_cert_stats.cend());
    std::string local_certificate_id;
    if (certificate_stats_it->second.local) {
      local_certificate_id = RTCCertificateIDFromFingerprint(
          certificate_stats_it->second.local->fingerprint);
    }
    std::string remote_certificate_id;
    if (certificate_stats_it->second.remote) {
      remote_certificate_id = RTCCertificateIDFromFingerprint(
          certificate_stats_it->second.remote->fingerprint);
    }

    // There is one transport stats for each channel.
    for (const cricket::TransportChannelStats& channel_stats :
         transport_stats.channel_stats) {
      std::unique_ptr<RTCTransportStats> transport_stats(
          new RTCTransportStats(RTCTransportStatsIDFromTransportChannel(
                                    transport_name, channel_stats.component),
                                timestamp_us));
      transport_stats->bytes_sent = 0;
      transport_stats->packets_sent = 0;
      transport_stats->bytes_received = 0;
      transport_stats->packets_received = 0;
      transport_stats->dtls_state =
          DtlsTransportStateToRTCDtlsTransportState(channel_stats.dtls_state);
      transport_stats->selected_candidate_pair_changes =
          channel_stats.ice_transport_stats.selected_candidate_pair_changes;
      for (const cricket::ConnectionInfo& info :
           channel_stats.ice_transport_stats.connection_infos) {
        *transport_stats->bytes_sent += info.sent_total_bytes;
        *transport_stats->packets_sent +=
            info.sent_total_packets - info.sent_discarded_packets;
        *transport_stats->bytes_received += info.recv_total_bytes;
        *transport_stats->packets_received += info.packets_received;
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
      if (channel_stats.ssl_cipher_suite != rtc::TLS_NULL_WITH_NULL_NULL &&
          rtc::SSLStreamAdapter::SslCipherSuiteToName(
              channel_stats.ssl_cipher_suite)
              .length()) {
        transport_stats->dtls_cipher =
            rtc::SSLStreamAdapter::SslCipherSuiteToName(
                channel_stats.ssl_cipher_suite);
      }
      if (channel_stats.srtp_crypto_suite != rtc::SRTP_INVALID_CRYPTO_SUITE &&
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
        transport_stats_by_name) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  std::map<std::string, CertificateStatsPair> transport_cert_stats;
  for (const auto& entry : transport_stats_by_name) {
    const std::string& transport_name = entry.first;

    CertificateStatsPair certificate_stats_pair;
    rtc::scoped_refptr<rtc::RTCCertificate> local_certificate;
    if (pc_->GetLocalCertificate(transport_name, &local_certificate)) {
      certificate_stats_pair.local =
          local_certificate->GetSSLCertificateChain().GetStats();
    }

    std::unique_ptr<rtc::SSLCertChain> remote_cert_chain =
        pc_->GetRemoteSSLCertChain(transport_name);
    if (remote_cert_chain) {
      certificate_stats_pair.remote = remote_cert_chain->GetStats();
    }

    transport_cert_stats.insert(
        std::make_pair(transport_name, std::move(certificate_stats_pair)));
  }
  return transport_cert_stats;
}

void RTCStatsCollector::PrepareTransceiverStatsInfosAndCallStats_s_w_n() {
  RTC_DCHECK_RUN_ON(signaling_thread_);

  transceiver_stats_infos_.clear();
  // These are used to invoke GetStats for all the media channels together in
  // one worker thread hop.
  std::map<cricket::VoiceMediaChannel*,
           std::unique_ptr<cricket::VoiceMediaInfo>>
      voice_stats;
  std::map<cricket::VideoMediaChannel*,
           std::unique_ptr<cricket::VideoMediaInfo>>
      video_stats;

  auto transceivers = pc_->GetTransceiversInternal();

  // TODO(tommi): See if we can avoid synchronously blocking the signaling
  // thread while we do this (or avoid the Invoke at all).
  network_thread_->Invoke<void>(RTC_FROM_HERE, [this, &transceivers,
                                                &voice_stats, &video_stats] {
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

      stats.mid = channel->content_name();
      stats.transport_name = channel->transport_name();

      if (media_type == cricket::MEDIA_TYPE_AUDIO) {
        auto* voice_channel = static_cast<cricket::VoiceChannel*>(channel);
        RTC_DCHECK(voice_stats.find(voice_channel->media_channel()) ==
                   voice_stats.end());
        voice_stats[voice_channel->media_channel()] =
            std::make_unique<cricket::VoiceMediaInfo>();
      } else if (media_type == cricket::MEDIA_TYPE_VIDEO) {
        auto* video_channel = static_cast<cricket::VideoChannel*>(channel);
        RTC_DCHECK(video_stats.find(video_channel->media_channel()) ==
                   video_stats.end());
        video_stats[video_channel->media_channel()] =
            std::make_unique<cricket::VideoMediaInfo>();
      } else {
        RTC_NOTREACHED();
      }
    }
  });

  // We jump to the worker thread and call GetStats() on each media channel as
  // well as GetCallStats(). At the same time we construct the
  // TrackMediaInfoMaps, which also needs info from the worker thread. This
  // minimizes the number of thread jumps.
  worker_thread_->Invoke<void>(RTC_FROM_HERE, [&] {
    rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

    for (const auto& entry : voice_stats) {
      if (!entry.first->GetStats(entry.second.get(),
                                 /*get_and_clear_legacy_stats=*/false)) {
        RTC_LOG(LS_WARNING) << "Failed to get voice stats.";
      }
    }
    for (const auto& entry : video_stats) {
      if (!entry.first->GetStats(entry.second.get())) {
        RTC_LOG(LS_WARNING) << "Failed to get video stats.";
      }
    }

    // Create the TrackMediaInfoMap for each transceiver stats object.
    for (auto& stats : transceiver_stats_infos_) {
      auto transceiver = stats.transceiver;
      std::unique_ptr<cricket::VoiceMediaInfo> voice_media_info;
      std::unique_ptr<cricket::VideoMediaInfo> video_media_info;
      if (transceiver->channel()) {
        cricket::MediaType media_type = transceiver->media_type();
        if (media_type == cricket::MEDIA_TYPE_AUDIO) {
          auto* voice_channel =
              static_cast<cricket::VoiceChannel*>(transceiver->channel());
          RTC_DCHECK(voice_stats[voice_channel->media_channel()]);
          voice_media_info =
              std::move(voice_stats[voice_channel->media_channel()]);
        } else if (media_type == cricket::MEDIA_TYPE_VIDEO) {
          auto* video_channel =
              static_cast<cricket::VideoChannel*>(transceiver->channel());
          RTC_DCHECK(video_stats[video_channel->media_channel()]);
          video_media_info =
              std::move(video_stats[video_channel->media_channel()]);
        }
      }
      std::vector<rtc::scoped_refptr<RtpSenderInternal>> senders;
      for (const auto& sender : transceiver->senders()) {
        senders.push_back(sender->internal());
      }
      std::vector<rtc::scoped_refptr<RtpReceiverInternal>> receivers;
      for (const auto& receiver : transceiver->receivers()) {
        receivers.push_back(receiver->internal());
      }
      stats.track_media_info_map = std::make_unique<TrackMediaInfoMap>(
          std::move(voice_media_info), std::move(video_media_info), senders,
          receivers);
    }

    call_stats_ = pc_->GetCallStats();
  });
}

void RTCStatsCollector::OnSctpDataChannelCreated(SctpDataChannel* channel) {
  channel->SignalOpened.connect(this, &RTCStatsCollector::OnDataChannelOpened);
  channel->SignalClosed.connect(this, &RTCStatsCollector::OnDataChannelClosed);
}

void RTCStatsCollector::OnDataChannelOpened(DataChannelInterface* channel) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  bool result = internal_record_.opened_data_channels
                    .insert(reinterpret_cast<uintptr_t>(channel))
                    .second;
  ++internal_record_.data_channels_opened;
  RTC_DCHECK(result);
}

void RTCStatsCollector::OnDataChannelClosed(DataChannelInterface* channel) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  // Only channels that have been fully opened (and have increased the
  // |data_channels_opened_| counter) increase the closed counter.
  if (internal_record_.opened_data_channels.erase(
          reinterpret_cast<uintptr_t>(channel))) {
    ++internal_record_.data_channels_closed;
  }
}

const char* CandidateTypeToRTCIceCandidateTypeForTesting(
    const std::string& type) {
  return CandidateTypeToRTCIceCandidateType(type);
}

const char* DataStateToRTCDataChannelStateForTesting(
    DataChannelInterface::DataState state) {
  return DataStateToRTCDataChannelState(state);
}

}  // namespace webrtc
