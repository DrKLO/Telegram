/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/stats/rtcstats_objects.h"

#include <utility>

#include "api/stats/rtc_stats.h"
#include "rtc_base/checks.h"

namespace webrtc {

const char* const RTCDataChannelState::kConnecting = "connecting";
const char* const RTCDataChannelState::kOpen = "open";
const char* const RTCDataChannelState::kClosing = "closing";
const char* const RTCDataChannelState::kClosed = "closed";

const char* const RTCStatsIceCandidatePairState::kFrozen = "frozen";
const char* const RTCStatsIceCandidatePairState::kWaiting = "waiting";
const char* const RTCStatsIceCandidatePairState::kInProgress = "in-progress";
const char* const RTCStatsIceCandidatePairState::kFailed = "failed";
const char* const RTCStatsIceCandidatePairState::kSucceeded = "succeeded";

// Strings defined in https://tools.ietf.org/html/rfc5245.
const char* const RTCIceCandidateType::kHost = "host";
const char* const RTCIceCandidateType::kSrflx = "srflx";
const char* const RTCIceCandidateType::kPrflx = "prflx";
const char* const RTCIceCandidateType::kRelay = "relay";

const char* const RTCDtlsTransportState::kNew = "new";
const char* const RTCDtlsTransportState::kConnecting = "connecting";
const char* const RTCDtlsTransportState::kConnected = "connected";
const char* const RTCDtlsTransportState::kClosed = "closed";
const char* const RTCDtlsTransportState::kFailed = "failed";

const char* const RTCMediaStreamTrackKind::kAudio = "audio";
const char* const RTCMediaStreamTrackKind::kVideo = "video";

// https://w3c.github.io/webrtc-stats/#dom-rtcnetworktype
const char* const RTCNetworkType::kBluetooth = "bluetooth";
const char* const RTCNetworkType::kCellular = "cellular";
const char* const RTCNetworkType::kEthernet = "ethernet";
const char* const RTCNetworkType::kWifi = "wifi";
const char* const RTCNetworkType::kWimax = "wimax";
const char* const RTCNetworkType::kVpn = "vpn";
const char* const RTCNetworkType::kUnknown = "unknown";

// https://w3c.github.io/webrtc-stats/#dom-rtcqualitylimitationreason
const char* const RTCQualityLimitationReason::kNone = "none";
const char* const RTCQualityLimitationReason::kCpu = "cpu";
const char* const RTCQualityLimitationReason::kBandwidth = "bandwidth";
const char* const RTCQualityLimitationReason::kOther = "other";

// https://webrtc.org/experiments/rtp-hdrext/video-content-type/
const char* const RTCContentType::kUnspecified = "unspecified";
const char* const RTCContentType::kScreenshare = "screenshare";

// https://w3c.github.io/webrtc-stats/#dom-rtcdtlsrole
const char* const RTCDtlsRole::kUnknown = "unknown";
const char* const RTCDtlsRole::kClient = "client";
const char* const RTCDtlsRole::kServer = "server";

// https://www.w3.org/TR/webrtc/#rtcicerole
const char* const RTCIceRole::kUnknown = "unknown";
const char* const RTCIceRole::kControlled = "controlled";
const char* const RTCIceRole::kControlling = "controlling";

// https://www.w3.org/TR/webrtc/#dom-rtcicetransportstate
const char* const RTCIceTransportState::kNew = "new";
const char* const RTCIceTransportState::kChecking = "checking";
const char* const RTCIceTransportState::kConnected = "connected";
const char* const RTCIceTransportState::kCompleted = "completed";
const char* const RTCIceTransportState::kDisconnected = "disconnected";
const char* const RTCIceTransportState::kFailed = "failed";
const char* const RTCIceTransportState::kClosed = "closed";

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCCertificateStats, RTCStats, "certificate",
    &fingerprint,
    &fingerprint_algorithm,
    &base64_certificate,
    &issuer_certificate_id)
// clang-format on

RTCCertificateStats::RTCCertificateStats(const std::string& id,
                                         int64_t timestamp_us)
    : RTCCertificateStats(std::string(id), timestamp_us) {}

RTCCertificateStats::RTCCertificateStats(std::string&& id, int64_t timestamp_us)
    : RTCStats(std::move(id), timestamp_us),
      fingerprint("fingerprint"),
      fingerprint_algorithm("fingerprintAlgorithm"),
      base64_certificate("base64Certificate"),
      issuer_certificate_id("issuerCertificateId") {}

RTCCertificateStats::RTCCertificateStats(const RTCCertificateStats& other) =
    default;
RTCCertificateStats::~RTCCertificateStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCCodecStats, RTCStats, "codec",
    &transport_id,
    &payload_type,
    &mime_type,
    &clock_rate,
    &channels,
    &sdp_fmtp_line)
// clang-format on

RTCCodecStats::RTCCodecStats(const std::string& id, int64_t timestamp_us)
    : RTCCodecStats(std::string(id), timestamp_us) {}

RTCCodecStats::RTCCodecStats(std::string&& id, int64_t timestamp_us)
    : RTCStats(std::move(id), timestamp_us),
      transport_id("transportId"),
      payload_type("payloadType"),
      mime_type("mimeType"),
      clock_rate("clockRate"),
      channels("channels"),
      sdp_fmtp_line("sdpFmtpLine") {}

RTCCodecStats::RTCCodecStats(const RTCCodecStats& other) = default;

RTCCodecStats::~RTCCodecStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCDataChannelStats, RTCStats, "data-channel",
    &label,
    &protocol,
    &data_channel_identifier,
    &state,
    &messages_sent,
    &bytes_sent,
    &messages_received,
    &bytes_received)
// clang-format on

RTCDataChannelStats::RTCDataChannelStats(const std::string& id,
                                         int64_t timestamp_us)
    : RTCDataChannelStats(std::string(id), timestamp_us) {}

RTCDataChannelStats::RTCDataChannelStats(std::string&& id, int64_t timestamp_us)
    : RTCStats(std::move(id), timestamp_us),
      label("label"),
      protocol("protocol"),
      data_channel_identifier("dataChannelIdentifier"),
      state("state"),
      messages_sent("messagesSent"),
      bytes_sent("bytesSent"),
      messages_received("messagesReceived"),
      bytes_received("bytesReceived") {}

RTCDataChannelStats::RTCDataChannelStats(const RTCDataChannelStats& other) =
    default;

RTCDataChannelStats::~RTCDataChannelStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCIceCandidatePairStats, RTCStats, "candidate-pair",
    &transport_id,
    &local_candidate_id,
    &remote_candidate_id,
    &state,
    &priority,
    &nominated,
    &writable,
    &packets_sent,
    &packets_received,
    &bytes_sent,
    &bytes_received,
    &total_round_trip_time,
    &current_round_trip_time,
    &available_outgoing_bitrate,
    &available_incoming_bitrate,
    &requests_received,
    &requests_sent,
    &responses_received,
    &responses_sent,
    &consent_requests_sent,
    &packets_discarded_on_send,
    &bytes_discarded_on_send)
// clang-format on

RTCIceCandidatePairStats::RTCIceCandidatePairStats(const std::string& id,
                                                   int64_t timestamp_us)
    : RTCIceCandidatePairStats(std::string(id), timestamp_us) {}

RTCIceCandidatePairStats::RTCIceCandidatePairStats(std::string&& id,
                                                   int64_t timestamp_us)
    : RTCStats(std::move(id), timestamp_us),
      transport_id("transportId"),
      local_candidate_id("localCandidateId"),
      remote_candidate_id("remoteCandidateId"),
      state("state"),
      priority("priority"),
      nominated("nominated"),
      writable("writable"),
      packets_sent("packetsSent"),
      packets_received("packetsReceived"),
      bytes_sent("bytesSent"),
      bytes_received("bytesReceived"),
      total_round_trip_time("totalRoundTripTime"),
      current_round_trip_time("currentRoundTripTime"),
      available_outgoing_bitrate("availableOutgoingBitrate"),
      available_incoming_bitrate("availableIncomingBitrate"),
      requests_received("requestsReceived"),
      requests_sent("requestsSent"),
      responses_received("responsesReceived"),
      responses_sent("responsesSent"),
      consent_requests_sent("consentRequestsSent"),
      packets_discarded_on_send("packetsDiscardedOnSend"),
      bytes_discarded_on_send("bytesDiscardedOnSend") {}

RTCIceCandidatePairStats::RTCIceCandidatePairStats(
    const RTCIceCandidatePairStats& other) = default;

RTCIceCandidatePairStats::~RTCIceCandidatePairStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCIceCandidateStats, RTCStats, "abstract-ice-candidate",
    &transport_id,
    &is_remote,
    &network_type,
    &ip,
    &address,
    &port,
    &protocol,
    &relay_protocol,
    &candidate_type,
    &priority,
    &url,
    &foundation,
    &related_address,
    &related_port,
    &username_fragment,
    &tcp_type,
    &vpn,
    &network_adapter_type)
// clang-format on

RTCIceCandidateStats::RTCIceCandidateStats(const std::string& id,
                                           int64_t timestamp_us,
                                           bool is_remote)
    : RTCIceCandidateStats(std::string(id), timestamp_us, is_remote) {}

RTCIceCandidateStats::RTCIceCandidateStats(std::string&& id,
                                           int64_t timestamp_us,
                                           bool is_remote)
    : RTCStats(std::move(id), timestamp_us),
      transport_id("transportId"),
      is_remote("isRemote", is_remote),
      network_type("networkType"),
      ip("ip"),
      address("address"),
      port("port"),
      protocol("protocol"),
      relay_protocol("relayProtocol"),
      candidate_type("candidateType"),
      priority("priority"),
      url("url"),
      foundation("foundation"),
      related_address("relatedAddress"),
      related_port("relatedPort"),
      username_fragment("usernameFragment"),
      tcp_type("tcpType"),
      vpn("vpn"),
      network_adapter_type("networkAdapterType") {}

RTCIceCandidateStats::RTCIceCandidateStats(const RTCIceCandidateStats& other) =
    default;

RTCIceCandidateStats::~RTCIceCandidateStats() {}

const char RTCLocalIceCandidateStats::kType[] = "local-candidate";

RTCLocalIceCandidateStats::RTCLocalIceCandidateStats(const std::string& id,
                                                     int64_t timestamp_us)
    : RTCIceCandidateStats(id, timestamp_us, false) {}

RTCLocalIceCandidateStats::RTCLocalIceCandidateStats(std::string&& id,
                                                     int64_t timestamp_us)
    : RTCIceCandidateStats(std::move(id), timestamp_us, false) {}

std::unique_ptr<RTCStats> RTCLocalIceCandidateStats::copy() const {
  return std::make_unique<RTCLocalIceCandidateStats>(*this);
}

const char* RTCLocalIceCandidateStats::type() const {
  return kType;
}

const char RTCRemoteIceCandidateStats::kType[] = "remote-candidate";

RTCRemoteIceCandidateStats::RTCRemoteIceCandidateStats(const std::string& id,
                                                       int64_t timestamp_us)
    : RTCIceCandidateStats(id, timestamp_us, true) {}

RTCRemoteIceCandidateStats::RTCRemoteIceCandidateStats(std::string&& id,
                                                       int64_t timestamp_us)
    : RTCIceCandidateStats(std::move(id), timestamp_us, true) {}

std::unique_ptr<RTCStats> RTCRemoteIceCandidateStats::copy() const {
  return std::make_unique<RTCRemoteIceCandidateStats>(*this);
}

const char* RTCRemoteIceCandidateStats::type() const {
  return kType;
}

// clang-format off
WEBRTC_RTCSTATS_IMPL(DEPRECATED_RTCMediaStreamStats, RTCStats, "stream",
    &stream_identifier,
    &track_ids)
// clang-format on

DEPRECATED_RTCMediaStreamStats::DEPRECATED_RTCMediaStreamStats(
    const std::string& id,
    int64_t timestamp_us)
    : DEPRECATED_RTCMediaStreamStats(std::string(id), timestamp_us) {}

DEPRECATED_RTCMediaStreamStats::DEPRECATED_RTCMediaStreamStats(
    std::string&& id,
    int64_t timestamp_us)
    : RTCStats(std::move(id), timestamp_us),
      stream_identifier("streamIdentifier"),
      track_ids("trackIds") {}

DEPRECATED_RTCMediaStreamStats::DEPRECATED_RTCMediaStreamStats(
    const DEPRECATED_RTCMediaStreamStats& other) = default;

DEPRECATED_RTCMediaStreamStats::~DEPRECATED_RTCMediaStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(DEPRECATED_RTCMediaStreamTrackStats, RTCStats, "track",
                     &track_identifier,
                     &media_source_id,
                     &remote_source,
                     &ended,
                     &detached,
                     &kind,
                     &jitter_buffer_delay,
                     &jitter_buffer_emitted_count,
                     &frame_width,
                     &frame_height,
                     &frames_sent,
                     &huge_frames_sent,
                     &frames_received,
                     &frames_decoded,
                     &frames_dropped,
                     &audio_level,
                     &total_audio_energy,
                     &echo_return_loss,
                     &echo_return_loss_enhancement,
                     &total_samples_received,
                     &total_samples_duration,
                     &concealed_samples,
                     &silent_concealed_samples,
                     &concealment_events,
                     &inserted_samples_for_deceleration,
                     &removed_samples_for_acceleration,
                     &jitter_buffer_flushes,
                     &delayed_packet_outage_samples,
                     &relative_packet_arrival_delay,
                     &interruption_count,
                     &total_interruption_duration,
                     &total_frames_duration,
                     &sum_squared_frame_durations,
                     &freeze_count,
                     &pause_count,
                     &total_freezes_duration,
                     &total_pauses_duration)
// clang-format on

DEPRECATED_RTCMediaStreamTrackStats::DEPRECATED_RTCMediaStreamTrackStats(
    const std::string& id,
    int64_t timestamp_us,
    const char* kind)
    : DEPRECATED_RTCMediaStreamTrackStats(std::string(id), timestamp_us, kind) {
}

DEPRECATED_RTCMediaStreamTrackStats::DEPRECATED_RTCMediaStreamTrackStats(
    std::string&& id,
    int64_t timestamp_us,
    const char* kind)
    : RTCStats(std::move(id), timestamp_us),
      track_identifier("trackIdentifier"),
      media_source_id("mediaSourceId"),
      remote_source("remoteSource"),
      ended("ended"),
      detached("detached"),
      kind("kind", kind),
      jitter_buffer_delay("jitterBufferDelay"),
      jitter_buffer_emitted_count("jitterBufferEmittedCount"),
      frame_width("frameWidth"),
      frame_height("frameHeight"),
      frames_sent("framesSent"),
      huge_frames_sent("hugeFramesSent"),
      frames_received("framesReceived"),
      frames_decoded("framesDecoded"),
      frames_dropped("framesDropped"),
      audio_level("audioLevel"),
      total_audio_energy("totalAudioEnergy"),
      echo_return_loss("echoReturnLoss"),
      echo_return_loss_enhancement("echoReturnLossEnhancement"),
      total_samples_received("totalSamplesReceived"),
      total_samples_duration("totalSamplesDuration"),
      concealed_samples("concealedSamples"),
      silent_concealed_samples("silentConcealedSamples"),
      concealment_events("concealmentEvents"),
      inserted_samples_for_deceleration("insertedSamplesForDeceleration"),
      removed_samples_for_acceleration("removedSamplesForAcceleration"),
      jitter_buffer_flushes(
          "jitterBufferFlushes",
          {NonStandardGroupId::kRtcAudioJitterBufferMaxPackets}),
      delayed_packet_outage_samples(
          "delayedPacketOutageSamples",
          {NonStandardGroupId::kRtcAudioJitterBufferMaxPackets,
           NonStandardGroupId::kRtcStatsRelativePacketArrivalDelay}),
      relative_packet_arrival_delay(
          "relativePacketArrivalDelay",
          {NonStandardGroupId::kRtcStatsRelativePacketArrivalDelay}),
      interruption_count("interruptionCount"),
      total_interruption_duration("totalInterruptionDuration"),
      total_frames_duration("totalFramesDuration"),
      sum_squared_frame_durations("sumOfSquaredFramesDuration"),
      freeze_count("freezeCount"),
      pause_count("pauseCount"),
      total_freezes_duration("totalFreezesDuration"),
      total_pauses_duration("totalPausesDuration") {
  RTC_DCHECK(kind == RTCMediaStreamTrackKind::kAudio ||
             kind == RTCMediaStreamTrackKind::kVideo);
}

DEPRECATED_RTCMediaStreamTrackStats::DEPRECATED_RTCMediaStreamTrackStats(
    const DEPRECATED_RTCMediaStreamTrackStats& other) = default;

DEPRECATED_RTCMediaStreamTrackStats::~DEPRECATED_RTCMediaStreamTrackStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCPeerConnectionStats, RTCStats, "peer-connection",
    &data_channels_opened,
    &data_channels_closed)
// clang-format on

RTCPeerConnectionStats::RTCPeerConnectionStats(const std::string& id,
                                               int64_t timestamp_us)
    : RTCPeerConnectionStats(std::string(id), timestamp_us) {}

RTCPeerConnectionStats::RTCPeerConnectionStats(std::string&& id,
                                               int64_t timestamp_us)
    : RTCStats(std::move(id), timestamp_us),
      data_channels_opened("dataChannelsOpened"),
      data_channels_closed("dataChannelsClosed") {}

RTCPeerConnectionStats::RTCPeerConnectionStats(
    const RTCPeerConnectionStats& other) = default;

RTCPeerConnectionStats::~RTCPeerConnectionStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCRTPStreamStats, RTCStats, "rtp",
    &ssrc,
    &kind,
    &track_id,
    &transport_id,
    &codec_id,
    &media_type)
// clang-format on

RTCRTPStreamStats::RTCRTPStreamStats(const std::string& id,
                                     int64_t timestamp_us)
    : RTCRTPStreamStats(std::string(id), timestamp_us) {}

RTCRTPStreamStats::RTCRTPStreamStats(std::string&& id, int64_t timestamp_us)
    : RTCStats(std::move(id), timestamp_us),
      ssrc("ssrc"),
      kind("kind"),
      track_id("trackId"),
      transport_id("transportId"),
      codec_id("codecId"),
      media_type("mediaType") {}

RTCRTPStreamStats::RTCRTPStreamStats(const RTCRTPStreamStats& other) = default;

RTCRTPStreamStats::~RTCRTPStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCReceivedRtpStreamStats, RTCRTPStreamStats, "received-rtp",
    &jitter,
    &packets_lost)
// clang-format on

RTCReceivedRtpStreamStats::RTCReceivedRtpStreamStats(const std::string&& id,
                                                     int64_t timestamp_us)
    : RTCReceivedRtpStreamStats(std::string(id), timestamp_us) {}

RTCReceivedRtpStreamStats::RTCReceivedRtpStreamStats(std::string&& id,
                                                     int64_t timestamp_us)
    : RTCRTPStreamStats(std::move(id), timestamp_us),
      jitter("jitter"),
      packets_lost("packetsLost") {}

RTCReceivedRtpStreamStats::RTCReceivedRtpStreamStats(
    const RTCReceivedRtpStreamStats& other) = default;

RTCReceivedRtpStreamStats::~RTCReceivedRtpStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCSentRtpStreamStats, RTCRTPStreamStats, "sent-rtp",
    &packets_sent,
    &bytes_sent)
// clang-format on

RTCSentRtpStreamStats::RTCSentRtpStreamStats(const std::string&& id,
                                             int64_t timestamp_us)
    : RTCSentRtpStreamStats(std::string(id), timestamp_us) {}

RTCSentRtpStreamStats::RTCSentRtpStreamStats(std::string&& id,
                                             int64_t timestamp_us)
    : RTCRTPStreamStats(std::move(id), timestamp_us),
      packets_sent("packetsSent"),
      bytes_sent("bytesSent") {}

RTCSentRtpStreamStats::RTCSentRtpStreamStats(
    const RTCSentRtpStreamStats& other) = default;

RTCSentRtpStreamStats::~RTCSentRtpStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCInboundRTPStreamStats, RTCReceivedRtpStreamStats, "inbound-rtp",
    &track_identifier,
    &mid,
    &remote_id,
    &packets_received,
    &packets_discarded,
    &fec_packets_received,
    &fec_packets_discarded,
    &bytes_received,
    &header_bytes_received,
    &last_packet_received_timestamp,
    &jitter_buffer_delay,
    &jitter_buffer_target_delay,
    &jitter_buffer_minimum_delay,
    &jitter_buffer_emitted_count,
    &total_samples_received,
    &concealed_samples,
    &silent_concealed_samples,
    &concealment_events,
    &inserted_samples_for_deceleration,
    &removed_samples_for_acceleration,
    &audio_level,
    &total_audio_energy,
    &total_samples_duration,
    &frames_received,
    &frame_width,
    &frame_height,
    &frames_per_second,
    &frames_decoded,
    &key_frames_decoded,
    &frames_dropped,
    &total_decode_time,
    &total_processing_delay,
    &total_assembly_time,
    &frames_assembled_from_multiple_packets,
    &total_inter_frame_delay,
    &total_squared_inter_frame_delay,
    &pause_count,
    &total_pauses_duration,
    &freeze_count,
    &total_freezes_duration,
    &content_type,
    &estimated_playout_timestamp,
    &decoder_implementation,
    &fir_count,
    &pli_count,
    &nack_count,
    &qp_sum,
    &goog_timing_frame_info,
    &jitter_buffer_flushes,
    &delayed_packet_outage_samples,
    &relative_packet_arrival_delay,
    &interruption_count,
    &total_interruption_duration,
    &min_playout_delay)
// clang-format on

RTCInboundRTPStreamStats::RTCInboundRTPStreamStats(const std::string& id,
                                                   int64_t timestamp_us)
    : RTCInboundRTPStreamStats(std::string(id), timestamp_us) {}

RTCInboundRTPStreamStats::RTCInboundRTPStreamStats(std::string&& id,
                                                   int64_t timestamp_us)
    : RTCReceivedRtpStreamStats(std::move(id), timestamp_us),
      track_identifier("trackIdentifier"),
      mid("mid"),
      remote_id("remoteId"),
      packets_received("packetsReceived"),
      packets_discarded("packetsDiscarded"),
      fec_packets_received("fecPacketsReceived"),
      fec_packets_discarded("fecPacketsDiscarded"),
      bytes_received("bytesReceived"),
      header_bytes_received("headerBytesReceived"),
      last_packet_received_timestamp("lastPacketReceivedTimestamp"),
      jitter_buffer_delay("jitterBufferDelay"),
      jitter_buffer_target_delay("jitterBufferTargetDelay"),
      jitter_buffer_minimum_delay("jitterBufferMinimumDelay"),
      jitter_buffer_emitted_count("jitterBufferEmittedCount"),
      total_samples_received("totalSamplesReceived"),
      concealed_samples("concealedSamples"),
      silent_concealed_samples("silentConcealedSamples"),
      concealment_events("concealmentEvents"),
      inserted_samples_for_deceleration("insertedSamplesForDeceleration"),
      removed_samples_for_acceleration("removedSamplesForAcceleration"),
      audio_level("audioLevel"),
      total_audio_energy("totalAudioEnergy"),
      total_samples_duration("totalSamplesDuration"),
      frames_received("framesReceived"),
      frame_width("frameWidth"),
      frame_height("frameHeight"),
      frames_per_second("framesPerSecond"),
      frames_decoded("framesDecoded"),
      key_frames_decoded("keyFramesDecoded"),
      frames_dropped("framesDropped"),
      total_decode_time("totalDecodeTime"),
      total_processing_delay("totalProcessingDelay"),
      total_assembly_time("totalAssemblyTime"),
      frames_assembled_from_multiple_packets(
          "framesAssembledFromMultiplePackets"),
      total_inter_frame_delay("totalInterFrameDelay"),
      total_squared_inter_frame_delay("totalSquaredInterFrameDelay"),
      pause_count("pauseCount"),
      total_pauses_duration("totalPausesDuration"),
      freeze_count("freezeCount"),
      total_freezes_duration("totalFreezesDuration"),
      content_type("contentType"),
      estimated_playout_timestamp("estimatedPlayoutTimestamp"),
      decoder_implementation("decoderImplementation"),
      fir_count("firCount"),
      pli_count("pliCount"),
      nack_count("nackCount"),
      qp_sum("qpSum"),
      goog_timing_frame_info("googTimingFrameInfo"),
      jitter_buffer_flushes(
          "jitterBufferFlushes",
          {NonStandardGroupId::kRtcAudioJitterBufferMaxPackets}),
      delayed_packet_outage_samples(
          "delayedPacketOutageSamples",
          {NonStandardGroupId::kRtcAudioJitterBufferMaxPackets,
           NonStandardGroupId::kRtcStatsRelativePacketArrivalDelay}),
      relative_packet_arrival_delay(
          "relativePacketArrivalDelay",
          {NonStandardGroupId::kRtcStatsRelativePacketArrivalDelay}),
      interruption_count("interruptionCount"),
      total_interruption_duration("totalInterruptionDuration"),
      min_playout_delay("minPlayoutDelay") {}

RTCInboundRTPStreamStats::RTCInboundRTPStreamStats(
    const RTCInboundRTPStreamStats& other) = default;
RTCInboundRTPStreamStats::~RTCInboundRTPStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCOutboundRTPStreamStats, RTCRTPStreamStats, "outbound-rtp",
    &media_source_id,
    &remote_id,
    &mid,
    &rid,
    &packets_sent,
    &retransmitted_packets_sent,
    &bytes_sent,
    &header_bytes_sent,
    &retransmitted_bytes_sent,
    &target_bitrate,
    &frames_encoded,
    &key_frames_encoded,
    &total_encode_time,
    &total_encoded_bytes_target,
    &frame_width,
    &frame_height,
    &frames_per_second,
    &frames_sent,
    &huge_frames_sent,
    &total_packet_send_delay,
    &quality_limitation_reason,
    &quality_limitation_durations,
    &quality_limitation_resolution_changes,
    &content_type,
    &encoder_implementation,
    &fir_count,
    &pli_count,
    &nack_count,
    &qp_sum,
    &active)
// clang-format on

RTCOutboundRTPStreamStats::RTCOutboundRTPStreamStats(const std::string& id,
                                                     int64_t timestamp_us)
    : RTCOutboundRTPStreamStats(std::string(id), timestamp_us) {}

RTCOutboundRTPStreamStats::RTCOutboundRTPStreamStats(std::string&& id,
                                                     int64_t timestamp_us)
    : RTCRTPStreamStats(std::move(id), timestamp_us),
      media_source_id("mediaSourceId"),
      remote_id("remoteId"),
      mid("mid"),
      rid("rid"),
      packets_sent("packetsSent"),
      retransmitted_packets_sent("retransmittedPacketsSent"),
      bytes_sent("bytesSent"),
      header_bytes_sent("headerBytesSent"),
      retransmitted_bytes_sent("retransmittedBytesSent"),
      target_bitrate("targetBitrate"),
      frames_encoded("framesEncoded"),
      key_frames_encoded("keyFramesEncoded"),
      total_encode_time("totalEncodeTime"),
      total_encoded_bytes_target("totalEncodedBytesTarget"),
      frame_width("frameWidth"),
      frame_height("frameHeight"),
      frames_per_second("framesPerSecond"),
      frames_sent("framesSent"),
      huge_frames_sent("hugeFramesSent"),
      total_packet_send_delay("totalPacketSendDelay"),
      quality_limitation_reason("qualityLimitationReason"),
      quality_limitation_durations("qualityLimitationDurations"),
      quality_limitation_resolution_changes(
          "qualityLimitationResolutionChanges"),
      content_type("contentType"),
      encoder_implementation("encoderImplementation"),
      fir_count("firCount"),
      pli_count("pliCount"),
      nack_count("nackCount"),
      qp_sum("qpSum"),
      active("active") {}

RTCOutboundRTPStreamStats::RTCOutboundRTPStreamStats(
    const RTCOutboundRTPStreamStats& other) = default;

RTCOutboundRTPStreamStats::~RTCOutboundRTPStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCRemoteInboundRtpStreamStats, RTCReceivedRtpStreamStats,
        "remote-inbound-rtp",
    &local_id,
    &round_trip_time,
    &fraction_lost,
    &total_round_trip_time,
    &round_trip_time_measurements)
// clang-format on

RTCRemoteInboundRtpStreamStats::RTCRemoteInboundRtpStreamStats(
    const std::string& id,
    int64_t timestamp_us)
    : RTCRemoteInboundRtpStreamStats(std::string(id), timestamp_us) {}

RTCRemoteInboundRtpStreamStats::RTCRemoteInboundRtpStreamStats(
    std::string&& id,
    int64_t timestamp_us)
    : RTCReceivedRtpStreamStats(std::move(id), timestamp_us),
      local_id("localId"),
      round_trip_time("roundTripTime"),
      fraction_lost("fractionLost"),
      total_round_trip_time("totalRoundTripTime"),
      round_trip_time_measurements("roundTripTimeMeasurements") {}

RTCRemoteInboundRtpStreamStats::RTCRemoteInboundRtpStreamStats(
    const RTCRemoteInboundRtpStreamStats& other) = default;

RTCRemoteInboundRtpStreamStats::~RTCRemoteInboundRtpStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCRemoteOutboundRtpStreamStats, RTCSentRtpStreamStats,
    "remote-outbound-rtp",
    &local_id,
    &remote_timestamp,
    &reports_sent,
    &round_trip_time,
    &round_trip_time_measurements,
    &total_round_trip_time)
// clang-format on

RTCRemoteOutboundRtpStreamStats::RTCRemoteOutboundRtpStreamStats(
    const std::string& id,
    int64_t timestamp_us)
    : RTCRemoteOutboundRtpStreamStats(std::string(id), timestamp_us) {}

RTCRemoteOutboundRtpStreamStats::RTCRemoteOutboundRtpStreamStats(
    std::string&& id,
    int64_t timestamp_us)
    : RTCSentRtpStreamStats(std::move(id), timestamp_us),
      local_id("localId"),
      remote_timestamp("remoteTimestamp"),
      reports_sent("reportsSent"),
      round_trip_time("roundTripTime"),
      round_trip_time_measurements("roundTripTimeMeasurements"),
      total_round_trip_time("totalRoundTripTime") {}

RTCRemoteOutboundRtpStreamStats::RTCRemoteOutboundRtpStreamStats(
    const RTCRemoteOutboundRtpStreamStats& other) = default;

RTCRemoteOutboundRtpStreamStats::~RTCRemoteOutboundRtpStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCMediaSourceStats, RTCStats, "parent-media-source",
    &track_identifier,
    &kind)
// clang-format on

RTCMediaSourceStats::RTCMediaSourceStats(const std::string& id,
                                         int64_t timestamp_us)
    : RTCMediaSourceStats(std::string(id), timestamp_us) {}

RTCMediaSourceStats::RTCMediaSourceStats(std::string&& id, int64_t timestamp_us)
    : RTCStats(std::move(id), timestamp_us),
      track_identifier("trackIdentifier"),
      kind("kind") {}

RTCMediaSourceStats::RTCMediaSourceStats(const RTCMediaSourceStats& other) =
    default;

RTCMediaSourceStats::~RTCMediaSourceStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCAudioSourceStats, RTCMediaSourceStats, "media-source",
    &audio_level,
    &total_audio_energy,
    &total_samples_duration,
    &echo_return_loss,
    &echo_return_loss_enhancement)
// clang-format on

RTCAudioSourceStats::RTCAudioSourceStats(const std::string& id,
                                         int64_t timestamp_us)
    : RTCAudioSourceStats(std::string(id), timestamp_us) {}

RTCAudioSourceStats::RTCAudioSourceStats(std::string&& id, int64_t timestamp_us)
    : RTCMediaSourceStats(std::move(id), timestamp_us),
      audio_level("audioLevel"),
      total_audio_energy("totalAudioEnergy"),
      total_samples_duration("totalSamplesDuration"),
      echo_return_loss("echoReturnLoss"),
      echo_return_loss_enhancement("echoReturnLossEnhancement") {}

RTCAudioSourceStats::RTCAudioSourceStats(const RTCAudioSourceStats& other) =
    default;

RTCAudioSourceStats::~RTCAudioSourceStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCVideoSourceStats, RTCMediaSourceStats, "media-source",
    &width,
    &height,
    &frames,
    &frames_per_second)
// clang-format on

RTCVideoSourceStats::RTCVideoSourceStats(const std::string& id,
                                         int64_t timestamp_us)
    : RTCVideoSourceStats(std::string(id), timestamp_us) {}

RTCVideoSourceStats::RTCVideoSourceStats(std::string&& id, int64_t timestamp_us)
    : RTCMediaSourceStats(std::move(id), timestamp_us),
      width("width"),
      height("height"),
      frames("frames"),
      frames_per_second("framesPerSecond") {}

RTCVideoSourceStats::RTCVideoSourceStats(const RTCVideoSourceStats& other) =
    default;

RTCVideoSourceStats::~RTCVideoSourceStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCTransportStats, RTCStats, "transport",
    &bytes_sent,
    &packets_sent,
    &bytes_received,
    &packets_received,
    &rtcp_transport_stats_id,
    &dtls_state,
    &selected_candidate_pair_id,
    &local_certificate_id,
    &remote_certificate_id,
    &tls_version,
    &dtls_cipher,
    &dtls_role,
    &srtp_cipher,
    &selected_candidate_pair_changes,
    &ice_role,
    &ice_local_username_fragment,
    &ice_state)
// clang-format on

RTCTransportStats::RTCTransportStats(const std::string& id,
                                     int64_t timestamp_us)
    : RTCTransportStats(std::string(id), timestamp_us) {}

RTCTransportStats::RTCTransportStats(std::string&& id, int64_t timestamp_us)
    : RTCStats(std::move(id), timestamp_us),
      bytes_sent("bytesSent"),
      packets_sent("packetsSent"),
      bytes_received("bytesReceived"),
      packets_received("packetsReceived"),
      rtcp_transport_stats_id("rtcpTransportStatsId"),
      dtls_state("dtlsState"),
      selected_candidate_pair_id("selectedCandidatePairId"),
      local_certificate_id("localCertificateId"),
      remote_certificate_id("remoteCertificateId"),
      tls_version("tlsVersion"),
      dtls_cipher("dtlsCipher"),
      dtls_role("dtlsRole"),
      srtp_cipher("srtpCipher"),
      selected_candidate_pair_changes("selectedCandidatePairChanges"),
      ice_role("iceRole"),
      ice_local_username_fragment("iceLocalUsernameFragment"),
      ice_state("iceState") {}

RTCTransportStats::RTCTransportStats(const RTCTransportStats& other) = default;

RTCTransportStats::~RTCTransportStats() {}

}  // namespace webrtc
