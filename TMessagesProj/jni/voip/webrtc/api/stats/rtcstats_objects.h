/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_STATS_RTCSTATS_OBJECTS_H_
#define API_STATS_RTCSTATS_OBJECTS_H_

#include <stdint.h>

#include <map>
#include <memory>
#include <string>
#include <vector>

#include "api/stats/rtc_stats.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// https://w3c.github.io/webrtc-pc/#idl-def-rtcdatachannelstate
struct RTCDataChannelState {
  static const char* const kConnecting;
  static const char* const kOpen;
  static const char* const kClosing;
  static const char* const kClosed;
};

// https://w3c.github.io/webrtc-stats/#dom-rtcstatsicecandidatepairstate
struct RTCStatsIceCandidatePairState {
  static const char* const kFrozen;
  static const char* const kWaiting;
  static const char* const kInProgress;
  static const char* const kFailed;
  static const char* const kSucceeded;
};

// https://w3c.github.io/webrtc-pc/#rtcicecandidatetype-enum
struct RTCIceCandidateType {
  static const char* const kHost;
  static const char* const kSrflx;
  static const char* const kPrflx;
  static const char* const kRelay;
};

// https://w3c.github.io/webrtc-pc/#idl-def-rtcdtlstransportstate
struct RTCDtlsTransportState {
  static const char* const kNew;
  static const char* const kConnecting;
  static const char* const kConnected;
  static const char* const kClosed;
  static const char* const kFailed;
};

// `RTCMediaStreamTrackStats::kind` is not an enum in the spec but the only
// valid values are "audio" and "video".
// https://w3c.github.io/webrtc-stats/#dom-rtcmediastreamtrackstats-kind
struct RTCMediaStreamTrackKind {
  static const char* const kAudio;
  static const char* const kVideo;
};

// https://w3c.github.io/webrtc-stats/#dom-rtcnetworktype
struct RTCNetworkType {
  static const char* const kBluetooth;
  static const char* const kCellular;
  static const char* const kEthernet;
  static const char* const kWifi;
  static const char* const kWimax;
  static const char* const kVpn;
  static const char* const kUnknown;
};

// https://w3c.github.io/webrtc-stats/#dom-rtcqualitylimitationreason
struct RTCQualityLimitationReason {
  static const char* const kNone;
  static const char* const kCpu;
  static const char* const kBandwidth;
  static const char* const kOther;
};

// https://webrtc.org/experiments/rtp-hdrext/video-content-type/
struct RTCContentType {
  static const char* const kUnspecified;
  static const char* const kScreenshare;
};

// https://w3c.github.io/webrtc-stats/#dom-rtcdtlsrole
struct RTCDtlsRole {
  static const char* const kUnknown;
  static const char* const kClient;
  static const char* const kServer;
};

// https://www.w3.org/TR/webrtc/#rtcicerole
struct RTCIceRole {
  static const char* const kUnknown;
  static const char* const kControlled;
  static const char* const kControlling;
};

// https://www.w3.org/TR/webrtc/#dom-rtcicetransportstate
struct RTCIceTransportState {
  static const char* const kNew;
  static const char* const kChecking;
  static const char* const kConnected;
  static const char* const kCompleted;
  static const char* const kDisconnected;
  static const char* const kFailed;
  static const char* const kClosed;
};

// https://w3c.github.io/webrtc-stats/#certificatestats-dict*
class RTC_EXPORT RTCCertificateStats final : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCCertificateStats(const std::string& id, int64_t timestamp_us);
  RTCCertificateStats(std::string&& id, int64_t timestamp_us);
  RTCCertificateStats(const RTCCertificateStats& other);
  ~RTCCertificateStats() override;

  RTCStatsMember<std::string> fingerprint;
  RTCStatsMember<std::string> fingerprint_algorithm;
  RTCStatsMember<std::string> base64_certificate;
  RTCStatsMember<std::string> issuer_certificate_id;
};

// Non standard extension mapping to rtc::AdapterType
struct RTCNetworkAdapterType {
  static constexpr char kUnknown[] = "unknown";
  static constexpr char kEthernet[] = "ethernet";
  static constexpr char kWifi[] = "wifi";
  static constexpr char kCellular[] = "cellular";
  static constexpr char kLoopback[] = "loopback";
  static constexpr char kAny[] = "any";
  static constexpr char kCellular2g[] = "cellular2g";
  static constexpr char kCellular3g[] = "cellular3g";
  static constexpr char kCellular4g[] = "cellular4g";
  static constexpr char kCellular5g[] = "cellular5g";
};

// https://w3c.github.io/webrtc-stats/#codec-dict*
class RTC_EXPORT RTCCodecStats final : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCCodecStats(const std::string& id, int64_t timestamp_us);
  RTCCodecStats(std::string&& id, int64_t timestamp_us);
  RTCCodecStats(const RTCCodecStats& other);
  ~RTCCodecStats() override;

  RTCStatsMember<std::string> transport_id;
  RTCStatsMember<uint32_t> payload_type;
  RTCStatsMember<std::string> mime_type;
  RTCStatsMember<uint32_t> clock_rate;
  RTCStatsMember<uint32_t> channels;
  RTCStatsMember<std::string> sdp_fmtp_line;
};

// https://w3c.github.io/webrtc-stats/#dcstats-dict*
class RTC_EXPORT RTCDataChannelStats final : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCDataChannelStats(const std::string& id, int64_t timestamp_us);
  RTCDataChannelStats(std::string&& id, int64_t timestamp_us);
  RTCDataChannelStats(const RTCDataChannelStats& other);
  ~RTCDataChannelStats() override;

  RTCStatsMember<std::string> label;
  RTCStatsMember<std::string> protocol;
  RTCStatsMember<int32_t> data_channel_identifier;
  // Enum type RTCDataChannelState.
  RTCStatsMember<std::string> state;
  RTCStatsMember<uint32_t> messages_sent;
  RTCStatsMember<uint64_t> bytes_sent;
  RTCStatsMember<uint32_t> messages_received;
  RTCStatsMember<uint64_t> bytes_received;
};

// https://w3c.github.io/webrtc-stats/#candidatepair-dict*
class RTC_EXPORT RTCIceCandidatePairStats final : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCIceCandidatePairStats(const std::string& id, int64_t timestamp_us);
  RTCIceCandidatePairStats(std::string&& id, int64_t timestamp_us);
  RTCIceCandidatePairStats(const RTCIceCandidatePairStats& other);
  ~RTCIceCandidatePairStats() override;

  RTCStatsMember<std::string> transport_id;
  RTCStatsMember<std::string> local_candidate_id;
  RTCStatsMember<std::string> remote_candidate_id;
  // Enum type RTCStatsIceCandidatePairState.
  RTCStatsMember<std::string> state;
  // Obsolete: priority
  RTCStatsMember<uint64_t> priority;
  RTCStatsMember<bool> nominated;
  // `writable` does not exist in the spec and old comments suggest it used to
  // exist but was incorrectly implemented.
  // TODO(https://crbug.com/webrtc/14171): Standardize and/or modify
  // implementation.
  RTCStatsMember<bool> writable;
  RTCStatsMember<uint64_t> packets_sent;
  RTCStatsMember<uint64_t> packets_received;
  RTCStatsMember<uint64_t> bytes_sent;
  RTCStatsMember<uint64_t> bytes_received;
  RTCStatsMember<double> total_round_trip_time;
  RTCStatsMember<double> current_round_trip_time;
  RTCStatsMember<double> available_outgoing_bitrate;
  RTCStatsMember<double> available_incoming_bitrate;
  RTCStatsMember<uint64_t> requests_received;
  RTCStatsMember<uint64_t> requests_sent;
  RTCStatsMember<uint64_t> responses_received;
  RTCStatsMember<uint64_t> responses_sent;
  RTCStatsMember<uint64_t> consent_requests_sent;
  RTCStatsMember<uint64_t> packets_discarded_on_send;
  RTCStatsMember<uint64_t> bytes_discarded_on_send;
};

// https://w3c.github.io/webrtc-stats/#icecandidate-dict*
class RTC_EXPORT RTCIceCandidateStats : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCIceCandidateStats(const RTCIceCandidateStats& other);
  ~RTCIceCandidateStats() override;

  RTCStatsMember<std::string> transport_id;
  // Obsolete: is_remote
  RTCStatsMember<bool> is_remote;
  RTCStatsMember<std::string> network_type;
  RTCStatsMember<std::string> ip;
  RTCStatsMember<std::string> address;
  RTCStatsMember<int32_t> port;
  RTCStatsMember<std::string> protocol;
  RTCStatsMember<std::string> relay_protocol;
  // Enum type RTCIceCandidateType.
  RTCStatsMember<std::string> candidate_type;
  RTCStatsMember<int32_t> priority;
  RTCStatsMember<std::string> url;
  RTCStatsMember<std::string> foundation;
  RTCStatsMember<std::string> related_address;
  RTCStatsMember<int32_t> related_port;
  RTCStatsMember<std::string> username_fragment;
  // Enum type RTCIceTcpCandidateType.
  RTCStatsMember<std::string> tcp_type;

  RTCNonStandardStatsMember<bool> vpn;
  RTCNonStandardStatsMember<std::string> network_adapter_type;

 protected:
  RTCIceCandidateStats(const std::string& id,
                       int64_t timestamp_us,
                       bool is_remote);
  RTCIceCandidateStats(std::string&& id, int64_t timestamp_us, bool is_remote);
};

// In the spec both local and remote varieties are of type RTCIceCandidateStats.
// But here we define them as subclasses of `RTCIceCandidateStats` because the
// `kType` need to be different ("RTCStatsType type") in the local/remote case.
// https://w3c.github.io/webrtc-stats/#rtcstatstype-str*
// This forces us to have to override copy() and type().
class RTC_EXPORT RTCLocalIceCandidateStats final : public RTCIceCandidateStats {
 public:
  static const char kType[];
  RTCLocalIceCandidateStats(const std::string& id, int64_t timestamp_us);
  RTCLocalIceCandidateStats(std::string&& id, int64_t timestamp_us);
  std::unique_ptr<RTCStats> copy() const override;
  const char* type() const override;
};

class RTC_EXPORT RTCRemoteIceCandidateStats final
    : public RTCIceCandidateStats {
 public:
  static const char kType[];
  RTCRemoteIceCandidateStats(const std::string& id, int64_t timestamp_us);
  RTCRemoteIceCandidateStats(std::string&& id, int64_t timestamp_us);
  std::unique_ptr<RTCStats> copy() const override;
  const char* type() const override;
};

// TODO(https://crbug.com/webrtc/14419): Delete this class, it's deprecated.
class RTC_EXPORT DEPRECATED_RTCMediaStreamStats final : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  DEPRECATED_RTCMediaStreamStats(const std::string& id, int64_t timestamp_us);
  DEPRECATED_RTCMediaStreamStats(std::string&& id, int64_t timestamp_us);
  DEPRECATED_RTCMediaStreamStats(const DEPRECATED_RTCMediaStreamStats& other);
  ~DEPRECATED_RTCMediaStreamStats() override;

  RTCStatsMember<std::string> stream_identifier;
  RTCStatsMember<std::vector<std::string>> track_ids;
};
using RTCMediaStreamStats [[deprecated("bugs.webrtc.org/14419")]] =
    DEPRECATED_RTCMediaStreamStats;

// TODO(https://crbug.com/webrtc/14175): Delete this class, it's deprecated.
class RTC_EXPORT DEPRECATED_RTCMediaStreamTrackStats final : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  DEPRECATED_RTCMediaStreamTrackStats(const std::string& id,
                                      int64_t timestamp_us,
                                      const char* kind);
  DEPRECATED_RTCMediaStreamTrackStats(std::string&& id,
                                      int64_t timestamp_us,
                                      const char* kind);
  DEPRECATED_RTCMediaStreamTrackStats(
      const DEPRECATED_RTCMediaStreamTrackStats& other);
  ~DEPRECATED_RTCMediaStreamTrackStats() override;

  RTCStatsMember<std::string> track_identifier;
  RTCStatsMember<std::string> media_source_id;
  RTCStatsMember<bool> remote_source;
  RTCStatsMember<bool> ended;
  // TODO(https://crbug.com/webrtc/14173): Remove this obsolete metric.
  RTCStatsMember<bool> detached;
  // Enum type RTCMediaStreamTrackKind.
  RTCStatsMember<std::string> kind;
  RTCStatsMember<double> jitter_buffer_delay;
  RTCStatsMember<uint64_t> jitter_buffer_emitted_count;
  // Video-only members
  RTCStatsMember<uint32_t> frame_width;
  RTCStatsMember<uint32_t> frame_height;
  RTCStatsMember<uint32_t> frames_sent;
  RTCStatsMember<uint32_t> huge_frames_sent;
  RTCStatsMember<uint32_t> frames_received;
  RTCStatsMember<uint32_t> frames_decoded;
  RTCStatsMember<uint32_t> frames_dropped;
  // Audio-only members
  RTCStatsMember<double> audio_level;         // Receive-only
  RTCStatsMember<double> total_audio_energy;  // Receive-only
  RTCStatsMember<double> echo_return_loss;
  RTCStatsMember<double> echo_return_loss_enhancement;
  RTCStatsMember<uint64_t> total_samples_received;
  RTCStatsMember<double> total_samples_duration;  // Receive-only
  RTCStatsMember<uint64_t> concealed_samples;
  RTCStatsMember<uint64_t> silent_concealed_samples;
  RTCStatsMember<uint64_t> concealment_events;
  RTCStatsMember<uint64_t> inserted_samples_for_deceleration;
  RTCStatsMember<uint64_t> removed_samples_for_acceleration;
  // TODO(crbug.com/webrtc/14524): These metrics have been moved, delete them.
  RTCNonStandardStatsMember<uint64_t> jitter_buffer_flushes;
  RTCNonStandardStatsMember<uint64_t> delayed_packet_outage_samples;
  RTCNonStandardStatsMember<double> relative_packet_arrival_delay;
  RTCNonStandardStatsMember<uint32_t> interruption_count;
  RTCNonStandardStatsMember<double> total_interruption_duration;
  // Non-standard video-only members.
  // https://w3c.github.io/webrtc-provisional-stats/#dom-rtcvideoreceiverstats
  RTCNonStandardStatsMember<double> total_frames_duration;
  RTCNonStandardStatsMember<double> sum_squared_frame_durations;
  // TODO(crbug.com/webrtc/14521): These metrics have been moved, delete them.
  RTCNonStandardStatsMember<uint32_t> freeze_count;
  RTCNonStandardStatsMember<uint32_t> pause_count;
  RTCNonStandardStatsMember<double> total_freezes_duration;
  RTCNonStandardStatsMember<double> total_pauses_duration;
};
using RTCMediaStreamTrackStats [[deprecated("bugs.webrtc.org/14175")]] =
    DEPRECATED_RTCMediaStreamTrackStats;

// https://w3c.github.io/webrtc-stats/#pcstats-dict*
class RTC_EXPORT RTCPeerConnectionStats final : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCPeerConnectionStats(const std::string& id, int64_t timestamp_us);
  RTCPeerConnectionStats(std::string&& id, int64_t timestamp_us);
  RTCPeerConnectionStats(const RTCPeerConnectionStats& other);
  ~RTCPeerConnectionStats() override;

  RTCStatsMember<uint32_t> data_channels_opened;
  RTCStatsMember<uint32_t> data_channels_closed;
};

// https://w3c.github.io/webrtc-stats/#streamstats-dict*
class RTC_EXPORT RTCRTPStreamStats : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCRTPStreamStats(const RTCRTPStreamStats& other);
  ~RTCRTPStreamStats() override;

  RTCStatsMember<uint32_t> ssrc;
  RTCStatsMember<std::string> kind;
  // Obsolete: track_id
  RTCStatsMember<std::string> track_id;
  RTCStatsMember<std::string> transport_id;
  RTCStatsMember<std::string> codec_id;

  // Obsolete
  RTCStatsMember<std::string> media_type;  // renamed to kind.

 protected:
  RTCRTPStreamStats(const std::string& id, int64_t timestamp_us);
  RTCRTPStreamStats(std::string&& id, int64_t timestamp_us);
};

// https://www.w3.org/TR/webrtc-stats/#receivedrtpstats-dict*
class RTC_EXPORT RTCReceivedRtpStreamStats : public RTCRTPStreamStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCReceivedRtpStreamStats(const RTCReceivedRtpStreamStats& other);
  ~RTCReceivedRtpStreamStats() override;

  RTCStatsMember<double> jitter;
  RTCStatsMember<int32_t> packets_lost;  // Signed per RFC 3550

 protected:
  RTCReceivedRtpStreamStats(const std::string&& id, int64_t timestamp_us);
  RTCReceivedRtpStreamStats(std::string&& id, int64_t timestamp_us);
};

// https://www.w3.org/TR/webrtc-stats/#sentrtpstats-dict*
class RTC_EXPORT RTCSentRtpStreamStats : public RTCRTPStreamStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCSentRtpStreamStats(const RTCSentRtpStreamStats& other);
  ~RTCSentRtpStreamStats() override;

  RTCStatsMember<uint32_t> packets_sent;
  RTCStatsMember<uint64_t> bytes_sent;

 protected:
  RTCSentRtpStreamStats(const std::string&& id, int64_t timestamp_us);
  RTCSentRtpStreamStats(std::string&& id, int64_t timestamp_us);
};

// https://w3c.github.io/webrtc-stats/#inboundrtpstats-dict*
class RTC_EXPORT RTCInboundRTPStreamStats final
    : public RTCReceivedRtpStreamStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCInboundRTPStreamStats(const std::string& id, int64_t timestamp_us);
  RTCInboundRTPStreamStats(std::string&& id, int64_t timestamp_us);
  RTCInboundRTPStreamStats(const RTCInboundRTPStreamStats& other);
  ~RTCInboundRTPStreamStats() override;

  // TODO(https://crbug.com/webrtc/14174): Implement trackIdentifier and kind.

  RTCStatsMember<std::string> track_identifier;
  RTCStatsMember<std::string> mid;
  RTCStatsMember<std::string> remote_id;
  RTCStatsMember<uint32_t> packets_received;
  RTCStatsMember<uint64_t> packets_discarded;
  RTCStatsMember<uint64_t> fec_packets_received;
  RTCStatsMember<uint64_t> fec_packets_discarded;
  RTCStatsMember<uint64_t> bytes_received;
  RTCStatsMember<uint64_t> header_bytes_received;
  RTCStatsMember<double> last_packet_received_timestamp;
  RTCStatsMember<double> jitter_buffer_delay;
  RTCStatsMember<double> jitter_buffer_target_delay;
  RTCStatsMember<double> jitter_buffer_minimum_delay;
  RTCStatsMember<uint64_t> jitter_buffer_emitted_count;
  RTCStatsMember<uint64_t> total_samples_received;
  RTCStatsMember<uint64_t> concealed_samples;
  RTCStatsMember<uint64_t> silent_concealed_samples;
  RTCStatsMember<uint64_t> concealment_events;
  RTCStatsMember<uint64_t> inserted_samples_for_deceleration;
  RTCStatsMember<uint64_t> removed_samples_for_acceleration;
  RTCStatsMember<double> audio_level;
  RTCStatsMember<double> total_audio_energy;
  RTCStatsMember<double> total_samples_duration;
  // Stats below are only implemented or defined for video.
  RTCStatsMember<int32_t> frames_received;
  RTCStatsMember<uint32_t> frame_width;
  RTCStatsMember<uint32_t> frame_height;
  RTCStatsMember<double> frames_per_second;
  RTCStatsMember<uint32_t> frames_decoded;
  RTCStatsMember<uint32_t> key_frames_decoded;
  RTCStatsMember<uint32_t> frames_dropped;
  RTCStatsMember<double> total_decode_time;
  RTCStatsMember<double> total_processing_delay;
  RTCStatsMember<double> total_assembly_time;
  RTCStatsMember<uint32_t> frames_assembled_from_multiple_packets;
  RTCStatsMember<double> total_inter_frame_delay;
  RTCStatsMember<double> total_squared_inter_frame_delay;
  RTCStatsMember<uint32_t> pause_count;
  RTCStatsMember<double> total_pauses_duration;
  RTCStatsMember<uint32_t> freeze_count;
  RTCStatsMember<double> total_freezes_duration;
  // https://w3c.github.io/webrtc-provisional-stats/#dom-rtcinboundrtpstreamstats-contenttype
  RTCStatsMember<std::string> content_type;
  // Only populated if audio/video sync is enabled.
  // TODO(https://crbug.com/webrtc/14177): Expose even if A/V sync is off?
  RTCStatsMember<double> estimated_playout_timestamp;
  // Only implemented for video.
  // TODO(https://crbug.com/webrtc/14178): Also implement for audio.
  RTCStatsMember<std::string> decoder_implementation;
  // FIR and PLI counts are only defined for |kind == "video"|.
  RTCStatsMember<uint32_t> fir_count;
  RTCStatsMember<uint32_t> pli_count;
  RTCStatsMember<uint32_t> nack_count;
  RTCStatsMember<uint64_t> qp_sum;
  // This is a remnant of the legacy getStats() API. When the "video-timing"
  // header extension is used,
  // https://webrtc.github.io/webrtc-org/experiments/rtp-hdrext/video-timing/,
  // `googTimingFrameInfo` is exposed with the value of
  // TimingFrameInfo::ToString().
  // TODO(https://crbug.com/webrtc/14586): Unship or standardize this metric.
  RTCStatsMember<std::string> goog_timing_frame_info;
  // Non-standard audio metrics.
  RTCNonStandardStatsMember<uint64_t> jitter_buffer_flushes;
  RTCNonStandardStatsMember<uint64_t> delayed_packet_outage_samples;
  RTCNonStandardStatsMember<double> relative_packet_arrival_delay;
  RTCNonStandardStatsMember<uint32_t> interruption_count;
  RTCNonStandardStatsMember<double> total_interruption_duration;

  // The former googMinPlayoutDelayMs (in seconds).
  RTCNonStandardStatsMember<double> min_playout_delay;
};

// https://w3c.github.io/webrtc-stats/#outboundrtpstats-dict*
class RTC_EXPORT RTCOutboundRTPStreamStats final : public RTCRTPStreamStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCOutboundRTPStreamStats(const std::string& id, int64_t timestamp_us);
  RTCOutboundRTPStreamStats(std::string&& id, int64_t timestamp_us);
  RTCOutboundRTPStreamStats(const RTCOutboundRTPStreamStats& other);
  ~RTCOutboundRTPStreamStats() override;

  RTCStatsMember<std::string> media_source_id;
  RTCStatsMember<std::string> remote_id;
  RTCStatsMember<std::string> mid;
  RTCStatsMember<std::string> rid;
  RTCStatsMember<uint32_t> packets_sent;
  RTCStatsMember<uint64_t> retransmitted_packets_sent;
  RTCStatsMember<uint64_t> bytes_sent;
  RTCStatsMember<uint64_t> header_bytes_sent;
  RTCStatsMember<uint64_t> retransmitted_bytes_sent;
  RTCStatsMember<double> target_bitrate;
  RTCStatsMember<uint32_t> frames_encoded;
  RTCStatsMember<uint32_t> key_frames_encoded;
  RTCStatsMember<double> total_encode_time;
  RTCStatsMember<uint64_t> total_encoded_bytes_target;
  RTCStatsMember<uint32_t> frame_width;
  RTCStatsMember<uint32_t> frame_height;
  RTCStatsMember<double> frames_per_second;
  RTCStatsMember<uint32_t> frames_sent;
  RTCStatsMember<uint32_t> huge_frames_sent;
  RTCStatsMember<double> total_packet_send_delay;
  // Enum type RTCQualityLimitationReason
  RTCStatsMember<std::string> quality_limitation_reason;
  RTCStatsMember<std::map<std::string, double>> quality_limitation_durations;
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-qualitylimitationresolutionchanges
  RTCStatsMember<uint32_t> quality_limitation_resolution_changes;
  // https://w3c.github.io/webrtc-provisional-stats/#dom-rtcoutboundrtpstreamstats-contenttype
  RTCStatsMember<std::string> content_type;
  // Only implemented for video.
  // TODO(https://crbug.com/webrtc/14178): Implement for audio as well.
  RTCStatsMember<std::string> encoder_implementation;
  // FIR and PLI counts are only defined for |kind == "video"|.
  RTCStatsMember<uint32_t> fir_count;
  RTCStatsMember<uint32_t> pli_count;
  RTCStatsMember<uint32_t> nack_count;
  RTCStatsMember<uint64_t> qp_sum;
  RTCStatsMember<bool> active;
};

// https://w3c.github.io/webrtc-stats/#remoteinboundrtpstats-dict*
class RTC_EXPORT RTCRemoteInboundRtpStreamStats final
    : public RTCReceivedRtpStreamStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCRemoteInboundRtpStreamStats(const std::string& id, int64_t timestamp_us);
  RTCRemoteInboundRtpStreamStats(std::string&& id, int64_t timestamp_us);
  RTCRemoteInboundRtpStreamStats(const RTCRemoteInboundRtpStreamStats& other);
  ~RTCRemoteInboundRtpStreamStats() override;

  RTCStatsMember<std::string> local_id;
  RTCStatsMember<double> round_trip_time;
  RTCStatsMember<double> fraction_lost;
  RTCStatsMember<double> total_round_trip_time;
  RTCStatsMember<int32_t> round_trip_time_measurements;
};

// https://w3c.github.io/webrtc-stats/#remoteoutboundrtpstats-dict*
class RTC_EXPORT RTCRemoteOutboundRtpStreamStats final
    : public RTCSentRtpStreamStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCRemoteOutboundRtpStreamStats(const std::string& id, int64_t timestamp_us);
  RTCRemoteOutboundRtpStreamStats(std::string&& id, int64_t timestamp_us);
  RTCRemoteOutboundRtpStreamStats(const RTCRemoteOutboundRtpStreamStats& other);
  ~RTCRemoteOutboundRtpStreamStats() override;

  RTCStatsMember<std::string> local_id;
  RTCStatsMember<double> remote_timestamp;
  RTCStatsMember<uint64_t> reports_sent;
  RTCStatsMember<double> round_trip_time;
  RTCStatsMember<uint64_t> round_trip_time_measurements;
  RTCStatsMember<double> total_round_trip_time;
};

// https://w3c.github.io/webrtc-stats/#dom-rtcmediasourcestats
class RTC_EXPORT RTCMediaSourceStats : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCMediaSourceStats(const RTCMediaSourceStats& other);
  ~RTCMediaSourceStats() override;

  RTCStatsMember<std::string> track_identifier;
  RTCStatsMember<std::string> kind;

 protected:
  RTCMediaSourceStats(const std::string& id, int64_t timestamp_us);
  RTCMediaSourceStats(std::string&& id, int64_t timestamp_us);
};

// https://w3c.github.io/webrtc-stats/#dom-rtcaudiosourcestats
class RTC_EXPORT RTCAudioSourceStats final : public RTCMediaSourceStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCAudioSourceStats(const std::string& id, int64_t timestamp_us);
  RTCAudioSourceStats(std::string&& id, int64_t timestamp_us);
  RTCAudioSourceStats(const RTCAudioSourceStats& other);
  ~RTCAudioSourceStats() override;

  RTCStatsMember<double> audio_level;
  RTCStatsMember<double> total_audio_energy;
  RTCStatsMember<double> total_samples_duration;
  RTCStatsMember<double> echo_return_loss;
  RTCStatsMember<double> echo_return_loss_enhancement;
};

// https://w3c.github.io/webrtc-stats/#dom-rtcvideosourcestats
class RTC_EXPORT RTCVideoSourceStats final : public RTCMediaSourceStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCVideoSourceStats(const std::string& id, int64_t timestamp_us);
  RTCVideoSourceStats(std::string&& id, int64_t timestamp_us);
  RTCVideoSourceStats(const RTCVideoSourceStats& other);
  ~RTCVideoSourceStats() override;

  RTCStatsMember<uint32_t> width;
  RTCStatsMember<uint32_t> height;
  RTCStatsMember<uint32_t> frames;
  RTCStatsMember<double> frames_per_second;
};

// https://w3c.github.io/webrtc-stats/#transportstats-dict*
class RTC_EXPORT RTCTransportStats final : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCTransportStats(const std::string& id, int64_t timestamp_us);
  RTCTransportStats(std::string&& id, int64_t timestamp_us);
  RTCTransportStats(const RTCTransportStats& other);
  ~RTCTransportStats() override;

  RTCStatsMember<uint64_t> bytes_sent;
  RTCStatsMember<uint64_t> packets_sent;
  RTCStatsMember<uint64_t> bytes_received;
  RTCStatsMember<uint64_t> packets_received;
  RTCStatsMember<std::string> rtcp_transport_stats_id;
  // Enum type RTCDtlsTransportState.
  RTCStatsMember<std::string> dtls_state;
  RTCStatsMember<std::string> selected_candidate_pair_id;
  RTCStatsMember<std::string> local_certificate_id;
  RTCStatsMember<std::string> remote_certificate_id;
  RTCStatsMember<std::string> tls_version;
  RTCStatsMember<std::string> dtls_cipher;
  RTCStatsMember<std::string> dtls_role;
  RTCStatsMember<std::string> srtp_cipher;
  RTCStatsMember<uint32_t> selected_candidate_pair_changes;
  RTCStatsMember<std::string> ice_role;
  RTCStatsMember<std::string> ice_local_username_fragment;
  RTCStatsMember<std::string> ice_state;
};

}  // namespace webrtc

#endif  // API_STATS_RTCSTATS_OBJECTS_H_
