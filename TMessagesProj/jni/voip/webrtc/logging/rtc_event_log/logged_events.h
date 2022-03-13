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
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/rtp_rtcp/source/rtcp_packet/bye.h"
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

struct LoggedRtpPacket {
  LoggedRtpPacket(Timestamp timestamp,
                  RTPHeader header,
                  size_t header_length,
                  size_t total_length)
      : timestamp(timestamp),
        header(header),
        header_length(header_length),
        total_length(total_length) {}

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp;
  // TODO(terelius): This allocates space for 15 CSRCs even if none are used.
  RTPHeader header;
  size_t header_length;
  size_t total_length;
};

struct LoggedRtpPacketIncoming {
  LoggedRtpPacketIncoming(Timestamp timestamp,
                          RTPHeader header,
                          size_t header_length,
                          size_t total_length)
      : rtp(timestamp, header, header_length, total_length) {}
  int64_t log_time_us() const { return rtp.timestamp.us(); }
  int64_t log_time_ms() const { return rtp.timestamp.ms(); }

  LoggedRtpPacket rtp;
};

struct LoggedRtpPacketOutgoing {
  LoggedRtpPacketOutgoing(Timestamp timestamp,
                          RTPHeader header,
                          size_t header_length,
                          size_t total_length)
      : rtp(timestamp, header, header_length, total_length) {}
  int64_t log_time_us() const { return rtp.timestamp.us(); }
  int64_t log_time_ms() const { return rtp.timestamp.ms(); }

  LoggedRtpPacket rtp;
};

struct LoggedRtcpPacket {
  LoggedRtcpPacket(Timestamp timestamp, const std::vector<uint8_t>& packet);
  LoggedRtcpPacket(Timestamp timestamp, const std::string& packet);
  LoggedRtcpPacket(const LoggedRtcpPacket&);
  ~LoggedRtcpPacket();

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp;
  std::vector<uint8_t> raw_data;
};

struct LoggedRtcpPacketIncoming {
  LoggedRtcpPacketIncoming(Timestamp timestamp,
                           const std::vector<uint8_t>& packet)
      : rtcp(timestamp, packet) {}
  LoggedRtcpPacketIncoming(Timestamp timestamp, const std::string& packet)
      : rtcp(timestamp, packet) {}

  int64_t log_time_us() const { return rtcp.timestamp.us(); }
  int64_t log_time_ms() const { return rtcp.timestamp.ms(); }

  LoggedRtcpPacket rtcp;
};

struct LoggedRtcpPacketOutgoing {
  LoggedRtcpPacketOutgoing(Timestamp timestamp,
                           const std::vector<uint8_t>& packet)
      : rtcp(timestamp, packet) {}
  LoggedRtcpPacketOutgoing(Timestamp timestamp, const std::string& packet)
      : rtcp(timestamp, packet) {}

  int64_t log_time_us() const { return rtcp.timestamp.us(); }
  int64_t log_time_ms() const { return rtcp.timestamp.ms(); }

  LoggedRtcpPacket rtcp;
};

struct LoggedRtcpPacketReceiverReport {
  LoggedRtcpPacketReceiverReport() = default;
  LoggedRtcpPacketReceiverReport(Timestamp timestamp,
                                 const rtcp::ReceiverReport& rr)
      : timestamp(timestamp), rr(rr) {}

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp = Timestamp::MinusInfinity();
  rtcp::ReceiverReport rr;
};

struct LoggedRtcpPacketSenderReport {
  LoggedRtcpPacketSenderReport() = default;
  LoggedRtcpPacketSenderReport(Timestamp timestamp,
                               const rtcp::SenderReport& sr)
      : timestamp(timestamp), sr(sr) {}

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp = Timestamp::MinusInfinity();
  rtcp::SenderReport sr;
};

struct LoggedRtcpPacketExtendedReports {
  LoggedRtcpPacketExtendedReports() = default;

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp = Timestamp::MinusInfinity();
  rtcp::ExtendedReports xr;
};

struct LoggedRtcpPacketRemb {
  LoggedRtcpPacketRemb() = default;
  LoggedRtcpPacketRemb(Timestamp timestamp, const rtcp::Remb& remb)
      : timestamp(timestamp), remb(remb) {}

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp = Timestamp::MinusInfinity();
  rtcp::Remb remb;
};

struct LoggedRtcpPacketNack {
  LoggedRtcpPacketNack() = default;
  LoggedRtcpPacketNack(Timestamp timestamp, const rtcp::Nack& nack)
      : timestamp(timestamp), nack(nack) {}

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp = Timestamp::MinusInfinity();
  rtcp::Nack nack;
};

struct LoggedRtcpPacketFir {
  LoggedRtcpPacketFir() = default;

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp = Timestamp::MinusInfinity();
  rtcp::Fir fir;
};

struct LoggedRtcpPacketPli {
  LoggedRtcpPacketPli() = default;

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp = Timestamp::MinusInfinity();
  rtcp::Pli pli;
};

struct LoggedRtcpPacketTransportFeedback {
  LoggedRtcpPacketTransportFeedback()
      : transport_feedback(/*include_timestamps=*/true, /*include_lost*/ true) {
  }
  LoggedRtcpPacketTransportFeedback(
      Timestamp timestamp,
      const rtcp::TransportFeedback& transport_feedback)
      : timestamp(timestamp), transport_feedback(transport_feedback) {}

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp = Timestamp::MinusInfinity();
  rtcp::TransportFeedback transport_feedback;
};

struct LoggedRtcpPacketLossNotification {
  LoggedRtcpPacketLossNotification() = default;
  LoggedRtcpPacketLossNotification(
      Timestamp timestamp,
      const rtcp::LossNotification& loss_notification)
      : timestamp(timestamp), loss_notification(loss_notification) {}

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp = Timestamp::MinusInfinity();
  rtcp::LossNotification loss_notification;
};

struct LoggedRtcpPacketBye {
  LoggedRtcpPacketBye() = default;

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp = Timestamp::MinusInfinity();
  rtcp::Bye bye;
};

struct LoggedStartEvent {
  explicit LoggedStartEvent(Timestamp timestamp)
      : LoggedStartEvent(timestamp, timestamp) {}

  LoggedStartEvent(Timestamp timestamp, Timestamp utc_start_time)
      : timestamp(timestamp), utc_start_time(utc_start_time) {}

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp utc_time() const { return utc_start_time; }

  Timestamp timestamp;
  Timestamp utc_start_time;
};

struct LoggedStopEvent {
  explicit LoggedStopEvent(Timestamp timestamp) : timestamp(timestamp) {}

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp;
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


}  // namespace webrtc
#endif  // LOGGING_RTC_EVENT_LOG_LOGGED_EVENTS_H_
