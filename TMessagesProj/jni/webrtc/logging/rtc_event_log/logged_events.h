/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef LOGGING_RTC_EVENT_LOG_LOGGED_EVENTS_H_
#define LOGGING_RTC_EVENT_LOG_LOGGED_EVENTS_H_

#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/rtp_headers.h"
#include "api/units/data_rate.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "api/video/video_codec_type.h"
#include "logging/rtc_event_log/events/rtc_event_dtls_transport_state.h"
#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair.h"
#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair_config.h"
#include "logging/rtc_event_log/events/rtc_event_probe_result_failure.h"
#include "logging/rtc_event_log/rtc_stream_config.h"
#include "modules/audio_coding/audio_network_adaptor/include/audio_network_adaptor_config.h"
#include "modules/remote_bitrate_estimator/include/bwe_defines.h"
#include "modules/rtp_rtcp/source/rtcp_packet/extended_reports.h"
#include "modules/rtp_rtcp/source/rtcp_packet/fir.h"
#include "modules/rtp_rtcp/source/rtcp_packet/loss_notification.h"
#include "modules/rtp_rtcp/source/rtcp_packet/nack.h"
#include "modules/rtp_rtcp/source/rtcp_packet/pli.h"
#include "modules/rtp_rtcp/source/rtcp_packet/receiver_report.h"
#include "modules/rtp_rtcp/source/rtcp_packet/remb.h"
#include "modules/rtp_rtcp/source/rtcp_packet/sender_report.h"
#include "modules/rtp_rtcp/source/rtcp_packet/transport_feedback.h"

namespace webrtc {

// The different event types are deliberately POD. Analysis of large logs is
// already resource intensive. The code simplifications that would be possible
// possible by having a base class (containing e.g. the log time) are not
// considered to outweigh the added memory and runtime overhead incurred by
// adding a vptr.
struct LoggedAlrStateEvent {
  LoggedAlrStateEvent() = default;
  LoggedAlrStateEvent(int64_t timestamp_us, bool in_alr)
      : timestamp_us(timestamp_us), in_alr(in_alr) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  bool in_alr;
};

struct LoggedAudioPlayoutEvent {
  LoggedAudioPlayoutEvent() = default;
  LoggedAudioPlayoutEvent(int64_t timestamp_us, uint32_t ssrc)
      : timestamp_us(timestamp_us), ssrc(ssrc) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  uint32_t ssrc;
};

struct LoggedAudioNetworkAdaptationEvent {
  LoggedAudioNetworkAdaptationEvent() = default;
  LoggedAudioNetworkAdaptationEvent(int64_t timestamp_us,
                                    const AudioEncoderRuntimeConfig& config)
      : timestamp_us(timestamp_us), config(config) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  AudioEncoderRuntimeConfig config;
};

struct LoggedBweDelayBasedUpdate {
  LoggedBweDelayBasedUpdate() = default;
  LoggedBweDelayBasedUpdate(int64_t timestamp_us,
                            int32_t bitrate_bps,
                            BandwidthUsage detector_state)
      : timestamp_us(timestamp_us),
        bitrate_bps(bitrate_bps),
        detector_state(detector_state) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  int32_t bitrate_bps;
  BandwidthUsage detector_state;
};

struct LoggedBweLossBasedUpdate {
  LoggedBweLossBasedUpdate() = default;
  LoggedBweLossBasedUpdate(int64_t timestamp_us,
                           int32_t bitrate_bps,
                           uint8_t fraction_lost,
                           int32_t expected_packets)
      : timestamp_us(timestamp_us),
        bitrate_bps(bitrate_bps),
        fraction_lost(fraction_lost),
        expected_packets(expected_packets) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  int32_t bitrate_bps;
  uint8_t fraction_lost;
  int32_t expected_packets;
};

struct LoggedDtlsTransportState {
  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  DtlsTransportState dtls_transport_state;
};

struct LoggedDtlsWritableState {
  LoggedDtlsWritableState() = default;
  explicit LoggedDtlsWritableState(bool writable) : writable(writable) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  bool writable;
};

struct LoggedBweProbeClusterCreatedEvent {
  LoggedBweProbeClusterCreatedEvent() = default;
  LoggedBweProbeClusterCreatedEvent(int64_t timestamp_us,
                                    int32_t id,
                                    int32_t bitrate_bps,
                                    uint32_t min_packets,
                                    uint32_t min_bytes)
      : timestamp_us(timestamp_us),
        id(id),
        bitrate_bps(bitrate_bps),
        min_packets(min_packets),
        min_bytes(min_bytes) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  int32_t id;
  int32_t bitrate_bps;
  uint32_t min_packets;
  uint32_t min_bytes;
};

struct LoggedBweProbeSuccessEvent {
  LoggedBweProbeSuccessEvent() = default;
  LoggedBweProbeSuccessEvent(int64_t timestamp_us,
                             int32_t id,
                             int32_t bitrate_bps)
      : timestamp_us(timestamp_us), id(id), bitrate_bps(bitrate_bps) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  int32_t id;
  int32_t bitrate_bps;
};

struct LoggedBweProbeFailureEvent {
  LoggedBweProbeFailureEvent() = default;
  LoggedBweProbeFailureEvent(int64_t timestamp_us,
                             int32_t id,
                             ProbeFailureReason failure_reason)
      : timestamp_us(timestamp_us), id(id), failure_reason(failure_reason) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  int32_t id;
  ProbeFailureReason failure_reason;
};

struct LoggedFrameDecoded {
  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  int64_t render_time_ms;
  uint32_t ssrc;
  int width;
  int height;
  VideoCodecType codec;
  uint8_t qp;
};

struct LoggedIceCandidatePairConfig {
  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  IceCandidatePairConfigType type;
  uint32_t candidate_pair_id;
  IceCandidateType local_candidate_type;
  IceCandidatePairProtocol local_relay_protocol;
  IceCandidateNetworkType local_network_type;
  IceCandidatePairAddressFamily local_address_family;
  IceCandidateType remote_candidate_type;
  IceCandidatePairAddressFamily remote_address_family;
  IceCandidatePairProtocol candidate_pair_protocol;
};

struct LoggedIceCandidatePairEvent {
  LoggedIceCandidatePairEvent() = default;
  LoggedIceCandidatePairEvent(int64_t timestamp_us,
                              IceCandidatePairEventType type,
                              uint32_t candidate_pair_id,
                              uint32_t transaction_id)
      : timestamp_us(timestamp_us),
        type(type),
        candidate_pair_id(candidate_pair_id),
        transaction_id(transaction_id) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  IceCandidatePairEventType type;
  uint32_t candidate_pair_id;
  uint32_t transaction_id;
};

struct LoggedRouteChangeEvent {
  LoggedRouteChangeEvent() = default;
  LoggedRouteChangeEvent(int64_t timestamp_ms,
                         bool connected,
                         uint32_t overhead)
      : timestamp_ms(timestamp_ms), connected(connected), overhead(overhead) {}

  int64_t log_time_us() const { return timestamp_ms * 1000; }
  int64_t log_time_ms() const { return timestamp_ms; }

  int64_t timestamp_ms;
  bool connected;
  uint32_t overhead;
};

struct LoggedRemoteEstimateEvent {
  LoggedRemoteEstimateEvent() = default;

  int64_t log_time_us() const { return timestamp_ms * 1000; }
  int64_t log_time_ms() const { return timestamp_ms; }

  int64_t timestamp_ms;
  absl::optional<DataRate> link_capacity_lower;
  absl::optional<DataRate> link_capacity_upper;
};

struct LoggedRtpPacket {
  LoggedRtpPacket(int64_t timestamp_us,
                  RTPHeader header,
                  size_t header_length,
                  size_t total_length)
      : timestamp_us(timestamp_us),
        header(header),
        header_length(header_length),
        total_length(total_length) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  // TODO(terelius): This allocates space for 15 CSRCs even if none are used.
  RTPHeader header;
  size_t header_length;
  size_t total_length;
};

struct LoggedRtpPacketIncoming {
  LoggedRtpPacketIncoming(int64_t timestamp_us,
                          RTPHeader header,
                          size_t header_length,
                          size_t total_length)
      : rtp(timestamp_us, header, header_length, total_length) {}
  int64_t log_time_us() const { return rtp.timestamp_us; }
  int64_t log_time_ms() const { return rtp.timestamp_us / 1000; }

  LoggedRtpPacket rtp;
};

struct LoggedRtpPacketOutgoing {
  LoggedRtpPacketOutgoing(int64_t timestamp_us,
                          RTPHeader header,
                          size_t header_length,
                          size_t total_length)
      : rtp(timestamp_us, header, header_length, total_length) {}
  int64_t log_time_us() const { return rtp.timestamp_us; }
  int64_t log_time_ms() const { return rtp.timestamp_us / 1000; }

  LoggedRtpPacket rtp;
};

struct LoggedRtcpPacket {
  LoggedRtcpPacket(int64_t timestamp_us,
                   const uint8_t* packet,
                   size_t total_length);
  LoggedRtcpPacket(int64_t timestamp_us, const std::string& packet);
  LoggedRtcpPacket(const LoggedRtcpPacket&);
  ~LoggedRtcpPacket();

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  std::vector<uint8_t> raw_data;
};

struct LoggedRtcpPacketIncoming {
  LoggedRtcpPacketIncoming(int64_t timestamp_us,
                           const uint8_t* packet,
                           size_t total_length)
      : rtcp(timestamp_us, packet, total_length) {}
  LoggedRtcpPacketIncoming(uint64_t timestamp_us, const std::string& packet)
      : rtcp(timestamp_us, packet) {}

  int64_t log_time_us() const { return rtcp.timestamp_us; }
  int64_t log_time_ms() const { return rtcp.timestamp_us / 1000; }

  LoggedRtcpPacket rtcp;
};

struct LoggedRtcpPacketOutgoing {
  LoggedRtcpPacketOutgoing(int64_t timestamp_us,
                           const uint8_t* packet,
                           size_t total_length)
      : rtcp(timestamp_us, packet, total_length) {}
  LoggedRtcpPacketOutgoing(uint64_t timestamp_us, const std::string& packet)
      : rtcp(timestamp_us, packet) {}

  int64_t log_time_us() const { return rtcp.timestamp_us; }
  int64_t log_time_ms() const { return rtcp.timestamp_us / 1000; }

  LoggedRtcpPacket rtcp;
};

struct LoggedRtcpPacketReceiverReport {
  LoggedRtcpPacketReceiverReport() = default;
  LoggedRtcpPacketReceiverReport(int64_t timestamp_us,
                                 const rtcp::ReceiverReport& rr)
      : timestamp_us(timestamp_us), rr(rr) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  rtcp::ReceiverReport rr;
};

struct LoggedRtcpPacketSenderReport {
  LoggedRtcpPacketSenderReport() = default;
  LoggedRtcpPacketSenderReport(int64_t timestamp_us,
                               const rtcp::SenderReport& sr)
      : timestamp_us(timestamp_us), sr(sr) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  rtcp::SenderReport sr;
};

struct LoggedRtcpPacketExtendedReports {
  LoggedRtcpPacketExtendedReports() = default;

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  rtcp::ExtendedReports xr;
};

struct LoggedRtcpPacketRemb {
  LoggedRtcpPacketRemb() = default;
  LoggedRtcpPacketRemb(int64_t timestamp_us, const rtcp::Remb& remb)
      : timestamp_us(timestamp_us), remb(remb) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  rtcp::Remb remb;
};

struct LoggedRtcpPacketNack {
  LoggedRtcpPacketNack() = default;
  LoggedRtcpPacketNack(int64_t timestamp_us, const rtcp::Nack& nack)
      : timestamp_us(timestamp_us), nack(nack) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  rtcp::Nack nack;
};

struct LoggedRtcpPacketFir {
  LoggedRtcpPacketFir() = default;

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  rtcp::Fir fir;
};

struct LoggedRtcpPacketPli {
  LoggedRtcpPacketPli() = default;

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  rtcp::Pli pli;
};

struct LoggedRtcpPacketTransportFeedback {
  LoggedRtcpPacketTransportFeedback()
      : transport_feedback(/*include_timestamps=*/true, /*include_lost*/ true) {
  }
  LoggedRtcpPacketTransportFeedback(
      int64_t timestamp_us,
      const rtcp::TransportFeedback& transport_feedback)
      : timestamp_us(timestamp_us), transport_feedback(transport_feedback) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  rtcp::TransportFeedback transport_feedback;
};

struct LoggedRtcpPacketLossNotification {
  LoggedRtcpPacketLossNotification() = default;
  LoggedRtcpPacketLossNotification(
      int64_t timestamp_us,
      const rtcp::LossNotification& loss_notification)
      : timestamp_us(timestamp_us), loss_notification(loss_notification) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  rtcp::LossNotification loss_notification;
};

struct LoggedStartEvent {
  explicit LoggedStartEvent(int64_t timestamp_us)
      : LoggedStartEvent(timestamp_us, timestamp_us / 1000) {}

  LoggedStartEvent(int64_t timestamp_us, int64_t utc_start_time_ms)
      : timestamp_us(timestamp_us), utc_start_time_ms(utc_start_time_ms) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  int64_t utc_start_time_ms;
};

struct LoggedStopEvent {
  explicit LoggedStopEvent(int64_t timestamp_us) : timestamp_us(timestamp_us) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
};

struct LoggedAudioRecvConfig {
  LoggedAudioRecvConfig() = default;
  LoggedAudioRecvConfig(int64_t timestamp_us, const rtclog::StreamConfig config)
      : timestamp_us(timestamp_us), config(config) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  rtclog::StreamConfig config;
};

struct LoggedAudioSendConfig {
  LoggedAudioSendConfig() = default;
  LoggedAudioSendConfig(int64_t timestamp_us, const rtclog::StreamConfig config)
      : timestamp_us(timestamp_us), config(config) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  rtclog::StreamConfig config;
};

struct LoggedVideoRecvConfig {
  LoggedVideoRecvConfig() = default;
  LoggedVideoRecvConfig(int64_t timestamp_us, const rtclog::StreamConfig config)
      : timestamp_us(timestamp_us), config(config) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  rtclog::StreamConfig config;
};

struct LoggedVideoSendConfig {
  LoggedVideoSendConfig() = default;
  LoggedVideoSendConfig(int64_t timestamp_us, const rtclog::StreamConfig config)
      : timestamp_us(timestamp_us), config(config) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  rtclog::StreamConfig config;
};

struct InferredRouteChangeEvent {
  int64_t log_time_ms() const { return log_time.ms(); }
  int64_t log_time_us() const { return log_time.us(); }
  uint32_t route_id;
  Timestamp log_time = Timestamp::MinusInfinity();
  uint16_t send_overhead;
  uint16_t return_overhead;
};

enum class LoggedMediaType : uint8_t { kUnknown, kAudio, kVideo };

struct LoggedPacketInfo {
  LoggedPacketInfo(const LoggedRtpPacket& rtp,
                   LoggedMediaType media_type,
                   bool rtx,
                   Timestamp capture_time);
  LoggedPacketInfo(const LoggedPacketInfo&);
  ~LoggedPacketInfo();
  int64_t log_time_ms() const { return log_packet_time.ms(); }
  int64_t log_time_us() const { return log_packet_time.us(); }
  uint32_t ssrc;
  uint16_t stream_seq_no;
  uint16_t size;
  uint16_t payload_size;
  uint16_t padding_size;
  uint16_t overhead = 0;
  uint8_t payload_type;
  LoggedMediaType media_type = LoggedMediaType::kUnknown;
  bool rtx = false;
  bool marker_bit = false;
  bool has_transport_seq_no = false;
  bool last_in_feedback = false;
  uint16_t transport_seq_no = 0;
  // The RTP header timestamp unwrapped and converted from tick count to seconds
  // based timestamp.
  Timestamp capture_time;
  // The time the packet was logged. This is the receive time for incoming
  // packets and send time for outgoing.
  Timestamp log_packet_time;
  // Send time as reported by abs-send-time extension, For outgoing packets this
  // corresponds to log_packet_time, but might be measured using another clock.
  Timestamp reported_send_time;
  // The receive time that was reported in feedback. For incoming packets this
  // corresponds to log_packet_time, but might be measured using another clock.
  // PlusInfinity indicates that the packet was lost.
  Timestamp reported_recv_time = Timestamp::MinusInfinity();
  // The time feedback message was logged. This is the feedback send time for
  // incoming packets and feedback receive time for outgoing.
  // PlusInfinity indicates that feedback was expected but not received.
  Timestamp log_feedback_time = Timestamp::MinusInfinity();
  // The delay betweeen receiving an RTP packet and sending feedback for
  // incoming packets. For outgoing packets we don't know the feedback send
  // time, and this is instead calculated as the difference in reported receive
  // time between this packet and the last packet in the same feedback message.
  TimeDelta feedback_hold_duration = TimeDelta::MinusInfinity();
};

enum class LoggedIceEventType {
  kAdded,
  kUpdated,
  kDestroyed,
  kSelected,
  kCheckSent,
  kCheckReceived,
  kCheckResponseSent,
  kCheckResponseReceived,
};

struct LoggedIceEvent {
  uint32_t candidate_pair_id;
  Timestamp log_time;
  LoggedIceEventType event_type;
};

struct LoggedGenericPacketSent {
  LoggedGenericPacketSent() = default;
  LoggedGenericPacketSent(int64_t timestamp_us,
                          int64_t packet_number,
                          size_t overhead_length,
                          size_t payload_length,
                          size_t padding_length)
      : timestamp_us(timestamp_us),
        packet_number(packet_number),
        overhead_length(overhead_length),
        payload_length(payload_length),
        padding_length(padding_length) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  size_t packet_length() const {
    return payload_length + padding_length + overhead_length;
  }
  int64_t timestamp_us;
  int64_t packet_number;
  size_t overhead_length;
  size_t payload_length;
  size_t padding_length;
};

struct LoggedGenericPacketReceived {
  LoggedGenericPacketReceived() = default;
  LoggedGenericPacketReceived(int64_t timestamp_us,
                              int64_t packet_number,
                              int packet_length)
      : timestamp_us(timestamp_us),
        packet_number(packet_number),
        packet_length(packet_length) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  int64_t packet_number;
  int packet_length;
};

struct LoggedGenericAckReceived {
  LoggedGenericAckReceived() = default;
  LoggedGenericAckReceived(int64_t timestamp_us,
                           int64_t packet_number,
                           int64_t acked_packet_number,
                           absl::optional<int64_t> receive_acked_packet_time_ms)
      : timestamp_us(timestamp_us),
        packet_number(packet_number),
        acked_packet_number(acked_packet_number),
        receive_acked_packet_time_ms(receive_acked_packet_time_ms) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  int64_t packet_number;
  int64_t acked_packet_number;
  absl::optional<int64_t> receive_acked_packet_time_ms;
};

}  // namespace webrtc
#endif  // LOGGING_RTC_EVENT_LOG_LOGGED_EVENTS_H_
