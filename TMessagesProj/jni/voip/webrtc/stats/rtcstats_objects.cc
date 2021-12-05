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

RTCCertificateStats::RTCCertificateStats(const RTCCertificateStats& other)
    : RTCStats(other.id(), other.timestamp_us()),
      fingerprint(other.fingerprint),
      fingerprint_algorithm(other.fingerprint_algorithm),
      base64_certificate(other.base64_certificate),
      issuer_certificate_id(other.issuer_certificate_id) {}

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

RTCCodecStats::RTCCodecStats(const RTCCodecStats& other)
    : RTCStats(other.id(), other.timestamp_us()),
      transport_id(other.transport_id),
      payload_type(other.payload_type),
      mime_type(other.mime_type),
      clock_rate(other.clock_rate),
      channels(other.channels),
      sdp_fmtp_line(other.sdp_fmtp_line) {}

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

RTCDataChannelStats::RTCDataChannelStats(const RTCDataChannelStats& other)
    : RTCStats(other.id(), other.timestamp_us()),
      label(other.label),
      protocol(other.protocol),
      data_channel_identifier(other.data_channel_identifier),
      state(other.state),
      messages_sent(other.messages_sent),
      bytes_sent(other.bytes_sent),
      messages_received(other.messages_received),
      bytes_received(other.bytes_received) {}

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
    &readable,
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
    &retransmissions_received,
    &retransmissions_sent,
    &consent_requests_received,
    &consent_requests_sent,
    &consent_responses_received,
    &consent_responses_sent)
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
      readable("readable"),
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
      retransmissions_received("retransmissionsReceived"),
      retransmissions_sent("retransmissionsSent"),
      consent_requests_received("consentRequestsReceived"),
      consent_requests_sent("consentRequestsSent"),
      consent_responses_received("consentResponsesReceived"),
      consent_responses_sent("consentResponsesSent") {}

RTCIceCandidatePairStats::RTCIceCandidatePairStats(
    const RTCIceCandidatePairStats& other)
    : RTCStats(other.id(), other.timestamp_us()),
      transport_id(other.transport_id),
      local_candidate_id(other.local_candidate_id),
      remote_candidate_id(other.remote_candidate_id),
      state(other.state),
      priority(other.priority),
      nominated(other.nominated),
      writable(other.writable),
      readable(other.readable),
      bytes_sent(other.bytes_sent),
      bytes_received(other.bytes_received),
      total_round_trip_time(other.total_round_trip_time),
      current_round_trip_time(other.current_round_trip_time),
      available_outgoing_bitrate(other.available_outgoing_bitrate),
      available_incoming_bitrate(other.available_incoming_bitrate),
      requests_received(other.requests_received),
      requests_sent(other.requests_sent),
      responses_received(other.responses_received),
      responses_sent(other.responses_sent),
      retransmissions_received(other.retransmissions_received),
      retransmissions_sent(other.retransmissions_sent),
      consent_requests_received(other.consent_requests_received),
      consent_requests_sent(other.consent_requests_sent),
      consent_responses_received(other.consent_responses_received),
      consent_responses_sent(other.consent_responses_sent) {}

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
    &url)
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
      url("url") {}

RTCIceCandidateStats::RTCIceCandidateStats(const RTCIceCandidateStats& other)
    : RTCStats(other.id(), other.timestamp_us()),
      transport_id(other.transport_id),
      is_remote(other.is_remote),
      network_type(other.network_type),
      ip(other.ip),
      address(other.address),
      port(other.port),
      protocol(other.protocol),
      relay_protocol(other.relay_protocol),
      candidate_type(other.candidate_type),
      priority(other.priority),
      url(other.url) {}

RTCIceCandidateStats::~RTCIceCandidateStats() {}

const char RTCLocalIceCandidateStats::kType[] = "local-candidate";

RTCLocalIceCandidateStats::RTCLocalIceCandidateStats(const std::string& id,
                                                     int64_t timestamp_us)
    : RTCIceCandidateStats(id, timestamp_us, false) {}

RTCLocalIceCandidateStats::RTCLocalIceCandidateStats(std::string&& id,
                                                     int64_t timestamp_us)
    : RTCIceCandidateStats(std::move(id), timestamp_us, false) {}

std::unique_ptr<RTCStats> RTCLocalIceCandidateStats::copy() const {
  return std::unique_ptr<RTCStats>(new RTCLocalIceCandidateStats(*this));
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
  return std::unique_ptr<RTCStats>(new RTCRemoteIceCandidateStats(*this));
}

const char* RTCRemoteIceCandidateStats::type() const {
  return kType;
}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCMediaStreamStats, RTCStats, "stream",
    &stream_identifier,
    &track_ids)
// clang-format on

RTCMediaStreamStats::RTCMediaStreamStats(const std::string& id,
                                         int64_t timestamp_us)
    : RTCMediaStreamStats(std::string(id), timestamp_us) {}

RTCMediaStreamStats::RTCMediaStreamStats(std::string&& id, int64_t timestamp_us)
    : RTCStats(std::move(id), timestamp_us),
      stream_identifier("streamIdentifier"),
      track_ids("trackIds") {}

RTCMediaStreamStats::RTCMediaStreamStats(const RTCMediaStreamStats& other)
    : RTCStats(other.id(), other.timestamp_us()),
      stream_identifier(other.stream_identifier),
      track_ids(other.track_ids) {}

RTCMediaStreamStats::~RTCMediaStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCMediaStreamTrackStats, RTCStats, "track",
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
                     &frames_per_second,
                     &frames_sent,
                     &huge_frames_sent,
                     &frames_received,
                     &frames_decoded,
                     &frames_dropped,
                     &frames_corrupted,
                     &partial_frames_lost,
                     &full_frames_lost,
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
                     &jitter_buffer_target_delay,
                     &interruption_count,
                     &total_interruption_duration,
                     &freeze_count,
                     &pause_count,
                     &total_freezes_duration,
                     &total_pauses_duration,
                     &total_frames_duration,
                     &sum_squared_frame_durations)
// clang-format on

RTCMediaStreamTrackStats::RTCMediaStreamTrackStats(const std::string& id,
                                                   int64_t timestamp_us,
                                                   const char* kind)
    : RTCMediaStreamTrackStats(std::string(id), timestamp_us, kind) {}

RTCMediaStreamTrackStats::RTCMediaStreamTrackStats(std::string&& id,
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
      frames_per_second("framesPerSecond"),
      frames_sent("framesSent"),
      huge_frames_sent("hugeFramesSent"),
      frames_received("framesReceived"),
      frames_decoded("framesDecoded"),
      frames_dropped("framesDropped"),
      frames_corrupted("framesCorrupted"),
      partial_frames_lost("partialFramesLost"),
      full_frames_lost("fullFramesLost"),
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
      jitter_buffer_target_delay("jitterBufferTargetDelay"),
      interruption_count("interruptionCount"),
      total_interruption_duration("totalInterruptionDuration"),
      freeze_count("freezeCount"),
      pause_count("pauseCount"),
      total_freezes_duration("totalFreezesDuration"),
      total_pauses_duration("totalPausesDuration"),
      total_frames_duration("totalFramesDuration"),
      sum_squared_frame_durations("sumOfSquaredFramesDuration") {
  RTC_DCHECK(kind == RTCMediaStreamTrackKind::kAudio ||
             kind == RTCMediaStreamTrackKind::kVideo);
}

RTCMediaStreamTrackStats::RTCMediaStreamTrackStats(
    const RTCMediaStreamTrackStats& other)
    : RTCStats(other.id(), other.timestamp_us()),
      track_identifier(other.track_identifier),
      media_source_id(other.media_source_id),
      remote_source(other.remote_source),
      ended(other.ended),
      detached(other.detached),
      kind(other.kind),
      jitter_buffer_delay(other.jitter_buffer_delay),
      jitter_buffer_emitted_count(other.jitter_buffer_emitted_count),
      frame_width(other.frame_width),
      frame_height(other.frame_height),
      frames_per_second(other.frames_per_second),
      frames_sent(other.frames_sent),
      huge_frames_sent(other.huge_frames_sent),
      frames_received(other.frames_received),
      frames_decoded(other.frames_decoded),
      frames_dropped(other.frames_dropped),
      frames_corrupted(other.frames_corrupted),
      partial_frames_lost(other.partial_frames_lost),
      full_frames_lost(other.full_frames_lost),
      audio_level(other.audio_level),
      total_audio_energy(other.total_audio_energy),
      echo_return_loss(other.echo_return_loss),
      echo_return_loss_enhancement(other.echo_return_loss_enhancement),
      total_samples_received(other.total_samples_received),
      total_samples_duration(other.total_samples_duration),
      concealed_samples(other.concealed_samples),
      silent_concealed_samples(other.silent_concealed_samples),
      concealment_events(other.concealment_events),
      inserted_samples_for_deceleration(
          other.inserted_samples_for_deceleration),
      removed_samples_for_acceleration(other.removed_samples_for_acceleration),
      jitter_buffer_flushes(other.jitter_buffer_flushes),
      delayed_packet_outage_samples(other.delayed_packet_outage_samples),
      relative_packet_arrival_delay(other.relative_packet_arrival_delay),
      jitter_buffer_target_delay(other.jitter_buffer_target_delay),
      interruption_count(other.interruption_count),
      total_interruption_duration(other.total_interruption_duration),
      freeze_count(other.freeze_count),
      pause_count(other.pause_count),
      total_freezes_duration(other.total_freezes_duration),
      total_pauses_duration(other.total_pauses_duration),
      total_frames_duration(other.total_frames_duration),
      sum_squared_frame_durations(other.sum_squared_frame_durations) {}

RTCMediaStreamTrackStats::~RTCMediaStreamTrackStats() {}

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
    const RTCPeerConnectionStats& other)
    : RTCStats(other.id(), other.timestamp_us()),
      data_channels_opened(other.data_channels_opened),
      data_channels_closed(other.data_channels_closed) {}

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

RTCRTPStreamStats::RTCRTPStreamStats(const RTCRTPStreamStats& other)
    : RTCStats(other.id(), other.timestamp_us()),
      ssrc(other.ssrc),
      kind(other.kind),
      track_id(other.track_id),
      transport_id(other.transport_id),
      codec_id(other.codec_id),
      media_type(other.media_type) {}

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
    const RTCReceivedRtpStreamStats& other)
    : RTCRTPStreamStats(other),
      jitter(other.jitter),
      packets_lost(other.packets_lost) {}

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

RTCSentRtpStreamStats::RTCSentRtpStreamStats(const RTCSentRtpStreamStats& other)
    : RTCRTPStreamStats(other),
      packets_sent(other.packets_sent),
      bytes_sent(other.bytes_sent) {}

RTCSentRtpStreamStats::~RTCSentRtpStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCInboundRTPStreamStats, RTCReceivedRtpStreamStats, "inbound-rtp",
    &remote_id,
    &packets_received,
    &fec_packets_received,
    &fec_packets_discarded,
    &bytes_received,
    &header_bytes_received,
    &last_packet_received_timestamp,
    &jitter_buffer_delay,
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
    &round_trip_time,
    &packets_discarded,
    &packets_repaired,
    &burst_packets_lost,
    &burst_packets_discarded,
    &burst_loss_count,
    &burst_discard_count,
    &burst_loss_rate,
    &burst_discard_rate,
    &gap_loss_rate,
    &gap_discard_rate,
    &frame_width,
    &frame_height,
    &frame_bit_depth,
    &frames_per_second,
    &frames_decoded,
    &key_frames_decoded,
    &frames_dropped,
    &total_decode_time,
    &total_inter_frame_delay,
    &total_squared_inter_frame_delay,
    &content_type,
    &estimated_playout_timestamp,
    &decoder_implementation,
    &fir_count,
    &pli_count,
    &nack_count,
    &qp_sum)
// clang-format on

RTCInboundRTPStreamStats::RTCInboundRTPStreamStats(const std::string& id,
                                                   int64_t timestamp_us)
    : RTCInboundRTPStreamStats(std::string(id), timestamp_us) {}

RTCInboundRTPStreamStats::RTCInboundRTPStreamStats(std::string&& id,
                                                   int64_t timestamp_us)
    : RTCReceivedRtpStreamStats(std::move(id), timestamp_us),
      remote_id("remoteId"),
      packets_received("packetsReceived"),
      fec_packets_received("fecPacketsReceived"),
      fec_packets_discarded("fecPacketsDiscarded"),
      bytes_received("bytesReceived"),
      header_bytes_received("headerBytesReceived"),
      last_packet_received_timestamp("lastPacketReceivedTimestamp"),
      jitter_buffer_delay("jitterBufferDelay"),
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
      round_trip_time("roundTripTime"),
      packets_discarded("packetsDiscarded"),
      packets_repaired("packetsRepaired"),
      burst_packets_lost("burstPacketsLost"),
      burst_packets_discarded("burstPacketsDiscarded"),
      burst_loss_count("burstLossCount"),
      burst_discard_count("burstDiscardCount"),
      burst_loss_rate("burstLossRate"),
      burst_discard_rate("burstDiscardRate"),
      gap_loss_rate("gapLossRate"),
      gap_discard_rate("gapDiscardRate"),
      frame_width("frameWidth"),
      frame_height("frameHeight"),
      frame_bit_depth("frameBitDepth"),
      frames_per_second("framesPerSecond"),
      frames_decoded("framesDecoded"),
      key_frames_decoded("keyFramesDecoded"),
      frames_dropped("framesDropped"),
      total_decode_time("totalDecodeTime"),
      total_inter_frame_delay("totalInterFrameDelay"),
      total_squared_inter_frame_delay("totalSquaredInterFrameDelay"),
      content_type("contentType"),
      estimated_playout_timestamp("estimatedPlayoutTimestamp"),
      decoder_implementation("decoderImplementation"),
      fir_count("firCount"),
      pli_count("pliCount"),
      nack_count("nackCount"),
      qp_sum("qpSum") {}

RTCInboundRTPStreamStats::RTCInboundRTPStreamStats(
    const RTCInboundRTPStreamStats& other)
    : RTCReceivedRtpStreamStats(other),
      remote_id(other.remote_id),
      packets_received(other.packets_received),
      fec_packets_received(other.fec_packets_received),
      fec_packets_discarded(other.fec_packets_discarded),
      bytes_received(other.bytes_received),
      header_bytes_received(other.header_bytes_received),
      last_packet_received_timestamp(other.last_packet_received_timestamp),
      jitter_buffer_delay(other.jitter_buffer_delay),
      jitter_buffer_emitted_count(other.jitter_buffer_emitted_count),
      total_samples_received(other.total_samples_received),
      concealed_samples(other.concealed_samples),
      silent_concealed_samples(other.silent_concealed_samples),
      concealment_events(other.concealment_events),
      inserted_samples_for_deceleration(
          other.inserted_samples_for_deceleration),
      removed_samples_for_acceleration(other.removed_samples_for_acceleration),
      audio_level(other.audio_level),
      total_audio_energy(other.total_audio_energy),
      total_samples_duration(other.total_samples_duration),
      frames_received(other.frames_received),
      round_trip_time(other.round_trip_time),
      packets_discarded(other.packets_discarded),
      packets_repaired(other.packets_repaired),
      burst_packets_lost(other.burst_packets_lost),
      burst_packets_discarded(other.burst_packets_discarded),
      burst_loss_count(other.burst_loss_count),
      burst_discard_count(other.burst_discard_count),
      burst_loss_rate(other.burst_loss_rate),
      burst_discard_rate(other.burst_discard_rate),
      gap_loss_rate(other.gap_loss_rate),
      gap_discard_rate(other.gap_discard_rate),
      frame_width(other.frame_width),
      frame_height(other.frame_height),
      frame_bit_depth(other.frame_bit_depth),
      frames_per_second(other.frames_per_second),
      frames_decoded(other.frames_decoded),
      key_frames_decoded(other.key_frames_decoded),
      frames_dropped(other.frames_dropped),
      total_decode_time(other.total_decode_time),
      total_inter_frame_delay(other.total_inter_frame_delay),
      total_squared_inter_frame_delay(other.total_squared_inter_frame_delay),
      content_type(other.content_type),
      estimated_playout_timestamp(other.estimated_playout_timestamp),
      decoder_implementation(other.decoder_implementation),
      fir_count(other.fir_count),
      pli_count(other.pli_count),
      nack_count(other.nack_count),
      qp_sum(other.qp_sum) {}

RTCInboundRTPStreamStats::~RTCInboundRTPStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCOutboundRTPStreamStats, RTCRTPStreamStats, "outbound-rtp",
    &media_source_id,
    &remote_id,
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
    &quality_limitation_resolution_changes,
    &content_type,
    &encoder_implementation,
    &fir_count,
    &pli_count,
    &nack_count,
    &qp_sum)
// clang-format on

RTCOutboundRTPStreamStats::RTCOutboundRTPStreamStats(const std::string& id,
                                                     int64_t timestamp_us)
    : RTCOutboundRTPStreamStats(std::string(id), timestamp_us) {}

RTCOutboundRTPStreamStats::RTCOutboundRTPStreamStats(std::string&& id,
                                                     int64_t timestamp_us)
    : RTCRTPStreamStats(std::move(id), timestamp_us),
      media_source_id("mediaSourceId"),
      remote_id("remoteId"),
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
      quality_limitation_resolution_changes(
          "qualityLimitationResolutionChanges"),
      content_type("contentType"),
      encoder_implementation("encoderImplementation"),
      fir_count("firCount"),
      pli_count("pliCount"),
      nack_count("nackCount"),
      qp_sum("qpSum") {}

RTCOutboundRTPStreamStats::RTCOutboundRTPStreamStats(
    const RTCOutboundRTPStreamStats& other)
    : RTCRTPStreamStats(other),
      media_source_id(other.media_source_id),
      remote_id(other.remote_id),
      rid(other.rid),
      packets_sent(other.packets_sent),
      retransmitted_packets_sent(other.retransmitted_packets_sent),
      bytes_sent(other.bytes_sent),
      header_bytes_sent(other.header_bytes_sent),
      retransmitted_bytes_sent(other.retransmitted_bytes_sent),
      target_bitrate(other.target_bitrate),
      frames_encoded(other.frames_encoded),
      key_frames_encoded(other.key_frames_encoded),
      total_encode_time(other.total_encode_time),
      total_encoded_bytes_target(other.total_encoded_bytes_target),
      frame_width(other.frame_width),
      frame_height(other.frame_height),
      frames_per_second(other.frames_per_second),
      frames_sent(other.frames_sent),
      huge_frames_sent(other.huge_frames_sent),
      total_packet_send_delay(other.total_packet_send_delay),
      quality_limitation_reason(other.quality_limitation_reason),
      quality_limitation_resolution_changes(
          other.quality_limitation_resolution_changes),
      content_type(other.content_type),
      encoder_implementation(other.encoder_implementation),
      fir_count(other.fir_count),
      pli_count(other.pli_count),
      nack_count(other.nack_count),
      qp_sum(other.qp_sum) {}

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
    const RTCRemoteInboundRtpStreamStats& other)
    : RTCReceivedRtpStreamStats(other),
      local_id(other.local_id),
      round_trip_time(other.round_trip_time),
      fraction_lost(other.fraction_lost),
      total_round_trip_time(other.total_round_trip_time),
      round_trip_time_measurements(other.round_trip_time_measurements) {}

RTCRemoteInboundRtpStreamStats::~RTCRemoteInboundRtpStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCRemoteOutboundRtpStreamStats, RTCSentRtpStreamStats,
    "remote-outbound-rtp",
    &local_id,
    &remote_timestamp,
    &reports_sent)
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
      reports_sent("reportsSent") {}

RTCRemoteOutboundRtpStreamStats::RTCRemoteOutboundRtpStreamStats(
    const RTCRemoteOutboundRtpStreamStats& other)
    : RTCSentRtpStreamStats(other),
      local_id(other.local_id),
      remote_timestamp(other.remote_timestamp),
      reports_sent(other.reports_sent) {}

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

RTCMediaSourceStats::RTCMediaSourceStats(const RTCMediaSourceStats& other)
    : RTCStats(other.id(), other.timestamp_us()),
      track_identifier(other.track_identifier),
      kind(other.kind) {}

RTCMediaSourceStats::~RTCMediaSourceStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCAudioSourceStats, RTCMediaSourceStats, "media-source",
    &audio_level,
    &total_audio_energy,
    &total_samples_duration)
// clang-format on

RTCAudioSourceStats::RTCAudioSourceStats(const std::string& id,
                                         int64_t timestamp_us)
    : RTCAudioSourceStats(std::string(id), timestamp_us) {}

RTCAudioSourceStats::RTCAudioSourceStats(std::string&& id, int64_t timestamp_us)
    : RTCMediaSourceStats(std::move(id), timestamp_us),
      audio_level("audioLevel"),
      total_audio_energy("totalAudioEnergy"),
      total_samples_duration("totalSamplesDuration") {}

RTCAudioSourceStats::RTCAudioSourceStats(const RTCAudioSourceStats& other)
    : RTCMediaSourceStats(other),
      audio_level(other.audio_level),
      total_audio_energy(other.total_audio_energy),
      total_samples_duration(other.total_samples_duration) {}

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

RTCVideoSourceStats::RTCVideoSourceStats(const RTCVideoSourceStats& other)
    : RTCMediaSourceStats(other),
      width(other.width),
      height(other.height),
      frames(other.frames),
      frames_per_second(other.frames_per_second) {}

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
    &srtp_cipher,
    &selected_candidate_pair_changes)
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
      srtp_cipher("srtpCipher"),
      selected_candidate_pair_changes("selectedCandidatePairChanges") {}

RTCTransportStats::RTCTransportStats(const RTCTransportStats& other)
    : RTCStats(other.id(), other.timestamp_us()),
      bytes_sent(other.bytes_sent),
      packets_sent(other.packets_sent),
      bytes_received(other.bytes_received),
      packets_received(other.packets_received),
      rtcp_transport_stats_id(other.rtcp_transport_stats_id),
      dtls_state(other.dtls_state),
      selected_candidate_pair_id(other.selected_candidate_pair_id),
      local_certificate_id(other.local_certificate_id),
      remote_certificate_id(other.remote_certificate_id),
      tls_version(other.tls_version),
      dtls_cipher(other.dtls_cipher),
      srtp_cipher(other.srtp_cipher),
      selected_candidate_pair_changes(other.selected_candidate_pair_changes) {}

RTCTransportStats::~RTCTransportStats() {}

}  // namespace webrtc
