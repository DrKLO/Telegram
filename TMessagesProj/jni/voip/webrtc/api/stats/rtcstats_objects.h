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

// |RTCMediaStreamTrackStats::kind| is not an enum in the spec but the only
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
  // TODO(hbos): Support enum types? "RTCStatsMember<RTCDataChannelState>"?
  RTCStatsMember<std::string> state;
  RTCStatsMember<uint32_t> messages_sent;
  RTCStatsMember<uint64_t> bytes_sent;
  RTCStatsMember<uint32_t> messages_received;
  RTCStatsMember<uint64_t> bytes_received;
};

// https://w3c.github.io/webrtc-stats/#candidatepair-dict*
// TODO(hbos): Tracking bug https://bugs.webrtc.org/7062
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
  // TODO(hbos): Support enum types?
  // "RTCStatsMember<RTCStatsIceCandidatePairState>"?
  RTCStatsMember<std::string> state;
  RTCStatsMember<uint64_t> priority;
  RTCStatsMember<bool> nominated;
  // TODO(hbos): Collect this the way the spec describes it. We have a value for
  // it but it is not spec-compliant. https://bugs.webrtc.org/7062
  RTCStatsMember<bool> writable;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7062
  RTCStatsMember<bool> readable;
  RTCStatsMember<uint64_t> bytes_sent;
  RTCStatsMember<uint64_t> bytes_received;
  RTCStatsMember<double> total_round_trip_time;
  RTCStatsMember<double> current_round_trip_time;
  RTCStatsMember<double> available_outgoing_bitrate;
  // TODO(hbos): Populate this value. It is wired up and collected the same way
  // "VideoBwe.googAvailableReceiveBandwidth" is, but that value is always
  // undefined. https://bugs.webrtc.org/7062
  RTCStatsMember<double> available_incoming_bitrate;
  RTCStatsMember<uint64_t> requests_received;
  RTCStatsMember<uint64_t> requests_sent;
  RTCStatsMember<uint64_t> responses_received;
  RTCStatsMember<uint64_t> responses_sent;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7062
  RTCStatsMember<uint64_t> retransmissions_received;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7062
  RTCStatsMember<uint64_t> retransmissions_sent;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7062
  RTCStatsMember<uint64_t> consent_requests_received;
  RTCStatsMember<uint64_t> consent_requests_sent;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7062
  RTCStatsMember<uint64_t> consent_responses_received;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7062
  RTCStatsMember<uint64_t> consent_responses_sent;
};

// https://w3c.github.io/webrtc-stats/#icecandidate-dict*
// TODO(hbos): |RTCStatsCollector| only collects candidates that are part of
// ice candidate pairs, but there could be candidates not paired with anything.
// crbug.com/632723
// TODO(qingsi): Add the stats of STUN binding requests (keepalives) and collect
// them in the new PeerConnection::GetStats.
class RTC_EXPORT RTCIceCandidateStats : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCIceCandidateStats(const RTCIceCandidateStats& other);
  ~RTCIceCandidateStats() override;

  RTCStatsMember<std::string> transport_id;
  RTCStatsMember<bool> is_remote;
  RTCStatsMember<std::string> network_type;
  RTCStatsMember<std::string> ip;
  RTCStatsMember<int32_t> port;
  RTCStatsMember<std::string> protocol;
  RTCStatsMember<std::string> relay_protocol;
  // TODO(hbos): Support enum types? "RTCStatsMember<RTCIceCandidateType>"?
  RTCStatsMember<std::string> candidate_type;
  RTCStatsMember<int32_t> priority;
  // TODO(hbos): Not collected by |RTCStatsCollector|. crbug.com/632723
  RTCStatsMember<std::string> url;
  // TODO(hbos): |deleted = true| case is not supported by |RTCStatsCollector|.
  // crbug.com/632723
  RTCStatsMember<bool> deleted;  // = false

 protected:
  RTCIceCandidateStats(const std::string& id,
                       int64_t timestamp_us,
                       bool is_remote);
  RTCIceCandidateStats(std::string&& id, int64_t timestamp_us, bool is_remote);
};

// In the spec both local and remote varieties are of type RTCIceCandidateStats.
// But here we define them as subclasses of |RTCIceCandidateStats| because the
// |kType| need to be different ("RTCStatsType type") in the local/remote case.
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

// https://w3c.github.io/webrtc-stats/#msstats-dict*
// TODO(hbos): Tracking bug crbug.com/660827
class RTC_EXPORT RTCMediaStreamStats final : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCMediaStreamStats(const std::string& id, int64_t timestamp_us);
  RTCMediaStreamStats(std::string&& id, int64_t timestamp_us);
  RTCMediaStreamStats(const RTCMediaStreamStats& other);
  ~RTCMediaStreamStats() override;

  RTCStatsMember<std::string> stream_identifier;
  RTCStatsMember<std::vector<std::string>> track_ids;
};

// https://w3c.github.io/webrtc-stats/#mststats-dict*
// TODO(hbos): Tracking bug crbug.com/659137
class RTC_EXPORT RTCMediaStreamTrackStats final : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCMediaStreamTrackStats(const std::string& id,
                           int64_t timestamp_us,
                           const char* kind);
  RTCMediaStreamTrackStats(std::string&& id,
                           int64_t timestamp_us,
                           const char* kind);
  RTCMediaStreamTrackStats(const RTCMediaStreamTrackStats& other);
  ~RTCMediaStreamTrackStats() override;

  RTCStatsMember<std::string> track_identifier;
  RTCStatsMember<std::string> media_source_id;
  RTCStatsMember<bool> remote_source;
  RTCStatsMember<bool> ended;
  // TODO(hbos): |RTCStatsCollector| does not return stats for detached tracks.
  // crbug.com/659137
  RTCStatsMember<bool> detached;
  // See |RTCMediaStreamTrackKind| for valid values.
  RTCStatsMember<std::string> kind;
  RTCStatsMember<double> jitter_buffer_delay;
  RTCStatsMember<uint64_t> jitter_buffer_emitted_count;
  // Video-only members
  RTCStatsMember<uint32_t> frame_width;
  RTCStatsMember<uint32_t> frame_height;
  // TODO(hbos): Not collected by |RTCStatsCollector|. crbug.com/659137
  RTCStatsMember<double> frames_per_second;
  RTCStatsMember<uint32_t> frames_sent;
  RTCStatsMember<uint32_t> huge_frames_sent;
  RTCStatsMember<uint32_t> frames_received;
  RTCStatsMember<uint32_t> frames_decoded;
  RTCStatsMember<uint32_t> frames_dropped;
  // TODO(hbos): Not collected by |RTCStatsCollector|. crbug.com/659137
  RTCStatsMember<uint32_t> frames_corrupted;
  // TODO(hbos): Not collected by |RTCStatsCollector|. crbug.com/659137
  RTCStatsMember<uint32_t> partial_frames_lost;
  // TODO(hbos): Not collected by |RTCStatsCollector|. crbug.com/659137
  RTCStatsMember<uint32_t> full_frames_lost;
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
  // Non-standard audio-only member
  // TODO(kuddai): Add description to standard. crbug.com/webrtc/10042
  RTCNonStandardStatsMember<uint64_t> jitter_buffer_flushes;
  RTCNonStandardStatsMember<uint64_t> delayed_packet_outage_samples;
  RTCNonStandardStatsMember<double> relative_packet_arrival_delay;
  // Non-standard metric showing target delay of jitter buffer.
  // This value is increased by the target jitter buffer delay every time a
  // sample is emitted by the jitter buffer. The added target is the target
  // delay, in seconds, at the time that the sample was emitted from the jitter
  // buffer. (https://github.com/w3c/webrtc-provisional-stats/pull/20)
  // Currently it is implemented only for audio.
  // TODO(titovartem) implement for video streams when will be requested.
  RTCNonStandardStatsMember<double> jitter_buffer_target_delay;
  // TODO(henrik.lundin): Add description of the interruption metrics at
  // https://github.com/henbos/webrtc-provisional-stats/issues/17
  RTCNonStandardStatsMember<uint32_t> interruption_count;
  RTCNonStandardStatsMember<double> total_interruption_duration;
  // Non-standard video-only members.
  // https://henbos.github.io/webrtc-provisional-stats/#RTCVideoReceiverStats-dict*
  RTCNonStandardStatsMember<uint32_t> freeze_count;
  RTCNonStandardStatsMember<uint32_t> pause_count;
  RTCNonStandardStatsMember<double> total_freezes_duration;
  RTCNonStandardStatsMember<double> total_pauses_duration;
  RTCNonStandardStatsMember<double> total_frames_duration;
  RTCNonStandardStatsMember<double> sum_squared_frame_durations;
};

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
// TODO(hbos): Tracking bug crbug.com/657854
class RTC_EXPORT RTCRTPStreamStats : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCRTPStreamStats(const RTCRTPStreamStats& other);
  ~RTCRTPStreamStats() override;

  RTCStatsMember<uint32_t> ssrc;
  // TODO(hbos): Remote case not supported by |RTCStatsCollector|.
  // crbug.com/657855, 657856
  RTCStatsMember<bool> is_remote;          // = false
  RTCStatsMember<std::string> media_type;  // renamed to kind.
  RTCStatsMember<std::string> kind;
  RTCStatsMember<std::string> track_id;
  RTCStatsMember<std::string> transport_id;
  RTCStatsMember<std::string> codec_id;
  // FIR and PLI counts are only defined for |media_type == "video"|.
  RTCStatsMember<uint32_t> fir_count;
  RTCStatsMember<uint32_t> pli_count;
  // TODO(hbos): NACK count should be collected by |RTCStatsCollector| for both
  // audio and video but is only defined in the "video" case. crbug.com/657856
  RTCStatsMember<uint32_t> nack_count;
  // TODO(hbos): Not collected by |RTCStatsCollector|. crbug.com/657854
  // SLI count is only defined for |media_type == "video"|.
  RTCStatsMember<uint32_t> sli_count;
  RTCStatsMember<uint64_t> qp_sum;

 protected:
  RTCRTPStreamStats(const std::string& id, int64_t timestamp_us);
  RTCRTPStreamStats(std::string&& id, int64_t timestamp_us);
};

// https://w3c.github.io/webrtc-stats/#inboundrtpstats-dict*
// TODO(hbos): Support the remote case |is_remote = true|.
// https://bugs.webrtc.org/7065
class RTC_EXPORT RTCInboundRTPStreamStats final : public RTCRTPStreamStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCInboundRTPStreamStats(const std::string& id, int64_t timestamp_us);
  RTCInboundRTPStreamStats(std::string&& id, int64_t timestamp_us);
  RTCInboundRTPStreamStats(const RTCInboundRTPStreamStats& other);
  ~RTCInboundRTPStreamStats() override;

  RTCStatsMember<uint32_t> packets_received;
  RTCStatsMember<uint64_t> fec_packets_received;
  RTCStatsMember<uint64_t> fec_packets_discarded;
  RTCStatsMember<uint64_t> bytes_received;
  RTCStatsMember<uint64_t> header_bytes_received;
  RTCStatsMember<int32_t> packets_lost;  // Signed per RFC 3550
  RTCStatsMember<double> last_packet_received_timestamp;
  // TODO(hbos): Collect and populate this value for both "audio" and "video",
  // currently not collected for "video". https://bugs.webrtc.org/7065
  RTCStatsMember<double> jitter;
  RTCStatsMember<double> jitter_buffer_delay;
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
  RTCStatsMember<int32_t> frames_received;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7065
  RTCStatsMember<double> round_trip_time;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7065
  RTCStatsMember<uint32_t> packets_discarded;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7065
  RTCStatsMember<uint32_t> packets_repaired;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7065
  RTCStatsMember<uint32_t> burst_packets_lost;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7065
  RTCStatsMember<uint32_t> burst_packets_discarded;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7065
  RTCStatsMember<uint32_t> burst_loss_count;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7065
  RTCStatsMember<uint32_t> burst_discard_count;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7065
  RTCStatsMember<double> burst_loss_rate;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7065
  RTCStatsMember<double> burst_discard_rate;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7065
  RTCStatsMember<double> gap_loss_rate;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7065
  RTCStatsMember<double> gap_discard_rate;
  RTCStatsMember<uint32_t> frame_width;
  RTCStatsMember<uint32_t> frame_height;
  RTCStatsMember<uint32_t> frame_bit_depth;
  RTCStatsMember<double> frames_per_second;
  RTCStatsMember<uint32_t> frames_decoded;
  RTCStatsMember<uint32_t> key_frames_decoded;
  RTCStatsMember<uint32_t> frames_dropped;
  RTCStatsMember<double> total_decode_time;
  RTCStatsMember<double> total_inter_frame_delay;
  RTCStatsMember<double> total_squared_inter_frame_delay;
  // https://henbos.github.io/webrtc-provisional-stats/#dom-rtcinboundrtpstreamstats-contenttype
  RTCStatsMember<std::string> content_type;
  // TODO(asapersson): Currently only populated if audio/video sync is enabled.
  RTCStatsMember<double> estimated_playout_timestamp;
  // TODO(hbos): This is only implemented for video; implement it for audio as
  // well.
  RTCStatsMember<std::string> decoder_implementation;
};

// https://w3c.github.io/webrtc-stats/#outboundrtpstats-dict*
// TODO(hbos): Support the remote case |is_remote = true|.
// https://bugs.webrtc.org/7066
class RTC_EXPORT RTCOutboundRTPStreamStats final : public RTCRTPStreamStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCOutboundRTPStreamStats(const std::string& id, int64_t timestamp_us);
  RTCOutboundRTPStreamStats(std::string&& id, int64_t timestamp_us);
  RTCOutboundRTPStreamStats(const RTCOutboundRTPStreamStats& other);
  ~RTCOutboundRTPStreamStats() override;

  RTCStatsMember<std::string> media_source_id;
  RTCStatsMember<std::string> remote_id;
  RTCStatsMember<std::string> rid;
  RTCStatsMember<uint32_t> packets_sent;
  RTCStatsMember<uint64_t> retransmitted_packets_sent;
  RTCStatsMember<uint64_t> bytes_sent;
  RTCStatsMember<uint64_t> header_bytes_sent;
  RTCStatsMember<uint64_t> retransmitted_bytes_sent;
  // TODO(hbos): Collect and populate this value. https://bugs.webrtc.org/7066
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
  // TODO(https://crbug.com/webrtc/10635): This is only implemented for video;
  // implement it for audio as well.
  RTCStatsMember<double> total_packet_send_delay;
  // Enum type RTCQualityLimitationReason
  // TODO(https://crbug.com/webrtc/10686): Also expose
  // qualityLimitationDurations. Requires RTCStatsMember support for
  // "record<DOMString, double>", see https://crbug.com/webrtc/10685.
  RTCStatsMember<std::string> quality_limitation_reason;
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-qualitylimitationresolutionchanges
  RTCStatsMember<uint32_t> quality_limitation_resolution_changes;
  // https://henbos.github.io/webrtc-provisional-stats/#dom-rtcoutboundrtpstreamstats-contenttype
  RTCStatsMember<std::string> content_type;
  // TODO(hbos): This is only implemented for video; implement it for audio as
  // well.
  RTCStatsMember<std::string> encoder_implementation;
};

// TODO(https://crbug.com/webrtc/10671): Refactor the stats dictionaries to have
// the same hierarchy as in the spec; implement RTCReceivedRtpStreamStats.
// Several metrics are shared between "outbound-rtp", "remote-inbound-rtp",
// "inbound-rtp" and "remote-outbound-rtp". In the spec there is a hierarchy of
// dictionaries that minimizes defining the same metrics in multiple places.
// From JavaScript this hierarchy is not observable and the spec's hierarchy is
// purely editorial. In C++ non-final classes in the hierarchy could be used to
// refer to different stats objects within the hierarchy.
// https://w3c.github.io/webrtc-stats/#remoteinboundrtpstats-dict*
class RTC_EXPORT RTCRemoteInboundRtpStreamStats final : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCRemoteInboundRtpStreamStats(const std::string& id, int64_t timestamp_us);
  RTCRemoteInboundRtpStreamStats(std::string&& id, int64_t timestamp_us);
  RTCRemoteInboundRtpStreamStats(const RTCRemoteInboundRtpStreamStats& other);
  ~RTCRemoteInboundRtpStreamStats() override;

  // In the spec RTCRemoteInboundRtpStreamStats inherits from RTCRtpStreamStats
  // and RTCReceivedRtpStreamStats. The members here are listed based on where
  // they are defined in the spec.
  // RTCRtpStreamStats
  RTCStatsMember<uint32_t> ssrc;
  RTCStatsMember<std::string> kind;
  RTCStatsMember<std::string> transport_id;
  RTCStatsMember<std::string> codec_id;
  // RTCReceivedRtpStreamStats
  RTCStatsMember<int32_t> packets_lost;
  RTCStatsMember<double> jitter;
  // TODO(hbos): The following RTCReceivedRtpStreamStats metrics should also be
  // implemented: packetsReceived, packetsDiscarded, packetsRepaired,
  // burstPacketsLost, burstPacketsDiscarded, burstLossCount, burstDiscardCount,
  // burstLossRate, burstDiscardRate, gapLossRate and gapDiscardRate.
  // RTCRemoteInboundRtpStreamStats
  RTCStatsMember<std::string> local_id;
  RTCStatsMember<double> round_trip_time;
  // TODO(hbos): The following RTCRemoteInboundRtpStreamStats metric should also
  // be implemented: fractionLost.
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
  // TODO(hbos): Implement this metric.
  RTCStatsMember<uint32_t> frames;
  RTCStatsMember<uint32_t> frames_per_second;
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
  // TODO(hbos): Support enum types? "RTCStatsMember<RTCDtlsTransportState>"?
  RTCStatsMember<std::string> dtls_state;
  RTCStatsMember<std::string> selected_candidate_pair_id;
  RTCStatsMember<std::string> local_certificate_id;
  RTCStatsMember<std::string> remote_certificate_id;
  RTCStatsMember<std::string> tls_version;
  RTCStatsMember<std::string> dtls_cipher;
  RTCStatsMember<std::string> srtp_cipher;
  RTCStatsMember<uint32_t> selected_candidate_pair_changes;
};

}  // namespace webrtc

#endif  // API_STATS_RTCSTATS_OBJECTS_H_
