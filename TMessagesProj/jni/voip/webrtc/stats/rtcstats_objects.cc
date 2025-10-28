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

#include "api/stats/attribute.h"
#include "api/stats/rtc_stats.h"
#include "rtc_base/checks.h"

namespace webrtc {

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCCertificateStats, RTCStats, "certificate",
    AttributeInit("fingerprint", &fingerprint),
    AttributeInit("fingerprintAlgorithm", &fingerprint_algorithm),
    AttributeInit("base64Certificate", &base64_certificate),
    AttributeInit("issuerCertificateId", &issuer_certificate_id))
// clang-format on

RTCCertificateStats::RTCCertificateStats(std::string id, Timestamp timestamp)
    : RTCStats(std::move(id), timestamp) {}

RTCCertificateStats::~RTCCertificateStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCCodecStats, RTCStats, "codec",
    AttributeInit("transportId", &transport_id),
    AttributeInit("payloadType", &payload_type),
    AttributeInit("mimeType", &mime_type),
    AttributeInit("clockRate", &clock_rate),
    AttributeInit("channels", &channels),
    AttributeInit("sdpFmtpLine", &sdp_fmtp_line))
// clang-format on

RTCCodecStats::RTCCodecStats(std::string id, Timestamp timestamp)
    : RTCStats(std::move(id), timestamp) {}

RTCCodecStats::~RTCCodecStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCDataChannelStats, RTCStats, "data-channel",
    AttributeInit("label", &label),
    AttributeInit("protocol", &protocol),
    AttributeInit("dataChannelIdentifier", &data_channel_identifier),
    AttributeInit("state", &state),
    AttributeInit("messagesSent", &messages_sent),
    AttributeInit("bytesSent", &bytes_sent),
    AttributeInit("messagesReceived", &messages_received),
    AttributeInit("bytesReceived", &bytes_received))
// clang-format on

RTCDataChannelStats::RTCDataChannelStats(std::string id, Timestamp timestamp)
    : RTCStats(std::move(id), timestamp) {}

RTCDataChannelStats::~RTCDataChannelStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCIceCandidatePairStats, RTCStats, "candidate-pair",
    AttributeInit("transportId", &transport_id),
    AttributeInit("localCandidateId", &local_candidate_id),
    AttributeInit("remoteCandidateId", &remote_candidate_id),
    AttributeInit("state", &state),
    AttributeInit("priority", &priority),
    AttributeInit("nominated", &nominated),
    AttributeInit("writable", &writable),
    AttributeInit("packetsSent", &packets_sent),
    AttributeInit("packetsReceived", &packets_received),
    AttributeInit("bytesSent", &bytes_sent),
    AttributeInit("bytesReceived", &bytes_received),
    AttributeInit("totalRoundTripTime", &total_round_trip_time),
    AttributeInit("currentRoundTripTime", &current_round_trip_time),
    AttributeInit("availableOutgoingBitrate", &available_outgoing_bitrate),
    AttributeInit("availableIncomingBitrate", &available_incoming_bitrate),
    AttributeInit("requestsReceived", &requests_received),
    AttributeInit("requestsSent", &requests_sent),
    AttributeInit("responsesReceived", &responses_received),
    AttributeInit("responsesSent", &responses_sent),
    AttributeInit("consentRequestsSent", &consent_requests_sent),
    AttributeInit("packetsDiscardedOnSend", &packets_discarded_on_send),
    AttributeInit("bytesDiscardedOnSend", &bytes_discarded_on_send),
    AttributeInit("lastPacketReceivedTimestamp",
                  &last_packet_received_timestamp),
    AttributeInit("lastPacketSentTimestamp", &last_packet_sent_timestamp))
// clang-format on

RTCIceCandidatePairStats::RTCIceCandidatePairStats(std::string id,
                                                   Timestamp timestamp)
    : RTCStats(std::move(id), timestamp) {}

RTCIceCandidatePairStats::~RTCIceCandidatePairStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCIceCandidateStats, RTCStats, "abstract-ice-candidate",
    AttributeInit("transportId", &transport_id),
    AttributeInit("isRemote", &is_remote),
    AttributeInit("networkType", &network_type),
    AttributeInit("ip", &ip),
    AttributeInit("address", &address),
    AttributeInit("port", &port),
    AttributeInit("protocol", &protocol),
    AttributeInit("relayProtocol", &relay_protocol),
    AttributeInit("candidateType", &candidate_type),
    AttributeInit("priority", &priority),
    AttributeInit("url", &url),
    AttributeInit("foundation", &foundation),
    AttributeInit("relatedAddress", &related_address),
    AttributeInit("relatedPort", &related_port),
    AttributeInit("usernameFragment", &username_fragment),
    AttributeInit("tcpType", &tcp_type),
    AttributeInit("vpn", &vpn),
    AttributeInit("networkAdapterType", &network_adapter_type))
// clang-format on

RTCIceCandidateStats::RTCIceCandidateStats(std::string id,
                                           Timestamp timestamp,
                                           bool is_remote)
    : RTCStats(std::move(id), timestamp), is_remote(is_remote) {}

RTCIceCandidateStats::~RTCIceCandidateStats() {}

const char RTCLocalIceCandidateStats::kType[] = "local-candidate";

RTCLocalIceCandidateStats::RTCLocalIceCandidateStats(std::string id,
                                                     Timestamp timestamp)
    : RTCIceCandidateStats(std::move(id), timestamp, false) {}

std::unique_ptr<RTCStats> RTCLocalIceCandidateStats::copy() const {
  return std::make_unique<RTCLocalIceCandidateStats>(*this);
}

const char* RTCLocalIceCandidateStats::type() const {
  return kType;
}

const char RTCRemoteIceCandidateStats::kType[] = "remote-candidate";

RTCRemoteIceCandidateStats::RTCRemoteIceCandidateStats(std::string id,
                                                       Timestamp timestamp)
    : RTCIceCandidateStats(std::move(id), timestamp, true) {}

std::unique_ptr<RTCStats> RTCRemoteIceCandidateStats::copy() const {
  return std::make_unique<RTCRemoteIceCandidateStats>(*this);
}

const char* RTCRemoteIceCandidateStats::type() const {
  return kType;
}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCPeerConnectionStats, RTCStats, "peer-connection",
    AttributeInit("dataChannelsOpened", &data_channels_opened),
    AttributeInit("dataChannelsClosed", &data_channels_closed))
// clang-format on

RTCPeerConnectionStats::RTCPeerConnectionStats(std::string id,
                                               Timestamp timestamp)
    : RTCStats(std::move(id), timestamp) {}

RTCPeerConnectionStats::~RTCPeerConnectionStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCRtpStreamStats, RTCStats, "rtp",
    AttributeInit("ssrc", &ssrc),
    AttributeInit("kind", &kind),
    AttributeInit("transportId", &transport_id),
    AttributeInit("codecId", &codec_id))
// clang-format on

RTCRtpStreamStats::RTCRtpStreamStats(std::string id, Timestamp timestamp)
    : RTCStats(std::move(id), timestamp) {}

RTCRtpStreamStats::~RTCRtpStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCReceivedRtpStreamStats, RTCRtpStreamStats, "received-rtp",
    AttributeInit("jitter", &jitter),
    AttributeInit("packetsLost", &packets_lost))
// clang-format on

RTCReceivedRtpStreamStats::RTCReceivedRtpStreamStats(std::string id,
                                                     Timestamp timestamp)
    : RTCRtpStreamStats(std::move(id), timestamp) {}

RTCReceivedRtpStreamStats::~RTCReceivedRtpStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCSentRtpStreamStats, RTCRtpStreamStats, "sent-rtp",
    AttributeInit("packetsSent", &packets_sent),
    AttributeInit("bytesSent", &bytes_sent))
// clang-format on

RTCSentRtpStreamStats::RTCSentRtpStreamStats(std::string id,
                                             Timestamp timestamp)
    : RTCRtpStreamStats(std::move(id), timestamp) {}

RTCSentRtpStreamStats::~RTCSentRtpStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCInboundRtpStreamStats, RTCReceivedRtpStreamStats, "inbound-rtp",
    AttributeInit("playoutId", &playout_id),
    AttributeInit("trackIdentifier", &track_identifier),
    AttributeInit("mid", &mid),
    AttributeInit("remoteId", &remote_id),
    AttributeInit("packetsReceived", &packets_received),
    AttributeInit("packetsDiscarded", &packets_discarded),
    AttributeInit("fecPacketsReceived", &fec_packets_received),
    AttributeInit("fecBytesReceived", &fec_bytes_received),
    AttributeInit("fecPacketsDiscarded", &fec_packets_discarded),
    AttributeInit("fecSsrc", &fec_ssrc),
    AttributeInit("bytesReceived", &bytes_received),
    AttributeInit("headerBytesReceived", &header_bytes_received),
    AttributeInit("retransmittedPacketsReceived",
                  &retransmitted_packets_received),
    AttributeInit("retransmittedBytesReceived", &retransmitted_bytes_received),
    AttributeInit("rtxSsrc", &rtx_ssrc),
    AttributeInit("lastPacketReceivedTimestamp",
                  &last_packet_received_timestamp),
    AttributeInit("jitterBufferDelay", &jitter_buffer_delay),
    AttributeInit("jitterBufferTargetDelay", &jitter_buffer_target_delay),
    AttributeInit("jitterBufferMinimumDelay", &jitter_buffer_minimum_delay),
    AttributeInit("jitterBufferEmittedCount", &jitter_buffer_emitted_count),
    AttributeInit("totalSamplesReceived", &total_samples_received),
    AttributeInit("concealedSamples", &concealed_samples),
    AttributeInit("silentConcealedSamples", &silent_concealed_samples),
    AttributeInit("concealmentEvents", &concealment_events),
    AttributeInit("insertedSamplesForDeceleration",
                  &inserted_samples_for_deceleration),
    AttributeInit("removedSamplesForAcceleration",
                  &removed_samples_for_acceleration),
    AttributeInit("audioLevel", &audio_level),
    AttributeInit("totalAudioEnergy", &total_audio_energy),
    AttributeInit("totalSamplesDuration", &total_samples_duration),
    AttributeInit("framesReceived", &frames_received),
    AttributeInit("frameWidth", &frame_width),
    AttributeInit("frameHeight", &frame_height),
    AttributeInit("framesPerSecond", &frames_per_second),
    AttributeInit("framesDecoded", &frames_decoded),
    AttributeInit("keyFramesDecoded", &key_frames_decoded),
    AttributeInit("framesDropped", &frames_dropped),
    AttributeInit("totalDecodeTime", &total_decode_time),
    AttributeInit("totalProcessingDelay", &total_processing_delay),
    AttributeInit("totalAssemblyTime", &total_assembly_time),
    AttributeInit("framesAssembledFromMultiplePackets",
                  &frames_assembled_from_multiple_packets),
    AttributeInit("totalInterFrameDelay", &total_inter_frame_delay),
    AttributeInit("totalSquaredInterFrameDelay",
                  &total_squared_inter_frame_delay),
    AttributeInit("pauseCount", &pause_count),
    AttributeInit("totalPausesDuration", &total_pauses_duration),
    AttributeInit("freezeCount", &freeze_count),
    AttributeInit("totalFreezesDuration", &total_freezes_duration),
    AttributeInit("contentType", &content_type),
    AttributeInit("estimatedPlayoutTimestamp", &estimated_playout_timestamp),
    AttributeInit("decoderImplementation", &decoder_implementation),
    AttributeInit("firCount", &fir_count),
    AttributeInit("pliCount", &pli_count),
    AttributeInit("nackCount", &nack_count),
    AttributeInit("qpSum", &qp_sum),
    AttributeInit("googTimingFrameInfo", &goog_timing_frame_info),
    AttributeInit("powerEfficientDecoder", &power_efficient_decoder),
    AttributeInit("jitterBufferFlushes", &jitter_buffer_flushes),
    AttributeInit("delayedPacketOutageSamples", &delayed_packet_outage_samples),
    AttributeInit("relativePacketArrivalDelay", &relative_packet_arrival_delay),
    AttributeInit("interruptionCount", &interruption_count),
    AttributeInit("totalInterruptionDuration", &total_interruption_duration),
    AttributeInit("minPlayoutDelay", &min_playout_delay))
// clang-format on

RTCInboundRtpStreamStats::RTCInboundRtpStreamStats(std::string id,
                                                   Timestamp timestamp)
    : RTCReceivedRtpStreamStats(std::move(id), timestamp) {}

RTCInboundRtpStreamStats::~RTCInboundRtpStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCOutboundRtpStreamStats, RTCSentRtpStreamStats, "outbound-rtp",
    AttributeInit("mediaSourceId", &media_source_id),
    AttributeInit("remoteId", &remote_id),
    AttributeInit("mid", &mid),
    AttributeInit("rid", &rid),
    AttributeInit("retransmittedPacketsSent", &retransmitted_packets_sent),
    AttributeInit("headerBytesSent", &header_bytes_sent),
    AttributeInit("retransmittedBytesSent", &retransmitted_bytes_sent),
    AttributeInit("targetBitrate", &target_bitrate),
    AttributeInit("framesEncoded", &frames_encoded),
    AttributeInit("keyFramesEncoded", &key_frames_encoded),
    AttributeInit("totalEncodeTime", &total_encode_time),
    AttributeInit("totalEncodedBytesTarget", &total_encoded_bytes_target),
    AttributeInit("frameWidth", &frame_width),
    AttributeInit("frameHeight", &frame_height),
    AttributeInit("framesPerSecond", &frames_per_second),
    AttributeInit("framesSent", &frames_sent),
    AttributeInit("hugeFramesSent", &huge_frames_sent),
    AttributeInit("totalPacketSendDelay", &total_packet_send_delay),
    AttributeInit("qualityLimitationReason", &quality_limitation_reason),
    AttributeInit("qualityLimitationDurations", &quality_limitation_durations),
    AttributeInit("qualityLimitationResolutionChanges",
                  &quality_limitation_resolution_changes),
    AttributeInit("contentType", &content_type),
    AttributeInit("encoderImplementation", &encoder_implementation),
    AttributeInit("firCount", &fir_count),
    AttributeInit("pliCount", &pli_count),
    AttributeInit("nackCount", &nack_count),
    AttributeInit("qpSum", &qp_sum),
    AttributeInit("active", &active),
    AttributeInit("powerEfficientEncoder", &power_efficient_encoder),
    AttributeInit("scalabilityMode", &scalability_mode),
    AttributeInit("rtxSsrc", &rtx_ssrc))
// clang-format on

RTCOutboundRtpStreamStats::RTCOutboundRtpStreamStats(std::string id,
                                                     Timestamp timestamp)
    : RTCSentRtpStreamStats(std::move(id), timestamp) {}

RTCOutboundRtpStreamStats::~RTCOutboundRtpStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCRemoteInboundRtpStreamStats, RTCReceivedRtpStreamStats,
        "remote-inbound-rtp",
    AttributeInit("localId", &local_id),
    AttributeInit("roundTripTime", &round_trip_time),
    AttributeInit("fractionLost", &fraction_lost),
    AttributeInit("totalRoundTripTime", &total_round_trip_time),
    AttributeInit("roundTripTimeMeasurements", &round_trip_time_measurements))
// clang-format on

RTCRemoteInboundRtpStreamStats::RTCRemoteInboundRtpStreamStats(
    std::string id,
    Timestamp timestamp)
    : RTCReceivedRtpStreamStats(std::move(id), timestamp) {}

RTCRemoteInboundRtpStreamStats::~RTCRemoteInboundRtpStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(
    RTCRemoteOutboundRtpStreamStats, RTCSentRtpStreamStats,
    "remote-outbound-rtp",
    AttributeInit("localId", &local_id),
    AttributeInit("remoteTimestamp", &remote_timestamp),
    AttributeInit("reportsSent", &reports_sent),
    AttributeInit("roundTripTime", &round_trip_time),
    AttributeInit("roundTripTimeMeasurements", &round_trip_time_measurements),
    AttributeInit("totalRoundTripTime", &total_round_trip_time))
// clang-format on

RTCRemoteOutboundRtpStreamStats::RTCRemoteOutboundRtpStreamStats(
    std::string id,
    Timestamp timestamp)
    : RTCSentRtpStreamStats(std::move(id), timestamp) {}

RTCRemoteOutboundRtpStreamStats::~RTCRemoteOutboundRtpStreamStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCMediaSourceStats, RTCStats, "parent-media-source",
    AttributeInit("trackIdentifier", &track_identifier),
    AttributeInit("kind", &kind))
// clang-format on

RTCMediaSourceStats::RTCMediaSourceStats(std::string id, Timestamp timestamp)
    : RTCStats(std::move(id), timestamp) {}

RTCMediaSourceStats::~RTCMediaSourceStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCAudioSourceStats, RTCMediaSourceStats, "media-source",
    AttributeInit("audioLevel", &audio_level),
    AttributeInit("totalAudioEnergy", &total_audio_energy),
    AttributeInit("totalSamplesDuration", &total_samples_duration),
    AttributeInit("echoReturnLoss", &echo_return_loss),
    AttributeInit("echoReturnLossEnhancement", &echo_return_loss_enhancement))
// clang-format on

RTCAudioSourceStats::RTCAudioSourceStats(std::string id, Timestamp timestamp)
    : RTCMediaSourceStats(std::move(id), timestamp) {}

RTCAudioSourceStats::~RTCAudioSourceStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCVideoSourceStats, RTCMediaSourceStats, "media-source",
    AttributeInit("width", &width),
    AttributeInit("height", &height),
    AttributeInit("frames", &frames),
    AttributeInit("framesPerSecond", &frames_per_second))
// clang-format on

RTCVideoSourceStats::RTCVideoSourceStats(std::string id, Timestamp timestamp)
    : RTCMediaSourceStats(std::move(id), timestamp) {}

RTCVideoSourceStats::~RTCVideoSourceStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCTransportStats, RTCStats, "transport",
    AttributeInit("bytesSent", &bytes_sent),
    AttributeInit("packetsSent", &packets_sent),
    AttributeInit("bytesReceived", &bytes_received),
    AttributeInit("packetsReceived", &packets_received),
    AttributeInit("rtcpTransportStatsId", &rtcp_transport_stats_id),
    AttributeInit("dtlsState", &dtls_state),
    AttributeInit("selectedCandidatePairId", &selected_candidate_pair_id),
    AttributeInit("localCertificateId", &local_certificate_id),
    AttributeInit("remoteCertificateId", &remote_certificate_id),
    AttributeInit("tlsVersion", &tls_version),
    AttributeInit("dtlsCipher", &dtls_cipher),
    AttributeInit("dtlsRole", &dtls_role),
    AttributeInit("srtpCipher", &srtp_cipher),
    AttributeInit("selectedCandidatePairChanges",
                  &selected_candidate_pair_changes),
    AttributeInit("iceRole", &ice_role),
    AttributeInit("iceLocalUsernameFragment", &ice_local_username_fragment),
    AttributeInit("iceState", &ice_state))
// clang-format on

RTCTransportStats::RTCTransportStats(std::string id, Timestamp timestamp)
    : RTCStats(std::move(id), timestamp) {}

RTCTransportStats::~RTCTransportStats() {}

// clang-format off
WEBRTC_RTCSTATS_IMPL(RTCAudioPlayoutStats, RTCStats, "media-playout",
    AttributeInit("kind", &kind),
    AttributeInit("synthesizedSamplesDuration", &synthesized_samples_duration),
    AttributeInit("synthesizedSamplesEvents", &synthesized_samples_events),
    AttributeInit("totalSamplesDuration", &total_samples_duration),
    AttributeInit("totalPlayoutDelay", &total_playout_delay),
    AttributeInit("totalSamplesCount", &total_samples_count))
// clang-format on

RTCAudioPlayoutStats::RTCAudioPlayoutStats(const std::string& id,
                                           Timestamp timestamp)
    : RTCStats(std::move(id), timestamp), kind("audio") {}

RTCAudioPlayoutStats::~RTCAudioPlayoutStats() {}

}  // namespace webrtc
