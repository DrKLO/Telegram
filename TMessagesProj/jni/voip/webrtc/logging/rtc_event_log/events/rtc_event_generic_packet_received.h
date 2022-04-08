/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_GENERIC_PACKET_RECEIVED_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_GENERIC_PACKET_RECEIVED_H_

#include <memory>

#include "api/rtc_event_log/rtc_event.h"
#include "api/units/timestamp.h"

namespace webrtc {

class RtcEventGenericPacketReceived final : public RtcEvent {
 public:
  static constexpr Type kType = Type::GenericPacketReceived;

  RtcEventGenericPacketReceived(int64_t packet_number, size_t packet_length);
  ~RtcEventGenericPacketReceived() override;

  std::unique_ptr<RtcEventGenericPacketReceived> Copy() const;

  Type GetType() const override { return kType; }
  bool IsConfigEvent() const override { return false; }

  // An identifier of the packet.
  int64_t packet_number() const { return packet_number_; }

  // Total packet length, including all packetization overheads, but not
  // including ICE/TURN/IP overheads.
  size_t packet_length() const { return packet_length_; }

 private:
  RtcEventGenericPacketReceived(const RtcEventGenericPacketReceived& packet);

  const int64_t packet_number_;
  const size_t packet_length_;
};

struct LoggedGenericPacketReceived {
  LoggedGenericPacketReceived() = default;
  LoggedGenericPacketReceived(Timestamp timestamp,
                              int64_t packet_number,
                              int packet_length)
      : timestamp(timestamp),
        packet_number(packet_number),
        packet_length(packet_length) {}

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp = Timestamp::MinusInfinity();
  int64_t packet_number;
  int packet_length;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_GENERIC_PACKET_RECEIVED_H_
